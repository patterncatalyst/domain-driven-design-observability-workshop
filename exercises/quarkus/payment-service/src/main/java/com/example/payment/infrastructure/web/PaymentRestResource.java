package com.example.payment.infrastructure.web;

import com.example.payment.application.AuthorizePaymentCommand;
import com.example.payment.application.AuthorizePaymentUseCase;
import com.example.payment.domain.model.Authorization;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;

/**
 * REST endpoint for payment authorization.
 *
 * <p>Wire DTOs are inner records on this resource - mirrors the thin-client
 * pattern in Order's {@code PaymentRestClient}. The vocabularies align,
 * so a separate {@code dto/} package would be busywork.
 */
@Path("/api/payment")
public class PaymentRestResource {

    private final AuthorizePaymentUseCase useCase;

    public PaymentRestResource(AuthorizePaymentUseCase useCase) {
        this.useCase = useCase;
    }

    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        AuthorizePaymentCommand command = new AuthorizePaymentCommand(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                request.paymentMethod());

        Authorization auth = useCase.authorize(command);

        return new AuthorizeResponse(
                auth.id().value(),
                auth.outcome().name(),
                auth.reason());
    }

    public record AuthorizeRequest(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod
    ) {}

    public record AuthorizeResponse(
            String authorizationId,
            String outcome,
            String reason
    ) {}
}
