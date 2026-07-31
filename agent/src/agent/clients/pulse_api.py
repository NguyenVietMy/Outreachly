from typing import Any

import httpx

from agent.config import settings

_client: httpx.AsyncClient | None = None


def get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(
            base_url=settings.pulse_api_url,
            headers={"X-Internal-Token": settings.internal_token},
            timeout=10.0,
        )
    return _client


async def _get(path: str, user_id: int) -> dict[str, Any]:
    response = await get_client().get(path, params={"userId": user_id})
    response.raise_for_status()
    return response.json()


async def get_profile(user_id: int) -> dict[str, Any]:
    return await _get("/internal/context/profile", user_id)


async def get_items(user_id: int) -> dict[str, Any]:
    return await _get("/internal/context/items", user_id)


async def get_github(user_id: int) -> dict[str, Any]:
    return await _get("/internal/context/github", user_id)


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None
