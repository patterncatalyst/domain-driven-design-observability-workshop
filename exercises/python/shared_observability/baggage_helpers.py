"""Helpers for reading and writing OpenTelemetry Baggage."""

from __future__ import annotations

from opentelemetry import baggage, context


def set_baggage(key: str, value: str) -> context.Context:
    """Attach a key/value pair to the current OTel baggage.

    Returns the new context (callers can pass it forward or attach it).
    """
    ctx = baggage.set_baggage(key, value)
    context.attach(ctx)
    return ctx


def get_baggage(key: str) -> str | None:
    """Read a value from the current OTel baggage, or None if absent."""
    return baggage.get_baggage(key)
