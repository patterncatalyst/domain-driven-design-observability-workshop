"""Domain models for the Notification bounded context.

Pure Python -- no framework dependencies. The Notification aggregate root
and its value objects live here.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum


class NotificationKind(Enum):
    """The kinds of notifications the service sends.

    Used as metric labels and span attributes -- the bounded set means
    cardinality stays sane.
    """

    PLACED_ACK = "PLACED_ACK"
    """Acknowledgment that an order was received and is being processed."""

    CONFIRMATION = "CONFIRMATION"
    """Confirmation that an order completed all saga steps."""

    CANCELLATION = "CANCELLATION"
    """Apology that an order was cancelled."""


@dataclass(frozen=True)
class NotificationId:
    """Value object wrapping a notification identifier."""

    value: str

    _PREFIX = "notif_"

    @classmethod
    def generate(cls) -> NotificationId:
        """Create a new, unique notification identifier."""
        return cls(f"{cls._PREFIX}{uuid.uuid4()}")


@dataclass(frozen=True)
class Notification:
    """The Notification aggregate root.

    Records what was sent, to whom, of what kind, with what tier-aware
    customization. No persistence -- created in-memory and logged.

    The ``customer_tier`` field is the focus of Module 4's debugging
    exercise: on the broken branch, the consumer fails to read tier from
    baggage, which means every notification gets recorded with tier
    "unknown". The "notifications by tier" dashboard panel goes silent
    except for one bucket.
    """

    id: NotificationId
    kind: NotificationKind
    order_id: str
    customer_id: str
    customer_tier: str
    channel: str
    sent_at: datetime

    @classmethod
    def send(
        cls,
        kind: NotificationKind,
        order_id: str,
        customer_id: str,
        customer_tier: str,
        channel: str,
    ) -> Notification:
        """Factory: create and 'send' a notification (log only, no real email/SMS)."""
        return cls(
            id=NotificationId.generate(),
            kind=kind,
            order_id=order_id,
            customer_id=customer_id,
            customer_tier=customer_tier,
            channel=channel,
            sent_at=datetime.now(timezone.utc),
        )
