import secrets
from typing import Annotated

from fastapi import Header, HTTPException, status

from agent.config import settings


async def require_internal_token(
    x_internal_token: Annotated[str | None, Header()] = None,
) -> None:
    """Reject any request not carrying the shared secret.

    Applied per-route rather than globally so /health stays reachable by container and load
    balancer health checks, which cannot set headers.
    """
    if x_internal_token is None or not secrets.compare_digest(
        x_internal_token, settings.internal_token
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid X-Internal-Token",
        )
