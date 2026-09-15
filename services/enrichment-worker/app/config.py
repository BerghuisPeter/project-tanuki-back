import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    goshuin_service_url: str = os.getenv("GOSHUIN_SERVICE_URL", "http://localhost:8083")
    google_maps_api_key: str = os.getenv("GOOGLE_MAPS_API_KEY", "")
    google_gemini_api_key: str = os.getenv("GOOGLE_GEMINI_API_KEY", "")
    google_cloud_project: str = os.getenv("GOOGLE_CLOUD_PROJECT", "")
    log_level: str = os.getenv("LOG_LEVEL", "INFO")

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
