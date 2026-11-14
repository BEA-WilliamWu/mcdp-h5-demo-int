package com.hkbea.dsp.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.dsp.dto.CryptKeyDto;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.exception.DspSupportErrorMapping;
import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PasswordModificationResponse;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.model.PublicKeyQueryResponse;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class PasswordApiServiceImplTest {

    @InjectMocks
    private PasswordApiServiceImpl passwordApiService;

    @Mock
    private PasswordAppService passwordAppService;

    @Mock
    private DspAuthCredentialRepoService dspAuthCredentialRepoService;

    private static final String MOCK_HASH_VALUE = "4acf0b39d9c4766709a3689f553ac01ab550545ffa4544dfc0b2cea82fba02a3";
    private static final String MOCK_CLIENT_ID = "mockClientId";
    private static final String MOCK_CLOSE_ID = "mockCloseId";
    private static final String MOCK_RANDOM_NUMBER = "123456";
    private String MOCK_PUBLIC_KEY;
    private String MOCK_PRIVATE_KEY;
    private String MOCK_ENCRYPTED_NEW_PASSWORD;
    private String MOCK_ENCRYPTED_OLD_PASSWORD;
    private String MOCK_PASSWORD_HASH;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("com.hkbea.dsp.exception.DspSupportErrorMapping");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        MOCK_PUBLIC_KEY = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        MOCK_PRIVATE_KEY = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        MOCK_PASSWORD_HASH = passwordHash("Fixture1234");
        MOCK_ENCRYPTED_NEW_PASSWORD = encrypt(MOCK_PASSWORD_HASH, MGF1ParameterSpec.SHA256);
        MOCK_ENCRYPTED_OLD_PASSWORD = "ignored-old-password-fixture";
    }

    private String passwordHash(String value) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String encrypt(String value, MGF1ParameterSpec mgf) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(MOCK_PUBLIC_KEY))),
                new OAEPParameterSpec("SHA-256", "MGF1", mgf, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private void cachedKey() {
        when(passwordAppService.findCryptoKeyDto(HashUtils.sha256(MOCK_RANDOM_NUMBER)))
                .thenReturn(new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY));
    }

    private PasswordModificationRequest request(String function, String encrypted) {
        return new PasswordModificationRequest().function(PasswordModificationRequest.FunctionEnum.fromValue(function))
                .clientId(MOCK_CLIENT_ID).closeId(MOCK_CLOSE_ID).randomNumber(MOCK_RANDOM_NUMBER)
                .newEncryptedPassword(encrypted);
    }

    @Test
    void getPublicKey_Successful_ReturnsPublicKey() {
        // Given
        PublicKeyQueryRequest request = new PublicKeyQueryRequest();
        request.setHashValue(MOCK_HASH_VALUE);

        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
        when(passwordAppService.getCryptoKeyDto(MOCK_HASH_VALUE)).thenReturn(cryptKeyDto);

        // When
        PublicKeyQueryResponse response = passwordApiService.queryPublicKey(request);

        // Then
        assertNotNull(response);
        assertEquals(MOCK_PUBLIC_KEY, response.getPublicKey());
        assertEquals("000000", response.getStatus());
        verify(passwordAppService).getCryptoKeyDto(MOCK_HASH_VALUE);
    }

    @Test
    void getPublicKey_NullRequest_ThrowsServiceException() {
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> passwordApiService.queryPublicKey(null));
        assertEquals(DspSupportErrorMapping.REQ_BODY_MISS_OR_EMPTY, exception.getErrCode());
    }

    @Test
    void getPublicKey_EmptyHashValue_ThrowsParameterException() {
        // Given
        PublicKeyQueryRequest request = new PublicKeyQueryRequest();
        request.setHashValue("   ");

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.queryPublicKey(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("hashValue"));
    }

    @Test
    void getPublicKey_InvalidHashValue_ThrowsParameterException() {
        // Given
        PublicKeyQueryRequest request = new PublicKeyQueryRequest();
        request.setHashValue("abcdef");

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.queryPublicKey(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_INVALID_EXCEPTION, exception.getErrCode());
    }

    @Test
    void modifyPassword_NewPassword_Successful() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(cryptKeyDto);
        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(null);

        // When
        PasswordModificationResponse response = passwordApiService.modifyPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("000000", response.getStatus());
        verify(dspAuthCredentialRepoService).insert(argThat(row -> MOCK_PASSWORD_HASH.equals(row.getPasswordHash())));
    }

    @Test
    void modifyPassword_ModifyExistingPassword_Successful() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.M);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);
        request.setOldEncryptedPassword(MOCK_ENCRYPTED_OLD_PASSWORD);

        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(cryptKeyDto);

        DspAuthCredential existingCredential = new DspAuthCredential();
        existingCredential.setPasswordHash(MOCK_PASSWORD_HASH);
        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(existingCredential);

        // When
        PasswordModificationResponse response = passwordApiService.modifyPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("000000", response.getStatus());
        verify(dspAuthCredentialRepoService).updatePassword(argThat(row -> MOCK_PASSWORD_HASH.equals(row.getPasswordHash())));
    }

    @Test
    void modifyPassword_NullRequest_ThrowsServiceException() {
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> passwordApiService.modifyPassword(null));
        assertEquals(DspSupportErrorMapping.REQ_BODY_MISS_OR_EMPTY, exception.getErrCode());
    }

    @Test
    void modifyPassword_NullFunction_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("function"));
    }

    @Test
    void modifyPassword_InvalidFunction_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(null);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("function"));
    }

    @Test
    void modifyPassword_EmptyNewPassword_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword("   ");

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("newEncryptedPassword"));
    }

