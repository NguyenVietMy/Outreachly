import os

# Set before agent.config is imported: Settings requires INTERNAL_TOKEN, and an explicit env var
# also wins over any .env file a developer has sitting in agent/.
os.environ["INTERNAL_TOKEN"] = "test-token"
os.environ.setdefault("QDRANT_URL", "")

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from agent.main import app  # noqa: E402


@pytest.fixture
def token() -> str:
    return os.environ["INTERNAL_TOKEN"]


@pytest.fixture
def client() -> TestClient:
    # Not used as a context manager, so lifespan (and the Qdrant call in it) does not run.
    return TestClient(app)
