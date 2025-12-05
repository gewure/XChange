package org.knowm.xchange.bitpanda;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.bitpanda.dto.BitpandaResponse;
import org.knowm.xchange.bitpanda.dto.marketdata.BitpandaAsset;
import org.knowm.xchange.bitpanda.dto.trade.BitpandaTrade;

@Path("v1")
@Produces(MediaType.APPLICATION_JSON)
public interface Bitpanda {

  @GET
  @Path("trades")
  BitpandaResponse getTrades(@HeaderParam("X-Api-Key") String apiKey, @QueryParam("cursor") String cursor,
      @QueryParam("page_size") Integer pageSize) throws IOException;

  @GET
  @Path("asset-wallets")
  BitpandaResponse getAssetWallets(@HeaderParam("X-Api-Key") String apiKey) throws IOException;

  @GET
  @Path("ticker")
  Map<String, Map<String, String>> getTicker() throws IOException;

  @GET
  @Path("trades")
  BitpandaResponse<List<BitpandaTrade>> getTrades(
      @HeaderParam("X-Api-Key") String apiKey,
      @QueryParam("type") String type,
      @QueryParam("cursor") String cursor,
      @QueryParam("page_size") Integer pageSize)
      throws IOException;

  @GET
  @Path("assets")
  BitpandaResponse<List<BitpandaAsset>> getAssets(@HeaderParam("X-Api-Key") String apiKey)
      throws IOException;

  // Add more endpoints as needed
}
