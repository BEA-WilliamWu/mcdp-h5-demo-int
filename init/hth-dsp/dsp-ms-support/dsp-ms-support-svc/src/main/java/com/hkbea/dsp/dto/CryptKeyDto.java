package com.hkbea.dsp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CryptKeyDto {

    private final String publicKey;

    private final String privateKey;

    // Redis uses Jackson to reconstruct the cached key pair during password modification.
    @JsonCreator
    public CryptKeyDto(@JsonProperty("publicKey") String publicKey,
                       @JsonProperty("privateKey") String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
