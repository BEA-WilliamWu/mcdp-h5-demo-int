package com.hkbea.dsp.service;

import com.hkbea.common.cache.CacheConfig;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PasswordModificationResponse;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.model.PublicKeyQueryResponse;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal CM protocol. Legacy public A/M callers continue through PasswordApiServiceImpl. */
@Service
public class CmPasswordService {
    public static final String PROTOCOL = "CM_SHA256_BASE64_V1";
    private static final long KEY_TTL = 5 * 60 * 1000L;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    @Autowired private DspAuthCredentialRepoService credentials;
    @Autowired private CacheManager cacheManager;
    // Dedicated internal APIC subscription; never accept Channel alone as authorization.
    @Value("${password.cm.client-id:}") private String cmClientId;
    @Value("${password.cm.client-secret:}") private String cmClientSecret;

    public PublicKeyQueryResponse query(PublicKeyQueryRequest request, HttpHeaders headers) {
        authorize(request.getProtocol(), headers);
        validateIdentity(request.getClientId(), request.getCloseId(), request.getRequestId());
        DspAuthCredential row = credentials.queryWithStatus(request.getCloseId(), request.getClientId(), "A");
        PublicKeyQueryResponse result = new PublicKeyQueryResponse();
        result.setStatus("000000");
        result.setProtocol(PROTOCOL);
        result.setCredentialStatus(row == null ? "NOT_SETUP" : "ACTIVE");
        if (!Boolean.TRUE.equals(request.getQueryOnly())) {
            if (request.getHashValue() == null || !request.getHashValue().matches("[0-9a-f]{64}")) {
                throw rejected("INVALID_KEY_REQUEST");
            }
            String cacheKey = cacheKey(request.getClientId(), request.getCloseId(), request.getRequestId(), request.getHashValue());
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                KeyEntry entry = new KeyEntry();
                entry.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
                entry.setExpiresAt(System.currentTimeMillis() + KEY_TTL);
                cache().put(cacheKey, entry);
                result.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            } catch (java.security.GeneralSecurityException failure) {
                throw rejected("KEY_GENERATION_FAILED");
            }
        }
        return result;
    }

    @Transactional
    public PasswordModificationResponse modify(PasswordModificationRequest request, HttpHeaders headers) {
        authorize(request.getProtocol(), headers);
        validateIdentity(request.getClientId(), request.getCloseId(), request.getRequestId());
        boolean setup = request.getFunction() == PasswordModificationRequest.FunctionEnum.A;
        if (!setup && request.getFunction() != PasswordModificationRequest.FunctionEnum.R) {
            throw rejected("INVALID_FUNCTION");
        }
        if (request.getRandomNumber() == null || !request.getRandomNumber().matches("[0-9]{6}")) {
            throw rejected("INVALID_KEY_REQUEST");
        }
        String key = cacheKey(request.getClientId(), request.getCloseId(), request.getRequestId(),
                HashUtils.sha256(request.getRandomNumber()));
        KeyEntry entry = cache().get(key, KeyEntry.class);
        if (entry == null || entry.getExpiresAt() <= System.currentTimeMillis()) {
            cache().evict(key);
            throw rejected("TRANSPORT_KEY_EXPIRED");
        }
        String hash = decryptHash(entry.getPrivateKey(), request.getNewEncryptedPassword());
        DspAuthCredential row = credentials.queryWithStatus(request.getCloseId(), request.getClientId(), "A");
        if (setup == (row != null)) { throw rejected("CREDENTIAL_STATE_CONFLICT"); }
        // CM verified and reserved the approval Code before calling R. No plaintext password is sent.
        cache().evict(key);
        LocalDateTime now = LocalDateTime.now();
        if (setup) {
            row = new DspAuthCredential();
            row.setClientId(request.getClientId());
            row.setCloseId(request.getCloseId());
            row.setObjectStatus("A");
            row.setCreatedBy("CM");
            row.setCreationDate(now);
        }
        row.setPasswordHash(hash); // Already Base64(SHA256(UTF8(password))); do not hash again.
        row.setLastUpdatedBy("CM");
        row.setLastUpdateDate(now);
        if (setup) { credentials.insert(row); } else { credentials.updatePassword(row); }
        PasswordModificationResponse result = new PasswordModificationResponse();
        result.setStatus("000000");
        result.setMessage(setup ? "password created." : "password updated.");
        return result;
    }

    private void authorize(String protocol, HttpHeaders headers) {
        if (!PROTOCOL.equals(protocol) || headers == null
                || !"BCO".equals(headers.getHeaderString("Channel"))
                || !matches(cmClientId, headers.getHeaderString("X-IBM-Client-Id"))
                || !matches(cmClientSecret, headers.getHeaderString("X-IBM-Client-Secret"))) {
            throw new Rejected(403, "CM_CALLER_REJECTED");
        }
    }

    private static boolean matches(String expected, String actual) {
        return expected != null && !expected.isBlank() && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateIdentity(String clientId, String closeId, String requestId) {
        try {
            if (clientId == null || clientId.isBlank() || clientId.length() > 64
                    || closeId == null || !closeId.matches("[^@\\s]+@[^@\\s]+") || closeId.length() > 128
                    || requestId == null || !UUID.fromString(requestId).toString().equals(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException failure) { throw rejected("INVALID_IDENTITY"); }
    }

    private Cache cache() {
        Cache result = cacheManager.getCache(CacheConfig.VARIABLE);
        if (result == null) { throw rejected("KEY_CACHE_UNAVAILABLE"); }
        return result;
    }

    private static String cacheKey(String clientId, String closeId, String requestId, String hash) {
        // Length-delimited identity prevents ambiguous concatenations; UUID avoids six-digit nonce collisions.
        return "cmPassword:" + HashUtils.sha256(clientId.length() + ":" + clientId + closeId.length()
                + ":" + closeId + ":" + requestId + ":" + hash);
    }

    static String decryptHash(String privateKey, String ciphertext) {
        byte[] clear = null;
        try {
            if (ciphertext == null || ciphertext.length() > 344) { throw new IllegalArgumentException(); }
            byte[] encrypted = Base64.getDecoder().decode(ciphertext);
            if (encrypted.length != 256) { throw new IllegalArgumentException(); }
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))), OAEP);
            clear = cipher.doFinal(encrypted);
            String hash = new String(clear, StandardCharsets.US_ASCII);
            byte[] digest = Base64.getDecoder().decode(hash);
            if (digest.length != 32 || !Base64.getEncoder().encodeToString(digest).equals(hash)) {
                throw new IllegalArgumentException();
            }
            return hash;
        } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
            throw rejected("INVALID_ENCRYPTED_HASH");
        } finally { if (clear != null) { java.util.Arrays.fill(clear, (byte) 0); } }
    }

    private static Rejected rejected(String stage) {
        // Safe stage only; no input, ciphertext, key or decrypted hash in exception/logging.
        return new Rejected(400, stage);
    }

    public static final class Rejected extends WebApplicationException {
        private static final long serialVersionUID = 1L;
        Rejected(int status, String stage) {
            super(Response.status(status).type("application/json").entity(Map.of("status", stage)).build());
        }
    }

    /** Serializable cache payload; absolute expiry also applies to the local cache fallback. */
    public static class KeyEntry {
        private String privateKey;
        private long expiresAt;
        public KeyEntry() { }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String value) { privateKey = value; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long value) { expiresAt = value; }
        @Override public String toString() { return "CmPasswordKey[REDACTED]"; }
    }
}
