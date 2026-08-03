package com.example.order.infrastructure.shipping;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "shipping-rest")
@Path("/api/shipments")
public interface ShippingRestClient {

    @POST
    @Path("/schedule")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ScheduleResponse schedule(ScheduleRequest request);

    record ScheduleRequest(
            String orderId,
            String customerId,
            String shippingClass
    ) {}

    record ScheduleResponse(String shipmentId, String outcome, int estimatedDays) {}
}
