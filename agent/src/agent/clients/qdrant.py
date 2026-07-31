import logging

from qdrant_client import AsyncQdrantClient

from agent.config import settings

logger = logging.getLogger(__name__)

# Must match QdrantSchemaInitializer on the Java side — the Java write path owns this collection.
COLLECTION = "knowledge_chunks"
DENSE_VECTOR = "dense"
SPARSE_VECTOR = "bm25"

_client: AsyncQdrantClient | None = None


def get_client() -> AsyncQdrantClient:
    global _client
    if _client is None:
        _client = AsyncQdrantClient(url=settings.qdrant_url, api_key=settings.qdrant_api_key)
    return _client


async def log_collection_info() -> None:
    """Prove connectivity at startup. Logs rather than raises: a retrieval-less /health should
    still come up so the container reports why it is unhealthy instead of crash-looping."""
    if not settings.qdrant_url:
        logger.warning("QDRANT_URL is unset — skipping Qdrant connectivity check")
        return
    try:
        info = await get_client().get_collection(COLLECTION)
        logger.info(
            "Qdrant collection %s: status=%s points=%s vectors=%s",
            COLLECTION,
            info.status,
            info.points_count,
            info.indexed_vectors_count,
        )
    except Exception:
        logger.exception("Could not read Qdrant collection %s", COLLECTION)


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.close()
        _client = None
