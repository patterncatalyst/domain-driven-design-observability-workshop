"""In-memory implementation of CustomerProfileLookup.

For workshop simplicity, customer tier is derived deterministically
from a suffix in the customer id::

    cust_alice_silver  -> SILVER
    cust_dave_gold     -> GOLD
    cust_xyz           -> BRONZE   (default for unknown tiers)
"""

from __future__ import annotations

from domain.models import CustomerId, CustomerTier
from domain.services import CustomerProfile


class InMemoryCustomerProfileLookup:
    """Derives customer tier from the customer ID suffix.

    A real Order context would talk to a Customer service or read from
    a customer-projection table. The ``CustomerProfileLookup`` protocol
    is what allows swapping implementations without changing the saga.
    """

    def lookup(self, customer_id: CustomerId) -> CustomerProfile:
        tier = self._derive_tier_from_id(customer_id.value)
        return CustomerProfile(customer_id=customer_id, tier=tier)

    @staticmethod
    def _derive_tier_from_id(customer_id_value: str) -> CustomerTier:
        """Match the longest suffix first to avoid ambiguity."""
        lower = customer_id_value.lower()
        if lower.endswith("_platinum"):
            return CustomerTier.PLATINUM
        if lower.endswith("_gold"):
            return CustomerTier.GOLD
        if lower.endswith("_silver"):
            return CustomerTier.SILVER
        if lower.endswith("_bronze"):
            return CustomerTier.BRONZE
        return CustomerTier.BRONZE
