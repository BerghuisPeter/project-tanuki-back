from pydantic import BaseModel, Field
from typing import Optional, Dict


class EnrichmentRequest(BaseModel):
    jobId: Optional[str] = Field(default=None, description="Enrichment job ID")
    resourceType: Optional[str] = Field(default="GOSHUIN",
                                        description="Type of resource to enrich (e.g., TEMPLE, GOSHUIN)")
    resourceId: Optional[str] = Field(default=None, description="Unique identifier (UUID) of the resource")


class PubSubMessage(BaseModel):
    data: str = Field(..., description="Base64 encoded message payload")
    messageId: Optional[str] = None
    publishTime: Optional[str] = None


class PubSubEnvelope(BaseModel):
    message: PubSubMessage


class GeocodingResult(BaseModel):
    address: str
    latitude: float
    longitude: float


class EnrichmentUpdate(BaseModel):
    """
    Body of PATCH /internal/v1/{temples|goshuins}/{id}/enrichment (see ENRICHMENT.md, section 5).
    Fields that are None are omitted when sent. A missing status means a data-only write.
    """
    jobId: Optional[str] = None
    status: Optional[str] = Field(default=None, description="PROCESSING, COMPLETE or FAILED")
    error: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    translations: Optional[Dict[str, Dict[str, str]]] = Field(
        default=None, description="locale -> {field: value}, filled in only where the stored value is empty")
