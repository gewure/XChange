package org.knowm.xchange.polymarket;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public interface Polymarket {

  @GET
  @Path("book")
  PolymarketOrderBook getOrderBook(@QueryParam("token_id") String tokenId) throws PolymarketException, IOException;

  @POST
  @Path("order")
  @Consumes(MediaType.APPLICATION_JSON)
  Object postOrder(Object payload) throws PolymarketException, IOException;
}
