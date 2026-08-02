package com.example.order.infrastructure.inventory;

import com.example.order.infrastructure.inventory.dto.InventoryReserveRequestDto;
import com.example.order.infrastructure.inventory.dto.InventoryReserveResponseDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client interface for the Inventory service's reserve
 * endpoint.
 *
 * <p>{@code configKey} aligns with the prefix in {@code application.properties}:
 *
 * <pre>{@code
 *   quarkus.rest-client."inventory-rest".url=http://localhost:8081
 * }</pre>
 *
 * <p>This is a transport concern - it speaks Inventory's wire types, throws
 * Jakarta-REST exceptions on transport failure, and has no notion of
 * Order's domain. The {@code InventoryRestAdapter} wraps it to provide the
 * domain-language {@link com.example.order.domain.outbound.InventoryPort}.
 */
@RegisterRestClient(configKey = "inventory-rest")
@Path("/api/inventory")
public interface InventoryRestClient {

    @POST
    @Path("/reserve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    InventoryReserveResponseDto reserve(InventoryReserveRequestDto request);
}
