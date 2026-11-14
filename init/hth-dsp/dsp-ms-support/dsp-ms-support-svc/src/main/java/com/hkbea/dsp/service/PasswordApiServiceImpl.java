package com.hkbea.dsp.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.dsp.api.PasswordApi;
import com.hkbea.dsp.dto.CryptKeyDto;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.exception.DspSupportErrorMapping;
import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PasswordModificationResponse;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.model.PublicKeyQueryResponse;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Arrays;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.regex.Pattern;


@Path("/")
@Service
public class PasswordApiServiceImpl implements PasswordApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordApiServiceImpl.class);

    private static final String NORMAL_RETURN_CODE = "000000";

    private static final String NORMAL_UPDATE_MSG = "password updated.";

    private static final String NORMAL_CREATE_MSG = "password created.";


    public static final String ADMIN_CREATOR = "admin";

    /**
     * random number check, only 6 digits
     */
    private static final int VALID_RANDOM_DIGITS = 6;

    private static final Pattern PATTERN = Pattern.compile(String.format("^\\d{%d}$", VALID_RANDOM_DIGITS));

    /**
     * no need use spring to inject this
     */
    @Autowired
    private PasswordAppService passwordAppService;

    @Autowired
    private DspAuthCredentialRepoService dspAuthCredentialRepoService;

    /**
     * API to get public key.
     *
     * @param publicKeyQueryRequest
     * @return Public key retrieved.
     * @return Parameter Exception. Triggered by API Gateway validation (e.g., payload limits) or rejected by service parameter validation.  Possible Error Codes: - **500050**: Request body is missing or empty. - **500051**: The mandatory parameter is missing or empty.
     * @return Service Exception. Triggered by downstream integration failure or response mapping failure.  Possible Error Codes: - **600052**: Failed to verify the password_signature.
     * @return System Exception.  Possible Error Codes: - **700000**: System process exception.
     * @return Other / General Exception.  Possible Error Codes: - **900000**: General exception.
     */
    @Override
    public PublicKeyQueryResponse queryPublicKey(PublicKeyQueryRequest publicKeyQueryRequest) {
        checkPublicKeyQueryRequest(publicKeyQueryRequest);

        String hashValue = publicKeyQueryRequest.getHashValue();
        CryptKeyDto cryptKeyDto = passwordAppService.getCryptoKeyDto(hashValue);
        PublicKeyQueryResponse response = new PublicKeyQueryResponse();
        response.setPublicKey(cryptKeyDto.getPublicKey());
        response.setStatus(NORMAL_RETURN_CODE);

        return response;
    }

    private void checkPublicKeyQueryRequest(PublicKeyQueryRequest publicKeyQueryRequest) {
        if (publicKeyQueryRequest == null) {
            LOGGER.error("request missed or is empty");
            throw new ServiceException(DspSupportErrorMapping.REQ_BODY_MISS_OR_EMPTY);
        }

        String hashValue = publicKeyQueryRequest.getHashValue();
        if (hashValue == null || hashValue.isBlank()) {
            LOGGER.error("parameter is mandatory");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "hashValue");
        }
        if (!hashValue.matches("[0-9a-f]{64}")) {
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_INVALID_EXCEPTION, "hashValue");
        }
    }

    /**
     * API to modify user password.
     *
     * @param passwordModificationRequest
     * @return Password modification completed.
     * @return Parameter Exception. Triggered by API Gateway validation (e.g., payload limits) or rejected by service parameter validation.  Possible Error Codes: - **500050**: Request body is missing or empty. - **500051**: The mandatory parameter is missing or empty.
     * @return Service Exception. Triggered by downstream integration failure or response mapping failure.  Possible Error Codes: - **600052**: Failed to verify the password_signature.
     * @return System Exception.  Possible Error Codes: - **700000**: System process exception.
     * @return Other / General Exception.  Possible Error Codes: - **900000**: General exception.
     */
    @Override
    @Transactional
    public PasswordModificationResponse modifyPassword(PasswordModificationRequest passwordModificationRequest) {
        validateRequestForPasswordModify(passwordModificationRequest);

        // get hash
        String randomNumber = passwordModificationRequest.getRandomNumber();
        String hashKey = HashUtils.sha256(randomNumber);

        // get cache key, if not found, then throw exception
        CryptKeyDto cryptoKeyDto = passwordAppService.findCryptoKeyDto(hashKey);
        if (cryptoKeyDto == null) {
            LOGGER.error("can't find any key information");
            throw new ServiceException(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION);
        }

        // query user
        String closeId = passwordModificationRequest.getCloseId();
        String clientId = passwordModificationRequest.getClientId();
        DspAuthCredential dspAuthCredential = dspAuthCredentialRepoService.queryWithStatus(
                closeId, clientId, DspAuthCredentialRepoService.AUTH_STATUS_ACTIVE);

        // change or set new password
        PasswordModificationRequest.FunctionEnum function = passwordModificationRequest.getFunction();
        String message = "";
        if (function == PasswordModificationRequest.FunctionEnum.A) {
            message = NORMAL_CREATE_MSG;
            newPassword(passwordModificationRequest, cryptoKeyDto, dspAuthCredential);
        } else if (function == PasswordModificationRequest.FunctionEnum.M) {
            message = NORMAL_UPDATE_MSG;
            modifyPassword(passwordModificationRequest, cryptoKeyDto, dspAuthCredential);
        }

        passwordAppService.removeCryptoKeyDto(hashKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    LOGGER.info("HTH_API_PASSWORD stage=DSP_SAVE result=SUCCESS function={} action={}",
                            function, dspAuthCredential == null ? "INSERT" : "UPDATE");
                }
            });
        }

        // return
        PasswordModificationResponse response = new PasswordModificationResponse();
        response.setStatus(NORMAL_RETURN_CODE);
        response.setMessage(message);
        return response;
    }

    private void validateRequestForPasswordModify(PasswordModificationRequest request) {
        if (request == null) {
            LOGGER.error("request missed or is empty");
            throw new ServiceException(DspSupportErrorMapping.REQ_BODY_MISS_OR_EMPTY);
        }

        PasswordModificationRequest.FunctionEnum function = request.getFunction();
        if (function == null) {
            LOGGER.error("function is mandatory");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "function");
        }
        if (function != PasswordModificationRequest.FunctionEnum.A && function != PasswordModificationRequest.FunctionEnum.M) {
            LOGGER.error("function is invalid");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_INVALID_EXCEPTION, "function");
        }

        String newEncryptedPassword = request.getNewEncryptedPassword();
        if (newEncryptedPassword == null || newEncryptedPassword.isBlank()) {
            LOGGER.error("please input pass word");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "newEncryptedPassword");
        }

        // This internal CM endpoint is an approved-Code reset, not a self-service old-password change.
        // CM validates the Code and target entitlement before calling DSP.

        String randomNumber = request.getRandomNumber();
        if (randomNumber == null || randomNumber.isBlank()) {
            LOGGER.error("random number is mandatory");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "randomNumber");
        }
        if (!PATTERN.matcher(randomNumber).matches()) {
            LOGGER.error("random number format is invalid");
            throw new ParameterException(DspSupportErrorMapping.DATA_FORMAT_INVALID, "randomNumber", String.format("{%d} digits",VALID_RANDOM_DIGITS));
        }

        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            LOGGER.error("client id is mandatory");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "clientId");
        }

        String closeId = request.getCloseId();
        if (closeId == null || closeId.isBlank()) {
            LOGGER.error("close id is mandatory");
            throw new ParameterException(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, "closeId");
        }
    }

    private void newPassword(PasswordModificationRequest request, CryptKeyDto cryptoKeyDto, DspAuthCredential dspAuthCredential) {
        if (dspAuthCredential != null) {
            LOGGER.error("already exists active record");
            throw new ServiceException(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION);
        }
        String decrypted = decrypt(cryptoKeyDto.getPrivateKey(), request.getNewEncryptedPassword());

        // add
        DspAuthCredential authCredential = new DspAuthCredential();
        authCredential.setClientId(request.getClientId());
        authCredential.setCloseId(request.getCloseId());
        authCredential.setPasswordHash(decrypted);
        authCredential.setObjectStatus(DspAuthCredentialRepoService.AUTH_STATUS_ACTIVE);
        authCredential.setCreatedBy(ADMIN_CREATOR);
        authCredential.setLastUpdatedBy(ADMIN_CREATOR);
        LocalDateTime now = LocalDateTime.now();
        authCredential.setCreationDate(now);
        authCredential.setLastUpdateDate(now);

        dspAuthCredentialRepoService.insert(authCredential);
    }

    private void modifyPassword(PasswordModificationRequest request, CryptKeyDto cryptoKeyDto, DspAuthCredential dspAuthCredential) {
        if (dspAuthCredential == null) {
            // Legacy CM users can already be in RESET before their first DSP credential exists.
            // Do not treat inactive records or a failed query as an absent credential.
            if (dspAuthCredentialRepoService.existsForClientAndCloseId(request.getCloseId(), request.getClientId())) {
                LOGGER.warn("HTH_API_PASSWORD stage=DSP_SAVE result=REJECTED function=M reason=NO_ACTIVE_CREDENTIAL");
                throw new ServiceException(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION);
            }
            newPassword(request, cryptoKeyDto, null);
            return;
        }

        // The browser already computed Base64(SHA-256(UTF-8(password))). Do not hash it again.
        String newHash = decrypt(cryptoKeyDto.getPrivateKey(), request.getNewEncryptedPassword());

        // update
        dspAuthCredential.setPasswordHash(newHash);
        dspAuthCredential.setLastUpdatedBy(ADMIN_CREATOR);
        LocalDateTime now = LocalDateTime.now();
        dspAuthCredential.setLastUpdateDate(now);

        dspAuthCredentialRepoService.updatePassword(dspAuthCredential);
    }

    /**
     * decrypted
     * @param key  Base64 encode
     * @param encryptedText base64 encode
     * @return UTF8 String
     */
    private String decrypt(String key, String encryptedText) {
        byte[] clear = null;
        byte[] digest = null;
        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(key);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes)),
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            clear = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            String hash = new String(clear, StandardCharsets.US_ASCII);
            digest = Base64.getDecoder().decode(hash);
            if (digest.length != 32 || !Base64.getEncoder().encodeToString(digest).equals(hash)) {
                throw new IllegalArgumentException();
            }
            return hash;
        } catch (java.lang.Exception failure) {
            LOGGER.warn("HTH_API_PASSWORD stage=DSP_DECRYPT exceptionType={}", failure.getClass().getSimpleName());
            throw new ServiceException(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION);
        } finally {
            if (clear != null) { Arrays.fill(clear, (byte) 0); }
            if (digest != null) { Arrays.fill(digest, (byte) 0); }
            if (keyBytes != null) { Arrays.fill(keyBytes, (byte) 0); }
        }
    }
}
