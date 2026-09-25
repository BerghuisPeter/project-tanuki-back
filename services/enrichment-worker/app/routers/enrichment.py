import base64
import json
import logging
from app.models.enrichment import PubSubEnvelope, EnrichmentRequest
from app.services.enrichment_processor import EnrichmentProcessor
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field
from typing import Optional

logger = logging.getLogger(__name__)

router = APIRouter()
processor = EnrichmentProcessor()


class MapsTestRequest(BaseModel):
    name: str = Field(..., description="Name of the place or temple (required)")
    city: Optional[str] = Field(None, description="Optional city name")


@router.get("/")
async def root():
    return {"message": "Hello World from Goshuin Enrichment Worker"}


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.get("/maps/test", tags=["Maps"])
@router.get("/test/maps", tags=["Maps"])
@router.get("/test-maps", include_in_schema=False)
@router.get("/maps", include_in_schema=False)
@router.get("/geocode", include_in_schema=False)
@router.get("/test/geocode", include_in_schema=False)
async def test_maps_api(
        name: str = Query(..., min_length=1, description="Name of the temple or place to geocode"),
        city: Optional[str] = Query(None, description="Optional city of the temple or place")
):
    """
    Test endpoint for Google Maps API geocoding.
    Accepts required 'name' and optional 'city' query parameters and queries the real Google Maps service.
    """
    if not name or not name.strip():
        raise HTTPException(status_code=400, detail="The 'name' parameter is required.")
    query = f"{name.strip()} {city.strip()}" if city and city.strip() else name.strip()
    try:
        return await processor.geocoding_service.geocode_raw(query)
    except Exception as e:
        logger.error(f"Google Maps API call failed: {e}")
        raise HTTPException(status_code=502, detail=f"Google Maps API failed: {str(e)}")


@router.post("/maps/test", tags=["Maps"])
@router.post("/test/maps", tags=["Maps"])
@router.post("/test-maps", include_in_schema=False)
@router.post("/maps", include_in_schema=False)
@router.post("/geocode", include_in_schema=False)
@router.post("/test/geocode", include_in_schema=False)
async def test_maps_api_post(
        request: MapsTestRequest
):
    """
    Test endpoint for Google Maps API geocoding (POST).
    Accepts required 'name' and optional 'city' in JSON payload and queries the real Google Maps service.
    """
    if not request.name or not request.name.strip():
        raise HTTPException(status_code=400, detail="The 'name' parameter is required.")
    query = f"{request.name.strip()} {request.city.strip()}" if request.city and request.city.strip() else request.name.strip()
    try:
        return await processor.geocoding_service.geocode_raw(query)
    except Exception as e:
        logger.error(f"Google Maps API call failed: {e}")
        raise HTTPException(status_code=502, detail=f"Google Maps API failed: {str(e)}")


@router.post("/enrich")
async def handle_enrichment(envelope: dict):
    """
    Handle Pub/Sub push notifications.
    Expected format from Pub/Sub:
    {
        "message": {
            "data": "base64-encoded-payload",
            "messageId": "..."
        }
    }
    """
    if "message" not in envelope or "data" not in envelope["message"]:
        logger.error("Invalid Pub/Sub message received: no 'message' or 'data' field")
        raise HTTPException(status_code=400, detail="Invalid Pub/Sub message format")

    try:
        # Decode base64 Pub/Sub data
        data_bytes = base64.b64decode(envelope["message"]["data"])
        data_str = data_bytes.decode("utf-8")
        payload = json.loads(data_str)
        request = EnrichmentRequest(**payload)

        logger.info(f"Received enrichment request for {request.resourceType} with ID {request.resourceId}")

        # Process enrichment asynchronously or synchronously in worker
        await processor.process(request)

        return {"status": "complete", "id": request.resourceId}

    except Exception as e:
        logger.error(f"Failed to process enrichment request: {str(e)}")
        raise HTTPException(status_code=422, detail=f"Enrichment failed: {str(e)}")
