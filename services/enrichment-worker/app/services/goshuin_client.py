import httpx
import logging
from app.config import settings
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)


class GoshuinClient:
    def __init__(self):
        self.base_url = settings.goshuin_service_url

    async def get_resource(self, resource_type: str, resource_Id: str) -> Optional[Dict[str, Any]]:
        """
        Fetch resource details (e.g. Temple or Goshuin) from goshuin-service.
        """
        url = f"{self.base_url}/internal/v1/{resource_type.lower()}s/{resource_Id}"
        logger.info(f"Fetching resource from {url}")
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url)
                if response.status_code == 200:
                    return response.json()
                logger.warning(f"Failed to fetch resource {resource_Id}: status {response.status_code}")
        except Exception as e:
            logger.error(f"Error communicating with goshuin-service: {e}")
        return None

    async def send_enrichment_callback(self, resource_type: str, resource_id: str, update_data: Dict[str, Any]) -> bool:
        """
        Send enrichment results back to goshuin-service via internal API callback.
        """
        url = f"{self.base_url}/internal/v1/{resource_type.lower()}s/{resource_id}/enrichment"
        logger.info(f"Sending enrichment callback to {url}")
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.patch(url, json=update_data)
                if response.status_code in (200, 204):
                    logger.info(f"Enrichment callback successful for {resource_id}")
                    return True
                logger.warning(f"Enrichment callback returned status {response.status_code}: {response.text}")
        except Exception as e:
            logger.error(f"Error sending enrichment callback to goshuin-service: {e}")
        return False
