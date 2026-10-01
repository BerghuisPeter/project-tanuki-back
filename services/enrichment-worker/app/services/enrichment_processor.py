import copy
import logging
from app.models.enrichment import EnrichmentRequest, EnrichmentUpdate
from app.services.ai_service import AIService
from app.services.geocoding_service import GeocodingService
from app.services.goshuin_client import GoshuinClient
from app.services.translation_service import TranslationService
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)

# i18n fields the worker may fill in per resource type
TRANSLATABLE_FIELDS = {
    "TEMPLE": ("name", "description"),
    "GOSHUIN": ("label", "description"),
}


class EnrichmentProcessor:
    def __init__(self):
        self.geocoding_service = GeocodingService()
        self.translation_service = TranslationService()
        self.ai_service = AIService()
        self.goshuin_client = GoshuinClient()

    async def process(self, request: EnrichmentRequest) -> EnrichmentUpdate:
        """
        Orchestrates the enrichment workflow for a temple or goshuin (see ENRICHMENT.md, section 5):
        1. Read the resource and mark it PROCESSING
        2. Geocode the temple if it has no coordinates (a goshuin writes the result to its temple)
        3. Generate a description if no locale has one
        4. Translate the missing locale variants
        5. Write the results back with status COMPLETE (or FAILED on error)
        """
        resource_type = (request.resourceType or "GOSHUIN").upper()
        resource_id = request.resourceId
        if resource_type not in TRANSLATABLE_FIELDS:
            raise ValueError(f"Unsupported resource type: {resource_type}")
        logger.info(f"Starting enrichment process for {resource_type} ID: {resource_id}")

        try:
            resource = await self.goshuin_client.get_resource(resource_type, resource_id)
            if resource is None:
                raise RuntimeError(f"{resource_type} {resource_id} could not be read from goshuin-service")

            await self._send(resource_type, resource_id,
                             EnrichmentUpdate(jobId=request.jobId, status="PROCESSING"))

            original_locale = resource.get("originalLocale") or "ja"
            temple = resource if resource_type == "TEMPLE" else (resource.get("temple") or {})
            # Work on a copy: generated text is added to it so it can also be used as a translation source
            translations = copy.deepcopy(resource.get("translations") or {})
            written: Dict[str, Dict[str, str]] = {}

            geo_update = await self._geocode_temple(temple, original_locale)

            description = await self._generate_description(resource_type, resource, temple, original_locale)
            if description:
                translations.setdefault(original_locale, {})["description"] = description
                written.setdefault(original_locale, {})["description"] = description

            for locale, fields in (await self.translation_service.translate_missing(
                    TRANSLATABLE_FIELDS[resource_type], translations, original_locale)).items():
                written.setdefault(locale, {}).update(fields)

            update = EnrichmentUpdate(jobId=request.jobId, status="COMPLETE")
            if geo_update and resource_type == "TEMPLE":
                update.latitude = geo_update.latitude
                update.longitude = geo_update.longitude
                for locale, fields in geo_update.translations.items():
                    written.setdefault(locale, {}).update(fields)
            elif geo_update:
                # Geocoding belongs to the temple. No status: the temple's own lifecycle is not touched.
                await self._send("TEMPLE", temple["id"], geo_update)
            update.translations = written or None

            await self._send(resource_type, resource_id, update)
            logger.info(f"Successfully completed enrichment for {resource_id}")
            return update

        except Exception as e:
            error_msg = str(e)
            logger.error(f"Enrichment failed for {resource_id}: {error_msg}")
            # Best effort: if goshuin-service is unreachable this fails too, and the sweep picks the record up
            await self.goshuin_client.send_enrichment_callback(
                resource_type, resource_id,
                EnrichmentUpdate(jobId=request.jobId, status="FAILED", error=error_msg))
            raise

    async def _geocode_temple(self, temple: Dict[str, Any], original_locale: str) -> Optional[EnrichmentUpdate]:
        """
        Returns a data-only update with coordinates and address, or None if the temple already has coordinates.
        """
        if temple.get("latitude") is not None and temple.get("longitude") is not None:
            return None

        row = self._row(temple.get("translations"), original_locale)
        name = row.get("name")
        if not name:
            raise ValueError("Temple has no name to geocode")
        geo = await self.geocoding_service.geocode(f"{name} {row.get('city') or ''}".strip())
        return EnrichmentUpdate(
            latitude=geo.latitude,
            longitude=geo.longitude,
            translations={original_locale: {"address": geo.address}},
        )

    async def _generate_description(self, resource_type: str, resource: Dict[str, Any], temple: Dict[str, Any],
                                    original_locale: str) -> Optional[str]:
        """
        Returns a generated description in the original locale, or None if any locale already has one.
        """
        if any((row or {}).get("description") for row in (resource.get("translations") or {}).values()):
            return None

        temple_name = self._row(temple.get("translations"), original_locale).get("name") or ""
        if resource_type == "TEMPLE":
            return await self.ai_service.generate_description(
                temple_name, category="Temple", context=temple.get("affiliationType") or "", locale=original_locale)

        label = self._row(resource.get("translations"), original_locale).get("label") or ""
        return await self.ai_service.generate_description(
            label or temple_name, category="Goshuin", context=f"Goshuin from the temple {temple_name}",
            locale=original_locale)

    @staticmethod
    def _row(translations: Optional[Dict[str, Dict[str, Any]]], original_locale: str) -> Dict[str, Any]:
        """
        The original locale's row, or any other row if it has none.
        """
        translations = translations or {}
        return translations.get(original_locale) or next(iter(translations.values()), {})

    async def _send(self, resource_type: str, resource_id: str, update: EnrichmentUpdate) -> None:
        if not await self.goshuin_client.send_enrichment_callback(resource_type, resource_id, update):
            raise RuntimeError(f"Enrichment callback for {resource_type} {resource_id} was not accepted")