//    no old password current
//    @Test
//    void modifyPassword_ModifyFunctionWithEmptyOldPassword_ThrowsParameterException() {
//        // Given
//        PasswordModificationRequest request = new PasswordModificationRequest();
//        request.setFunction(PasswordModificationRequest.FunctionEnum.M);
//        request.setClientId(MOCK_CLIENT_ID);
//        request.setCloseId(MOCK_CLOSE_ID);
//        request.setRandomNumber(MOCK_RANDOM_NUMBER);
//        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);
//        request.setOldEncryptedPassword("   ");
//
//        // When & Then
//        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
//        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
//        assertTrue(exception.getMessage().contains("oldEncryptedPassword"));
//    }

    @Test
    void modifyPassword_EmptyRandomNumber_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber("   ");
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("randomNumber"));
    }

    @Test
    void modifyPassword_InvalidRandomNumberFormat_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber("123"); // Invalid format
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.DATA_FORMAT_INVALID, exception.getErrCode());
        assertTrue(exception.getMessage().contains("randomNumber"));
    }

    @Test
    void modifyPassword_EmptyClientId_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId("   ");
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("clientId"));
    }

    @Test
    void modifyPassword_EmptyCloseId_ThrowsParameterException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId("   ");
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PARAMETER_IS_MANDATORY, exception.getErrCode());
        assertTrue(exception.getMessage().contains("closeId"));
    }

    @Test
    void modifyPassword_CryptoKeyNotFound_ThrowsServiceException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(null);

        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, exception.getErrCode());
    }

    @Test
    void modifyPassword_NewPasswordExistingRecord_ThrowsServiceException() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.A);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(cryptKeyDto);
        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(new DspAuthCredential());

        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, exception.getErrCode());
    }

    @Test
    void modifyPassword_LegacyResetCreatesFirstCredential() {
        // Given
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setFunction(PasswordModificationRequest.FunctionEnum.M);
        request.setClientId(MOCK_CLIENT_ID);
        request.setCloseId(MOCK_CLOSE_ID);
        request.setRandomNumber(MOCK_RANDOM_NUMBER);
        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);

        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(cryptKeyDto);
        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(null);

        // When & Then
        assertEquals("000000", passwordApiService.modifyPassword(request).getStatus());
        verify(dspAuthCredentialRepoService).existsForClientAndCloseId(MOCK_CLOSE_ID, MOCK_CLIENT_ID);
        verify(dspAuthCredentialRepoService).insert(argThat(row -> MOCK_PASSWORD_HASH.equals(row.getPasswordHash())
                && MOCK_CLIENT_ID.equals(row.getClientId()) && MOCK_CLOSE_ID.equals(row.getCloseId())
                && "A".equals(row.getObjectStatus()) && row.getCreationDate() != null));
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService).removeCryptoKeyDto(HashUtils.sha256(MOCK_RANDOM_NUMBER));
    }

