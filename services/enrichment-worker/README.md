# Goshuin Enrichment Worker

The **Goshuin Enrichment Worker** is an asynchronous Python service built with **FastAPI** that processes background
data enrichment tasks (geocoding, multi-lingual translation, and AI description generation) for temples and goshuins in
Project Tanuki.

---

## Architecture & Design

- **Event-Driven / Pub/Sub Push**: Designed to scale to zero (e.g., on Cloud Run) and spin up instantly upon receiving a
  Google Cloud Pub/Sub push notification at `POST /enrich`.
- **Modular Architecture**:
    - `app/routers/`: FastAPI endpoints (`/`, `/health`, `/enrich`).
    - `app/services/`: Core logic modules:
        - `enrichment_processor.py`: Orchestrates the enrichment pipeline.
        - `geocoding_service.py`: Google Maps API integration (with development mocks).
        - `translation_service.py`: Google Cloud Translation integration (with mock/fallback).
        - `ai_service.py`: Google Gemini / Vertex AI description generation.
        - `goshuin_client.py`: HTTP client for fetching resource details and sending callback updates to
          `goshuin-service`.
    - `app/models/`: Pydantic validation schemas.
    - `app/config.py`: Environment configuration via `pydantic-settings`.

---

## Setup & Installation

### 1. Prerequisites

- Python 3.12+
- `pip`

### 2. Environment Configuration

Copy the example environment file and configure your API keys:

```powershell
cp .env.example .env
```

Edit `.env` to set your keys:

```env
GOSHUIN_SERVICE_URL=http://localhost:8083
GOOGLE_MAPS_API_KEY=your-google-maps-api-key
GOOGLE_GEMINI_API_KEY=your-google-gemini-api-key
GOOGLE_CLOUD_PROJECT=your-gcp-project-id
LOG_LEVEL=INFO
```

### 3. Install Dependencies

Create a virtual environment and install required packages:

```powershell
python -m venv venv
# On Windows (PowerShell):
.\venv\Scripts\Activate.ps1
# On macOS / Linux:
# source venv/bin/activate

pip install -r requirements.txt
```

---

## Running Locally

Start the development server with hot reloading via Uvicorn:

```powershell
uvicorn main:app --host 0.0.0.0 --port 8080 --reload
```

- **Health Check**: `GET http://localhost:8080/health`
- **Root Endpoint**: `GET http://localhost:8080/`
- **Enrichment Webhook**: `POST http://localhost:8080/enrich`

---

## Testing

Run unit tests using `pytest`:

```powershell
pytest
```

---

## Docker Support

Build and run the container locally:

```powershell
docker build -t enrichment-worker .
docker run -p 8080:8080 --env-file .env enrichment-worker
```
