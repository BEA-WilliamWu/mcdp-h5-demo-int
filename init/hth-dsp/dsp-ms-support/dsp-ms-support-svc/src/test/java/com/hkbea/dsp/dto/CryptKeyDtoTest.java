package com.hkbea.dsp.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.redisson.client.handler.State;
import org.redisson.codec.JsonJacksonCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CryptKeyDtoTest {
    @Test
    void jacksonReconstructsBothImmutableKeyFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CryptKeyDto original = new CryptKeyDto("fixture-public-key", "fixture-private-key");
        CryptKeyDto restored = mapper.readValue(mapper.writeValueAsBytes(original), CryptKeyDto.class);
        assertEquals(original.getPublicKey(), restored.getPublicKey());
        assertEquals(original.getPrivateKey(), restored.getPrivateKey());
    }

    @Test
    void redissonReadsExistingCacheFormatAndNewWrites() throws Exception {
        JsonJacksonCodec codec = new JsonJacksonCodec();
        // Existing Redis entries use the same class/property names; no cache flush is needed.
        ByteBuf existing = Unpooled.copiedBuffer("{\"@class\":\"com.hkbea.dsp.dto.CryptKeyDto\","
                + "\"privateKey\":\"fixture-private-key\",\"publicKey\":\"fixture-public-key\"}",
                StandardCharsets.UTF_8);
        try {
            CryptKeyDto restored = assertInstanceOf(CryptKeyDto.class,
                    codec.getValueDecoder().decode(existing, new State()));
            assertEquals("fixture-public-key", restored.getPublicKey());
            assertEquals("fixture-private-key", restored.getPrivateKey());
            ByteBuf encoded = codec.getValueEncoder().encode(restored);
            try {
                CryptKeyDto reloaded = assertInstanceOf(CryptKeyDto.class,
                        codec.getValueDecoder().decode(encoded, new State()));
                assertEquals(restored.getPublicKey(), reloaded.getPublicKey());
                assertEquals(restored.getPrivateKey(), reloaded.getPrivateKey());
            } finally {
                encoded.release();
            }
        } finally {
            existing.release();
        }
    }
}
