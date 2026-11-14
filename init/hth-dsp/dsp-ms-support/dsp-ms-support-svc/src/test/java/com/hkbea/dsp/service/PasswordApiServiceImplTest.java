package com.hkbea.dsp.service;

import com.hkbea.common.cipher.AsymmetricCipher;
import com.hkbea.common.cipher.config.RsaCryptoConfig;
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
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
public class PasswordApiServiceImplTest {

    @InjectMocks
    private PasswordApiServiceImpl passwordApiService;

    @Mock
    private PasswordAppService passwordAppService;

    @Mock
    private DspAuthCredentialRepoService dspAuthCredentialRepoService;

    @Mock
    private AsymmetricCipher<RsaCryptoConfig> rsa;

    private static final String MOCK_HASH_VALUE = "4acf0b39d9c4766709a3689f553ac01ab550545ffa4544dfc0b2cea82fba02a3";
//    private static final String MOCK_PUBLIC_KEY = "mockPublicKey";
//    private static final String MOCK_PRIVATE_KEY = "mockPrivateKey";
    private static final String MOCK_CLIENT_ID = "mockClientId";
    private static final String MOCK_CLOSE_ID = "mockCloseId";
    private static final String MOCK_RANDOM_NUMBER = "123456";
    private static final String MOCK_ENCRYPTED_NEW_PASSWORD = "M/5lkAXrYmUvL9zcJDau4qwGlpkD5AQA5/K5FRQj8VdFM9CaHOIdztg863FultsFRpk0sSUh2HC+4RpU03IRSZ0JtoXX+VcMwMlVkk2yRui/XWOT7gs+7RmG7kXtZfInsRmxvXF2s74Kbm5HyJSNaEqox1crDgD7hnXSFgayfeCi/XbqUEvS2xB7SFtIOD09oQ1UZeXODu/BGsPvkL0zgKsnxtpB8OYidsaCbPrM8k9SkKI/co1IDIOfU9paJw16LSQM0iNkCjVCLUXKiAAMI3OB3/e69VrTFXTwKBvmX/sTTlbe5U7vUV+MWG7abTue04f88dtNI01RaPM7u/XM8Q==";
    private static final String MOCK_ENCRYPTED_OLD_PASSWORD = "ldtAsRw7SE5XOGRo+eYqZEtGg5LxzO6QnpNVBGGdIlMHHdwd8sHh5+07gdvpySn7OjbMnaQQ6TFIClJ7BZZuM/vrpfXtfa7CtYozK1DGIxi9+0L90hc3k+/NPZ8gSuLmYWR54172o2uRm5xNfhsB2gM9J3IJOR8w2gzTXWYRWG63BF9Ybzed+5ZuA1sqp8Zrm0XjuwGfzW8ByMggHac4Pmn370xe4yqxGpNfu5KDTfZYYpssgwH1ZlFIp7ar+S+WZe9vh1ZivuFV2mefEXuXhgjWlfCAknnY2IRD6L7ERsl0nwcAtWXC15h2ygknOdNWgQJhaL1TUP9c96PyAYOSQQ==";
//    private static final String MOCK_ENCRYPTED_NEW_PASSWORD = "KZEckzSqQ9UaKEDTMWT2LTovNFoPiqAd0wkjxNe2KAu7sTOhawm7LtOItgazJoTTllYP5WQDr/vIJhCDXFcJ1n4CecD+/D5ctGEgnV7vVpx7f1KdfsYL8QIOIFWmL5urA6QjbeTZynK4dswl3InJ7x37Tp07GJw9TzPLXqvMAVoJK2OLSMkoz9g2afTl35Td00Y4cmZ18B5g09KMS6uVv3xMJ4pVUYxch4tAplmuFJjSaswGfzMoaw2s/Nd8rA7eVMYzy/cnYKE1iSBcCQinSAOQFdgLq+suZ8bPKY9G7pxLiO1jfWc7hRbTAd5HIDRfVLqTm1Zd7zxANQ36QCVKYA==";
//    private static final String MOCK_ENCRYPTED_OLD_PASSWORD = "ldtAsRw7SE5XOGRo+eYqZEtGg5LxzO6QnpNVBGGdIlMHHdwd8sHh5+07gdvpySn7OjbMnaQQ6TFIClJ7BZZuM/vrpfXtfa7CtYozK1DGIxi9+0L90hc3k+/NPZ8gSuLmYWR54172o2uRm5xNfhsB2gM9J3IJOR8w2gzTXWYRWG63BF9Ybzed+5ZuA1sqp8Zrm0XjuwGfzW8ByMggHac4Pmn370xe4yqxGpNfu5KDTfZYYpssgwH1ZlFIp7ar+S+WZe9vh1ZivuFV2mefEXuXhgjWlfCAknnY2IRD6L7ERsl0nwcAtWXC15h2ygknOdNWgQJhaL1TUP9c96PyAYOSQQ==";
    private static final String MOCK_DECRYPTED_PASSWORD = "123456abc";
    private static final String MOCK_PASSWORD_HASH = "passwordHash";

