import logging
import uvicorn
from fastapi import FastAPI

from app.config import settings
from app.routers.enrichment import router as enrichment_router

# Setup logging
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Goshuin Enrichment Worker",
    description="Asynchronous background worker for geocoding, translation, and AI description enrichment",
    version="1.0.0"
)

# Include routers
app.include_router(enrichment_router)


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.port, reload=True)
