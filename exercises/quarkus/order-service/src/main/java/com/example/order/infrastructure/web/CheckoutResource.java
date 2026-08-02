package com.example.order.infrastructure.web;

import com.example.order.application.CheckoutCommand;
import com.example.order.application.CheckoutResult;
import com.example.order.application.CheckoutSaga;
import com.example.order.domain.model.CartId;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.LineItem;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Sku;
import com.example.order.infrastructure.web.dto.CheckoutRequestDto;
import com.example.order.infrastructure.web.dto.CheckoutResponseDto;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * JAX-RS resource for checkout requests.
 *
 * <p>Web is an adapter, not a domain concern - this class translates
 * inbound HTTP DTOs into a domain {@link CheckoutCommand}, calls the
 * application use case, translates the result back to a JSON response.
 * No business logic lives here.
 *
 * <p>The resource follows the same architectural discipline as the
 * outbound adapters: <em>web vocabulary in, domain vocabulary out (to
 * the saga)</em>, then domain vocabulary in, web vocabulary out (in the
 * response). Two translation steps that mirror each other.
 */
@Path("/api/orders")
public class CheckoutResource {

    private final CheckoutSaga saga;

    public CheckoutResource(CheckoutSaga saga) {
        this.saga = saga;
    }

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkout(CheckoutRequestDto request) {
        CheckoutCommand command = toCommand(request);
        CheckoutResult result = saga.checkout(command);

        return switch (result) {
            case CheckoutResult.Confirmed c -> Response
                    .status(Response.Status.CREATED)
                    .entity(CheckoutResponseDto.confirmed(c))
                    .build();
            case CheckoutResult.Cancelled x -> Response
                    .status(422)
                    .entity(CheckoutResponseDto.cancelled(x))
                    .build();
        };
    }

    // ------------------------------------------------------------------------
    // Translation: web DTO -> domain CheckoutCommand
    // ------------------------------------------------------------------------
    // Validation lives in the domain types' compact constructors -
    // OrderId.of, Money.usd, LineItem record, etc. - so any malformed
    // input throws IllegalArgumentException, which Quarkus's default
    // exception mapping turns into a 400 Bad Request.
    private static CheckoutCommand toCommand(CheckoutRequestDto request) {
        List<LineItem> domainLines = request.lineItems().stream()
                .map(li -> new LineItem(
                        Sku.of(li.sku()),
                        li.quantity(),
                        Money.usd(li.unitPrice())))
                .toList();

        return new CheckoutCommand(
                CustomerId.of(request.customerId()),
                CartId.of(request.cartId()),
                domainLines);
    }
}
