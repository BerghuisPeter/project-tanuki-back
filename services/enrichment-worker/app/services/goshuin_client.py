import asyncio
import httpx
import logging
from app.config import settings
from app.models.enrichment import EnrichmentUpdate
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)


class GoshuinClient:
    def __init__(self):
        self.base_url = settings.goshuin_service_url
        self._credentials = None

    def _resource_url(self, resource_type: str, resource_id: str) -> str:
        # TEMPLE -> /internal/v1/temples/{id}, GOSHUIN -> /internal/v1/goshuins/{id}
        return f"{self.base_url}/internal/v1/{resource_type.lower()}s/{resource_id}"

    def _fetch_id_token(self) -> str:
        """
        Google-signed OIDC identity token of this service's account, for the goshuin-service URL as audience.
        Blocking (metadata server call), so callers run it in a thread. The token is reused until it expires.
        """
        if self._credentials is None:
            self._credentials = id_token.fetch_id_token_credentials(self.base_url.rstrip("/"))
        if not self._credentials.valid:
            self._credentials.refresh(google_requests.Request())
        return self._credentials.token

    async def _auth_headers(self) -> Dict[str, str]:
        # Off locally: goshuin-service only checks the token when app.internal-auth.enabled is true.
        if not settings.goshuin_auth_enabled:
            return {}
        token = await asyncio.to_thread(self._fetch_id_token)
        return {"Authorization": f"Bearer {token}"}

    async def _send(self, method: str, url: str, json: Optional[Dict[str, Any]] = None) -> httpx.Response:
        headers = await self._auth_headers()
        async with httpx.AsyncClient(timeout=10.0) as client:
            return await client.request(method, url, headers=headers, json=json)

    async def get_resource(self, resource_type: str, resource_id: str) -> Optional[Dict[str, Any]]:
        """
        Fetch a temple or goshuin (with its i18n variants and enrichment metadata) from goshuin-service.
        """
        url = self._resource_url(resource_type, resource_id)
        logger.info(f"Fetching resource from {url}")
        try:
            response = await self._send("GET", url)
            if response.status_code == 200:
                return response.json()
            logger.warning(f"Failed to fetch resource {resource_id}: status {response.status_code}")
        except Exception as e:
            logger.error(f"Error communicating with goshuin-service: {e}")
        return None

    async def send_enrichment_callback(self, resource_type: str, resource_id: str, update: EnrichmentUpdate) -> bool:
        """
        Send progress or results back to goshuin-service via the internal enrichment PATCH endpoint.
        """
        url = f"{self._resource_url(resource_type, resource_id)}/enrichment"
        logger.info(f"Sending enrichment callback to {url}")
        try:
            response = await self._send("PATCH", url, json=update.model_dump(exclude_none=True))
            if response.status_code in (200, 204):
                logger.info(f"Enrichment callback successful for {resource_id}")
                return True
            logger.warning(f"Enrichment callback returned status {response.status_code}: {response.text}")
        except Exception as e:
            logger.error(f"Error sending enrichment callback to goshuin-service: {e}")
        return False
