"""Tracing tests: redaction, and joining the trace the Java API started.

Nothing here talks to Langfuse — the client is disabled in tests (no keys), so these cover the two
pieces that are ours: the export-stage mask and the W3C context handoff.
"""

from types import MappingProxyType

from langfuse import LangfuseOtelSpanAttributes
from langfuse.types import MaskOtelSpansParams, OtelSpanData, OtelSpanIdentifier
from opentelemetry import trace

from agent.observability import continue_upstream_trace, mask_otel_spans

TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
SPAN_ID = "00f067aa0ba902b7"


def span(**attributes) -> tuple[OtelSpanIdentifier, OtelSpanData]:
    identifier = OtelSpanIdentifier(trace_id=TRACE_ID, span_id=SPAN_ID)
    return identifier, OtelSpanData(
        trace_id=TRACE_ID,
        span_id=SPAN_ID,
        parent_span_id=None,
        name="answer",
        instrumentation_scope_name="langfuse-sdk",
        instrumentation_scope_version="4",
        attributes=MappingProxyType(dict(attributes)),
        resource_attributes=MappingProxyType({}),
    )


def test_prompt_and_completion_bodies_are_stripped_on_export() -> None:
    identifier, data = span(
        **{
            LangfuseOtelSpanAttributes.OBSERVATION_INPUT: "=== RESUME ===\nBackend intern at Acme.",
            LangfuseOtelSpanAttributes.OBSERVATION_OUTPUT: "Your resume says Acme.",
            LangfuseOtelSpanAttributes.OBSERVATION_MODEL: "claude-haiku-4-5",
        }
    )

    result = mask_otel_spans(params=MaskOtelSpansParams(spans={identifier: data}))
    patch = result.span_patches[identifier]

    assert set(patch.delete_attributes) == {
        LangfuseOtelSpanAttributes.OBSERVATION_INPUT,
        LangfuseOtelSpanAttributes.OBSERVATION_OUTPUT,
    }
    assert patch.set_attributes == {"pulse.redacted": True}
    # The attributes the trace is actually useful for must survive redaction.
    assert LangfuseOtelSpanAttributes.OBSERVATION_MODEL not in patch.delete_attributes


def test_spans_without_bodies_are_left_alone() -> None:
    identifier, data = span(
        **{LangfuseOtelSpanAttributes.OBSERVATION_USAGE_DETAILS: '{"input": 12}'}
    )

    result = mask_otel_spans(params=MaskOtelSpansParams(spans={identifier: data}))

    assert result.span_patches == {}


def test_incoming_traceparent_becomes_the_parent_context() -> None:
    headers = {"traceparent": f"00-{TRACE_ID}-{SPAN_ID}-01"}

    with continue_upstream_trace(headers):
        context = trace.get_current_span().get_span_context()
        assert format(context.trace_id, "032x") == TRACE_ID
        assert format(context.span_id, "016x") == SPAN_ID
        assert context.is_remote

    assert not trace.get_current_span().get_span_context().is_valid


def test_a_request_without_a_traceparent_still_runs() -> None:
    with continue_upstream_trace({"x-internal-token": "dev"}):
        assert not trace.get_current_span().get_span_context().is_valid
