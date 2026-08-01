"""Context manager for structured logging with domain identifiers."""

from __future__ import annotations

import contextvars
from contextlib import contextmanager
from typing import Any

import structlog

from shared_observability.domain_identifier import DomainIdentifier

# Context variable holding the current domain identifiers.
_domain_ctx: contextvars.ContextVar[dict[str, str]] = contextvars.ContextVar(
    "domain_context", default={}
)


def current_domain_context() -> dict[str, str]:
    """Return a snapshot of the current domain context."""
    return dict(_domain_ctx.get())


@contextmanager
def DomainContext(*identifiers: DomainIdentifier):
    """Set domain identifiers in the logging context for the duration of a block.

    Usage::

        with DomainContext(order_id, customer_id):
            logger.info("processing order")
            # logs will include order.id=... and customer.id=...

    The identifiers are added to structlog's context and to a ContextVar so
    they can be read by the baggage and Kafka header helpers.
    """
    merged = {**_domain_ctx.get()}
    bindings: dict[str, Any] = {}
    for ident in identifiers:
        merged[ident.key()] = ident.value()
        bindings[ident.key()] = ident.value()

    token = _domain_ctx.set(merged)
    try:
        with structlog.contextvars.bound_contextvars(**bindings):
            yield
    finally:
        _domain_ctx.reset(token)
