"""Langfuse tracing, and continuation of the trace the Java API started.

Langfuse's Python SDK is OpenTelemetry-native, so joining the Java trace needs no custom glue
beyond attaching the incoming W3C context: the spans this service creates become children of
`pulse.rag.chat` and land in the same Langfuse trace.
"""

from collections.abc import Iterator, Mapping
from contextlib import contextmanager

from langfuse import Langfuse, LangfuseOtelSpanAttributes
from langfuse.types import (
    MaskOtelSpansParams,
    MaskOtelSpansResult,
    OtelSpanPatch,
)
from opentelemetry import context as otel_context
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from agent.config import settings

# Prompts here contain the retrieved chunks and, when the planner inlines it, the whole resume —
# so no free-text body leaves the process at all. This matches the Java side (issue 09), and it
# is why the trace still has to carry structured attributes: model, token counts, retrieval
# scores and source keys are what is left to debug with.
REDACTED_ATTRIBUTES = (
    LangfuseOtelSpanAttributes.TRACE_INPUT,
    LangfuseOtelSpanAttributes.TRACE_OUTPUT,
    LangfuseOtelSpanAttributes.OBSERVATION_INPUT,
    LangfuseOtelSpanAttributes.OBSERVATION_OUTPUT,
)

_propagator = TraceContextTextMapPropagator()


def mask_otel_spans(*, params: MaskOtelSpansParams) -> MaskOtelSpansResult | None:
    """Drop prompt and completion bodies at export time.

    Export-stage rather than the `mask` hook: this also covers spans the LangChain
    ``CallbackHandler`` creates, which is where the retrieved resume text would otherwise be.
    """
    patches = {}
    for identifier, span in params.spans.items():
        present = tuple(key for key in REDACTED_ATTRIBUTES if key in span.attributes)
        if present:
            patches[identifier] = OtelSpanPatch(
                delete_attributes=present,
                set_attributes={"pulse.redacted": True},
            )
    return MaskOtelSpansResult(span_patches=patches)


def init_tracing() -> Langfuse:
    """Initialise the Langfuse client. Missing keys disable tracing rather than failing to boot."""
    return Langfuse(
        public_key=settings.langfuse_public_key,
        secret_key=settings.langfuse_secret_key,
        host=settings.langfuse_host,
        mask_otel_spans=mask_otel_spans,
    )


@contextmanager
def continue_upstream_trace(headers: Mapping[str, str]) -> Iterator[None]:
    """Adopt the `traceparent` the Java API sent, so this request joins its trace.

    Plain OTel extraction rather than `opentelemetry-instrumentation-fastapi`: Langfuse only
    exports spans it recognises, so a FastAPI server span would be dropped anyway and the
    instrumentation would buy nothing but these three lines.
    """
    token = otel_context.attach(_propagator.extract(dict(headers)))
    try:
        yield
    finally:
        otel_context.detach(token)
