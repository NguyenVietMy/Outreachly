from openai import AsyncOpenAI

from agent.config import settings

# Matches the Java EmbeddingService and the 1536-dim dense vector in Qdrant. OpenAI is used here
# *only* for embeddings — Anthropic has no embeddings API. All generation uses langchain-anthropic.
MODEL = "text-embedding-3-small"

_client: AsyncOpenAI | None = None


def get_client() -> AsyncOpenAI:
    global _client
    if _client is None:
        _client = AsyncOpenAI(api_key=settings.openai_api_key)
    return _client


async def embed(text: str) -> list[float]:
    response = await get_client().embeddings.create(model=MODEL, input=text)
    return response.data[0].embedding
