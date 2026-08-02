"""Domain services for the Order bounded context.

CustomerProfileLookup is a domain service (not an outbound port) because
the customer profile is stable enough that lookup is cached / in-memory,
and the saga's logic depends on the tier value rather than on the act of
fetching it.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from domain.models import CustomerId, CustomerTier


@dataclass(frozen=True)
class CustomerProfile:
    """The slice of customer information the Order context needs.

    We deliberately don't carry a full customer profile -- name, address,
    billing details would all be Customer's vocabulary, not Order's.
    Order needs the customer's tier to make tier-aware decisions and to
    propagate it as baggage.
    """

    customer_id: CustomerId
    tier: CustomerTier


class CustomerProfileLookup(Protocol):
    """Domain service for resolving a CustomerId to a CustomerProfile.

    Unknown customers default to BRONZE so the saga can always proceed.
    """

    def lookup(self, customer_id: CustomerId) -> CustomerProfile: ...
