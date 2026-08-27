'use strict'

const assert = require('node:assert/strict')
const fs = require('node:fs')
const http = require('node:http')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')

const {
  createServer,
  extractSessionCookie,
  getMockFileName,
  isPublicPath,
  loadConfig,
  resolveStaticPath,
  resolveStaticFallback,
  rewriteLocation,
  rewriteSetCookie
} = require('./server')

test('uses this repository consulting/channel as the default frontend directory', () => {
  const config = loadConfig({})
  assert.equal(
    config.frontendDir,
    path.resolve(__dirname, '../../consulting/channel')
  )
  assert.equal(config.targetMode, 'direct')
  assert.equal(loadConfig({ BCO_TARGET_MODE: 'BFF' }).targetMode, 'bff')
})

function listen (server) {
  return new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
}

function close (server) {
  return new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
}

function request (url, options = {}, body) {
  return new Promise((resolve, reject) => {
    const outgoing = http.request(url, options, response => {
      const chunks = []
      response.on('data', chunk => chunks.push(chunk))
      response.on('end', () => resolve({
        statusCode: response.statusCode,
        headers: response.headers,
        body: Buffer.concat(chunks).toString()
      }))
    })
    outgoing.on('error', reject)
    if (body) outgoing.write(body)
    outgoing.end()
  })
}

test('creates OHS-compatible mock names', () => {
  assert.equal(getMockFileName('/digx/cz/v1/accounts/123', 'get'), 'accounts_123_GET.json')
  assert.equal(getMockFileName('/digx/v1/me', 'post'), 'me_POST.json')
  assert.equal(getMockFileName('/other/path', 'get'), null)
})

test('matches exact and nested public BCO paths', () => {
  const publicPaths = [ '/digx/cz/v1/publicKey', '/digx/cz/v1/notification/' ]
  assert.equal(isPublicPath('/digx/cz/v1/publicKey', publicPaths), true)
  assert.equal(isPublicPath('/digx/cz/v1/notification/messages', publicPaths), true)
  assert.equal(isPublicPath('/digx/cz/v1/private', publicPaths), false)
})

test('keeps static paths inside the frontend directory', () => {
  const frontendDir = path.resolve('/tmp/bco-frontend')
  assert.equal(resolveStaticPath(frontendDir, '/framework/app.js'), path.join(frontendDir, 'framework/app.js'))
  assert.equal(resolveStaticPath(frontendDir, '/../../etc/passwd'), null)
})

test('maps unbuilt CSS fingerprints and CSS-relative images to source assets', () => {
  const frontendDir = path.resolve('/tmp/bco-frontend')
  assert.equal(
    resolveStaticFallback(frontendDir, '/framework/css/main.undefined.css'),
    path.join(frontendDir, 'framework/css/main.css')
  )
  assert.equal(
    resolveStaticFallback(frontendDir, '/framework/css/obdx-font.undefined.css'),
    path.join(frontendDir, 'framework/css/obdx-font.css')
  )
  assert.equal(
    resolveStaticFallback(frontendDir, '/framework/css/images/cz/header/logon_mobile.svg'),
    path.join(frontendDir, 'images/cz/header/logon_mobile.svg')
  )
  assert.equal(resolveStaticFallback(frontendDir, '/framework/css/other.css'), null)
})

test('rewrites upstream cookies and redirects for local HTTP', () => {
  assert.equal(
    rewriteSetCookie('JSESSIONID=abc; Path=/; Domain=cdc.example; Secure; HttpOnly; SameSite=None'),
    'JSESSIONID=abc; Path=/; HttpOnly; SameSite=Lax'
  )
  assert.equal(
    rewriteLocation('https://cdc.example/home.html', 'https://cdc.example', 'http://127.0.0.1:8850'),
    'http://127.0.0.1:8850/home.html'
  )
})

test('extracts both BCO session cookies', () => {
  assert.equal(
    extractSessionCookie([
      'JSESSIONID=abc; Path=/; Secure',
      '_WL_AUTHCOOKIE_JSESSIONID=def; Path=/; Secure'
    ]),
    'JSESSIONID=abc; _WL_AUTHCOOKIE_JSESSIONID=def'
  )
})

