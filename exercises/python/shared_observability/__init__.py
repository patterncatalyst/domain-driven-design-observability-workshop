"""Shared observability primitives for the DDD + OTel workshop."""

from shared_observability.domain_identifier import DomainIdentifier
from shared_observability.domain_context import DomainContext
from shared_observability.baggage_helpers import get_baggage, set_baggage
from shared_observability.kafka_header_propagator import (
    inject_domain_identifiers,
    extract_domain_identifiers,
)
from shared_observability.otel_setup import configure_otel

__all__ = [
    "DomainIdentifier",
    "DomainContext",
    "get_baggage",
    "set_baggage",
    "inject_domain_identifiers",
    "extract_domain_identifiers",
    "configure_otel",
]
