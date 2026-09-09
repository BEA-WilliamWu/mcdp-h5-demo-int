package com.ofss.digx.cz.bea.app.hosttohost.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic primitives for the HTH API Password feature (BCOH2H-787).
 *
 * <p>Setup codes are stored as AES-256-GCM ciphertext (random 12-byte IV prepended, Base64
 * envelope) so authorized operators can reveal them after submission while the database never
 * holds plaintext. All comparisons of secret material run in constant time.
 */
public final class HthApiPasswordCrypto {
  private static final String AES_GCM = "AES/GCM/NoPadding";

  private static final int GCM_IV_BYTES = 12;

  private static final int GCM_TAG_BITS = 128;

  private static final String DIGITS = "0123456789";

  private static final SecureRandom RANDOM = new SecureRandom();

  private HthApiPasswordCrypto() {
  }

  /** Random numeric setup code of the configured length, e.g. {@code 086482}. */
  public static String randomDigits(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
    }
    return builder.toString();
  }

  /**
   * Encrypts a setup code with the environment AES-256 key.
   *
   * @param plaintext numeric setup code
   * @param base64Key Base64-encoded 32-byte key from {@code HTH_API_PWD_CODE_CIPHER_KEY}
   * @return Base64 of IV||ciphertext||tag
   */
  public static String encrypt(String plaintext, String base64Key) {
    try {
      byte[] iv = new byte[GCM_IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.ENCRYPT_MODE, key(base64Key), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] envelope = new byte[iv.length + sealed.length];
      System.arraycopy(iv, 0, envelope, 0, iv.length);
      System.arraycopy(sealed, 0, envelope, iv.length, sealed.length);
      return Base64.getEncoder().encodeToString(envelope);
    } catch (java.lang.Exception e) {
      throw new IllegalStateException("Unable to encrypt HTH API password code", e);
    }
  }

  /** Decrypts the envelope produced by {@link #encrypt(String, String)}. */
  public static String decrypt(String base64Envelope, String base64Key) {
    try {
      byte[] envelope = Base64.getDecoder().decode(base64Envelope);
      byte[] iv = new byte[GCM_IV_BYTES];
      System.arraycopy(envelope, 0, iv, 0, iv.length);
      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.DECRYPT_MODE, key(base64Key), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] plain = cipher.doFinal(envelope, iv.length, envelope.length - iv.length);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (java.lang.Exception e) {
      throw new IllegalStateException("Unable to decrypt HTH API password code", e);
    }
  }

  public static boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
        right.getBytes(StandardCharsets.UTF_8));
  }

  /** Shared environment key. No source-embedded or generated fallback is permitted. */
  public static String codeCipherKey() {
    String value = ConfigurationFactory.getInstance()
        .getConfigurations("HthApiCredentialAdapterConfig")
        .get("HTH_API_PWD_CODE_CIPHER_KEY", "").trim();
    key(value); // Validate configuration before any code is generated or consumed.
    return value;
  }

  private static SecretKeySpec key(String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 32) {
      throw new IllegalStateException("HTH_API_PWD_CODE_CIPHER_KEY must be a 32-byte AES key");
    }
    return new SecretKeySpec(keyBytes, "AES");
  }
}
