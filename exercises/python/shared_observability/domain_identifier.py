"""Protocol for domain identifiers used in structured observability."""

from typing import Protocol, runtime_checkable


@runtime_checkable
class DomainIdentifier(Protocol):
    """A domain identifier that can be propagated through traces and Kafka headers.

    Implementors represent meaningful business identifiers (e.g. OrderId,
    CustomerId) that should travel alongside technical context across
    service boundaries.
    """

    def key(self) -> str:
        """The identifier key, used as the attribute/header name.

        Example: "order.id", "customer.id"
        """
        ...

    def value(self) -> str:
        """The identifier value.

        Example: "ord_abc123", "cust_alice_silver"
        """
        ...
