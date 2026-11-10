package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateFileDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateResponseDTO;
import com.ofss.digx.common.constants.ApprovalsErrorConstants;
import com.ofss.digx.cz.bea.common.constants.CZFieldConstants;
import com.ofss.digx.cz.bea.common.constants.HostToHostCertificateErrorConstants;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificate;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequestKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthCertificateRequestRepositoryAdapter;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.infra.exceptions.ExceptionTransformerFactory;
import com.ofss.digx.infra.exceptions.IExceptionTransformer;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.validation.error.ValidationError;
import com.ofss.fc.service.response.TransactionStatus;

import java.io.ByteArrayInputStream;
import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HostToHostCertificateManagement extends AbstractApplication
        implements IHostToHostCertificateManagement {
    private static final String THIS_COMPONENT_NAME = HostToHostCertificateManagement.class.getName();

    private static final MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();

    private static final Logger logger = formatter.getLogger(THIS_COMPONENT_NAME);

    private static final String SEARCH_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.search";
    private static final String SUBMIT_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.submit";
    private static final String DOWNLOAD_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostCertificateManagement.download";

    private static final int MAX_CERTIFICATE_COUNT = 2;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_PEM_CONTENT_BYTES = 64 * 1024;
    private static final int MIN_RSA_KEY_SIZE = 2048;
    private static final String ACTION_UPLOAD = "UPLOAD";
    private static final String HTH_STATUS_ENABLE = "ENABLE";
    private static final String OBJECT_STATUS_ACTIVE = "A";
    private static final String OBJECT_STATUS_INACTIVE = "I";
    private static final List<String> SUPPORTED_PEM_LABELS =
            Arrays.asList("CERTIFICATE", "PUBLIC KEY", "RSA PUBLIC KEY");

    @Override
    @Entitlement(name = "Search Host To Host Certificate", action = ActionType.VIEW, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
            subCategory = EntitlementSubCategory.Party_Preference)
    public HostToHostCertificateResponseDTO search(SessionContext sessionContext) throws Exception {
        super.checkAccessPolicy(SEARCH_SERVICE_ID, sessionContext);

        HostToHostCertificateResponseDTO response = new HostToHostCertificateResponseDTO();
        response.setStatus(fetchStatus());
        TransactionStatus transactionStatus = fetchTransactionStatus();
        Interaction.begin(sessionContext);

        try {
            String normalizedPartyId = resolveSessionPartyId(sessionContext);
            assertHostToHostEnabled(normalizedPartyId);
            response.setPartyId(normalizedPartyId);
            response.setAccountNumber(normalizedPartyId);
            response.setCertificates(toDTOList(listActiveCertificateConfigs(normalizedPartyId), false));
            response.setStatus(buildStatus(transactionStatus));
        } catch (Exception e) {
            fillTransactionStatus(transactionStatus, e);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "Exception from search() in class %s", THIS_COMPONENT_NAME), e);
            throw e;
        } catch (RuntimeException rte) {
            fillTransactionStatus(transactionStatus, rte);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "RuntimeException from search() in class %s", THIS_COMPONENT_NAME), rte);
            throw new Exception(rte);
        } finally {
            Interaction.close();
        }

        super.checkResponsePolicy(sessionContext, response);
        return response;
    }

    @Override
    @Entitlement(name = "Submit Host To Host Certificate", action = ActionType.PERFORM, requiredResources = {})
    @Entitlement(name = "Submit Host To Host Certificate", action = ActionType.APPROVE, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
            subCategory = EntitlementSubCategory.Party_Preference)
    @Task(id = "PP_N_HTH_CERT_UPL", parent = "AH_MN", name = "Host to Host Certificate Management - Upload",
            supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
            aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT,
                    TaskAspect.TWO_FACTOR_AUTHENTICATION},
            type = TaskType.MAINTENANCE)
    public HostToHostCertificateResponseDTO submit(
            SessionContext sessionContext, HostToHostCertificateDTO requestDTO) throws Exception {
        HostToHostCertificateResponseDTO response = new HostToHostCertificateResponseDTO();
        response.setStatus(fetchStatus());
        TransactionStatus transactionStatus = fetchTransactionStatus();
        boolean approvedExecution = isApprovedExecution();
        String referenceNumber = initializeReferenceNumber(requestDTO, approvedExecution);
        String frameworkReferenceNumber = null;
        boolean payloadValidated = false;

        Interaction.begin(sessionContext);

        try {
            if (!approvedExecution) {
                requestDTO = prepareNewSubmitRequest(requestDTO, sessionContext);
            }
            List<HostToHostCertificateFileDTO> validatedCertificates = prepareCertificatePayload(requestDTO);
            payloadValidated = true;
            super.checkAccessPolicy(SUBMIT_SERVICE_ID, sessionContext, requestDTO);

            if (approvedExecution) {
                String transactionId = normalize(readTransactionId());
                frameworkReferenceNumber = transactionId;
                referenceNumber = executeApprovedSubmit(sessionContext, requestDTO, transactionId);
            } else {
                frameworkReferenceNumber = createRequestSnapshotBestEffort(requestDTO, referenceNumber, sessionContext);
            }

            populateSubmitResponse(response, requestDTO, validatedCertificates, transactionStatus, referenceNumber);
        } catch (Exception e) {
            handleSubmitException(transactionStatus, e, requestDTO, referenceNumber, sessionContext,
                    approvedExecution, payloadValidated);
            throw e;
        } catch (RuntimeException rte) {
            handleSubmitException(transactionStatus, rte, requestDTO, referenceNumber, sessionContext,
                    approvedExecution, payloadValidated);
            throw new Exception(rte);
        } finally {
            Interaction.close();
        }

        if (!approvedExecution) {
            setExternalReferenceNumber(referenceNumber);
        }
        super.checkResponsePolicy(sessionContext, response);
        if (!approvedExecution) {
            setExternalReferenceNumber(referenceNumber);
        }
        populateFrameworkReferenceNumber(response, frameworkReferenceNumber);
        return response;
    }

    private String initializeReferenceNumber(
            HostToHostCertificateDTO requestDTO, boolean approvedExecution) {
        if (approvedExecution) {
            return null;
        }

        String referenceNumber = generateReferenceNumber();
        if (requestDTO != null) {
            requestDTO.setReferenceNumber(referenceNumber);
        }
        return referenceNumber;
    }

    private HostToHostCertificateDTO prepareNewSubmitRequest(
            HostToHostCertificateDTO requestDTO, SessionContext sessionContext) throws Exception {
        requestDTO = applySessionParty(requestDTO, sessionContext);
        checkPendingRequest(requestDTO == null ? null : requestDTO.getPartyId());
        return requestDTO;
    }

    private String executeApprovedSubmit(
            SessionContext sessionContext, HostToHostCertificateDTO requestDTO,
            String transactionId) throws Exception {
        String referenceNumber = resolveApprovedReferenceNumber(requestDTO, transactionId);
        if (!isBlank(referenceNumber)) {
            requestDTO.setReferenceNumber(referenceNumber);
        }
        applyApprovedConfiguration(requestDTO, readUserId(sessionContext), transactionId);
        return referenceNumber;
    }

    private void populateSubmitResponse(
            HostToHostCertificateResponseDTO response, HostToHostCertificateDTO requestDTO,
            List<HostToHostCertificateFileDTO> validatedCertificates, TransactionStatus transactionStatus,
            String referenceNumber) {
        populateResponse(response, requestDTO, validatedCertificates);
        response.setStatus(buildStatus(transactionStatus));
        if (!isBlank(referenceNumber)) {
            response.getStatus().setExternalReferenceNumber(referenceNumber);
        }
    }

    private void handleSubmitException(
            TransactionStatus transactionStatus, Exception exception, HostToHostCertificateDTO requestDTO,
            String referenceNumber, SessionContext sessionContext, boolean approvedExecution,
            boolean payloadValidated) {
        fillTransactionStatus(transactionStatus, exception);
        logSubmitException(exception, requestDTO, referenceNumber, sessionContext, approvedExecution,
                payloadValidated, "Exception");
    }

    private void handleSubmitException(
            TransactionStatus transactionStatus, RuntimeException exception, HostToHostCertificateDTO requestDTO,
            String referenceNumber, SessionContext sessionContext, boolean approvedExecution,
            boolean payloadValidated) {
        fillTransactionStatus(transactionStatus, exception);
        logSubmitException(exception, requestDTO, referenceNumber, sessionContext, approvedExecution,
                payloadValidated, "RuntimeException");
    }

    private void logSubmitException(
            java.lang.Exception exception, HostToHostCertificateDTO requestDTO, String referenceNumber,
            SessionContext sessionContext, boolean approvedExecution, boolean payloadValidated,
            String exceptionType) {
        if (payloadValidated) {
            createRequestSnapshotIfTransactionStarted(requestDTO, referenceNumber, sessionContext, approvedExecution);
        }
        logger.log(Level.SEVERE, formatter.formatMessage(
                "%s from submit() for request '%s' in class %s",
                exceptionType, describeRequest(requestDTO), THIS_COMPONENT_NAME), exception);
    }

    @Override
    @Entitlement(name = "Download Host To Host Certificate", action = ActionType.VIEW, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
            subCategory = EntitlementSubCategory.Party_Preference)
    public HostToHostCertificateFileDTO download(SessionContext sessionContext, String certificateId) throws Exception {
        super.checkAccessPolicy(DOWNLOAD_SERVICE_ID, sessionContext);

        HostToHostCertificateFileDTO response = null;
        TransactionStatus transactionStatus = fetchTransactionStatus();
        Interaction.begin(sessionContext);

        try {
            String normalizedPartyId = resolveSessionPartyId(sessionContext);
            assertHostToHostEnabled(normalizedPartyId);

            String normalizedCertificateId = normalize(certificateId);
            if (normalizedCertificateId == null) {
                throw validationException(HostToHostCertificateErrorConstants.ERROR_CERTIFICATE_ID_REQUIRED);
            }

            response = findActiveCertificateFile(normalizedPartyId, normalizedCertificateId);
            if (response == null) {
                throw validationException(HostToHostCertificateErrorConstants.ERROR_CERTIFICATE_NOT_AVAILABLE);
            }
        } catch (Exception e) {
            fillTransactionStatus(transactionStatus, e);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "Exception from download() for certificateId '%s' in class %s", certificateId, THIS_COMPONENT_NAME), e);
            throw e;
        } catch (RuntimeException rte) {
            fillTransactionStatus(transactionStatus, rte);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "RuntimeException from download() for certificateId '%s' in class %s",
                    certificateId, THIS_COMPONENT_NAME), rte);
            throw new Exception(rte);
        } finally {
            Interaction.close();
        }

        super.checkResponsePolicy(sessionContext, response);
        return response;
    }

    private List<HostToHostCertificateFileDTO> prepareCertificatePayload(
            HostToHostCertificateDTO requestDTO) throws Exception {
        if (requestDTO == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_REQUEST_REQUIRED);
        }

        String partyId = requirePartyId(requestDTO.getPartyId());
        requestDTO.setPartyId(partyId);
        assertHostToHostEnabled(partyId);

        List<HostToHostCertificateFileDTO> certificates = requestDTO.getCertificates();
        if (certificates == null || certificates.isEmpty()) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_CERTIFICATE_REQUIRED);
        }
        if (certificates.size() > MAX_CERTIFICATE_COUNT) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_CERTIFICATE_LIMIT);
        }

        Set<String> uploadedFileNames = new HashSet<String>();
        Set<String> uploadedKeyFingerprints = new HashSet<String>();
        Set<String> existingKeyFingerprints = getExistingPublicKeyFingerprints(partyId);
        List<HostToHostCertificateFileDTO> validated = new ArrayList<HostToHostCertificateFileDTO>();
        for (HostToHostCertificateFileDTO certificateDTO : certificates) {
            validated.add(validatePublicKey(certificateDTO, uploadedFileNames,
                    uploadedKeyFingerprints, existingKeyFingerprints));
        }

        requestDTO.setCertificates(validated);
        return validated;
    }

    private HostToHostCertificateFileDTO validatePublicKey(
            HostToHostCertificateFileDTO certificateDTO, Set<String> uploadedFileNames,
            Set<String> uploadedKeyFingerprints, Set<String> existingKeyFingerprints) throws Exception {
        if (certificateDTO == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_FILE_NAME_REQUIRED);
        }

        String fileName = validateFileName(certificateDTO.getFileName());
        validateUniqueUploadedFileName(uploadedFileNames, fileName);
        String pemContent = normalize(certificateDTO.getPemContent());
        if (pemContent == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_CONTENT_REQUIRED);
        }
        if (pemContent.getBytes(StandardCharsets.UTF_8).length > MAX_PEM_CONTENT_BYTES) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_CONTENT_TOO_LARGE);
        }
        if (pemContent.toUpperCase(Locale.ENGLISH).contains("PRIVATE KEY")) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_PRIVATE_KEY_NOT_ALLOWED);
        }

        String pemLabel = resolveSinglePemLabel(pemContent);
        RSAPublicKey publicKey = readPublicKey(pemContent, pemLabel);
        validateRsaKeySize(publicKey);
        validateUniqueUploadedKeys(uploadedKeyFingerprints, publicKey);
        validateAgainstExistingKeys(existingKeyFingerprints, publicKey);

        HostToHostCertificateFileDTO validated = new HostToHostCertificateFileDTO();
        validated.setCertificateId(resolveCertificateFileId(certificateDTO));
        validated.setFileName(fileName);
        validated.setPemContent(pemContent);
        return validated;
    }

    private void applyApprovedConfiguration(
            HostToHostCertificateDTO requestDTO, String userId, String transactionId) throws Exception {
        deactivateExistingCertificates(requestDTO.getPartyId(), userId);
        createApprovedCertificates(requestDTO, userId, transactionId);
    }

    private void deactivateExistingCertificates(String partyId, String userId) throws Exception {
        List<HthCertificate> existingCertificates = listActiveCertificateConfigs(partyId);
        for (HthCertificate existing : existingCertificates) {
            existing.setObjectStatus(OBJECT_STATUS_INACTIVE);
            existing.setLastUpdatedBy(userId);
            existing.update(existing);
        }
    }

    private void createApprovedCertificates(
            HostToHostCertificateDTO requestDTO, String userId, String transactionId) throws Exception {
        List<HostToHostCertificateFileDTO> certificates = requestDTO.getCertificates();
        for (int i = 0; i < certificates.size(); i++) {
            HthCertificate certificate = createApprovedCertificate(
                    requestDTO, certificates.get(i), Integer.valueOf(i + 1), userId, transactionId);
            certificate.create(certificate);
        }
    }

    private HthCertificate createApprovedCertificate(
            HostToHostCertificateDTO requestDTO, HostToHostCertificateFileDTO certificateDTO, Integer seqNo,
            String userId, String transactionId) {
        HthCertificate certificate = new HthCertificate();
        HthCertificateKey key = new HthCertificateKey();
        key.setId(resolveCertificateFileId(certificateDTO));
        certificate.setKey(key);
        certificate.setPartyId(requestDTO.getPartyId());
        certificate.setSeqNo(seqNo);
        certificate.setFileName(certificateDTO.getFileName());
        certificate.setPemContent(certificateDTO.getPemContent());
        certificate.setReferenceNo(requestDTO.getReferenceNumber());
        certificate.setEffectiveTransactionId(transactionId);
        certificate.setObjectStatus(OBJECT_STATUS_ACTIVE);
        certificate.setCreatedBy(userId);
        certificate.setLastUpdatedBy(userId);
        return certificate;
    }

    private String createRequestSnapshotBestEffort(
            HostToHostCertificateDTO requestDTO, String referenceNumber, SessionContext sessionContext) {
        String transactionId = null;
        try {
            transactionId = requireTransactionId();
            persistRequestSnapshotInNewTransaction(
                    requestDTO, referenceNumber, transactionId, readUserId(sessionContext));
        } catch (Exception e) {
            logRequestSnapshotFailure(requestDTO, e);
        } catch (RuntimeException e) {
            logRequestSnapshotFailure(requestDTO, e);
        }
        return transactionId;
    }

    private void persistRequestSnapshotInNewTransaction(HostToHostCertificateDTO requestDTO,
                                                        String referenceNumber, String transactionId, String userId) throws Exception {
        Session session = null;
        boolean committed = false;
        try {
            session = DataAccessManager.getManager().openNewSession("NONXA");
            session.beginTransaction();

            LocalHthCertificateRequestRepositoryAdapter requestAdapter =
                    LocalHthCertificateRequestRepositoryAdapter.getInstance();
            List<HthCertificateRequest> existingSnapshots = requestAdapter.listActiveByTransactionId(transactionId);
            if (existingSnapshots != null) {
                for (HthCertificateRequest existing : existingSnapshots) {
                    existing.setObjectStatus(OBJECT_STATUS_INACTIVE);
                    existing.setLastUpdatedBy(userId);
                    requestAdapter.update(existing);
                }
            }

            List<HostToHostCertificateFileDTO> certificates = requestDTO.getCertificates();
            for (int i = 0; i < certificates.size(); i++) {
                HthCertificateRequest request = buildRequestSnapshot(
                        requestDTO, certificates.get(i), Integer.valueOf(i + 1), referenceNumber, transactionId, userId);
                requestAdapter.create(request);
            }

            session.flush();
            session.fetchCurrentTransaction().commit();
            committed = true;
        } catch (java.lang.Exception e) {
            throw new Exception(e);
        } finally {
            if (session != null) {
                try {
                    if (!committed) {
                        session.fetchCurrentTransaction().rollback();
                    }
                } finally {
                    DataAccessManager.getManager().closeSession(session);
                }
            }
        }
    }

    private void createRequestSnapshotIfTransactionStarted(
            HostToHostCertificateDTO requestDTO, String referenceNumber, SessionContext sessionContext,
            boolean approvedExecution) {
        try {
            if (approvedExecution) {
                return;
            }

            String transactionId = normalize(readTransactionId());
            if (transactionId == null) {
                return;
            }

            persistRequestSnapshotInNewTransaction(
                    requestDTO, referenceNumber, transactionId, readUserId(sessionContext));
        } catch (Exception e) {
            logRequestSnapshotFallbackFailure(requestDTO, e);
        } catch (RuntimeException e) {
            logRequestSnapshotFallbackFailure(requestDTO, e);
        }
    }

    private void logRequestSnapshotFailure(HostToHostCertificateDTO requestDTO, Throwable throwable) {
        logger.log(Level.SEVERE, formatter.formatMessage(
                "Failed to persist HTH certificate request history snapshot for request '%s' in class %s "
                        + "(non-fatal; approval is unaffected)",
                describeRequest(requestDTO), THIS_COMPONENT_NAME), throwable);
    }

    private void logRequestSnapshotFallbackFailure(HostToHostCertificateDTO requestDTO, Throwable throwable) {
        logger.log(Level.SEVERE, formatter.formatMessage(
                "Failed to create HTH certificate request snapshot in fallback for request '%s' in class %s",
                describeRequest(requestDTO), THIS_COMPONENT_NAME), throwable);
    }

    private HthCertificateRequest buildRequestSnapshot(
            HostToHostCertificateDTO requestDTO, HostToHostCertificateFileDTO certificateDTO, Integer seqNo,
            String referenceNumber, String transactionId, String userId) {
        HthCertificateRequest request = new HthCertificateRequest();
        HthCertificateRequestKey key = new HthCertificateRequestKey();
        key.setId(generateId());
        request.setKey(key);
        request.setCreatedBy(userId);
        fillRequestSnapshot(request, requestDTO, certificateDTO, seqNo, referenceNumber, transactionId, userId);
        return request;
    }

    private void fillRequestSnapshot(HthCertificateRequest request, HostToHostCertificateDTO requestDTO,
                                     HostToHostCertificateFileDTO certificateDTO, Integer seqNo, String referenceNumber,
                                     String transactionId, String userId) {
        request.setTransactionId(transactionId);
        request.setReferenceNo(referenceNumber);
        request.setPartyId(requestDTO.getPartyId());
        request.setActionType(ACTION_UPLOAD);
        request.setSeqNo(seqNo);
        request.setFileName(certificateDTO.getFileName());
        request.setPemContent(certificateDTO.getPemContent());
        request.setObjectStatus(OBJECT_STATUS_ACTIVE);
        request.setLastUpdatedBy(userId);
    }

    private List<HthCertificate> listActiveCertificateConfigs(String partyId) throws Exception {
        HthCertificate certificateDomain = new HthCertificate();
        List<HthCertificate> certificates = certificateDomain.listActiveByPartyId(partyId);
        return certificates == null ? Collections.<HthCertificate>emptyList() : certificates;
    }

    private HostToHostCertificateFileDTO findActiveCertificateFile(String partyId, String certificateId)
            throws Exception {
        List<HthCertificate> certificates = listActiveCertificateConfigs(partyId);
        for (HthCertificate certificate : certificates) {
            if (certificate.getKey() != null && certificateId.equals(normalize(certificate.getKey().getId()))) {
                return toDTO(certificate, true);
            }
        }
        return null;
    }

    private void assertHostToHostEnabled(String partyId) throws Exception {
        HthManagement management = new HthManagement().findActiveByPartyId(partyId);
        String hthStatus = management == null ? null : normalize(management.getHthStatus());
        if (!HTH_STATUS_ENABLE.equals(hthStatus)) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_HTH_NOT_ENABLED);
        }
    }

    private void checkPendingRequest(String partyId) throws Exception {
        String normalizedPartyId = normalize(partyId);
        if (normalizedPartyId == null) {
            return;
        }

        HthCertificateRequest request = new HthCertificateRequest();
        if (!request.existsPendingApproval(normalizedPartyId)) {
            return;
        }

        logger.log(Level.SEVERE, formatter.formatMessage(
                "Duplicate Host to Host Certificate upload request detected for partyId '%s' in class %s",
                normalizedPartyId, THIS_COMPONENT_NAME));
        ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
        IExceptionTransformer transformer = transformerFactory
                .getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
        Exception e = new Exception();
        transformer.translate(e, ApprovalsErrorConstants.DUPLICATE_TRANSACTION,
                HostToHostCertificateManagement.class);
        throw e;
    }

    private void populateResponse(
            HostToHostCertificateResponseDTO response,
            HostToHostCertificateDTO requestDTO,
            List<HostToHostCertificateFileDTO> certificates) {
        response.setPartyId(requestDTO.getPartyId());
        response.setAccountNumber(requestDTO.getPartyId());
        response.setReferenceNumber(requestDTO.getReferenceNumber());
        response.setCertificates(toSafeDTOList(certificates));
    }

    private void populateFrameworkReferenceNumber(
            HostToHostCertificateResponseDTO response, String frameworkReferenceNumber) {
        if (response == null || response.getStatus() == null || isBlank(frameworkReferenceNumber)) {
            return;
        }

        response.getStatus().setReferenceNumber(frameworkReferenceNumber);
    }

    private List<HostToHostCertificateFileDTO> toDTOList(
            List<HthCertificate> certificates, boolean includeContent) throws Exception {
        List<HostToHostCertificateFileDTO> result = new ArrayList<HostToHostCertificateFileDTO>();
        if (certificates == null) {
            return result;
        }
        for (HthCertificate certificate : certificates) {
            result.add(toDTO(certificate, includeContent));
        }
        return result;
    }

    private HostToHostCertificateFileDTO toDTO(
            HthCertificate certificate, boolean includeContent) {
        HostToHostCertificateFileDTO dto = new HostToHostCertificateFileDTO();
        dto.setCertificateId(certificate.getKey() == null ? null : certificate.getKey().getId());
        dto.setFileName(certificate.getFileName());
        if (includeContent) {
            dto.setPemContent(certificate.getPemContent());
        }
        return dto;
    }

    private List<HostToHostCertificateFileDTO> toSafeDTOList(
            List<HostToHostCertificateFileDTO> certificates) {
        List<HostToHostCertificateFileDTO> result = new ArrayList<HostToHostCertificateFileDTO>();
        if (certificates == null) {
            return result;
        }
        for (HostToHostCertificateFileDTO certificate : certificates) {
            result.add(copyCertificate(certificate, false));
        }
        return result;
    }

    private HostToHostCertificateFileDTO copyCertificate(
            HostToHostCertificateFileDTO certificate, boolean includeContent) {
        HostToHostCertificateFileDTO dto = new HostToHostCertificateFileDTO();
        dto.setCertificateId(certificate.getCertificateId());
        dto.setFileName(certificate.getFileName());
        if (includeContent) {
            dto.setPemContent(certificate.getPemContent());
        }
        return dto;
    }

    private String validateFileName(String fileNameValue) throws Exception {
        String fileName = normalize(fileNameValue);
        if (fileName == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_FILE_NAME_REQUIRED);
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0 || fileName.indexOf('\r') >= 0 || fileName.indexOf('\n') >= 0
                || containsControlCharacter(fileName)) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_INVALID_FILE_NAME);
        }
        if (!fileName.toLowerCase(Locale.ENGLISH).endsWith(".pem")) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_PEM_FILE_REQUIRED);
        }
        return fileName;
    }

    private void validateUniqueUploadedFileName(Set<String> uploadedFileNames, String fileName)
            throws Exception {
        String normalizedFileName = fileName.toLowerCase(Locale.ENGLISH);
        if (uploadedFileNames.contains(normalizedFileName)) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_DUPLICATE_FILE_NAME);
        }
        uploadedFileNames.add(normalizedFileName);
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 || character == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getExistingPublicKeyFingerprints(String partyId) throws Exception {
        Set<String> fingerprints = new HashSet<String>();
        List<HthCertificate> existingCertificates = listActiveCertificateConfigs(partyId);
        for (HthCertificate certificate : existingCertificates) {
            String pemContent = normalize(certificate.getPemContent());
            if (pemContent != null) {
                fingerprints.add(fingerprintPublicKey(readPublicKey(pemContent)));
            }
        }
        return fingerprints;
    }

    private void validateUniqueUploadedKeys(Set<String> uploadedKeyFingerprints, RSAPublicKey publicKey)
            throws Exception {
        String fingerprint = fingerprintPublicKey(publicKey);
        if (uploadedKeyFingerprints.contains(fingerprint)) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_DUPLICATE_PUBLIC_KEY);
        }
        uploadedKeyFingerprints.add(fingerprint);
    }

    private void validateAgainstExistingKeys(Set<String> existingKeyFingerprints, RSAPublicKey publicKey)
            throws Exception {
        if (existingKeyFingerprints.contains(fingerprintPublicKey(publicKey))) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_PUBLIC_KEY_REGISTERED);
        }
    }

    private String fingerprintPublicKey(PublicKey publicKey) throws Exception {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new Exception(e);
        }
    }

    private void validateRsaKeySize(RSAPublicKey publicKey) throws Exception {
        if (publicKey.getModulus().bitLength() < MIN_RSA_KEY_SIZE) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_RSA_KEY_TOO_SHORT);
        }
    }

    private String resolveSinglePemLabel(String pemContent) throws Exception {
        if (countOccurrences(pemContent, "-----BEGIN ") != 1
                || countOccurrences(pemContent, "-----END ") != 1) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_SINGLE_PEM_REQUIRED);
        }

        for (String label : SUPPORTED_PEM_LABELS) {
            String beginMarker = "-----BEGIN " + label + "-----";
            String endMarker = "-----END " + label + "-----";
            if (pemContent.contains(beginMarker) && pemContent.contains(endMarker)) {
                return label;
            }
        }

        throw validationException(HostToHostCertificateErrorConstants.ERROR_RSA_ONLY);
    }

    private int countOccurrences(String value, String pattern) {
        int count = 0;
        int index = value.indexOf(pattern);
        while (index >= 0) {
            count++;
            index = value.indexOf(pattern, index + pattern.length());
        }
        return count;
    }

    private RSAPublicKey readPublicKey(String pemContent) throws Exception {
        return readPublicKey(pemContent, resolveSinglePemLabel(pemContent));
    }

    private RSAPublicKey readPublicKey(String pemContent, String pemLabel) throws Exception {
        try {
            if ("CERTIFICATE".equals(pemLabel)) {
                byte[] encodedCertificate = decodePemBlock(pemContent, "CERTIFICATE");
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(encodedCertificate));
                PublicKey publicKey = certificate.getPublicKey();
                if (!(publicKey instanceof RSAPublicKey)) {
                    throw validationException(HostToHostCertificateErrorConstants.ERROR_RSA_ONLY);
                }
                return (RSAPublicKey) publicKey;
            }

            if ("PUBLIC KEY".equals(pemLabel)) {
                byte[] encodedKey = decodePemBlock(pemContent, "PUBLIC KEY");
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
                if (!(publicKey instanceof RSAPublicKey)) {
                    throw validationException(HostToHostCertificateErrorConstants.ERROR_RSA_ONLY);
                }
                return (RSAPublicKey) publicKey;
            }

            if ("RSA PUBLIC KEY".equals(pemLabel)) {
                byte[] encodedKey = decodePemBlock(pemContent, "RSA PUBLIC KEY");
                return readPkcs1PublicKey(encodedKey);
            }

            throw validationException(HostToHostCertificateErrorConstants.ERROR_RSA_ONLY);
        } catch (GeneralSecurityException e) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_INVALID_PEM_CONTENT);
        } catch (IllegalArgumentException e) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_INVALID_RSA_PEM_CONTENT);
        }
    }

    private RSAPublicKey readPkcs1PublicKey(byte[] encodedKey) throws GeneralSecurityException {
        DerReader reader = new DerReader(encodedKey);
        DerReader sequence = new DerReader(reader.readSequence());
        BigInteger modulus = sequence.readInteger();
        BigInteger publicExponent = sequence.readInteger();
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
    }

    private byte[] decodePemBlock(String pemContent, String label) throws Exception {
        String beginMarker = "-----BEGIN " + label + "-----";
        String endMarker = "-----END " + label + "-----";
        int beginIndex = pemContent.indexOf(beginMarker);
        int endIndex = pemContent.indexOf(endMarker);
        if (beginIndex < 0 || endIndex < 0 || endIndex <= beginIndex) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_INVALID_PEM_CONTENT);
        }
        String base64Content = pemContent.substring(beginIndex + beginMarker.length(), endIndex)
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_INVALID_PEM_CONTENT);
        }
    }

    private String resolveCertificateFileId(HostToHostCertificateFileDTO certificateDTO) {
        String certificateId = normalize(certificateDTO.getCertificateId());
        return certificateId == null ? generateId() : certificateId;
    }

    private static final class DerReader {
        private final byte[] data;
        private int offset;

        private DerReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        private byte[] readSequence() {
            expectTag(0x30);
            return readValue();
        }

        private BigInteger readInteger() {
            expectTag(0x02);
            return new BigInteger(1, readValue());
        }

        private void expectTag(int expectedTag) {
            if (offset >= data.length || (data[offset++] & 0xff) != expectedTag) {
                throw new IllegalArgumentException("Invalid RSA public key PEM content.");
            }
        }

        private byte[] readValue() {
            int length = readLength();
            if (offset + length > data.length) {
                throw new IllegalArgumentException("Invalid RSA public key PEM content.");
            }
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return value;
        }

        private int readLength() {
            if (offset >= data.length) {
                throw new IllegalArgumentException("Invalid RSA public key PEM content.");
            }

            int length = data[offset++] & 0xff;
            if ((length & 0x80) == 0) {
                return length;
            }

            int lengthBytes = length & 0x7f;
            if (lengthBytes == 0 || lengthBytes > 4 || offset + lengthBytes > data.length) {
                throw new IllegalArgumentException("Invalid RSA public key PEM content.");
            }

            length = 0;
            for (int i = 0; i < lengthBytes; i++) {
                length = (length << 8) | (data[offset++] & 0xff);
            }
            return length;
        }
    }

    private String readRequestReferenceNumber(String transactionId) throws Exception {
        if (isBlank(transactionId)) {
            return null;
        }
        HthCertificateRequest existing = LocalHthCertificateRequestRepositoryAdapter.getInstance()
                .findActiveByTransactionId(transactionId);
        return existing == null ? null : existing.getReferenceNo();
    }

    private String resolveApprovedReferenceNumber(
            HostToHostCertificateDTO requestDTO, String transactionId) throws Exception {
        String referenceNumber = readRequestReferenceNumber(transactionId);
        if (isBlank(referenceNumber) && requestDTO != null) {
            referenceNumber = normalize(requestDTO.getReferenceNumber());
        }
        return isBlank(referenceNumber) ? transactionId : referenceNumber;
    }

    private boolean isApprovedExecution() {
        Object approvalStatus = com.ofss.fc.infra.thread.ThreadAttribute
                .get(com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
        if (approvalStatus == null) {
            approvalStatus = ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
        }
        return ApprovalStatus.APPROVED.toString().equals(String.valueOf(approvalStatus));
    }

    private void setExternalReferenceNumber(String externalReferenceNumber) {
        ThreadAttribute.set(ThreadAttribute.EXTERNAL_REFERENCE_NUMBER, externalReferenceNumber);
        com.ofss.fc.infra.thread.ThreadAttribute.set(
                com.ofss.fc.infra.thread.ThreadAttribute.EXTERNAL_REFERENCE_NUMBER, externalReferenceNumber);
    }

    private String generateReferenceNumber() {
        return "HTHCERT" + generateId();
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    private String requireTransactionId() {
        String transactionId = normalize(readTransactionId());
        if (transactionId == null) {
            throw new IllegalStateException("Transaction reference number is not available from approval framework.");
        }
        return transactionId;
    }

    private String readTransactionId() {
        Object transactionId = ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO);
        if (transactionId == null) {
            transactionId = ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
        }
        if (transactionId == null) {
            transactionId = com.ofss.fc.infra.thread.ThreadAttribute
                    .get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
        }
        if (transactionId == null) {
            transactionId = com.ofss.fc.infra.thread.ThreadAttribute
                    .get(com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
        }
        return transactionId == null ? null : String.valueOf(transactionId);
    }

    private String readUserId(SessionContext sessionContext) {
        if (sessionContext == null || isBlank(sessionContext.getUserId())) {
            return "system";
        }
        return sessionContext.getUserId();
    }

    private String describeRequest(HostToHostCertificateDTO requestDTO) {
        if (requestDTO == null) {
            return "null";
        }

        List<HostToHostCertificateFileDTO> certificates = requestDTO.getCertificates();
        int certificateCount = certificates == null ? 0 : certificates.size();
        return formatter.formatMessage("partyId=%s, referenceNumber=%s, certificateCount=%s",
                normalize(requestDTO.getPartyId()), normalize(requestDTO.getReferenceNumber()),
                Integer.valueOf(certificateCount));
    }

    private Exception validationException(String errorCode) {
        Exception exception = new Exception(errorCode);
        ValidationError[] validationErrors = new ValidationError[1];
        validationErrors[0] = new ValidationError(THIS_COMPONENT_NAME, null, null, errorCode, "");
        exception.setValidationErrors(validationErrors);
        return exception;
    }

    private HostToHostCertificateDTO applySessionParty(
            HostToHostCertificateDTO requestDTO, SessionContext sessionContext) throws Exception {
        if (requestDTO == null) {
            return null;
        }

        String partyId = resolveSessionPartyId(sessionContext);
        requestDTO.setPartyId(partyId);
        return requestDTO;
    }

    private String resolveSessionPartyId(SessionContext sessionContext) throws Exception {
        String partyId = sessionContext == null ? null : normalize(sessionContext.getTransactingPartyCode());
        if (partyId == null) {
            Object adminPartyId = ThreadAttribute.get(CZFieldConstants.PARTYID_FROM_ADMIN);
            partyId = adminPartyId == null ? null : normalize(String.valueOf(adminPartyId));
        }
        if (partyId == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_PARTY_ID_REQUIRED);
        }
        return partyId;
    }

    private String requirePartyId(String partyId) throws Exception {
        String normalizedPartyId = normalize(partyId);
        if (normalizedPartyId == null) {
            throw validationException(HostToHostCertificateErrorConstants.ERROR_PARTY_ID_REQUIRED);
        }
        return normalizedPartyId;
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