//    no old check now
//    @Test
//    void modifyPassword_ModifyPasswordMismatchOldPassword_ThrowsServiceException() {
//        // Given
//        PasswordModificationRequest request = new PasswordModificationRequest();
//        request.setFunction(PasswordModificationRequest.FunctionEnum.M);
//        request.setClientId(MOCK_CLIENT_ID);
//        request.setCloseId(MOCK_CLOSE_ID);
//        request.setRandomNumber(MOCK_RANDOM_NUMBER);
//        request.setNewEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);
//        request.setOldEncryptedPassword(MOCK_ENCRYPTED_OLD_PASSWORD);
//
//        CryptKeyDto cryptKeyDto = new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY);
//        when(passwordAppService.findCryptoKeyDto(anyString())).thenReturn(cryptKeyDto);
//
//        DspAuthCredential existingCredential = new DspAuthCredential();
//        existingCredential.setPasswordHash("differentHash"); // Mismatch
//        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(existingCredential);
//
//        // When & Then
//        ServiceException exception = assertThrows(ServiceException.class, () -> passwordApiService.modifyPassword(request));
//        assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, exception.getErrCode());
//    }
    @Test void legacyResetRejectsInactiveCredential() {
        cachedKey();
        when(dspAuthCredentialRepoService.existsForClientAndCloseId(MOCK_CLOSE_ID, MOCK_CLIENT_ID)).thenReturn(true);
        ServiceException failure = assertThrows(ServiceException.class,
                () -> passwordApiService.modifyPassword(request("M", MOCK_ENCRYPTED_NEW_PASSWORD)));
        assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, failure.getErrCode());
        verify(dspAuthCredentialRepoService, never()).insert(any());
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

    @Test void lookupFailureDoesNotBecomeLegacyCreation() {
        cachedKey();
        when(dspAuthCredentialRepoService.existsForClientAndCloseId(MOCK_CLOSE_ID, MOCK_CLIENT_ID))
                .thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,
                () -> passwordApiService.modifyPassword(request("M", MOCK_ENCRYPTED_NEW_PASSWORD)));
        verify(dspAuthCredentialRepoService, never()).insert(any());
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

    @Test void invalidCipherOrDigestIsRejectedBeforeLegacyInsert() throws Exception {
        cachedKey();
        for (String ciphertext : new String[]{"not-base64", encrypt("plaintext", MGF1ParameterSpec.SHA256),
                encrypt(HashUtils.sha256("Fixture1234"), MGF1ParameterSpec.SHA256),
                encrypt(MOCK_PASSWORD_HASH, MGF1ParameterSpec.SHA1)}) {
            ServiceException failure = assertThrows(ServiceException.class,
                    () -> passwordApiService.modifyPassword(request("M", ciphertext)));
            assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, failure.getErrCode());
        }
        verify(dspAuthCredentialRepoService, never()).insert(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

    @Test void databaseFailureIsNotReportedAsSuccessOrRetried() {
        cachedKey();
        doThrow(new IllegalStateException()).when(dspAuthCredentialRepoService).insert(any());
        assertThrows(IllegalStateException.class,
                () -> passwordApiService.modifyPassword(request("M", MOCK_ENCRYPTED_NEW_PASSWORD)));
        verify(dspAuthCredentialRepoService).insert(any(DspAuthCredential.class));
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

    @Test void requestStringDoesNotPrintCredentialsOrNonce() {
        String text = request("M", "cipher-sentinel").oldEncryptedPassword("old-sentinel").toString();
        assertFalse(text.contains("cipher-sentinel")); assertFalse(text.contains("old-sentinel"));
        assertFalse(text.contains(MOCK_RANDOM_NUMBER));
    }

    @Test void currentCmBrowserEncryptionWorksForLegacyReset() throws Exception {
        String script = System.getProperty("cm.browser.interop");
        org.junit.jupiter.api.Assumptions.assumeTrue(script != null, "Set cm.browser.interop to browser-interop.cjs");
        cachedKey();
        Process process = new ProcessBuilder(System.getProperty("node.bin", "node"), script,
                MOCK_PUBLIC_KEY, "Browser123", "RESET").start();
        String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        String ciphertext = new ObjectMapper().readTree(json).path("dspEncryptedPassword").asText();
        assertEquals("000000", passwordApiService.modifyPassword(request("M", ciphertext)).getStatus());
        String expected = passwordHash("Browser123");
        verify(dspAuthCredentialRepoService).insert(argThat(row -> expected.equals(row.getPasswordHash())));
    }

}
