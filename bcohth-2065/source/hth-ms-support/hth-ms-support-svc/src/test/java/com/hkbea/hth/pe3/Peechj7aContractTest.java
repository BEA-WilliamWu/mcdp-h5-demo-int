package com.hkbea.hth.pe3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.pe3.model.Peechj7aContractI;
import com.hkbea.hth.pe3.model.Peechj7aDfhcommarea;
import com.hkbea.hth.pe3.model.Peechj7aInput;
import com.hkbea.hth.pe3.model.Peechj7aKeyAccI;
import com.hkbea.hth.pe3.model.Peechj7aQaechcc;
import com.hkbea.hth.pe3.model.Peechj7aRecord;
import com.hkbea.hth.pe3.model.Peechj7aRequest;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs3.JAXRS3Contract;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Peechj7aContractTest {
    @Test
    void feignPostsAccountKeyAndDecodesContactEnvelopeUsingThePe3Contract() throws Exception {
        AtomicReference<Request> captured = new AtomicReference<>();
        String body = """
                {"dfhcommarea":{"qaechcc":{"qaechccErrData":"0000","peechj7a":{"ehj7aOutput":{
                  "ehj7aAnswer":{"ehj7aCodReturn":"00","ehj7aSqlcode":0},
                  "ehj7aContactO":[{"ehj7aNumCusO":"12345678","ehj7aNumLadr1O":1,
                    "ehj7aAddressO":{"ehj7aPrimAddFlagO":"Y","ehj7aLangCodO":"E",
                      "ehj7aExtAdrLin1O":"FIRST LINE","ehj7aExtAdrLin5O":"FIFTH LINE",
                      "ehj7aCodCountryAdrO":"HKG","ehj7aCodMailO":1}}]}}}}}
                """;
        Pe3Api api = Feign.builder().contract(new JAXRS3Contract())
                .encoder(new JacksonEncoder()).decoder(new JacksonDecoder())
                .client((request, options) -> {
                    captured.set(request);
                    return Response.builder().request(request).status(200).reason("OK")
                            .headers(Map.of("Content-Type", List.of("application/json")))
                            .body(body, StandardCharsets.UTF_8).build();
                }).target(Pe3Api.class, "https://pe3.test/hth");
        var request = new Peechj7aRequest().dfhcommarea(new Peechj7aDfhcommarea()
                .qaechcc(new Peechj7aQaechcc().qaechccApplId("PE").qaechccCollecName("PE9CHJ7A")
                        .peechj7a(new Peechj7aRecord().ehj7aInput(new Peechj7aInput()
                                .ehj7aCodEntityI("0015").ehj7aContractI(new Peechj7aContractI()
                                        .ehj7aKeyAccI(new Peechj7aKeyAccI().ehj7aKeyEntityI("0015")
                                                .ehj7aBrnOpenI("0521").ehj7aNumAccountI("6815032777")))))));

        var response = api.getCustomerContactInfo(request);

        assertEquals(Request.HttpMethod.POST, captured.get().httpMethod());
        assertEquals("https://pe3.test/hth/v1z/peechj7a.v1", captured.get().url());
        var json = new ObjectMapper().readTree(captured.get().body());
        assertEquals("6815032777", json.at("/dfhcommarea/qaechcc/peechj7a/ehj7aInput/ehj7aContractI/ehj7aKeyAccI/ehj7aNumAccountI").asText());
        assertFalse(json.at("/dfhcommarea/qaechcc/peechj7a").has("ehj7aOutput"));
        var output = response.getDfhcommarea().getQaechcc().getPeechj7a().getEhj7aOutput();
        assertEquals("00", output.getEhj7aAnswer().getEhj7aCodReturn());
        assertNotNull(output.getEhj7aContactO());
        var address = output.getEhj7aContactO().getFirst().getEhj7aAddressO();
        assertEquals("FIRST LINE", address.getEhj7aExtAdrLin1O());
        assertEquals("FIFTH LINE", address.getEhj7aExtAdrLin5O());
        assertEquals("HKG", address.getEhj7aCodCountryAdrO());
    }
}
