package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.app.context.ChannelContext;
import com.ofss.digx.app.core.ChannelInteraction;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.appx.AbstractRESTApplication;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessSearchDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST facade for HTH user access summary and maker/checker maintenance operations.
 *
 * <p>The facade owns channel interaction lifecycle only. Validation, approval snapshot handling,
 * and effective authorization changes remain in the application service.
 */
@Tag(
    name = "Host To Host User Access",
    description = "HTH user account and API access maintenance.")
@Path("/hostToHostUserAccess")
public class HostToHostUserAccess extends AbstractRESTApplication
    implements IHostToHostUserAccess {
  private static final String THIS_COMPONENT_NAME = HostToHostUserAccess.class.getName();

  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

  @GET
  @Path("/search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "Search HTH User Access Summary",
      description = "Returns effective and pending HTH user account access by company.",
      tags = "Host To Host User Access",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "HTH user access summary fetched successfully",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = HostToHostUserAccessResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Validation failure",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Status.class))),
      @ApiResponse(
          responseCode = "500",
          description = "Internal server error",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Status.class)))
  })
  public Response search(
      @Parameter(
          in = ParameterIn.QUERY,
          name = "partyId",
          description = "Corporate party ID",
          schema = @Schema(type = "String"))
      @QueryParam("partyId") String partyId,
      @Parameter(
          in = ParameterIn.QUERY,
          name = "closeId",
          description = "HTH user CloseID",
          schema = @Schema(type = "String"))
      @QueryParam("closeId") String closeId) {
    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      HostToHostUserAccessSearchDTO requestDTO = new HostToHostUserAccessSearchDTO();
      requestDTO.setPartyId(partyId);
      requestDTO.setCloseId(closeId);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess();
      HostToHostUserAccessResponseDTO responseDTO =
          service.search(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.OK);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while invoking HTH user access search for party '%s'", partyId), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      if (channelInteraction != null && channelContext != null) {
        try {
          channelInteraction.close(channelContext);
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
              "Exception while closing HTH user access channel interaction"), e);
          response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
      }
    }

    return response;
  }

  @GET
  @Path("/accounts")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "Read eligible HTH accounts and API mapping",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Eligible HTH accounts and APIs fetched",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostUserAccessResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation failure",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal server error",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = Status.class)))
  })
  public Response accounts(
      @Parameter(description = "Primary corporate party owning the HTH user")
      @QueryParam("partyId") String partyId,
      @Parameter(description = "HTH user CloseID")
      @QueryParam("closeId") String closeId,
      @Parameter(description = "Selected BCO user identifier used by Account Access")
      @QueryParam("username") String username,
      @Parameter(description = "Party owning the eligible accounts")
      @QueryParam("accessPartyId") String accessPartyId,
      @Parameter(description = "Company context: RELATED or ASSOCIATED")
      @QueryParam("linkageType") String linkageType,
      @Parameter(description = "Return only a platform-ready pending approval reference")
      @QueryParam("approvalReferenceOnly") Boolean approvalReferenceOnly) {
    Response response = null;
    ChannelInteraction interaction = null;
    ChannelContext context = null;
    try {
      context = super.getChannelContext();
      interaction = ChannelInteraction.getInstance();
      interaction.begin(context);
      HostToHostUserAccessSearchDTO request = new HostToHostUserAccessSearchDTO();
      request.setPartyId(partyId);
      request.setCloseId(closeId);
      request.setUsername(username);
      request.setAccessPartyId(accessPartyId);
      request.setLinkageType(linkageType);
      request.setApprovalReferenceOnly(approvalReferenceOnly);
      HostToHostUserAccessResponseDTO result =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess()
              .accounts(context.getSessionContext(), request);
      response = buildResponse(result, Response.Status.OK);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while reading HTH user access accounts for party '%s'", partyId), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      response = closeInteraction(interaction, context, response);
    }
    return response;
  }

  @POST
  @Path("/submit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Submit HTH user access for approval",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "HTH user access submitted",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostUserAccessResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation failure",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = Status.class)))
  })
  public Response submit(HostToHostUserAccessDTO requestDTO) {
    return invokeWrite(requestDTO, "submit");
  }

  @POST
  @Path("/edit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Submit HTH user access edit for approval",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "HTH user access edit submitted",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostUserAccessResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation failure",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = Status.class)))
  })
  public Response edit(HostToHostUserAccessDTO requestDTO) {
    return invokeWrite(requestDTO, "edit");
  }

  @POST
  @Path("/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(summary = "Submit HTH user access deletion for approval",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "HTH user access deletion submitted",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostUserAccessResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation failure",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = Status.class)))
  })
  public Response delete(HostToHostUserAccessDTO requestDTO) {
    return invokeWrite(requestDTO, "delete");
  }

  private Response invokeWrite(HostToHostUserAccessDTO requestDTO, String action) {
    Response response = null;
    ChannelInteraction interaction = null;
    ChannelContext context = null;
    try {
      context = super.getChannelContext();
      interaction = ChannelInteraction.getInstance();
      interaction.begin(context);
      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess();
      HostToHostUserAccessResponseDTO result;
      if ("edit".equals(action)) {
        result = service.edit(context.getSessionContext(), requestDTO);
      } else if ("delete".equals(action)) {
        result = service.delete(context.getSessionContext(), requestDTO);
      } else {
        result = service.submit(context.getSessionContext(), requestDTO);
      }
      response = buildResponse(result, Response.Status.CREATED);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while processing HTH user access action '%s'", action), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      response = closeInteraction(interaction, context, response);
    }
    return response;
  }

  private Response closeInteraction(ChannelInteraction interaction,
      ChannelContext context, Response response) {
    if (interaction == null || context == null) {
      return response;
    }
    try {
      interaction.close(context);
      return response;
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while closing HTH user access channel interaction"), e);
      return buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
    }
  }
}
