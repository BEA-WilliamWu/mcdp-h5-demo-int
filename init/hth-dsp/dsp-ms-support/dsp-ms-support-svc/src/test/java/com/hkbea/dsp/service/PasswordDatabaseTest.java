package com.hkbea.dsp.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.hkbea.dsp.dto.CryptKeyDto;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.mapper.DspAuthCredentialMapper;
import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.repo.DspAuthCredentialRepoService;
import com.hkbea.dsp.utils.HashUtils;
import jakarta.ws.rs.core.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real DSP service, MyBatis mapper and transaction manager. H2 replaces Oracle; Redis is an in-memory cache. */
class PasswordDatabaseTest {
    private PasswordApiServiceImpl service;
    private DspAuthCredentialRepoService repo;
    private DspAuthCredentialMapper mapper;
    private Keys keys;
    private String publicKey;
    static final String NONCE="314159";
    static class Keys extends PasswordAppService {
        final Map<String,CryptKeyDto> cache=new HashMap<>(); boolean failAfterWrite;
        @Override public CryptKeyDto getCryptoKeyDto(String h) { return cache.computeIfAbsent(h, x->super.getCryptoKeyDto(x)); }
        @Override public CryptKeyDto findCryptoKeyDto(String h) { return cache.get(h); }
        @Override public void removeCryptoKeyDto(String h) { cache.remove(h); if(failAfterWrite) throw new IllegalStateException(); }
    }
    @BeforeEach void init() throws Exception {
        JdbcDataSource data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=Oracle;DB_CLOSE_DELAY=-1");
        try(var c=data.getConnection()) {
            c.createStatement().execute("CREATE TABLE DSP_AUTH_CREDENTIAL (ID BIGINT PRIMARY KEY, PASSWORD_HASH VARCHAR(255), "
                +"CLIENT_ID VARCHAR(64), CLOSE_ID VARCHAR(255), OBJECT_STATUS CHAR(1), CREATION_DATE TIMESTAMP, CREATED_BY VARCHAR(100), "
                +"LAST_UPDATE_DATE TIMESTAMP, LAST_UPDATED_BY VARCHAR(100), UNIQUE(CLIENT_ID,CLOSE_ID,OBJECT_STATUS))");
        }
        MybatisConfiguration config=new MybatisConfiguration();
        config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),data));config.addMapper(DspAuthCredentialMapper.class);
        try(var stream=getClass().getResourceAsStream("/mapper/DspAuthCredentialMapper.xml")) {
            new XMLMapperBuilder(stream,config,"mapper/DspAuthCredentialMapper.xml",config.getSqlFragments()).parse();
        }
        SqlSessionTemplate template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        mapper=template.getMapper(DspAuthCredentialMapper.class);
        repo=new DspAuthCredentialRepoService();ReflectionTestUtils.setField(repo,"dspAuthCredentialMapper",mapper);
        keys=new Keys();CmPasswordCallerGuard guard=new CmPasswordCallerGuard();
        ReflectionTestUtils.setField(guard,"clientId","fixture-id");ReflectionTestUtils.setField(guard,"clientSecret","fixture-secret");
        HttpHeaders headers=mock(HttpHeaders.class);
        when(headers.getHeaderString("Channel")).thenReturn("BCO");
        when(headers.getHeaderString("X-IBM-Client-Id")).thenReturn("fixture-id");
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("fixture-secret");
        PasswordApiServiceImpl target=new PasswordApiServiceImpl();
        ReflectionTestUtils.setField(target,"headers",headers);ReflectionTestUtils.setField(target,"callerGuard",guard);
        ReflectionTestUtils.setField(target,"passwordAppService",keys);ReflectionTestUtils.setField(target,"dspAuthCredentialRepoService",repo);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));
        service=(PasswordApiServiceImpl)proxy.getProxy();
    }
    String hash(String p) throws Exception { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(p.getBytes(StandardCharsets.UTF_8))); }
    PasswordModificationRequest request(String f,String password) throws Exception {
        PublicKeyQueryRequest query=new PublicKeyQueryRequest();query.setHashValue(HashUtils.sha256(NONCE));
        publicKey=service.queryPublicKey(query).getPublicKey();
        Cipher cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE,KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))),
            new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        return new PasswordModificationRequest().function(PasswordModificationRequest.FunctionEnum.fromValue(f)).clientId("client").closeId("USER@PARTY")
            .randomNumber(NONCE).newEncryptedPassword(Base64.getEncoder().encodeToString(cipher.doFinal(hash(password).getBytes(StandardCharsets.US_ASCII))));
    }
    String stored() { DspAuthCredential row=repo.queryWithStatus("USER@PARTY","client","A");return row==null?null:row.getPasswordHash(); }
    @Test void setupAndResetPersistExactHashWithRealMapper() throws Exception {
        service.modifyPassword(request("A","First123"));assertEquals(hash("First123"),stored());
        service.modifyPassword(request("M","Second456"));assertEquals(hash("Second456"),stored());assertNotEquals(hash("First123"),stored());
    }
    @Test void successfulWriteConsumesKey() throws Exception {
        var r=request("A","First123");service.modifyPassword(r);
        assertThrows(RuntimeException.class,()->service.modifyPassword(r));assertEquals(hash("First123"),stored());
    }
    @Test void duplicateSetupAndAbsentResetDoNotWrite() throws Exception {
        assertThrows(RuntimeException.class,()->service.modifyPassword(request("M","First123")));assertNull(stored());
        service.modifyPassword(request("A","First123"));
        assertThrows(RuntimeException.class,()->service.modifyPassword(request("A","Other123")));assertEquals(hash("First123"),stored());
    }
    @Test void runtimeFailureAfterUpdateRollsBackRealTransaction() throws Exception {
        service.modifyPassword(request("A","First123"));keys.failAfterWrite=true;
        assertThrows(IllegalStateException.class,()->service.modifyPassword(request("M","Second456")));
        assertEquals(hash("First123"),stored());
    }

    /** AC4: production token entry point and signature verification read the latest real database row.
     * Only external UAM token issuance/public-key lookup are fixtures; no real UAM token is obtained. */
    @Test void tokenFlowAcceptsLatestPasswordAndRejectsPreviousPasswordAfterReset() throws Exception {
        var signingKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fixture").generate();
        CypherServiceImpl cypher = new CypherServiceImpl() {
            @Override public String getPublicKeyByClientIdV2(String clientId) {
                assertEquals("client", clientId);
                return new com.nimbusds.jose.jwk.JWKSet(signingKey.toPublicJWK()).toString();
            }
        };
        var uam = mock(com.hkbea.dsp.uam.api.UamService.class);
        var issuedToken = new com.hkbea.dsp.uam.model.GetTokenResult();
        issuedToken.setAccessToken("fixture-token");
        issuedToken.setTokenType("Bearer");
        when(uam.getAccessToken(anyString(), anyString(), anyString())).thenReturn(issuedToken);
        var tokens = new CorporateTokenServiceImpl();
        ReflectionTestUtils.setField(tokens, "dspAuthCredentialMapper", mapper);
        ReflectionTestUtils.setField(tokens, "cypherApi", cypher);
        ReflectionTestUtils.setField(tokens, "uamService", uam);
        ReflectionTestUtils.setField(tokens, "clientAssertionType", "fixture-type");
        ReflectionTestUtils.setField(tokens, "grantType", "fixture-grant");

        var assertion = new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),
            new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("client").subject("client")
                .audience("fixture-audience").claim("close-id", "USER@PARTY")
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60000))
                .jwtID(UUID.randomUUID().toString()).build());
        assertion.sign(new com.nimbusds.jose.crypto.RSASSASigner(signingKey));
        var login = new com.hkbea.dsp.model.CorporateTokenParam();
        login.setClientAssertion(assertion.serialize());
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(signingKey.toPrivateKey());
        signer.update(hash("First123").getBytes(StandardCharsets.UTF_8));
        String previousSignature = Base64.getEncoder().encodeToString(signer.sign());

        service.modifyPassword(request("A", "First123"));
        login.setPasswordSignature(previousSignature);
        assertEquals("fixture-token", tokens.getCorporateToken(login).getAccessToken());

        service.modifyPassword(request("M", "Second456"));
        clearInvocations(uam);
        var rejected = assertThrows(com.hkbea.common.exception.ParameterException.class,
            () -> tokens.getCorporateToken(login));
        assertEquals(com.hkbea.dsp.exception.DspSupportErrorMapping.PASSWORD_SIGNATURE_VERIFY_EXCEPTION,
            rejected.getErrCode());
        verifyNoInteractions(uam);

        signer.initSign(signingKey.toPrivateKey());
        signer.update(hash("Second456").getBytes(StandardCharsets.UTF_8));
        login.setPasswordSignature(Base64.getEncoder().encodeToString(signer.sign()));
        assertEquals("fixture-token", tokens.getCorporateToken(login).getAccessToken());
        verify(uam).getAccessToken(anyString(), anyString(), anyString());
    }
}
