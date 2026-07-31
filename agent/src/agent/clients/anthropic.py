from functools import cache

from langchain_anthropic import ChatAnthropic

from agent.config import settings

# Matches AnthropicService.FAST_MODEL on the Java side — chat runs on the cheap tier on both.
MODEL = "claude-haiku-4-5"

# No temperature/top_p/top_k overrides anywhere: Sonnet 5 and Opus 5 reject them with a 400, so
# staying on the defaults keeps the model swappable.


@cache
def get_llm(max_tokens: int) -> ChatAnthropic:
    return ChatAnthropic(
        model=MODEL,
        api_key=settings.anthropic_api_key,
        max_tokens=max_tokens,
        timeout=60,
    )
