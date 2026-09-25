import logging
from app.config import settings

logger = logging.getLogger(__name__)

try:
    from google import genai

    GENAI_AVAILABLE = True
except ImportError:
    GENAI_AVAILABLE = False


class AIService:
    def __init__(self):
        self.api_key = settings.google_gemini_api_key
        self.client = None
        if self.api_key and GENAI_AVAILABLE:
            try:
                self.client = genai.Client(api_key=self.api_key)
            except Exception as e:
                logger.error(f"Failed to initialize Gemini client: {e}")

    async def generate_description(self, name: str, category: str = "Temple", context: str = "") -> str:
        """
        Generate a rich AI description for a temple or goshuin using Gemini.
        """
        logger.info(f"Generating AI description for {category}: {name}")

        prompt = (
            f"Write a comprehensive, culturally rich, and engaging description in Japanese for the {category} named '{name}'. "
            f"Additional context: {context}. "
            "Describe its history, architectural significance, and spiritual atmosphere in 2-3 paragraphs."
        )

        if self.client:
            try:
                response = self.client.models.generate_content(
                    model="gemini-1.5-flash",
                    contents=prompt,
                )
                if response and response.text:
                    return response.text.strip()
            except Exception as e:
                logger.error(f"Gemini API generation failed: {e}. Falling back to default description.")

        # Fallback / Mock description generation
        return f"{name} is a historic {category.lower()} known for its serene grounds, beautiful traditional architecture, and spiritual heritage."