    private static final String testPublicKey  = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA34kvoqtczudfywanyNeFQIfqYKzaVEsBs+8vIbLN89QS2ANzX/uXGeDJG+9/gY717sadUWzQviv/r9d+zUmzg+dn+x2/k9qg9IdJuJEvKVV9/v+VM0vTVjwRTJkbU5BrvI66b4a1/fADGhzMqQUz9U33HftiTxOXv7XGXNKKMbIFMW+Ln3KwNQVrjG3kBRXY6lXAF8H2OTugjGujIioLpsZzQvfo76v1qch63GIYVqLTqCRdZFTx61TFenF76JOU2kKA8xDMTSArAcvj8kEe2r7RT/cUl//HxJqrSTUzgcKWHsDFFWXvY8NyKPVRYQS0dQs91SUgH4k4P4NEyBSd1wIDAQAB";
    private static final String testPrivateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDfiS+iq1zO51/LBqfI14VAh+pgrNpUSwGz7y8hss3z1BLYA3Nf+5cZ4Mkb73+BjvXuxp1RbNC+K/+v137NSbOD52f7Hb+T2qD0h0m4kS8pVX3+/5UzS9NWPBFMmRtTkGu8jrpvhrX98AMaHMypBTP1Tfcd+2JPE5e/tcZc0ooxsgUxb4ufcrA1BWuMbeQFFdjqVcAXwfY5O6CMa6MiKgumxnNC9+jvq/WpyHrcYhhWotOoJF1kVPHrVMV6cXvok5TaQoDzEMxNICsBy+PyQR7avtFP9xSX/8fEmqtJNTOBwpYewMUVZe9jw3Io9VFhBLR1Cz3VJSAfiTg/g0TIFJ3XAgMBAAECggEARaY1FXZXYJBvJI9aUBX3CvhOavjwvjrwuOPZx6Iy0bpOdMf+dvWpSwLjJrUlAXVN46w28Fv7XoXPETcP518DdCHyIChQvPzuEcLlkYCLNVHSBFYWYvlJTCFDMp6yCQOCzimapY1Pv2O9U7GNg0x/t/sm+EyPgRwVuEInIFzpejnsQ3hGoLoeJVir2rc8HXRMkXCWf/nqisnC0zcV318LcgdUP3K+k31WdhAVmAoXwzgWk6VUjAaaeIl9BuKd2w53UoqTz34nhn6FyMVJBA0xGQ0TY4wmzXsU1AjUuHC1mQCaliZX0TONQ2CAsIzQaAF265NvsgY7utrtGkVVuBq+IQKBgQDfrZ/yjWDqeoOybK2HB+HhD28+aQVY+oVvNZXg8rOlhGtc3nUmRIMC8H9aiaE3QPMcZoPT34l1rYzTZiPkxe/72mFnc7Fj6NUz5wRvy3awrzuVJdcBX7J86gyCRnxQPsEV3hC/TpGOqrlsqx70VPMD/3NirGEGzXZFiCeYHoSGVQKBgQD/1ku8QtpfyV1zraDiuOXQXh7SgydlRa2lxKH+R99fDM7ZIOAO9tUtp2bEc9q1FuRMJVhsaqD6xdSDTNK4nMr7ueaJP8MDCWakkldNhYptsMaggC7jnCvD62JFrcFjHWsQvde22eSx37r9NEDDYwAZ57+IDCh4lgo5G4VllgzHewKBgQCYvuZlKqquNO/OXmy5s2ZK3NFo2yrtslaAtSUesl5/f0gZ6Ugy08JrtXQija3sH4xlLpSDkTk7hUKP8kY8qfbSFDVEAjt3ML63bOEYvubUjTWxCmAi7gjjSKk2JZAq9E/C8Ryap7q6pFdvoAPJcmFpr35KOdEgZhqILQxs0xpI6QKBgG6rhuuTh9+zvedSjqxFC7UNYgvyjFI0yB9TZjgXYzs80SYAIKyYfjNXnQ8ShuObWXrPFOchYS3Z8koyK12NBgnKqz1oBbYElRBP9dAlGnLE68d2a9IuNoh0BHjb5zI3AZ+mX/BLZbDEdaEev31GED8kjygNClzPmvCFRTlipXiNAoGAIGBFBbuhrMs1pi8tv1inQLI4EbGrhloq+x6qTyspRBybEzn86o6F7Qnfwfz+9yE4lbmqAjXjuhr0Jig0oX09VjH4CoaREH9FA+HDiPgvwm/mnLy9LXUC9t21sOLfx/UKlFi9dKDbVcifb5x7lSokUB6YPbRwvXUSmREfogi8S+M=";
    private static final String MOCK_PUBLIC_KEY = testPublicKey;
    private static final String MOCK_PRIVATE_KEY = testPrivateKey;

