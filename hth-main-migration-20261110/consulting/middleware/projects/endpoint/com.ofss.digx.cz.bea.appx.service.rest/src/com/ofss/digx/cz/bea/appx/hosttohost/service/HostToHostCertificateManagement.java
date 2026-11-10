package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.app.context.ChannelContext;
import com.ofss.digx.app.core.ChannelInteraction;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.appx.AbstractRESTApplication;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateFileDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateSubmitDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateSubmitFileDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.Override;
import java.lang.String;
import java.util.ArrayList;
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
        name = "Host To Host Certificate Management",
        description = "Maintenance of Host To Host corporate public certificates."
)
@Path("/hostToHostCertificate")
public class HostToHostCertificateManagement extends AbstractRESTApplication
        implements IHostToHostCertificateManagement {
    private static final String THIS_COMPONENT_NAME = HostToHostCertificateManagement.class.getName();

    private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

    private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    @Operation(
            summary = "SEARCH Host To Host Certificate",
            description = "Search current Host To Host corporate public certificates.",
            tags = "Host To Host Certificate Management",
            operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.search"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Host To Host certificates fetched successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = HostToHostCertificateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation Failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
    })
    public Response search() {
        Response response = null;
        ChannelInteraction channelInteraction = null;
        ChannelContext channelContext = null;

        try {
            channelContext = super.getChannelContext();
            channelInteraction = ChannelInteraction.getInstance();
            channelInteraction.begin(channelContext);

            com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement service =
                    new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement();
            HostToHostCertificateResponseDTO responseDTO = service.search(channelContext.getSessionContext());
            response = buildResponse(responseDTO, Response.Status.OK);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
                    "Exception encountered while invoking search service %s", THIS_COMPONENT_NAME), e);
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
            summary = "SUBMIT Host To Host Certificate",
            description = "Submit Host To Host corporate public certificates.",
            tags = "Host To Host Certificate Management",
            operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.submit"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Host To Host certificate upload submitted",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = HostToHostCertificateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation Failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Status.class)))
    })
    public Response submit(
            @RequestBody(description = "Host To Host certificate upload request", required = true,
                    content = @Content(schema = @Schema(implementation = HostToHostCertificateSubmitDTO.class)))
            HostToHostCertificateSubmitDTO requestDTO) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, FORMATTER.formatMessage(
                    "Entering submit HostToHostCertificateManagement, certificateCount: %s",
                    requestDTO == null || requestDTO.getCertificates() == null
                            ? 0 : requestDTO.getCertificates().size()));
        }

        Response response = null;
        ChannelInteraction channelInteraction = null;
        ChannelContext channelContext = null;

        try {
            channelContext = super.getChannelContext();
            channelInteraction = ChannelInteraction.getInstance();
            channelInteraction.begin(channelContext);

            com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement service =
                    new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement();
            HostToHostCertificateResponseDTO responseDTO =
                    service.submit(channelContext.getSessionContext(), toInternalRequest(requestDTO));
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

    private HostToHostCertificateDTO toInternalRequest(HostToHostCertificateSubmitDTO requestDTO) {
        HostToHostCertificateDTO internalRequest = new HostToHostCertificateDTO();
        if (requestDTO != null) {
            internalRequest.setCertificates(toInternalCertificateFiles(requestDTO.getCertificates()));
        }
        return internalRequest;
    }

    private List<HostToHostCertificateFileDTO> toInternalCertificateFiles(
            List<HostToHostCertificateSubmitFileDTO> submittedFiles) {
        if (submittedFiles == null) {
            return null;
        }

        List<HostToHostCertificateFileDTO> certificates =
                new ArrayList<HostToHostCertificateFileDTO>();
        for (HostToHostCertificateSubmitFileDTO submittedFile : submittedFiles) {
            HostToHostCertificateFileDTO certificate = new HostToHostCertificateFileDTO();
            if (submittedFile != null) {
                certificate.setFileName(submittedFile.getFileName());
                certificate.setPemContent(submittedFile.getPemContent());
            }
            certificates.add(certificate);
        }
        return certificates;
    }

    @GET
    @Path("/download")
    @Produces("application/x-pem-file")
    @Override
    @Operation(
            summary = "DOWNLOAD Host To Host Certificate",
            description = "Download Host To Host corporate public certificate.",
            tags = "Host To Host Certificate Management",
            operationId = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.download"
    )
    public Response download(
            @Parameter(in = ParameterIn.QUERY, name = "certificateId", description = "Certificate ID",
                    schema = @Schema(type = "String")) @QueryParam("certificateId") String certificateId) {
        Response response = null;
        ChannelInteraction channelInteraction = null;
        ChannelContext channelContext = null;

        try {
            channelContext = super.getChannelContext();
            channelInteraction = ChannelInteraction.getInstance();
            channelInteraction.begin(channelContext);

            com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement service =
                    new com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement();
            HostToHostCertificateFileDTO certificateDTO =
                    service.download(channelContext.getSessionContext(), certificateId);
            String downloadFileName = sanitizeFileName(certificateDTO.getFileName());
            response = Response.ok(certificateDTO.getPemContent(), "application/x-pem-file")
                    .header("Content-Disposition", "attachment; filename=\"" + downloadFileName + "\"")
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
                    "Exception encountered while invoking download service %s", THIS_COMPONENT_NAME), e);
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

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "certificate.pem";
        }
        return fileName.replaceAll("[\\\\/\\r\\n\"]", "_");
    }
}
