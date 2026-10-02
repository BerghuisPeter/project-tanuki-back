import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    environment: str = os.getenv("ENVIRONMENT", "dev")
    port: int = int(os.getenv("PORT", "8085"))
    goshuin_service_url: str = os.getenv("GOSHUIN_SERVICE_URL", "http://localhost:8083")
    # Send a Google OIDC identity token to goshuin-service's /internal/**. Off by default so local runs need no token.
    goshuin_auth_enabled: bool = os.getenv("GOSHUIN_AUTH_ENABLED", "false").lower() == "true"
    google_maps_api_key: str = os.getenv("GOOGLE_MAPS_API_KEY", "")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_GEMINI_API_KEY", "")
    google_gemini_api_key: str = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_GEMINI_API_KEY", "")
    gcp_project_id: str = os.getenv("GCP_PROJECT_ID") or os.getenv("GOOGLE_CLOUD_PROJECT", "tanuki-dev")
    google_cloud_project: str = os.getenv("GCP_PROJECT_ID") or os.getenv("GOOGLE_CLOUD_PROJECT", "tanuki-dev")
    log_level: str = os.getenv("LOG_LEVEL", "INFO")


settings = Settings()
