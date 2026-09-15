import googlemaps
import logging
from app.config import settings
from app.models.enrichment import GeocodingResult

logger = logging.getLogger(__name__)


class GeocodingService:
    def __init__(self):
        self.api_key = settings.google_maps_api_key
        self.client = googlemaps.Client(key=self.api_key) if self.api_key else None

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
