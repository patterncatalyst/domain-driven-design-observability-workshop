using OrderService.Domain;

namespace OrderService.Infrastructure;

public sealed class InMemoryCustomerProfileLookup : ICustomerProfileLookup
{
    public CustomerProfile Lookup(CustomerId customerId)
    {
        var tier = DeriveTier(customerId.Value);
        return new CustomerProfile(customerId, tier);
    }

    private static CustomerTier DeriveTier(string id)
    {
        if (id.EndsWith("_platinum", StringComparison.OrdinalIgnoreCase))
            return CustomerTier.Platinum;
        if (id.EndsWith("_gold", StringComparison.OrdinalIgnoreCase))
            return CustomerTier.Gold;
        if (id.EndsWith("_silver", StringComparison.OrdinalIgnoreCase))
            return CustomerTier.Silver;
        if (id.EndsWith("_bronze", StringComparison.OrdinalIgnoreCase))
            return CustomerTier.Bronze;
        return CustomerTier.Bronze;
    }
}
