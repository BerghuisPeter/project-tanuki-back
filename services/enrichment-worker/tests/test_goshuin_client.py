import httpx
import pytest
from app.config import settings
from app.models.enrichment import EnrichmentUpdate
from app.services.goshuin_client import GoshuinClient
from unittest.mock import MagicMock

TEMPLE_ID = "3490-afde"


@pytest.fixture
def requests_seen(monkeypatch):
    """Replaces the HTTP call and records (method, url, headers) of every request."""
    seen = []

    async def fake_request(self, method, url, headers=None, json=None):
        seen.append((method, url, headers))
        return httpx.Response(200 if method == "GET" else 204, json={"id": TEMPLE_ID},
                              request=httpx.Request(method, url))

    monkeypatch.setattr(httpx.AsyncClient, "request", fake_request)
    return seen


@pytest.mark.asyncio
async def test_no_token_is_sent_when_auth_is_disabled(requests_seen, monkeypatch):
    monkeypatch.setattr(settings, "goshuin_auth_enabled", False)
    client = GoshuinClient()
    client._fetch_id_token = MagicMock(side_effect=AssertionError("must not fetch a token"))

    assert await client.get_resource("TEMPLE", TEMPLE_ID) == {"id": TEMPLE_ID}
    assert await client.send_enrichment_callback("TEMPLE", TEMPLE_ID, EnrichmentUpdate(status="PROCESSING"))

    assert [(method, headers) for method, _, headers in requests_seen] == [("GET", {}), ("PATCH", {})]


@pytest.mark.asyncio
async def test_identity_token_is_sent_as_bearer_when_auth_is_enabled(requests_seen, monkeypatch):
    monkeypatch.setattr(settings, "goshuin_auth_enabled", True)
    client = GoshuinClient()
    client._fetch_id_token = MagicMock(return_value="id-token")

    assert await client.get_resource("TEMPLE", TEMPLE_ID) == {"id": TEMPLE_ID}
    assert await client.send_enrichment_callback("TEMPLE", TEMPLE_ID, EnrichmentUpdate(status="PROCESSING"))

    assert all(headers == {"Authorization": "Bearer id-token"} for _, _, headers in requests_seen)
    assert len(requests_seen) == 2


@pytest.mark.asyncio
async def test_token_failure_is_handled_like_a_failed_call(requests_seen, monkeypatch):
    monkeypatch.setattr(settings, "goshuin_auth_enabled", True)
    client = GoshuinClient()
    client._fetch_id_token = MagicMock(side_effect=RuntimeError("metadata server unreachable"))

    assert await client.get_resource("TEMPLE", TEMPLE_ID) is None
    assert not await client.send_enrichment_callback("TEMPLE", TEMPLE_ID, EnrichmentUpdate(status="PROCESSING"))
    assert requests_seen == []


def test_token_is_fetched_for_the_goshuin_service_url_and_reused_until_invalid(monkeypatch):
    credentials = MagicMock(valid=False, token="id-token")
    fetch = MagicMock(return_value=credentials)
    monkeypatch.setattr("app.services.goshuin_client.id_token.fetch_id_token_credentials", fetch)
    monkeypatch.setattr(settings, "goshuin_service_url", "https://goshuin.example.run.app/")
    client = GoshuinClient()

    assert client._fetch_id_token() == "id-token"
    credentials.refresh.assert_called_once()
    fetch.assert_called_once_with("https://goshuin.example.run.app")

    credentials.valid = True
    assert client._fetch_id_token() == "id-token"
    credentials.refresh.assert_called_once()
    fetch.assert_called_once()
