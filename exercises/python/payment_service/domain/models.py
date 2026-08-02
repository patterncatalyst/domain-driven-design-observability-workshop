"""Domain models for the Payment bounded context.

Pure Python -- no framework dependencies. The Authorization aggregate root
and its value objects live here.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from enum import Enum


class AuthorizationOutcome(Enum):
    """Possible outcomes of a payment authorization attempt.

    Wire names are intentionally aligned with Order service's vocabulary
    (closely-coupled-by-vocabulary -- not ACL-worthy for this workshop).
    """

    AUTHORIZED = "AUTHORIZED"
    DECLINED = "DECLINED"
    FAILURE = "FAILURE"


@dataclass(frozen=True)
class AuthorizationId:
    """Value object wrapping a payment authorization identifier."""

    value: str

    _PREFIX = "auth_"

    @classmethod
    def generate(cls) -> AuthorizationId:
        """Create a new, unique authorization identifier."""
        return cls(f"{cls._PREFIX}{uuid.uuid4()}")


@dataclass(frozen=True)
class Authorization:
    """The Authorization aggregate root.

    No persistence -- created in-memory and returned. Outcome is determined
    by the use case (simulated via customer-id pattern matching).
    """

    id: AuthorizationId
    order_id: str
    amount: float
    currency: str
    outcome: AuthorizationOutcome
    reason: str | None

    @classmethod
    def authorized(
        cls, order_id: str, amount: float, currency: str
    ) -> Authorization:
        """Factory: successful authorization."""
        return cls(
            id=AuthorizationId.generate(),
            order_id=order_id,
            amount=amount,
            currency=currency,
            outcome=AuthorizationOutcome.AUTHORIZED,
            reason=None,
        )

    @classmethod
    def declined(
        cls, order_id: str, amount: float, currency: str, reason: str
    ) -> Authorization:
        """Factory: declined authorization."""
        if not reason:
            raise ValueError("Declined authorization requires a reason")
        return cls(
            id=AuthorizationId.generate(),
            order_id=order_id,
            amount=amount,
            currency=currency,
            outcome=AuthorizationOutcome.DECLINED,
            reason=reason,
        )

    @classmethod
    def failure(
        cls, order_id: str, amount: float, currency: str, reason: str
    ) -> Authorization:
        """Factory: authorization failure (gateway/system error)."""
        if not reason:
            raise ValueError("Failed authorization requires a reason")
        return cls(
            id=AuthorizationId.generate(),
            order_id=order_id,
            amount=amount,
            currency=currency,
            outcome=AuthorizationOutcome.FAILURE,
            reason=reason,
        )
