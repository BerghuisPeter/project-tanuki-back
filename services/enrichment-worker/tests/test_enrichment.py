import base64
import json
import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert "message" in response.json()


def test_handle_enrichment_invalid_format():
    response = client.post("/enrich", json={"invalid": "payload"})
    assert response.status_code == 400


def test_handle_enrichment_valid(monkeypatch):
    # Mock GoshuinClient and processor external calls so test doesn't require live services or keys
    async def mock_process(self, request):
        return None

    from app.services.enrichment_processor import EnrichmentProcessor
    monkeypatch.setattr(EnrichmentProcessor, "process", mock_process)

    payload = {
        "resourceType": "TEMPLE",
        "resourceId": "123e4567-e89b-12d3-a456-426614174000",
        "originalLocale": "ja"
    }
    encoded_data = base64.b64encode(json.dumps(payload).encode("utf-8")).decode("utf-8")
    envelope = {
        "message": {
            "data": encoded_data,
            "messageId": "test-msg-id"
        }
    }

    response = client.post("/enrich", json=envelope)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "complete"
    assert data["id"] == payload["resourceId"]


@pytest.mark.asyncio
async def test_ai_service_fallback_without_client():
    from app.services.ai_service import AIService
    service = AIService()
    service.client = None
    description = await service.generate_description("Kinkaku-ji", category="Temple")
    assert "Kinkaku-ji is a historic temple known for its serene grounds" in description


@pytest.mark.asyncio
async def test_ai_service_generate_content_success():
    from app.services.ai_service import AIService
    from unittest.mock import MagicMock

    service = AIService()
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = "Generated description of Kinkaku-ji"
    mock_client.models.generate_content.return_value = mock_response
    service.client = mock_client

    description = await service.generate_description("Kinkaku-ji", category="Temple", context="Golden Pavilion")
    assert description == "Generated description of Kinkaku-ji"
    mock_client.models.generate_content.assert_called_once_with(
        model="gemini-1.5-flash",
        contents="Write a comprehensive, culturally rich, and engaging description in Japanese for the Temple named 'Kinkaku-ji'. Additional context: Golden Pavilion. Describe its history, architectural significance, and spiritual atmosphere in 2-3 paragraphs."
    )


@pytest.mark.asyncio
async def test_ai_service_generate_content_error_fallback():
    from app.services.ai_service import AIService
    from unittest.mock import MagicMock

    service = AIService()
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = RuntimeError("API rate limit")
    service.client = mock_client

    description = await service.generate_description("Kinkaku-ji", category="Temple")
    assert "Kinkaku-ji is a historic temple known for its serene grounds" in description
