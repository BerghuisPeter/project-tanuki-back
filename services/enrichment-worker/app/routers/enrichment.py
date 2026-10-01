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
    Expected format from Google Cloud Pub/Sub Push Subscription:
    {
        "message": {
            "attributes": {
                "jobId": "...",
                "resourceType": "GOSHUIN",
                "resourceId": "..."
            },
            "data": "eyJqb2JJZCI6Ii4uLiIsInJlc291cmNlVHlwZSI6IkdPU0hVSU4iLCJyZXNvdXJjZUlkIjoiLi4uIn0=",
            "messageId": "..."
        },
        "subscription": "..."
    }
    Or direct JSON payload for testing:
    {
        "jobId": "...",
        "resourceType": "GOSHUIN",
        "resourceId": "..."
    }
    """
    if not isinstance(envelope, dict):
        raise HTTPException(status_code=400, detail="Invalid request payload")

    message = envelope.get("message")
    attributes = message.get("attributes", {}) if isinstance(message, dict) else {}
    payload = {}

    if isinstance(message, dict) and "data" in message:
        try:
            data_bytes = base64.b64decode(message["data"])
            data_str = data_bytes.decode("utf-8")
            try:
                payload = json.loads(data_str)
            except Exception:
                payload = data_str
        except Exception as e:
            logger.warning(f"Could not decode base64 data: {e}")
    elif "data" in envelope and isinstance(envelope["data"], str):
        try:
            data_bytes = base64.b64decode(envelope["data"])
            data_str = data_bytes.decode("utf-8")
            try:
                payload = json.loads(data_str)
            except Exception:
                payload = data_str
        except Exception as e:
            logger.warning(f"Could not decode base64 data: {e}")
    elif "resourceId" in envelope or "jobId" in envelope or "resourceType" in envelope:
        payload = envelope
    else:
        logger.error("Invalid Pub/Sub message received: no recognizable message or payload format")
        raise HTTPException(status_code=400, detail="Invalid Pub/Sub message format")

    job_id = None
    resource_type = "GOSHUIN"
    resource_id = None

    if attributes:
        job_id = attributes.get("jobId") or attributes.get("job_id")
        resource_type = attributes.get("resourceType") or attributes.get("resource_type") or resource_type
        resource_id = attributes.get("resourceId") or attributes.get("resource_id")

    if isinstance(payload, dict):
        job_id = payload.get("jobId") or payload.get("job_id") or job_id
        resource_type = payload.get("resourceType") or payload.get("resource_type") or resource_type
        resource_id = payload.get("resourceId") or payload.get("resource_id") or resource_id
    elif isinstance(payload, str):
        if not job_id and not resource_id:
            job_id = payload
            resource_id = payload

    if not job_id and not resource_id:
        logger.error("Invalid enrichment request: missing both jobId and resourceId")
        raise HTTPException(status_code=400, detail="Invalid Pub/Sub message: missing job or resource identification")

    # For now, simply print out the jobId and nothing else
    print(f"Received Pub/Sub message - Job ID: {job_id}", flush=True)
    logger.info(f"Received Pub/Sub message - Job ID: {job_id}")

    return {"status": "received", "jobId": job_id, "resourceId": resource_id or job_id}
