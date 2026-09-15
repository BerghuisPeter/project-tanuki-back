import logging
from app.models.enrichment import EnrichmentRequest, EnrichmentUpdate
from app.services.ai_service import AIService
from app.services.geocoding_service import GeocodingService
from app.services.goshuin_client import GoshuinClient
from app.services.translation_service import TranslationService
from typing import Dict, Any

logger = logging.getLogger(__name__)


class EnrichmentProcessor:
    def __init__(self):
        self.geocoding_service = GeocodingService()
        self.translation_service = TranslationService()
        self.ai_service = AIService()
        self.goshuin_client = GoshuinClient()

    async def process(self, request: EnrichmentRequest) -> EnrichmentUpdate:
        """
        Orchestrates the complete enrichment workflow for a resource:
        1. Fetch resource details (name, city, existing description)
        2. Geocode to get address, lat, lng
        3. Generate AI description if missing
        4. Translate name and description into target locales
        5. Send callback update to goshuin-service
        """
        logger.info(f"Starting enrichment process for {request.resourceType} ID: {request.resourceId}")

        # Step 1: Fetch resource details from goshuin-service
        resource_data = await self.goshuin_client.get_resource(request.resourceType, request.resourceId)

        # Extract basic info or use defaults if service unavailable/mocking
        name = "Senso-ji"
        city = "Tokyo"
        existing_desc = ""

        if resource_data:
            name = resource_data.get("name", name)
            city = resource_data.get("city", city)
            existing_desc = resource_data.get("description", existing_desc)

        try:
            # Step 2: Geocoding
            query = f"{name} {city}"
            geo_result = await self.geocoding_service.geocode(query)

            # Step 3: AI Description Generation
            description = existing_desc
            if not description:
                description = await self.ai_service.generate_description(name, category=request.resourceType)

            # Step 4: Translation
            translations = await self.translation_service.translate_entity(
                name=name,
                description=description,
                source_locale=request.originalLocale
            )

            # Step 5: Formulate update
            update_data = EnrichmentUpdate(
                status="COMPLETE",
                address=geo_result.address,
                latitude=geo_result.latitude,
                longitude=geo_result.longitude,
                description=description,
                translations=translations
            )

            # Step 6: Callback to Goshuin Service
            await self.goshuin_client.send_enrichment_callback(
                request.resourceType,
                request.resourceId,
                update_data.model_dump()
            )

            logger.info(f"Successfully completed enrichment for {request.resourceId}")
            return update_data

        except Exception as e:
            error_msg = str(e)
            logger.error(f"Enrichment failed for {request.resourceId}: {error_msg}")
            failed_update = EnrichmentUpdate(
                status="FAILED",
                error=error_msg
            )
            # Notify goshuin-service of failure
            await self.goshuin_client.send_enrichment_callback(
                request.resourceType,
                request.resourceId,
                failed_update.model_dump()
            )
            raise e
