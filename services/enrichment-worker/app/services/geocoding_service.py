import googlemaps
import logging
from app.config import settings
from app.models.enrichment import GeocodingResult
from typing import Any

logger = logging.getLogger(__name__)


class GeocodingService:
    def __init__(self):
        self.api_key = settings.google_maps_api_key
        self.client = None
        if self.api_key:
            try:
                self.client = googlemaps.Client(key=self.api_key)
            except Exception as e:
                logger.warning(
                    f"Failed to initialize Google Maps client (invalid or placeholder API key): {e}. Using mock geocoding.")

    def _get_client(self) -> googlemaps.Client:
        """
        Returns or initializes the Google Maps client.
        """
        if not self.client:
            api_key = self.api_key or settings.google_maps_api_key
            if api_key:
                try:
                    self.client = googlemaps.Client(key=api_key)
                except Exception as e:
                    logger.error(f"Failed to initialize Google Maps client: {e}")
                    raise RuntimeError(f"Failed to initialize Google Maps client: {e}")
            else:
                raise RuntimeError(
                    "Google Maps API key is not configured. Please set GOOGLE_MAPS_API_KEY in your environment (.env)."
                )
        return self.client

    async def geocode_raw(self, query: str) -> Any:
        """
        Geocode a query (e.g. Temple name + city) and return raw Google Maps API response directly.
        Does not fall back to mock data so real API connectivity and errors can be validated.
        """
        logger.info(f"Geocoding raw query against Google Maps API: {query}")
        client = self._get_client()
        results = client.geocode(query)
        return results

    async def geocode(self, query: str) -> GeocodingResult:
        """
        Geocode a query (e.g. Temple name + city) into address and lat/lng.
        """
        logger.info(f"Geocoding query: {query}")
        if self.client:
            try:
                # Run synchronous googlemaps client in async context or directly
                results = self.client.geocode(query)
                if results:
                    top_result = results[0]
                    formatted_address = top_result.get("formatted_address", query)
                    location = top_result.get("geometry", {}).get("location", {})
                    lat = location.get("lat", 35.6762)
                    lng = location.get("lng", 139.6503)
                    return GeocodingResult(address=formatted_address, latitude=lat, longitude=lng)
            except Exception as e:
                logger.error(f"Google Maps API geocoding failed: {e}. Falling back to mock data.")

        # Fallback / Mock geocoding for development or when API key is absent
        # Default coordinates around Tokyo
        return GeocodingResult(
            address=f"Japan, Tokyo, {query}",
            latitude=35.6762,
            longitude=139.6503
        )
