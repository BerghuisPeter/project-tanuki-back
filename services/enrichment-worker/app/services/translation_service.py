import logging
from app.config import settings
from typing import Dict

logger = logging.getLogger(__name__)


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

    async def translate_entity(self, name: str, description: str, source_locale: str = "ja") -> Dict[
        str, Dict[str, str]]:
        """
        Translate entity name and description into supported locales (e.g. 'en').
        """
        translations = {}
        # Target locales to support: 'en' (if source is 'ja')
        target_locales = ["en"] if source_locale == "ja" else ["ja"]

        for locale in target_locales:
            translated_name = await self.translate_text(name, locale, source_locale)
            translated_desc = await self.translate_text(description, locale, source_locale) if description else ""
            translations[locale] = {
                "name": translated_name,
                "description": translated_desc
            }

        return translations
