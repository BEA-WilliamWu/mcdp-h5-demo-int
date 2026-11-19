import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.awt.GraphicsEnvironment
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import javax.swing.JPasswordField
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

def stateName = 'bcoh2h2436.run_auth'
props.remove(stateName)
def get = { String name, String fallback = '' ->
    props.getProperty(name)?.trim() ?: vars.get(name)?.trim() ?: fallback
}
def requireValue = { String name ->
    def value = get(name)
    if (!value) throw new IllegalArgumentException("Set ${name} in User Defined Variables")
    value
}

char[] password = null
String failure = 'HTH login failed. Check the endpoint, certificates, and credentials.'
try {
    def source = get('token_source', 'login')
    if (source == 'env') {
        SampleResult.setIgnore()
        return
    }
    if (source != 'login') {
        failure = 'token_source must be login or env'
        throw new IllegalArgumentException()
    }

    def client = get('client_id', System.getenv('HTH_CLIENT_ID') ?: '')
    def keyFile = get('private_key_file', System.getenv('HTH_PRIVATE_KEY_FILE') ?: '')
    if (!client || !keyFile) {
        failure = 'Set client_id and private_key_file for HTH login'
        throw new IllegalArgumentException()
    }
    def closeId = requireValue('login_close_id')
    def audience = requireValue('login_audience')
    def kid = requireValue('login_kid')
    def endpoint = get('login_endpoint', '/auth/v1/token')
    def signingPath = get('login_signing_path', endpoint)
    URI origin = URI.create(requireValue('base_url'))
    URI url = URI.create(origin.toString().replaceFirst('/$', '') + endpoint)
    boolean loopback = origin.host in ['127.0.0.1', 'localhost']
    if (!origin.host || origin.rawUserInfo || origin.rawQuery || origin.rawFragment ||
            !(origin.path in ['', '/']) || !(origin.scheme == 'https' || (origin.scheme == 'http' && loopback)) ||
            !endpoint.startsWith('/') || url.rawQuery || url.rawFragment ||
            !signingPath.startsWith('/') || signingPath.contains('?') || signingPath.contains('#')) {
        failure = 'Use an HTTPS base_url origin and a login path without a query or fragment'
        throw new IllegalArgumentException()
    }

    def algorithm = get('login_assertion_alg', 'ES256')
    def assertionFile = get('login_assertion_key_file', algorithm == 'RS256' ? keyFile : '')
    if (!(algorithm in ['ES256', 'RS256']) || !assertionFile) {
        failure = 'Set login_assertion_key_file for ES256 or select the registered RS256 key'
        throw new IllegalArgumentException()
    }
    def loadKey = { String path, String keyType ->
        String pem = new File(path).getText('UTF-8')
        if (!pem.contains('-----BEGIN PRIVATE KEY-----')) throw new IllegalArgumentException()
        byte[] der = Base64.decoder.decode(pem.replaceAll(/-----[^-]+-----/, '').replaceAll(/\s/, ''))
        try {
            KeyFactory.getInstance(keyType).generatePrivate(new PKCS8EncodedKeySpec(der))
        } finally {
            Arrays.fill(der, (byte) 0)
        }
    }
    failure = 'Cannot read a configured PKCS#8 private key or its algorithm does not match'
    def rsaKey = loadKey(keyFile, 'RSA')
    def assertionKey = algorithm == 'RS256' && assertionFile == keyFile ? rsaKey :
        loadKey(assertionFile, algorithm == 'ES256' ? 'EC' : 'RSA')
    if (algorithm == 'ES256' && (assertionKey as ECPrivateKey).params.curve.field.fieldSize != 256) {
        throw new IllegalArgumentException()
    }

    def extra = new Properties()
    def headersFile = get('headers_file')
    if (headersFile) {
        failure = 'Cannot read headers_file'
        new File(headersFile).withInputStream { extra.load(it) }
    }
    failure = 'No logon password supplied. Use the JMeter GUI password prompt.'
    if (GraphicsEnvironment.isHeadless() || org.apache.jmeter.JMeter.isNonGUI()) {
        // Optional for unattended runs and the local fixture. GUI runs prompt at Start.
        password = System.getenv('HTH_LOGIN_PASSWORD')?.toCharArray()
    } else {
        SwingUtilities.invokeAndWait({
            def field = new JPasswordField(26)
            try {
                def dialog = JOptionPane.showConfirmDialog(
                    null, ['Enter the SIT logon password', field] as Object[],
                    'BCOH2H-2436 | HTH Login', JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                if (dialog == JOptionPane.OK_OPTION) password = field.password
            } finally {
                field.text = ''
            }
        } as Runnable)
    }
    if (!password || password.length == 0) throw new IllegalArgumentException()

    failure = 'Could not generate HTH login signatures'
    def sign = { String name, key, String value ->
        def signer = Signature.getInstance(name)
        signer.initSign(key)
        signer.update(value.getBytes(StandardCharsets.UTF_8))
        signer.sign()
    }
    def base64Url = { byte[] data -> Base64.urlEncoder.withoutPadding().encodeToString(data) }
    def jsonPart = { Map value -> base64Url(JsonOutput.toJson(value).getBytes(StandardCharsets.UTF_8)) }
    long now = System.currentTimeMillis() / 1000L
    def assertionInput = jsonPart([alg: algorithm, kid: kid]) + '.' +
        jsonPart([iss: client, sub: client, aud: audience, iat: now, exp: now + 300,
                  jti: UUID.randomUUID().toString(), closeid: closeId])
    def jwsAlgorithm = algorithm == 'ES256' ? 'SHA256withECDSAinP1363Format' : 'SHA256withRSA'
    def assertion = assertionInput + '.' + base64Url(sign(jwsAlgorithm, assertionKey, assertionInput))

    def passwordBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
    def digest = MessageDigest.getInstance('SHA-256')
    try {
        digest.update(passwordBytes)
    } finally {
        if (passwordBytes.hasArray()) Arrays.fill(passwordBytes.array(), (byte) 0)
        Arrays.fill(password, (char) 0)
    }
    String salt = props.getProperty('login_salt') ?: vars.get('login_salt') ?: ''
    def passwordHash = digest.digest(salt.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
    def body = JsonOutput.toJson([
        client_assertion: assertion,
        password_signature: Base64.encoder.encodeToString(sign('SHA256withRSA', rsaKey, passwordHash))
    ])
    def time = String.valueOf(System.currentTimeMillis() - (get('clock_skew_ms', '1000') as long))
    def canonical = 'POST\n' + signingPath + '\n\n' + time + '\n' + body
    def request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
        .header('Content-Type', 'application/json').header('Accept', 'application/json')
        .header('Channel', get('channel', 'HTH')).header('Message-Id', UUID.randomUUID().toString())
        .header('Client-Id', client).header('Request-Time', time)
        .header('Request-Sign', Base64.encoder.encodeToString(sign('SHA256withRSA', rsaKey, canonical)))
    extra.each { name, value ->
        if (name.toString().toLowerCase(Locale.ROOT) in ['x-ibm-client-id', 'x-ibm-client-secret']) {
            request.header(name.toString(), value.toString())
        }
    }
    failure = 'HTH login connection failed. Check the SIT URL, network, and Java truststore.'
    long requestedAt = System.currentTimeMillis()
    def response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        .send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
    failure = "HTH login returned HTTP ${response.statusCode()}"
    if (response.statusCode() != 200) throw new IllegalArgumentException()
    failure = 'HTH login response did not contain a usable Bearer access token and expiry'
    def reply = new JsonSlurper().parseText(response.body())
    def lifetime = reply.expires_in?.toString()
    if (!(reply.access_token instanceof String) || !(reply.access_token ==~ /\S+/) ||
            !'Bearer'.equalsIgnoreCase(reply.token_type?.toString()) ||
            !lifetime?.isInteger() || lifetime.toInteger() < 1) throw new IllegalArgumentException()
    props.put(stateName, [token: reply.access_token, client: client, key: rsaKey,
                          expiresAt: requestedAt + lifetime.toLong() * 1000L].asImmutable())
    log.info('HTH login succeeded. Starting the configured workload.')
    SampleResult.setIgnore()
} catch (Exception e) {
    // Never put the login body, response, token or Java exception details in results or logs.
    if (failure.startsWith('HTH login failed') && e instanceof IllegalArgumentException &&
            e.message?.startsWith('Set ')) failure = e.message
    log.error(failure)
    SampleResult.setSuccessful(false)
    SampleResult.setResponseCode('LOGIN_FAILED')
    SampleResult.setResponseMessage(failure)
    SampleResult.setStopTest(true)
    if (!GraphicsEnvironment.isHeadless() && !org.apache.jmeter.JMeter.isNonGUI()) {
        SwingUtilities.invokeLater({
            JOptionPane.showMessageDialog(null, failure, 'BCOH2H-2436 | HTH Login', JOptionPane.ERROR_MESSAGE)
        } as Runnable)
    }
} finally {
    if (password != null) Arrays.fill(password, (char) 0)
}
