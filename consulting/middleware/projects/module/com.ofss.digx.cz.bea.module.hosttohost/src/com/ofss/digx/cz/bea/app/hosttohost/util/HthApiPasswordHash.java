package com.ofss.digx.cz.bea.app.hosttohost.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * One-way DATABASE credential encoding using the JDK PBKDF2 provider.
 *
 * <p>The persisted format is algorithm version, iteration count, random salt and derived hash,
 * separated by dollar signs. The version fixes the algorithm and lengths for verification.
 */
public final class HthApiPasswordHash {
  private static final String FORMAT = "pbkdf2-sha256-v1";
  private static final int ITERATIONS = 600000;
  private static final SecureRandom RANDOM = new SecureRandom();
  private HthApiPasswordHash() { }

  /**
   * Encodes a non-empty password with a fresh 16-byte salt and a 32-byte derived hash.
   *
   * @return the versioned value stored in PASSWORD_HASH
   * @throws IllegalArgumentException when the password is absent
   * @throws IllegalStateException when the JDK cryptographic provider is unavailable
   */
  public static String hash(String password) {
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("Password required");
    }
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] derived = derive(password, salt, ITERATIONS);
    try {
      return FORMAT + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
          + "$" + Base64.getEncoder().encodeToString(derived);
    } finally {
      Arrays.fill(derived, (byte) 0);
    }
  }

  /**
   * Compares a password with a supported stored encoding using a constant-time hash comparison.
   * Malformed or unsupported encodings return false. The authentication caller enforces account
   * status, throttling and lockout before accepting the credential.
   */
  public static boolean verify(String password, String encoded) {
    if (password == null || password.isEmpty() || encoded == null) {
      return false;
    }
    try {
      String[] parts = encoded.split("\\$", -1);
      if (parts.length != 4 || !FORMAT.equals(parts[0])) {
        return false;
      }
      int iterations = Integer.parseInt(parts[1]);
      if (iterations != ITERATIONS) {
        return false;
      }
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      if (salt.length != 16 || expected.length != 32) {
        return false;
      }
      byte[] actual = derive(password, salt, iterations);
      try {
        return MessageDigest.isEqual(expected, actual);
      } finally {
        Arrays.fill(actual, (byte) 0);
      }
    } catch (IllegalArgumentException malformed) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt, int iterations) {
    char[] chars = password.toCharArray();
    PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
    Arrays.fill(chars, '\0');
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("API password hashing unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
