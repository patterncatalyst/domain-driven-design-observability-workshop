package com.example.order.domain.outbound;

import com.example.order.domain.model.Order;

/**
 * Outbound port for shipment scheduling. Thin client (no full ACL) -
 * Shipping shares Order's vocabulary closely enough that translation cost
 * outweighs translation benefit.
 */
public interface ShippingPort {

    ShipmentOutcome schedule(Order order);
}
