import logging
from app.config import settings
from typing import Dict, Optional, Tuple

logger = logging.getLogger(__name__)

SUPPORTED_LOCALES = ("ja", "en")


class TranslationService:
    def __init__(self):
        self.project_id = settings.google_cloud_project

    async def translate_text(self, text: str, target_locale: str, source_locale: str = "ja") -> str:
        """
        Translate text from source_locale to target_locale.
        """
        if not text:
            return ""

        logger.info(f"Translating text from {source_locale} to {target_locale}: {text[:30]}...")

        try:
            if settings.google_cloud_project or settings.google_gemini_api_key:
                # Attempt using Google Cloud Translation if available or mock
                pass
        except Exception as e:
            logger.error(f"Translation API error: {e}. Using fallback translation.")

        # Fallback / Mock translation
        if target_locale == "en":
            return f"[En] {text}"
        return text

    async def translate_missing(
            self,
            fields: Tuple[str, ...],
            existing: Dict[str, Dict[str, Optional[str]]],
            original_locale: str,
    ) -> Dict[str, Dict[str, str]]:
        """
        Translate the given fields into every supported locale where they are still empty.

        `existing` is the locale -> {field: value} map read from goshuin-service. Each field is translated from the
        original locale if it has a value there, otherwise from any other locale that does. Fields that are already
        populated, or that have no source text anywhere, are left out of the result.
        """
        result: Dict[str, Dict[str, str]] = {}
        for locale in SUPPORTED_LOCALES:
            row = existing.get(locale) or {}
            for field in fields:
                if row.get(field):
                    continue
                source_locale = next(
                    (candidate for candidate in (original_locale, *existing)
                     if (existing.get(candidate) or {}).get(field)),
                    None,
                )
                if source_locale is None or source_locale == locale:
                    continue
                translated = await self.translate_text(existing[source_locale][field], locale, source_locale)
                result.setdefault(locale, {})[field] = translated
        return result
