package com.hkbea.dsp.service;

import com.hkbea.common.exception.ServiceException;
import com.hkbea.dsp.dto.CryptKeyDto;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordApiServiceImplTest {
    @InjectMocks private PasswordApiServiceImpl service;
    @Mock private PasswordAppService keys;
    @Mock private DspAuthCredentialRepoService repo;
    @Mock private CmPasswordCallerGuard guard;
    private KeyPair pair;
    private CryptKeyDto key;
    private static final String NONCE = "314159";

    @BeforeEach void prepare() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); pair = generator.generateKeyPair();
        key = new CryptKeyDto(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
    }
    private void cached() { when(keys.findCryptoKeyDto(HashUtils.sha256(NONCE))).thenReturn(key); }
    private String hash(String password) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
    }
    private String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic(), new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    private PasswordModificationRequest request(String function, String cipher) {
        return new PasswordModificationRequest().function(PasswordModificationRequest.FunctionEnum.fromValue(function))
                .clientId("test-client").closeId("USER@PARTY").randomNumber(NONCE).newEncryptedPassword(cipher);
    }
    @Test void firstSetupStoresHashWithoutRehashing() throws Exception {
        cached(); String digest = hash("Setup1234");
        assertEquals("000000", service.modifyPassword(request("A", encrypt(digest))).getStatus());
        verify(repo).insert(argThat(row -> digest.equals(row.getPasswordHash())));
        verify(keys).removeCryptoKeyDto(HashUtils.sha256(NONCE)); verify(guard).check(null);
    }
    @Test void resetDoesNotRequireOldPassword() throws Exception {
        cached(); DspAuthCredential existing = new DspAuthCredential(); existing.setPasswordHash(hash("Old12345"));
        when(repo.queryWithStatus("USER@PARTY", "test-client", "A")).thenReturn(existing);
        String digest = hash("New12345");
        assertEquals("000000", service.modifyPassword(request("M", encrypt(digest))).getStatus());
        verify(repo).updatePassword(argThat(row -> digest.equals(row.getPasswordHash())));
        verify(repo, never()).insert(any());
    }
    @Test void setupCannotOverwriteExistingCredential() throws Exception {
        cached(); when(repo.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(new DspAuthCredential());
        assertThrows(ServiceException.class, () -> service.modifyPassword(request("A", encrypt(hash("Setup1234")))));
        verify(repo, never()).insert(any()); verify(repo, never()).updatePassword(any());
    }
    @Test void resetRequiresExistingActiveCredential() throws Exception {
        cached(); assertThrows(ServiceException.class, () -> service.modifyPassword(request("M", encrypt(hash("Reset1234")))));
        verify(repo, never()).updatePassword(any());
    }
    @Test void rejectsPlaintextAndHexInsteadOfBase64Digest() throws Exception {
        cached();
        for (String clear : new String[]{"Password123", HashUtils.sha256("Password123"), "AA=="}) {
            assertThrows(ServiceException.class, () -> service.modifyPassword(request("A", encrypt(clear))));
        }
        verify(repo, never()).insert(any());
    }
    @Test void rejectsWrongKeyAndMalformedCiphertext() throws Exception {
        cached();
        assertThrows(ServiceException.class, () -> service.modifyPassword(request("A", "not-base64")));
        KeyPairGenerator other = KeyPairGenerator.getInstance("RSA"); other.initialize(2048); pair=other.generateKeyPair();
        assertThrows(ServiceException.class, () -> service.modifyPassword(request("A", encrypt(hash("Wrong1234")))));
        verify(repo, never()).insert(any());
    }
    @Test void missingExpiredKeyCannotWrite() throws Exception {
        assertThrows(ServiceException.class, () -> service.modifyPassword(request("A", encrypt(hash("Setup1234")))));
        verifyNoInteractions(repo);
    }
    @Test void queryUsesHashOfNonceExactlyOnce() {
        String hash = HashUtils.sha256(NONCE); when(keys.getCryptoKeyDto(hash)).thenReturn(key);
        PublicKeyQueryRequest request = new PublicKeyQueryRequest(); request.setHashValue(hash);
        assertEquals(key.getPublicKey(), service.queryPublicKey(request).getPublicKey());
        verify(keys).getCryptoKeyDto(hash);
    }
    @Test void databaseFailureIsNotReportedAsSuccess() throws Exception {
        cached(); doThrow(new IllegalStateException()).when(repo).insert(any());
        assertThrows(IllegalStateException.class, () -> service.modifyPassword(request("A", encrypt(hash("Setup1234")))));
        verify(keys, never()).removeCryptoKeyDto(anyString());
    }
    @Test void requestStringDoesNotLeakSecrets() {
        String text=request("M", "cipher-sentinel").oldEncryptedPassword("old-sentinel").toString();
        assertFalse(text.contains("cipher-sentinel"));assertFalse(text.contains("old-sentinel")); assertFalse(text.contains(NONCE));
    }
    @Test void actualCmBrowserEncryptionWorksForSetupAndReset() throws Exception {
        String script = System.getProperty("cm.browser.interop");
        org.junit.jupiter.api.Assumptions.assumeTrue(script != null, "Set cm.browser.interop to CM browser-interop.cjs");
        cached();
        DspAuthCredential saved = new DspAuthCredential();
        for (String operation : new String[]{"SETUP", "RESET"}) {
            String password = operation.equals("SETUP") ? "Browser123" : "Reset45678";
            Process process = new ProcessBuilder(System.getProperty("node.bin", "node"), script,
                    key.getPublicKey(), password, operation).start();
            String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            String ciphertext = new ObjectMapper().readTree(json).path("dspEncryptedPassword").asText();
            if (operation.equals("RESET")) { when(repo.queryWithStatus(anyString(), anyString(), anyString())).thenReturn(saved); }
            service.modifyPassword(request(operation.equals("SETUP") ? "A" : "M", ciphertext));
            String digest=hash(password);
            if (operation.equals("SETUP")) { verify(repo).insert(argThat(row -> digest.equals(row.getPasswordHash()))); saved.setPasswordHash(digest); }
            else { verify(repo).updatePassword(argThat(row -> digest.equals(row.getPasswordHash()))); }
        }
    }
}
