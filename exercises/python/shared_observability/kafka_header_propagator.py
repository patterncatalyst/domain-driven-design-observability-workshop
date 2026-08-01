"""Propagate domain identifiers through Kafka message headers."""

from __future__ import annotations

from typing import Sequence

from shared_observability.domain_identifier import DomainIdentifier

# Prefix used to namespace domain identifiers in Kafka headers.
_HEADER_PREFIX = "domain."


def inject_domain_identifiers(
    identifiers: Sequence[DomainIdentifier],
) -> list[tuple[str, bytes]]:
    """Convert domain identifiers to Kafka headers.

    Returns a list of (header_name, header_value_bytes) tuples ready to
    pass to a Kafka producer's ``headers`` parameter.
    """
    return [
        (f"{_HEADER_PREFIX}{ident.key()}", ident.value().encode("utf-8"))
        for ident in identifiers
    ]


def extract_domain_identifiers(
    headers: list[tuple[str, bytes]] | None,
) -> dict[str, str]:
    """Extract domain identifiers from Kafka headers.

    Returns a dict mapping identifier keys to their string values.
    Only headers prefixed with ``domain.`` are considered.
    """
    if not headers:
        return {}

    result: dict[str, str] = {}
    for name, value in headers:
        if name.startswith(_HEADER_PREFIX):
            key = name[len(_HEADER_PREFIX) :]
            result[key] = value.decode("utf-8")
    return result
