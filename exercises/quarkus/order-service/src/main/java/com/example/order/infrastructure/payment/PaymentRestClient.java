package com.example.order.infrastructure.payment;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.math.BigDecimal;

@RegisterRestClient(configKey = "payment-rest")
@Path("/api/payments")
public interface PaymentRestClient {

    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    AuthorizeResponse authorize(AuthorizeRequest request);

    /**
     * Wire DTO for the authorize call. Lives here (file-local) because the
     * thin-client pattern doesn't justify a separate dto/ package - the
     * vocabulary doesn't drift between Order and Payment in the workshop's
     * scenario. Module 5 discusses when this distinction tips toward
     * needing a full ACL.
     */
    record AuthorizeRequest(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod
    ) {}

    record AuthorizeResponse(
            String authorizationId,
            Outcome outcome,
            String reason   // populated for DECLINED, may be null otherwise
    ) {}

    enum Outcome {
        AUTHORIZED,
        DECLINED
    }
}