    static {
        try {
            Class.forName("com.hkbea.dsp.exception.DspSupportErrorMapping");
        } catch (Exception e) {

        }
    }

    @BeforeEach
    void setUp() {
//        // Mock the rsa.decrypt method
//        when(rsa.decrypt(any(byte[].class), any(byte[].class))).thenReturn(MOCK_DECRYPTED_PASSWORD.getBytes());
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
        verify(dspAuthCredentialRepoService).insert(any(DspAuthCredential.class));
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
        existingCredential.setPasswordHash(HashUtils.sha256(MOCK_DECRYPTED_PASSWORD));
        when(dspAuthCredentialRepoService.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(existingCredential);

        // When
        PasswordModificationResponse response = passwordApiService.modifyPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("000000", response.getStatus());
        verify(dspAuthCredentialRepoService).updatePassword(any(DspAuthCredential.class));
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
        verify(dspAuthCredentialRepoService).insert(argThat(row -> MOCK_CLIENT_ID.equals(row.getClientId())
                && MOCK_CLOSE_ID.equals(row.getCloseId()) && "A".equals(row.getObjectStatus())));
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
    @Test
    void modifyPassword_InactiveCredentialDoesNotBecomeFirstSetup() {
        PasswordModificationRequest request = new PasswordModificationRequest()
                .function(PasswordModificationRequest.FunctionEnum.M).clientId(MOCK_CLIENT_ID)
                .closeId(MOCK_CLOSE_ID).randomNumber(MOCK_RANDOM_NUMBER)
                .newEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);
        when(passwordAppService.findCryptoKeyDto(anyString()))
                .thenReturn(new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY));
        when(dspAuthCredentialRepoService.existsForClientAndCloseId(MOCK_CLOSE_ID, MOCK_CLIENT_ID)).thenReturn(true);
        ServiceException failure = assertThrows(ServiceException.class, () -> passwordApiService.modifyPassword(request));
        assertEquals(DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION, failure.getErrCode());
        verify(dspAuthCredentialRepoService, never()).insert(any());
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

    @Test
    void modifyPassword_FailedLookupDoesNotBecomeFirstSetup() {
        PasswordModificationRequest request = new PasswordModificationRequest()
                .function(PasswordModificationRequest.FunctionEnum.M).clientId(MOCK_CLIENT_ID)
                .closeId(MOCK_CLOSE_ID).randomNumber(MOCK_RANDOM_NUMBER)
                .newEncryptedPassword(MOCK_ENCRYPTED_NEW_PASSWORD);
        when(passwordAppService.findCryptoKeyDto(anyString()))
                .thenReturn(new CryptKeyDto(MOCK_PUBLIC_KEY, MOCK_PRIVATE_KEY));
        when(dspAuthCredentialRepoService.existsForClientAndCloseId(MOCK_CLOSE_ID, MOCK_CLIENT_ID))
                .thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class, () -> passwordApiService.modifyPassword(request));
        verify(dspAuthCredentialRepoService, never()).insert(any());
        verify(dspAuthCredentialRepoService, never()).updatePassword(any());
        verify(passwordAppService, never()).removeCryptoKeyDto(anyString());
    }

}
