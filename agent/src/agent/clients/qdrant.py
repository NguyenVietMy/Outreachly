import logging

from qdrant_client import AsyncQdrantClient, models

from agent.config import settings

logger = logging.getLogger(__name__)

# Must match QdrantSchemaInitializer on the Java side — the Java write path owns this collection.
COLLECTION = "knowledge_chunks"
DENSE_VECTOR = "dense"
SPARSE_VECTOR = "bm25"

# Qdrant infers the sparse vector from the query text server-side; no BM25 encoder here either.
BM25_MODEL = "qdrant/bm25"

# Passed through explicitly rather than trusting the server default, exactly as QdrantVectorStore
# does, so fused scores land on the scale MIN_RRF_THRESHOLD assumes.
RRF_K = 60

_client: AsyncQdrantClient | None = None


def get_client() -> AsyncQdrantClient:
    global _client
    if _client is None:
        # cloud_inference: the qdrant/bm25 Document is resolved by the server, not by a local
        # fastembed install. Without this the client tries to embed it here and fails.
        _client = AsyncQdrantClient(
            url=settings.qdrant_url, api_key=settings.qdrant_api_key, cloud_inference=True
        )
    return _client


async def hybrid_search(
    user_id: int,
    query_text: str,
    dense_vector: list[float],
    source_types: list[str],
    prefetch_limit: int,
    limit: int,
) -> list[models.ScoredPoint]:
    """One dense prefetch and one BM25 prefetch over the same filter, fused server-side with RRF.

    Mirrors QdrantVectorStore.hybridSearch — the Java read path this replaces.
    """
    search_filter = models.Filter(
        must=[
            models.FieldCondition(key="user_id", match=models.MatchValue(value=user_id)),
            models.FieldCondition(key="source_type", match=models.MatchAny(any=source_types)),
        ]
    )
    response = await get_client().query_points(
        collection_name=COLLECTION,
        prefetch=[
            models.Prefetch(
                query=dense_vector,
                using=DENSE_VECTOR,
                filter=search_filter,
                limit=prefetch_limit,
            ),
            models.Prefetch(
                query=models.Document(text=query_text, model=BM25_MODEL),
                using=SPARSE_VECTOR,
                filter=search_filter,
                limit=prefetch_limit,
            ),
        ],
        query=models.RrfQuery(rrf=models.Rrf(k=RRF_K)),
        limit=limit,
        with_payload=True,
    )
    return response.points


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
