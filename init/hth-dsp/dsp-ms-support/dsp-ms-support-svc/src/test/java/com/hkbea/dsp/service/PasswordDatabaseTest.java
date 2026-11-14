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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.client.handler.State;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real DSP service, MyBatis mapper and transaction manager. H2 replaces Oracle; Redis storage is replaced by an in-memory map with the real Redisson JSON codec. */
class PasswordDatabaseTest {
    private PasswordApiServiceImpl service;
    private PasswordApiServiceImpl target;
    private DspAuthCredentialRepoService repo;
    private DspAuthCredentialMapper mapper;
    private Keys keys;
    private String publicKey;
    static final String NONCE="314159";
    static class Keys extends PasswordAppService {
        final Map<String,byte[]> cache=new ConcurrentHashMap<>(); boolean failAfterWrite;
        final JsonJacksonCodec codec=new JsonJacksonCodec();
        @Override public CryptKeyDto getCryptoKeyDto(String h) {
            if(cache.containsKey(h)) return findCryptoKeyDto(h);
            CryptKeyDto keys=super.getCryptoKeyDto(h);
            try {
                ByteBuf encoded=codec.getValueEncoder().encode(keys);
                try { byte[] bytes=new byte[encoded.readableBytes()]; encoded.readBytes(bytes); cache.put(h,bytes); }
                finally { encoded.release(); }
            } catch(java.io.IOException e) { throw new IllegalStateException("Cache encoding failed",e); }
            return keys;
        }
        @Override public CryptKeyDto findCryptoKeyDto(String h) {
            byte[] bytes=cache.get(h);
            if(bytes==null) return null;
            ByteBuf encoded=Unpooled.wrappedBuffer(bytes);
            try { return (CryptKeyDto)codec.getValueDecoder().decode(encoded,new State()); }
            catch(java.io.IOException e) { throw new IllegalStateException("Cache decoding failed",e); }
            finally { encoded.release(); }
        }
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
        keys=new Keys();
        // APIC authenticates upstream; exercise the actual service without APIC headers or credential settings.
        target=new PasswordApiServiceImpl();
        ReflectionTestUtils.setField(target,"passwordAppService",keys);ReflectionTestUtils.setField(target,"dspAuthCredentialRepoService",repo);
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));
        service=(PasswordApiServiceImpl)proxy.getProxy();
    }
    String hash(String p) throws Exception { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(p.getBytes(StandardCharsets.UTF_8))); }
    PasswordModificationRequest request(String f,String password) throws Exception {
        return request(f, password, NONCE);
    }
    PasswordModificationRequest request(String f,String password,String nonce) throws Exception {
        PublicKeyQueryRequest query=new PublicKeyQueryRequest();query.setHashValue(HashUtils.sha256(nonce));
        publicKey=service.queryPublicKey(query).getPublicKey();
        Cipher cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE,KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))),
            new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
        return new PasswordModificationRequest().function(PasswordModificationRequest.FunctionEnum.fromValue(f)).clientId("client").closeId("USER@PARTY")
            .randomNumber(nonce).newEncryptedPassword(Base64.getEncoder().encodeToString(cipher.doFinal(hash(password).getBytes(StandardCharsets.US_ASCII))));
    }
    String stored() { DspAuthCredential row=repo.queryWithStatus("USER@PARTY","client","A");return row==null?null:row.getPasswordHash(); }
    @Test void setupAndResetPersistExactHashWithoutApicHeaders() throws Exception {
        service.modifyPassword(request("A","First123"));assertEquals(hash("First123"),stored());
        service.modifyPassword(request("M","Second456"));assertEquals(hash("Second456"),stored());assertNotEquals(hash("First123"),stored());
    }
    @Test void successfulWriteConsumesKey() throws Exception {
        var r=request("A","First123");service.modifyPassword(r);
        assertThrows(RuntimeException.class,()->service.modifyPassword(r));assertEquals(hash("First123"),stored());
    }
    @Test void duplicateSetupDoesNotOverwriteLegacyResetCredential() throws Exception {
        service.modifyPassword(request("M","First123"));
        assertThrows(RuntimeException.class,()->service.modifyPassword(request("A","Other123")));assertEquals(hash("First123"),stored());
    }
    @Test void legacyResetCreatesCredentialThenUpdatesSameRowAndConsumesKey() throws Exception {
        var first = request("M", "First123");
        assertEquals("000000", service.modifyPassword(first).getStatus());
        assertEquals(hash("First123"), stored());
        Long id = repo.queryWithStatus("USER@PARTY", "client", "A").getId();
        assertThrows(RuntimeException.class, () -> service.modifyPassword(first));
        assertEquals(hash("First123"), stored());
        assertEquals("000000", service.modifyPassword(request("M", "Second456")).getStatus());
        assertEquals(id, repo.queryWithStatus("USER@PARTY", "client", "A").getId());
        assertEquals(hash("Second456"), stored());
        assertEquals(1L, mapper.selectCount(null));
    }
    @Test void inactiveCredentialBlocksResetWithoutChangingStoredData() throws Exception {
        DspAuthCredential inactive = new DspAuthCredential();
        inactive.setClientId("client"); inactive.setCloseId("USER@PARTY");
        inactive.setObjectStatus("I"); inactive.setPasswordHash(hash("First123"));
        repo.insert(inactive);
        assertThrows(com.hkbea.common.exception.ServiceException.class,
                () -> service.modifyPassword(request("M", "Second456")));
        assertNull(stored());
        assertEquals(hash("First123"), repo.queryWithStatus("USER@PARTY", "client", "I").getPasswordHash());
        assertEquals(1L, mapper.selectCount(null));
    }
    @Test void legacyCreationUsesExactClientAndCloseIdPair() throws Exception {
        // An unrelated inactive credential with either the same client or the same close ID must not be changed.
        for (String[] pair : new String[][]{{"other-client", "USER@PARTY"}, {"client", "OTHER@PARTY"}}) {
            DspAuthCredential other = new DspAuthCredential();
            other.setClientId(pair[0]); other.setCloseId(pair[1]); other.setObjectStatus("I");
            other.setPasswordHash(hash("Other123")); repo.insert(other);
        }
        service.modifyPassword(request("M", "First123"));
        assertEquals(hash("First123"), stored());
        assertEquals(hash("Other123"), repo.queryWithStatus("USER@PARTY", "other-client", "I").getPasswordHash());
        assertEquals(hash("Other123"), repo.queryWithStatus("OTHER@PARTY", "client", "I").getPasswordHash());
        assertEquals(3L, mapper.selectCount(null));
    }
    @Test void failureAfterLegacyInsertRollsBackRealTransaction() throws Exception {
        keys.failAfterWrite = true;
        assertThrows(IllegalStateException.class, () -> service.modifyPassword(request("M", "First123")));
        assertNull(stored()); assertEquals(0L, mapper.selectCount(null));
    }
    @Test void uniqueConstraintPreventsConcurrentLegacyInsertWithoutOverwritingWinner() throws Exception {
        var first = request("M", "First123", "314159");
        var second = request("M", "Second456", "271828");
        var raceRepo = spy(repo);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean exists = (Boolean) invocation.callRealMethod();
            assertFalse(exists);
            barrier.await(10, TimeUnit.SECONDS);
            return false;
        }).when(raceRepo).existsForClientAndCloseId("USER@PARTY", "client");
        ReflectionTestUtils.setField(target, "dspAuthCredentialRepoService", raceRepo);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (var request : List.of(first, second)) {
                results.add(executor.submit(() -> {
                    try { return "000000".equals(service.modifyPassword(request).getStatus()); }
                    catch (org.springframework.dao.DuplicateKeyException conflict) { return false; }
                }));
            }
            boolean firstWon = results.get(0).get(15, TimeUnit.SECONDS);
            boolean secondWon = results.get(1).get(15, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(hash(firstWon ? "First123" : "Second456"), stored());
            assertEquals(1L, mapper.selectCount(null));
            assertNull(keys.findCryptoKeyDto(HashUtils.sha256(firstWon ? "314159" : "271828")));
            assertNotNull(keys.findCryptoKeyDto(HashUtils.sha256(firstWon ? "271828" : "314159")));
        } finally { executor.shutdownNow(); }
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

        // A legacy CM RESET first creates its DSP credential, which must work with the existing token API.
        service.modifyPassword(request("M", "First123"));
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
