package com.ofss.digx.cz.bea.app.hosttohost.util;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;

/** Session-bound RSA transport for HTH credentials; independent of host PIN keys. */
public final class HthApiPasswordTransport {
  public static final String SESSION_ATTRIBUTE = "HTH_API_PASSWORD_TRANSPORT";
  public static final String THREAD_ATTRIBUTE = "HTH_API_PASSWORD_TRANSPORT_CONTEXT";
  private static final long VALIDITY_MILLIS = 10 * 60 * 1000L;

  private HthApiPasswordTransport() { }

  /** Serializable so the existing application session replication can retain its key. */
  public static final class KeyMaterial implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String owner;
    private final String keyId;
    private final String modulus;
    private final String publicExponent;
    private final PrivateKey privateKey;
    private final long expiresAt;

    private KeyMaterial(String owner, KeyPair pair, long now) {
      this.owner = owner;
      this.keyId = UUID.randomUUID().toString();
      RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
      this.modulus = publicKey.getModulus().toString(16);
      this.publicExponent = publicKey.getPublicExponent().toString(16);
      this.privateKey = pair.getPrivate();
      this.expiresAt = now + VALIDITY_MILLIS;
    }

    public String getKeyId() { return keyId; }
    public String getModulus() { return modulus; }
    public String getPublicExponent() { return publicExponent; }
    public boolean isUsable(String currentOwner, long now) {
      return owner.equals(currentOwner) && now < expiresAt;
    }
    @Override
    public String toString() { return "HthApiPasswordTransport.KeyMaterial[PROTECTED]"; }
  }

  public static KeyMaterial create(String owner, long now) throws GeneralSecurityException {
    if (owner == null || owner.trim().isEmpty()) { throw new GeneralSecurityException(); }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return new KeyMaterial(owner, generator.generateKeyPair(), now);
  }

  /** Decrypts only with a matching, unexpired key from the authenticated session. */
  public static String decrypt(KeyMaterial material, String owner, String keyId,
      String ciphertext, long now) throws GeneralSecurityException {
    if (material == null || !material.isUsable(owner, now) || !material.keyId.equals(keyId)
        || ciphertext == null || ciphertext.length() > 512) {
      throw new GeneralSecurityException();
    }
    byte[] encrypted;
    try { encrypted = Base64.getDecoder().decode(ciphertext); }
    catch (IllegalArgumentException e) { throw new GeneralSecurityException(); }
    if (encrypted.length == 0 || encrypted.length > 256) { throw new GeneralSecurityException(); }
    // The existing browser RSA library omits leading zero octets from the integer.
    byte[] block = new byte[256];
    System.arraycopy(encrypted, 0, block, block.length - encrypted.length, encrypted.length);
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.DECRYPT_MODE, material.privateKey);
    byte[] plain = cipher.doFinal(block);
    try { return new String(plain, StandardCharsets.UTF_8); }
    finally { java.util.Arrays.fill(plain, (byte) 0); }
  }
}
