import base64
import json
import logging
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Goshuin Enrichment Worker")


class EnrichmentRequest(BaseModel):
    resourceType: str
    resourceId: str
    originalLocale: str


@app.get("/")
async def root():
    return {"message": "Hello World from Goshuin Enrichment Worker"}


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/enrich")
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
    if "message" not in envelope:
        logger.error("Invalid Pub/Sub message received: no 'message' field")
        raise HTTPException(status_code=400, detail="Invalid Pub/Sub message")

    try:
        # Pub/Sub data is base64 encoded
        data_str = base64.b64decode(envelope["message"]["data"]).decode("utf-8")
        payload = json.loads(data_str)
        request = EnrichmentRequest(**payload)

        logger.info(f"Received enrichment request for {request.resourceType} with ID {request.resourceId}")

        # TODO: Implement enrichment logic (Geocoding, Translation, AI Description)

        return {"status": "processing", "id": request.resourceId}

    except Exception as e:
        logger.error(f"Failed to process enrichment request: {str(e)}")
        raise HTTPException(status_code=422, detail=f"Validation failed: {str(e)}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8080)