test('serves the BCO frontend and supports transparent and token proxy modes', async t => {
  const frontendDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bco-frontend-'))
  fs.writeFileSync(path.join(frontendDir, 'index.html'), '<h1>BCO local</h1>')

  const upstream = http.createServer(async (req, res) => {
    if (req.url.startsWith('/digx/j_security_check')) {
      res.writeHead(303, {
        location: 'http://upstream.example/home.html',
        'set-cookie': [
          'JSESSIONID=session-one; Path=/; Secure',
          '_WL_AUTHCOOKIE_JSESSIONID=session-two; Path=/; Secure'
        ]
      })
      res.end()
      return
    }

    const requestBody = await new Promise(resolve => {
      const chunks = []
      req.on('data', chunk => chunks.push(chunk))
      req.on('end', () => resolve(Buffer.concat(chunks).toString()))
    })

    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({
      body: requestBody,
      cookie: req.headers.cookie || null,
      path: req.url
    }))
  })
  await listen(upstream)

  const proxy = createServer({
    host: '127.0.0.1',
    port: 0,
    target: new URL(`http://127.0.0.1:${upstream.address().port}`),
    frontendDir,
    tlsRejectUnauthorized: true,
    rewriteCookies: true,
    disableServiceWorker: true,
    mockEnabled: false,
    mockDir: path.join(frontendDir, 'mocks'),
    publicPaths: [ '/digx/cz/v1/publicKey' ],
    sessionTtlMs: 60_000,
    requestTimeoutMs: 5_000,
    logRequests: false
  })
  await listen(proxy)

  t.after(async () => {
    await close(proxy)
    await close(upstream)
    fs.rmSync(frontendDir, { recursive: true, force: true })
  })

  const baseUrl = `http://127.0.0.1:${proxy.address().port}`
  const frontendResponse = await request(`${baseUrl}/`)
  assert.equal(frontendResponse.statusCode, 200)
  assert.equal(frontendResponse.body, '<h1>BCO local</h1>')

  const transparentResponse = await request(`${baseUrl}/digx/v1/me`, {
    headers: { cookie: 'JSESSIONID=browser-session' }
  })
  assert.deepEqual(JSON.parse(transparentResponse.body), {
    body: '',
    cookie: 'JSESSIONID=browser-session',
    path: '/digx/v1/me'
  })

  const loginBody = 'j_username=tester&j_password=secret'
  const loginResponse = await request(`${baseUrl}/api/login`, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      'content-length': Buffer.byteLength(loginBody)
    }
  }, loginBody)
  const loginPayload = JSON.parse(loginResponse.body)
  assert.match(loginPayload.accessToken, /^[a-f0-9]{32}$/)

  const bffResponse = await request(`${baseUrl}/digx/v1/me`, {
    headers: { authorization: `Bearer ${loginPayload.accessToken}` }
  })
  const bffPayload = JSON.parse(bffResponse.body)
  assert.equal(bffPayload.status, 200)
  assert.deepEqual(JSON.parse(bffPayload.body), {
    body: '',
    cookie: 'JSESSIONID=session-one; _WL_AUTHCOOKIE_JSESSIONID=session-two',
    path: '/digx/v1/me?locale=en'
  })

  const approveBody = '"approved"'
  const approveResponse = await request(`${baseUrl}/digx/cz/v1/transactions/123/approve?source=local`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${loginPayload.accessToken}`,
      'content-type': 'text/plain',
      'content-length': Buffer.byteLength(approveBody),
      locale: 'zh_TW'
    }
  }, approveBody)
  const approvePayload = JSON.parse(approveResponse.body)
  assert.deepEqual(JSON.parse(approvePayload.body), {
    body: 'approved',
    cookie: 'JSESSIONID=session-one; _WL_AUTHCOOKIE_JSESSIONID=session-two',
    path: '/digx/cz/v1/transactions/123/approve?source=local&locale=zh_TW'
  })
})

test('adapts a remote BFF envelope to the legacy OBDX browser contract', async t => {
  const frontendDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bco-frontend-bff-'))
  fs.writeFileSync(path.join(frontendDir, 'index.html'), '<h1>BCO through BFF</h1>')
  const capturedRequests = []

  const upstream = http.createServer(async (req, res) => {
    const requestBody = await new Promise(resolve => {
      const chunks = []
      req.on('data', chunk => chunks.push(chunk))
      req.on('end', () => resolve(Buffer.concat(chunks).toString()))
    })
    capturedRequests.push({
      authorization: req.headers.authorization || null,
      body: requestBody,
      path: req.url
    })

    res.setHeader('content-type', 'application/json')
    if (req.url.startsWith('/digx/j_security_check')) {
      if (requestBody.includes('j_username=invalid')) {
        res.end(JSON.stringify({
          success: false,
          statusCode: 403,
          headers: {
            'Content-Type': [ 'text/html; charset=UTF-8' ],
            'X-Auth-Failure-Response': [ '{"type":"INVALID_CRED","errorMessage":"INVALID_LOGIN_DETAILS"}' ]
          },
          body: '<html>login failed</html>'
        }))
      } else {
        res.end(JSON.stringify({
          accessToken: 'remote-access-token',
          refreshToken: 'remote-refresh-token'
        }))
      }
      return
    }

    res.end(JSON.stringify({
      status: 200,
      headers: {
        'Content-Type': [ 'application/json' ],
        'X-Backend-Response': [ 'remote-bff' ]
      },
      body: JSON.stringify({
        authorization: req.headers.authorization || null,
        path: req.url
      })
    }))
  })
  await listen(upstream)

  const proxy = createServer({
    host: '127.0.0.1',
    port: 0,
    target: new URL(`http://127.0.0.1:${upstream.address().port}`),
    targetMode: 'bff',
    frontendDir,
    tlsRejectUnauthorized: true,
    rewriteCookies: true,
    disableServiceWorker: true,
    mockEnabled: false,
    mockDir: path.join(frontendDir, 'mocks'),
    publicPaths: [ '/digx/cz/v1/publicKey' ],
    sessionTtlMs: 60_000,
    requestTimeoutMs: 5_000,
    logRequests: false
  })
  await listen(proxy)

  t.after(async () => {
    await close(proxy)
    await close(upstream)
    fs.rmSync(frontendDir, { recursive: true, force: true })
  })

  const baseUrl = `http://127.0.0.1:${proxy.address().port}`
  const publicResponse = await request(`${baseUrl}/digx/cz/v1/publicKey`)
  assert.equal(publicResponse.statusCode, 200)
  assert.equal(publicResponse.headers['x-backend-response'], 'remote-bff')
  assert.deepEqual(JSON.parse(publicResponse.body), {
    authorization: null,
    path: '/digx/cz/v1/publicKey'
  })

  const loginBody = 'j_username=tester&j_password=encrypted'
  const loginResponse = await request(`${baseUrl}/digx/j_security_check?locale=en`, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      'content-length': Buffer.byteLength(loginBody)
    }
  }, loginBody)
  assert.equal(loginResponse.statusCode, 404)
  assert.match(loginResponse.headers['set-cookie'][0], /^HTH_DEV_BFF_SESSION=/)

  const localSessionCookie = loginResponse.headers['set-cookie'][0].split(';', 1)[0]
  const privateResponse = await request(`${baseUrl}/digx/cz/v1/hostToHostUserAccess/search`, {
    headers: { cookie: localSessionCookie }
  })
  assert.equal(privateResponse.statusCode, 200)
  assert.deepEqual(JSON.parse(privateResponse.body), {
    authorization: 'Bearer remote-access-token',
    path: '/digx/cz/v1/hostToHostUserAccess/search'
  })
  assert.equal(capturedRequests.at(-1).authorization, 'Bearer remote-access-token')

  const invalidLoginBody = 'j_username=invalid&j_password=invalid'
  const invalidLoginResponse = await request(`${baseUrl}/digx/j_security_check?locale=en`, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      'content-length': Buffer.byteLength(invalidLoginBody)
    }
  }, invalidLoginBody)
  assert.equal(invalidLoginResponse.statusCode, 403)
  assert.deepEqual(JSON.parse(invalidLoginResponse.headers['x-auth-failure-response']), {
    type: 'INVALID_CRED',
    errorMessage: 'INVALID_LOGIN_DETAILS'
  })
  assert.equal(invalidLoginResponse.body, '<html>login failed</html>')
})
