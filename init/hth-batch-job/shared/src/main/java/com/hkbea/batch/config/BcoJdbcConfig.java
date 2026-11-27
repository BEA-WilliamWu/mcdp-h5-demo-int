package com.hkbea.batch.config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/** Reads BCO-format fields in the HTH JDBC configuration. */
public final class BcoJdbcConfig {
    private static final String WRAPPING_KEY = "HTH_BATCH_BCO_WRAPPING_KEY";
    private static final Set<String> BCO_FIELDS = Set.of(
        "encr_passphrase", "db.hostname", "db.port", "db.servicename", "db.username", "db.password"
    );
    private static final int AES_BLOCK_BYTES = 16;

    private BcoJdbcConfig() { }

    public static Map<String, String> resolve(Properties encrypted, Function<String, String> environment) {
        if (BCO_FIELDS.stream().noneMatch(encrypted::containsKey)) {
            return Map.of();
        }
        String wrappingKey = environment.apply(WRAPPING_KEY);
        if (wrappingKey == null || wrappingKey.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable " + WRAPPING_KEY);
        }

        String passphrase = decrypt(encrypted, "encr_passphrase", wrappingKey);
        String host = decrypt(encrypted, "db.hostname", passphrase);
        String port = decrypt(encrypted, "db.port", passphrase);
        String service = decrypt(encrypted, "db.servicename", passphrase);
        return Map.of(
            "jdbc.conn.string", "jdbc:oracle:thin:@//" + host + ":" + port + "/" + service,
            "jdbc.conn.username", decrypt(encrypted, "db.username", passphrase),
            "jdbc.conn.password", decrypt(encrypted, "db.password", passphrase)
        );
    }

    private static String decrypt(Properties encrypted, String field, String keyAndIv) {
        String ciphertext = encrypted.getProperty(field);
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("Missing BCO configuration key " + field);
        }
        String invalidValue = "Invalid BCO encrypted value for " + field;
        String value;
        try {
            // Match BCO's legacy key derivation and AES/CBC ciphertext format.
            String keyMaterial = keyAndIv.length() < 40 ? digest(keyAndIv) : keyAndIv;
            String ivMaterial = keyAndIv.length() < 40
                ? digest(keyMaterial)
                : keyMaterial.substring(keyMaterial.length() - AES_BLOCK_BYTES);
            byte[] key = keyMaterial.substring(0, AES_BLOCK_BYTES).getBytes(StandardCharsets.UTF_8);
            byte[] iv = Arrays.copyOf(ivMaterial.getBytes(StandardCharsets.UTF_8), AES_BLOCK_BYTES);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            value = new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8).trim();
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            // The batch entry points log this error. Report the field name only.
            throw new IllegalArgumentException(invalidValue);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(invalidValue);
        }
        return value;
    }

    private static String digest(String value) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
