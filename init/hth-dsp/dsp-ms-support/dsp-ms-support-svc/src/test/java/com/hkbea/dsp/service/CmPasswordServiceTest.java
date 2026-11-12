package com.hkbea.dsp.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.hkbea.common.cache.CacheConfig;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.mapper.DspAuthCredentialMapper;
import com.hkbea.dsp.model.*;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real RSA, MyBatis mapper XML and H2 writes; only caller headers are fixtures. */
class CmPasswordServiceTest {
    private CmPasswordService service;
    private DspAuthCredentialRepoService repo;
    private ConcurrentMapCacheManager cache;
    private HttpHeaders headers;
    private SqlSession session;
    private String requestId;
    private String publicKey;
    private static final String NONCE = "314159";
    private static final String CLIENT = "test-client";
    private static final String CLOSE = "USER@PARTY";

    @BeforeEach void init() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1");
        try (Connection connection = ds.getConnection()) {
            connection.createStatement().execute("CREATE TABLE DSP_AUTH_CREDENTIAL (ID BIGINT PRIMARY KEY, "
                + "PASSWORD_HASH VARCHAR(255), CLOSE_ID VARCHAR(128), CLIENT_ID VARCHAR(64), OBJECT_STATUS CHAR(1), "
                + "CREATION_DATE TIMESTAMP, CREATED_BY VARCHAR(100), LAST_UPDATE_DATE TIMESTAMP, LAST_UPDATED_BY VARCHAR(100), "
                + "UNIQUE(CLIENT_ID,CLOSE_ID,OBJECT_STATUS))");
        }
        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(DspAuthCredentialMapper.class);
        try (var stream = getClass().getResourceAsStream("/mapper/DspAuthCredentialMapper.xml")) {
            new XMLMapperBuilder(stream, config, "mapper/DspAuthCredentialMapper.xml", config.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        repo = new DspAuthCredentialRepoService();
        field(repo, "dspAuthCredentialMapper", session.getMapper(DspAuthCredentialMapper.class));
        service = new CmPasswordService();
        cache = new ConcurrentMapCacheManager();
        field(service, "credentials", repo); field(service, "cacheManager", cache);
        field(service, "cmClientId", "fixture-id"); field(service, "cmClientSecret", "fixture-secret");
        headers = mock(HttpHeaders.class);
        when(headers.getHeaderString("Channel")).thenReturn("BCO");
        when(headers.getHeaderString("X-IBM-Client-Id")).thenReturn("fixture-id");
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("fixture-secret");
        requestId = UUID.randomUUID().toString();
    }
    @AfterEach void close() { session.close(); }
    static void field(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    PublicKeyQueryRequest query(boolean only) {
        PublicKeyQueryRequest request = new PublicKeyQueryRequest();
        request.setProtocol(CmPasswordService.PROTOCOL); request.setClientId(CLIENT); request.setCloseId(CLOSE);
        request.setRequestId(requestId); request.setHashValue(HashUtils.sha256(NONCE)); request.setQueryOnly(only);
        return request;
    }
    void key() { publicKey = service.query(query(false), headers).getPublicKey(); }
    static String hash(String password) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest(password.getBytes(StandardCharsets.UTF_8)));
    }
    String encrypt(String value, MGF1ParameterSpec mgf) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))),
            new OAEPParameterSpec("SHA-256", "MGF1", mgf, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
    PasswordModificationRequest write(String operation, String encrypted) {
        PasswordModificationRequest request = new PasswordModificationRequest();
        request.setProtocol(CmPasswordService.PROTOCOL); request.setRequestId(requestId);
        request.setClientId(CLIENT); request.setCloseId(CLOSE); request.setRandomNumber(NONCE);
        request.setFunction(PasswordModificationRequest.FunctionEnum.fromValue(operation));
        request.setNewEncryptedPassword(encrypted); return request;
    }
    String stored() { DspAuthCredential row = repo.queryWithStatus(CLOSE, CLIENT, "A"); return row == null ? null : row.getPasswordHash(); }
    void setup() throws Exception { key(); service.modify(write("A", encrypt(hash("First123"), MGF1ParameterSpec.SHA256)), headers); }

    @Test void setupAndResetPersistExactBase64HashThroughRealMapper() throws Exception {
        assertEquals("NOT_SETUP", service.query(query(true),headers).getCredentialStatus());
        setup(); assertEquals(hash("First123"), stored());
        assertEquals("ACTIVE", service.query(query(true),headers).getCredentialStatus());
        requestId = UUID.randomUUID().toString(); key();
        assertEquals("000000", service.modify(write("R",encrypt(hash("Second456"),MGF1ParameterSpec.SHA256)),headers).getStatus());
        assertEquals(hash("Second456"),stored()); assertNotEquals(hash("First123"),stored());
    }
    @Test void setupCannotOverwriteAndResetRequiresExistingCredential() throws Exception {
        key(); assertThrows(WebApplicationException.class,()->service.modify(write("R",encrypt(hash("First123"),MGF1ParameterSpec.SHA256)),headers));
        assertNull(stored()); setup(); key();
        assertThrows(WebApplicationException.class,()->service.modify(write("A",encrypt(hash("Second456"),MGF1ParameterSpec.SHA256)),headers));
        assertEquals(hash("First123"),stored());
    }
    @Test void plaintextAndHexDigestAreRejectedWithoutWrite() throws Exception {
        for (String invalid : new String[]{"First123", HashUtils.sha256("First123"), Base64.getEncoder().encodeToString(new byte[31])}) {
            key(); assertThrows(WebApplicationException.class,()->service.modify(write("A",encrypt(invalid,MGF1ParameterSpec.SHA256)),headers));
            assertNull(stored());
        }
    }
    @Test void mismatchedOaepCiphertextIsRejected() throws Exception {
        key(); assertThrows(WebApplicationException.class,()->service.modify(write("A",encrypt(hash("First123"),MGF1ParameterSpec.SHA1)),headers));
        assertNull(stored());
    }
    @Test void nonceIdentityRequestAndExpiryAreBound() throws Exception {
        key(); String cipher = encrypt(hash("First123"), MGF1ParameterSpec.SHA256);
        for (String part : new String[]{"nonce","client","close","request"}) {
            PasswordModificationRequest request=write("A",cipher);
            if (part.equals("nonce")) request.setRandomNumber("271828");
            if (part.equals("client")) request.setClientId("other-client");
            if (part.equals("close")) request.setCloseId("OTHER@PARTY");
            if (part.equals("request")) request.setRequestId(UUID.randomUUID().toString());
            assertThrows(WebApplicationException.class,()->service.modify(request,headers));
        }
        var entries=(java.util.concurrent.ConcurrentMap<?,?>)cache.getCache(CacheConfig.VARIABLE).getNativeCache();
        entries.values().forEach(value->((CmPasswordService.KeyEntry)value).setExpiresAt(0));
        assertThrows(WebApplicationException.class,()->service.modify(write("A",cipher),headers)); assertNull(stored());
    }
    @Test void usedKeyCannotBeReplayed() throws Exception {
        key(); PasswordModificationRequest request=write("A",encrypt(hash("First123"),MGF1ParameterSpec.SHA256));
        service.modify(request,headers);
        assertThrows(WebApplicationException.class,()->service.modify(request,headers));
        assertEquals(hash("First123"),stored());
    }
    @Test void internalCallerIsRequiredAndLegacyModifyCannotBypassOldPassword() throws Exception {
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("wrong");
        assertEquals(403,assertThrows(WebApplicationException.class,()->service.query(query(true),headers)).getResponse().getStatus());
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("fixture-secret");
        when(headers.getHeaderString("Channel")).thenReturn("HTH");
        assertThrows(WebApplicationException.class,()->service.query(query(true),headers));
        when(headers.getHeaderString("Channel")).thenReturn("BCO"); key();
        assertThrows(WebApplicationException.class,()->service.modify(write("M",encrypt(hash("First123"),MGF1ParameterSpec.SHA256)),headers));
        assertNull(stored());
    }
    @Test void dspPersistenceFailureIsNotReportedAsSuccess() throws Exception {
        DspAuthCredentialRepoService failing=mock(DspAuthCredentialRepoService.class);
        doThrow(new IllegalStateException("fixture database unavailable")).when(failing).insert(any());
        field(service,"credentials",failing); key();
        assertThrows(IllegalStateException.class,()->service.modify(write("A",encrypt(hash("First123"),MGF1ParameterSpec.SHA256)),headers));
        assertNull(stored());
    }
    @Test void browserTransportHashCanBeDecryptedAndStoredByDsp() throws Exception {
        String bco=System.getProperty("bco.repo");
        Assumptions.assumeTrue(bco!=null,"Set -Dbco.repo to run the actual CM browser transport interoperability test");
        key();
        Process process=new ProcessBuilder(System.getProperty("node.bin","node"),
            Path.of(bco,"consulting/tests/hth1955/browser-interop.cjs").toString(), publicKey).start();
        String json=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),new String(process.getErrorStream().readAllBytes(),StandardCharsets.UTF_8));
        var payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        service.modify(write("A",payload.path("dspEncryptedPassword").asText()),headers);
        assertEquals(hash("Browser123"),stored());
    }
    @Test void jerseyJsonBoundaryPreservesProtocolAndDefiniteRejections() throws Exception {
        PasswordApiServiceImpl resource = new PasswordApiServiceImpl();
        field(resource, "cmPasswordService", service);
        org.springframework.context.support.GenericApplicationContext spring =
                new org.springframework.context.support.GenericApplicationContext();
        spring.getBeanFactory().registerSingleton("passwordApiService", resource);
        spring.getBeanFactory().registerSingleton("cmPasswordService", service);
        spring.getBeanFactory().registerSingleton("dspAuthCredentialRepoService", repo);
        spring.getBeanFactory().registerSingleton("passwordAppService", mock(PasswordAppService.class));
        spring.getBeanFactory().registerSingleton("cmPasswordExceptionMapper", new CmPasswordExceptionMapper());
        var fallback = new com.hkbea.common.exception.DefaultExceptionMapper();
        spring.getBeanFactory().registerSingleton("defaultExceptionMapper", fallback);
        spring.getBeanFactory().registerSingleton("exceptionResponseBuilder",
                mock(com.hkbea.common.exception.ExceptionResponseBuilder.class));
        spring.refresh();
        StringBuilder httpLog = new StringBuilder();
        var httpLogger = java.util.logging.Logger.getLogger("cm-password-test-" + UUID.randomUUID());
        httpLogger.setUseParentHandlers(false); httpLogger.setLevel(java.util.logging.Level.ALL);
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { httpLog.append(record.getMessage()); }
            public void flush() { }
            public void close() { }
        };
        httpLogger.addHandler(handler);
        var productionConfig = new com.hkbea.dsp.JerseyConfig();
        org.glassfish.jersey.server.ResourceConfig config = new org.glassfish.jersey.server.ResourceConfig()
                .property("contextConfig", spring)
                .register(PasswordApiServiceImpl.class).register(CmPasswordExceptionMapper.class)
                .register(com.hkbea.common.exception.DefaultExceptionMapper.class)
                .register(org.glassfish.jersey.jackson.JacksonFeature.class)
                .register(org.glassfish.jersey.logging.LoggingFeature.class)
                .property(org.glassfish.jersey.logging.LoggingFeature.LOGGING_FEATURE_LOGGER_NAME_SERVER, httpLogger.getName())
                .property(org.glassfish.jersey.logging.LoggingFeature.LOGGING_FEATURE_VERBOSITY_SERVER,
                        productionConfig.getProperty(org.glassfish.jersey.logging.LoggingFeature.LOGGING_FEATURE_VERBOSITY_SERVER))
                .property(org.glassfish.jersey.logging.LoggingFeature.LOGGING_FEATURE_REDACT_HEADERS_SERVER,
                        productionConfig.getProperty(org.glassfish.jersey.logging.LoggingFeature.LOGGING_FEATURE_REDACT_HEADERS_SERVER))
                .property("jersey.config.server.disableAutoDiscovery", true);
        org.glassfish.jersey.server.ApplicationHandler app = new org.glassfish.jersey.server.ApplicationHandler(config);
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            for (String secret : new String[]{"fixture-secret", "wrong"}) {
                postJson(app, "public-key", query(true), secret,
                        secret.equals("fixture-secret") ? 200 : 403,
                        secret.equals("fixture-secret") ? "000000" : "CM_CALLER_REJECTED");
            }
            for (String operation : new String[]{"A", "R"}) {
                requestId = UUID.randomUUID().toString();
                publicKey = postJson(app, "public-key", query(false), "fixture-secret", 200, "000000")
                        .path("publicKey").asText();
                String expected = hash(operation.equals("A") ? "First123" : "Second456");
                PasswordModificationRequest change = write(operation, encrypt(expected, MGF1ParameterSpec.SHA256));
                postJson(app, "modification", change, "fixture-secret", 200, "000000");
                assertEquals(expected, stored());
                postJson(app, "modification", change, "fixture-secret", 400, "TRANSPORT_KEY_EXPIRED");
            }
            assertTrue(httpLog.length() > 0);
            assertFalse(httpLog.toString().contains("fixture-secret"));
            assertFalse(httpLog.toString().contains("fixture-id"));
            assertFalse(httpLog.toString().contains("newEncryptedPassword"));
            assertFalse(httpLog.toString().contains(publicKey));
        } finally {
            httpLogger.removeHandler(handler);
            spring.close();
        }
    }

    private com.fasterxml.jackson.databind.JsonNode postJson(org.glassfish.jersey.server.ApplicationHandler app,
            String endpoint, Object value, String secret, int status, String result) throws Exception {
        var input = new org.glassfish.jersey.server.ContainerRequest(java.net.URI.create("http://localhost/dsp/api/"),
                java.net.URI.create("http://localhost/dsp/api/password/v1/" + endpoint), "POST", null,
                new org.glassfish.jersey.internal.MapPropertiesDelegate());
        input.getHeaders().putSingle("Content-Type", "application/json");
        input.getHeaders().putSingle("Channel", "BCO");
        input.getHeaders().putSingle("X-IBM-Client-Id", "fixture-id");
        input.getHeaders().putSingle("X-IBM-Client-Secret", secret);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        input.setEntityStream(new java.io.ByteArrayInputStream(json.writeValueAsBytes(value)));
        var output = new java.io.ByteArrayOutputStream();
        var response = app.apply(input, output).get();
        assertEquals(status, response.getStatus());
        var body = json.readTree(output.toByteArray());
        assertEquals(result, body.path("status").asText());
        return body;
    }

    @Test void cmResourceSanitizesUnexpectedPersistenceAndCommitFailures() throws Exception {
        PasswordApiServiceImpl resource = new PasswordApiServiceImpl();
        CmPasswordService failing = mock(CmPasswordService.class);
        field(resource, "cmPasswordService", failing);
        field(resource, "requestHeaders", headers);
        when(failing.modify(any(), any())).thenThrow(new IllegalStateException("sensitive fixture SQL"));
        when(failing.query(any(), any())).thenThrow(new IllegalStateException("sensitive fixture SQL"));
        for (Runnable call : new Runnable[]{() -> resource.queryPublicKey(query(true)),
                () -> resource.modifyPassword(write("A", "fixture"))}) {
            CmPasswordService.Rejected failure = assertThrows(CmPasswordService.Rejected.class, call::run);
            assertEquals(500, failure.getResponse().getStatus());
            assertNull(failure.getCause());
            assertEquals(java.util.Map.of("status", "CM_PASSWORD_SYSTEM_FAILURE"), failure.getResponse().getEntity());
        }
    }
    @Test void tokenValidationUsesOnlyLatestDspHashAfterReset() throws Exception {
        setup();
        var token = new CorporateTokenServiceImpl();
        field(token, "dspAuthCredentialMapper", session.getMapper(DspAuthCredentialMapper.class));
        field(token, "clientAssertionType", "fixture-type");
        field(token, "grantType", "fixture-grant");
        var signingKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
        var cypher = mock(com.hkbea.dsp.api.CypherApi.class);
        when(cypher.validateSignature(any())).thenAnswer(invocation -> {
            SignatureValidationParam input = invocation.getArgument(0);
            assertEquals(CLIENT, input.getKeySubject());
            var verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(signingKey.toRSAPublicKey());
            verifier.update(input.getContent().getBytes(StandardCharsets.UTF_8));
            ValidationResult result = new ValidationResult();
            result.setValid(verifier.verify(Base64.getDecoder().decode(input.getSignature())));
            return result;
        });
        field(token, "cypherApi", cypher);
        var uam = mock(com.hkbea.dsp.uam.api.UamService.class);
        var access = new com.hkbea.dsp.uam.model.GetTokenResult();
        access.setAccessToken("fixture-access-token"); access.setTokenType("Bearer");
        when(uam.getAccessToken(anyString(), anyString(), anyString())).thenReturn(access);
        field(token, "uamService", uam);
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(CLIENT).subject(CLIENT)
                .claim("close-id", CLOSE).audience("fixture-audience").jwtID(UUID.randomUUID().toString())
                .issueTime(new java.util.Date(System.currentTimeMillis() - 1000))
                .expirationTime(new java.util.Date(System.currentTimeMillis() + 60000)).build();
        var assertion = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(
                com.nimbusds.jose.JWSAlgorithm.RS256), claims);
        assertion.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
        CorporateTokenParam request = new CorporateTokenParam();
        request.setClientAssertion(assertion.serialize());
        request.setPasswordSignature(signHash(signingKey.toRSAPrivateKey(), hash("First123")));
        assertEquals("fixture-access-token", token.getCorporateToken(request).getAccessToken());
        requestId = UUID.randomUUID().toString(); key();
        service.modify(write("R", encrypt(hash("Second456"), MGF1ParameterSpec.SHA256)), headers);
        assertThrows(com.hkbea.common.exception.ParameterException.class, () -> token.getCorporateToken(request));
        request.setPasswordSignature(signHash(signingKey.toRSAPrivateKey(), hash("Second456")));
        assertEquals("fixture-access-token", token.getCorporateToken(request).getAccessToken());
        verify(uam, times(2)).getAccessToken(anyString(), anyString(), anyString());
    }

    private static String signHash(java.security.PrivateKey key, String hash) throws Exception {
        var signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(key); signer.update(hash.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

}
