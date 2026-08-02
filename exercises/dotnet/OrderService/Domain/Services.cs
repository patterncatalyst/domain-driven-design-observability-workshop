namespace OrderService.Domain;

public sealed record CustomerProfile(CustomerId CustomerId, CustomerTier Tier);

public interface ICustomerProfileLookup
{
    CustomerProfile Lookup(CustomerId customerId);
}
