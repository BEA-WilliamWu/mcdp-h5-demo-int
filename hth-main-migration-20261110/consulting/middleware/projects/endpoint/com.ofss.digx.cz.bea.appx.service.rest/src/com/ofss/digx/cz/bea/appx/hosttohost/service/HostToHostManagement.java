package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.app.context.ChannelContext;
import com.ofss.digx.app.core.ChannelInteraction;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.appx.AbstractRESTApplication;
import com.ofss.digx.cz.bea.app.hosttohost.dto.EligibleAccountDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementResponseDTO;
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
import java.lang.Override;
import java.lang.String;
import java.util.List;
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

@Tag(
    name = "Host To Host Management",
    description = "Maintenance of Host To Host management records."
)
@Path("/hostToHostManagement")
public class HostToHostManagement extends AbstractRESTApplication
    implements IHostToHostManagement {
  private static final String THIS_COMPONENT_NAME = HostToHostManagement.class.getName();

  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

  @GET
  @Path("/search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "SEARCH Host To Host Management",
      description = "search method of class HostToHostManagement",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.search"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Host To Host Management fetched Successfully",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostManagementResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation Failure",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
  })
  public Response search(
      @Parameter(in = ParameterIn.QUERY, name = "partyId", description = "Party ID",
          schema = @Schema(type = "String")) @QueryParam("partyId") String partyId) {
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.log(Level.FINE, FORMATTER.formatMessage(
          "Entering search HostToHostManagement, partyId: %s", partyId));
    }

    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    HostToHostManagementResponseDTO responseDTO = null;
    HostToHostManagementDTO requestDTO = new HostToHostManagementDTO();

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      requestDTO.setPartyId(partyId);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      responseDTO = service.search(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.OK);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking the service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }

  @GET
  @Path("/validateUamClientId")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "VALIDATE HTH UAM Client ID",
      description = "validate UAM Client ID for Host To Host Management",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.validateUamClientId"
  )
  public Response validateUamClientId(
      @Parameter(in = ParameterIn.QUERY, name = "partyId", description = "Party ID",
          schema = @Schema(type = "String")) @QueryParam("partyId") String partyId,
      @Parameter(in = ParameterIn.QUERY, name = "correlateUamClientId", description = "Correlate UAM Client ID",
          schema = @Schema(type = "String")) @QueryParam("correlateUamClientId") String correlateUamClientId) {
    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    HostToHostManagementResponseDTO responseDTO = null;
    HostToHostManagementDTO requestDTO = new HostToHostManagementDTO();

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      requestDTO.setPartyId(partyId);
      requestDTO.setCorrelateUamClientId(correlateUamClientId);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      responseDTO = service.validateUamClientId(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.OK);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking validateUamClientId service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }

  @GET
  @Path("/eligibleChargeAccounts")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "GET Eligible Charge Accounts",
      description = "Fetch eligible charge accounts for a corporate customer filtered by product type and code",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.getEligibleChargeAccounts"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Eligible charge accounts fetched successfully",
          content = @Content(mediaType = "application/json")),
      @ApiResponse(responseCode = "400", description = "Validation Failure",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
  })
  public Response getEligibleChargeAccounts(
      @Parameter(in = ParameterIn.QUERY, name = "partyId", description = "Party ID",
          schema = @Schema(type = "String")) @QueryParam("partyId") String partyId,
      @Parameter(in = ParameterIn.QUERY, name = "accountNumber", description = "Account Number",
          schema = @Schema(type = "String")) @QueryParam("accountNumber") String accountNumber) {
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.log(Level.FINE, FORMATTER.formatMessage(
          "Entering getEligibleChargeAccounts, partyId: %s, accountNumber: %s", partyId, accountNumber));
    }

    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    List<EligibleAccountDTO> eligibleAccounts = null;

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      eligibleAccounts = service.getEligibleChargeAccounts(channelContext.getSessionContext(), partyId, accountNumber);
      response = Response.ok(eligibleAccounts).build();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking getEligibleChargeAccounts service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }

  @POST
  @Path("/submit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "SUBMIT Host To Host Management",
      description = "submit method of class HostToHostManagement",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.submit"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Host To Host Management submitted for approval",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostManagementResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation Failure",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
  })
  public Response submit(HostToHostManagementDTO requestDTO) {
    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    HostToHostManagementResponseDTO responseDTO = null;

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      responseDTO = service.submit(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.CREATED);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking submit service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }

  @POST
  @Path("/edit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "EDIT Host To Host Management",
      description = "edit method of class HostToHostManagement",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.edit"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Host To Host Management edit submitted for approval",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostManagementResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation Failure",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
  })
  public Response edit(HostToHostManagementDTO requestDTO) {
    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    HostToHostManagementResponseDTO responseDTO = null;

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      responseDTO = service.edit(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.CREATED);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking edit service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }

  @POST
  @Path("/disable")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Override
  @Operation(
      summary = "DISABLE Host To Host Management",
      description = "disable method of class HostToHostManagement",
      tags = "Host To Host Management",
      operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.disable"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "202", description = "Host To Host Management disable submitted for approval",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = HostToHostManagementResponseDTO.class))),
      @ApiResponse(responseCode = "400", description = "Validation Failure",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
  })
  public Response disable(HostToHostManagementDTO requestDTO) {
    Response response = null;
    ChannelInteraction channelInteraction = null;
    ChannelContext channelContext = null;
    HostToHostManagementResponseDTO responseDTO = null;

    try {
      channelContext = super.getChannelContext();
      channelInteraction = ChannelInteraction.getInstance();
      channelInteraction.begin(channelContext);

      com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement service =
          new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement();
      responseDTO = service.disable(channelContext.getSessionContext(), requestDTO);
      response = buildResponse(responseDTO, Response.Status.CREATED);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception encountered while invoking disable service %s", THIS_COMPONENT_NAME), e);
      response = buildResponse(e, Response.Status.BAD_REQUEST);
    } finally {
      try {
        channelInteraction.close(channelContext);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception while closing the channel interaction", THIS_COMPONENT_NAME), e);
        response = buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
      }
    }

    return response;
  }
}
