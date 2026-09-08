package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.app.context.ChannelContext;
import com.ofss.digx.app.core.ChannelInteraction;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.appx.AbstractRESTApplication;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordResponseDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/** Authenticated REST boundary for first-time setup and reset of an HTH API password. */
@Tag(name = "Host To Host API Password", description = "HTH API-password self service.")
@Path("/hostToHostApiPassword")
public class HostToHostApiPassword extends AbstractRESTApplication
    implements IHostToHostApiPassword {
  private static final String THIS_COMPONENT_NAME = HostToHostApiPassword.class.getName();
  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();
  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Read HTH API password status and policy")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Status fetched",
          content = @Content(schema = @Schema(implementation = HostToHostApiPasswordResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Request rejected",
          content = @Content(schema = @Schema(implementation = Status.class)))
  })
  public Response status() {
    return invoke(null, "status");
  }

  @POST
  @Path("/setup")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Set up an HTH API password for the first time")
  public Response setup(HostToHostApiPasswordRequestDTO request) {
    return invoke(request, "setup");
  }

  @PUT
  @Path("/reset")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Reset an existing HTH API password")
  public Response reset(HostToHostApiPasswordRequestDTO request) {
    return invoke(request, "reset");
  }

  private Response invoke(HostToHostApiPasswordRequestDTO request, String operation) {
    Response response = null;
    ChannelInteraction interaction = null;
    ChannelContext context = null;
    try {
      context = super.getChannelContext();
      interaction = ChannelInteraction.getInstance();
      interaction.begin(context);
      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword();
      HostToHostApiPasswordResponseDTO result;
      if ("setup".equals(operation)) {
        result = service.setup(context.getSessionContext(), request);
      } else if ("reset".equals(operation)) {
        result = service.reset(context.getSessionContext(), request);
      } else {
        result = service.status(context.getSessionContext());
      }
      response = buildResponse(result, Response.Status.OK);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while invoking HTH API password operation '%s'", operation), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      if (interaction != null && context != null) {
        try {
          interaction.close(context);
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
              "Exception while closing HTH API password channel interaction"), e);
          response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
      }
    }
    return response;
  }
}
