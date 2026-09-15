import base64
import json
import logging
from app.models.enrichment import PubSubEnvelope, EnrichmentRequest
from app.services.enrichment_processor import EnrichmentProcessor
from fastapi import APIRouter, HTTPException

logger = logging.getLogger(__name__)

router = APIRouter()
processor = EnrichmentProcessor()


@router.get("/")
async def root():
    return {"message": "Hello World from Goshuin Enrichment Worker"}


@router.get("/health")
async def health():
    return {"status": "ok"}


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
