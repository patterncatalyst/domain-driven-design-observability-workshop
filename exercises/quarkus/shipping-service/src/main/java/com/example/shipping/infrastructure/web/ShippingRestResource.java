package com.example.shipping.infrastructure.web;

import com.example.shipping.application.ScheduleShipmentCommand;
import com.example.shipping.application.ScheduleShipmentUseCase;
import com.example.shipping.domain.model.Shipment;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/shipments")
public class ShippingRestResource {

    private final ScheduleShipmentUseCase useCase;

    public ShippingRestResource(ScheduleShipmentUseCase useCase) {
        this.useCase = useCase;
    }

    @POST
    @Path("/schedule")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduleResponse schedule(ScheduleRequest request) {
        Shipment shipment = useCase.schedule(new ScheduleShipmentCommand(
                request.orderId(),
                request.customerId(),
                request.shippingClass()));
        return new ScheduleResponse(
                shipment.id().value(),
                "SCHEDULED",
                shipment.estimatedDays());
    }

    public record ScheduleRequest(
            String orderId,
            String customerId,
            String shippingClass
    ) {}

    public record ScheduleResponse(String shipmentId, String outcome, int estimatedDays) {}
}
