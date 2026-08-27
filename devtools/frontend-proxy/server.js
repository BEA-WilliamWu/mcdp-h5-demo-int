'use strict'

const crypto = require('node:crypto')
const fs = require('node:fs')
const http = require('node:http')
const https = require('node:https')
const path = require('node:path')
const { pipeline } = require('node:stream')

const DEFAULT_PUBLIC_PATHS = [
  '/digx/cz/v1/publicKey',
  '/digx/v1/bankConfiguration',
  '/bcmApprove'
]

const DEV_BFF_SESSION_COOKIE = 'HTH_DEV_BFF_SESSION'

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'proxy-connection',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade'
])

const BFF_ALLOWED_HEADERS = new Set([
  'accept',
  'app-version',
  'auth-method',
  'bio-type',
  'content-type',
  'device-id',
  'itoken-auth-type',
  'locale',
  'login-auth-data',
  'login-auth-type',
  'login-channel',
  'logintype',
  'mackey',
  'macencrypteddata',
  'macmodulus',
  'macrsaindicator',
  'override-content-type',
  'platform',
  'publicexponent',
  'publickey',
  'reg-method',
  'rsakeyindicator',
  'token',
  'user-agent',
  'user-role',
  'x-challenge',
  'x-challenge_response',
  'x-device-brand',
  'x-device-id',
  'x-device-model',
  'x-device-os',
  'x-device-os-version',
  'x-nonce',
  'x-noncecount',
  'x-target-unit'
])

const MIME_TYPES = {
  '.br': 'application/octet-stream',
  '.css': 'text/css; charset=utf-8',
  '.csv': 'text/csv; charset=utf-8',
  '.eot': 'application/vnd.ms-fontobject',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.otf': 'font/otf',
  '.pdf': 'application/pdf',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.xml': 'application/xml; charset=utf-8'
}

function parseBoolean (value, defaultValue) {
  if (value == null || value === '') return defaultValue

  return ![ '0', 'false', 'no', 'off' ].includes(String(value).toLowerCase())
}

function parseList (value, defaultValue = []) {
  if (value == null || value.trim() === '') return defaultValue

  return value.split(',').map(item => item.trim()).filter(Boolean)
}

function loadConfig (env = process.env) {
  const frontendDir = env.BCO_FRONTEND_DIR || path.resolve(
    __dirname,
    '../../consulting/channel'
  )

  return {
    host: env.HOST || '127.0.0.1',
    port: Number(env.PORT || 8850),
    target: new URL(env.BCO_TARGET || 'https://210.177.116.65'),
    targetMode: String(env.BCO_TARGET_MODE || 'direct').toLowerCase(),
    frontendDir: path.resolve(frontendDir),
    tlsRejectUnauthorized: parseBoolean(env.BCO_TLS_REJECT_UNAUTHORIZED, false),
    rewriteCookies: parseBoolean(env.BCO_REWRITE_COOKIES, true),
    disableServiceWorker: parseBoolean(env.BCO_DISABLE_SERVICE_WORKER, true),
    mockEnabled: parseBoolean(env.BCO_MOCK_ENABLED, false),
    mockDir: path.resolve(env.BCO_MOCK_DIR || path.join(__dirname, 'mocks')),
    publicPaths: parseList(env.BCO_PUBLIC_PATHS, DEFAULT_PUBLIC_PATHS),
    sessionTtlMs: Number(env.BCO_SESSION_TTL_MS || 8 * 60 * 60 * 1000),
    requestTimeoutMs: Number(env.BCO_REQUEST_TIMEOUT_MS || 60 * 1000),
    logRequests: parseBoolean(env.BCO_LOG_REQUESTS, true)
  }
}

function readCookie (cookieHeader, name) {
  const cookies = String(cookieHeader || '').split(';')

  for (const cookie of cookies) {
    const separatorIndex = cookie.indexOf('=')
    if (separatorIndex < 1 || cookie.slice(0, separatorIndex).trim() !== name) continue

    try {
      return decodeURIComponent(cookie.slice(separatorIndex + 1).trim())
    } catch (error) {
      return null
    }
  }

  return null
}

function writeSessionCookie (response, sessionId) {
  response.setHeader(
    'set-cookie',
    `${DEV_BFF_SESSION_COOKIE}=${encodeURIComponent(sessionId)}; Path=/; HttpOnly; SameSite=Lax`
  )
}

function clearSessionCookie (response) {
  response.setHeader(
    'set-cookie',
    `${DEV_BFF_SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`
  )
}

function createLogger (config) {
  return function log (message) {
    if (config.logRequests) {
      console.log(`[${new Date().toISOString()}] ${message}`)
    }
  }
}

function getLocale (request) {
  return String(request.headers.locale || 'en').trim() || 'en'
}

function isPublicPath (pathname, publicPaths) {
  return publicPaths.some(publicPath => {
    const normalized = publicPath.endsWith('/') && publicPath !== '/'
      ? publicPath.slice(0, -1)
      : publicPath

    return pathname === normalized || pathname.startsWith(`${normalized}/`) || pathname.endsWith(normalized)
  })
}

function getMockFileName (pathname, method) {
  const prefixes = [ '/digx/cz/v1/', '/digx/v1/' ]
  const prefix = prefixes.find(candidate => pathname.startsWith(candidate))

  if (!prefix) return null

  const resourcePath = pathname.slice(prefix.length).replace(/^\/+|\/+$/g, '')
  if (!resourcePath) return null

  return `${resourcePath.replaceAll('/', '_')}_${method.toUpperCase()}.json`
}

function resolveStaticPath (frontendDir, pathname) {
  let decodedPath

  try {
    decodedPath = decodeURIComponent(pathname)
  } catch (error) {
    return null
  }

  if (decodedPath.includes('\0')) return null

  let relativePath = decodedPath === '/' ? 'index.html' : decodedPath.replace(/^\/+/, '')
  if (relativePath === 'home.html' && !fs.existsSync(path.join(frontendDir, relativePath))) {
    relativePath = 'index.html'
  }

  const resolvedPath = path.resolve(frontendDir, relativePath)
  const relative = path.relative(frontendDir, resolvedPath)

  if (relative.startsWith('..') || path.isAbsolute(relative)) return null

  return resolvedPath
}

function resolveStaticFallback (frontendDir, pathname) {
  const fingerprintedCss = pathname.match(/^\/framework\/css\/(main|obdx-font)\.undefined\.css$/)
  if (fingerprintedCss) {
    return resolveStaticPath(frontendDir, `/framework/css/${fingerprintedCss[1]}.css`)
  }

  const cssImagePrefix = '/framework/css/images/'
  if (pathname.startsWith(cssImagePrefix)) {
    return resolveStaticPath(frontendDir, `/images/${pathname.slice(cssImagePrefix.length)}`)
  }

  return null
}

function rewriteSetCookie (cookie) {
  return cookie
    .replace(/;\s*Domain=[^;]*/gi, '')
    .replace(/;\s*Secure/gi, '')
    .replace(/;\s*SameSite=None/gi, '; SameSite=Lax')
}

function rewriteLocation (location, targetOrigin, localOrigin) {
  if (!location) return location

  return location.startsWith(targetOrigin)
    ? `${localOrigin}${location.slice(targetOrigin.length)}`
    : location
}

function copyResponseHeaders (upstreamResponse, response, options) {
  const targetOrigin = options.targetOrigin
  const localOrigin = options.localOrigin

  for (const [ headerName, headerValue ] of Object.entries(upstreamResponse.headers)) {
    if (headerValue == null || HOP_BY_HOP_HEADERS.has(headerName)) continue

    if (headerName === 'set-cookie' && options.rewriteCookies) {
      const cookies = Array.isArray(headerValue) ? headerValue : [ headerValue ]
      response.setHeader(headerName, cookies.map(rewriteSetCookie))
    } else if (headerName === 'location') {
      response.setHeader(headerName, rewriteLocation(headerValue, targetOrigin, localOrigin))
    } else {
      response.setHeader(headerName, headerValue)
    }
  }
}

function buildForwardHeaders (request, config, mode, sessionCookie) {
  const headers = {}

  for (const [ headerName, headerValue ] of Object.entries(request.headers)) {
    if (headerValue == null || HOP_BY_HOP_HEADERS.has(headerName) || headerName === 'host') continue
    if (mode === 'bff' && !BFF_ALLOWED_HEADERS.has(headerName)) continue
    if (headerName === 'authorization' || (mode === 'bff' && headerName === 'cookie')) continue

    headers[headerName] = headerValue
  }

  const targetOrigin = config.target.origin
  const localOrigin = `${request.socket.encrypted ? 'https' : 'http'}://${request.headers.host}`

  if (headers.origin) headers.origin = targetOrigin
  if (headers.referer) headers.referer = headers.referer.replace(localOrigin, targetOrigin)

  const remoteAddress = request.socket.remoteAddress
  headers['x-forwarded-for'] = request.headers['x-forwarded-for']
    ? `${request.headers['x-forwarded-for']}, ${remoteAddress}`
    : remoteAddress
  headers['x-forwarded-proto'] = request.socket.encrypted ? 'https' : 'http'
  headers['x-forwarded-host'] = request.headers.host
  headers.host = config.target.host

  if (mode === 'bff') {
    headers.locale = getLocale(request)
    delete headers['accept-encoding']

    if (sessionCookie) headers.cookie = sessionCookie
    if (headers['override-content-type']) {
      headers['content-type'] = headers['override-content-type']
      delete headers['override-content-type']
    }
  }

  return headers
}

function readStream (stream, limit = 20 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let totalBytes = 0

    stream.on('data', chunk => {
      totalBytes += chunk.length
      if (totalBytes > limit) {
        reject(new Error(`Body exceeds ${limit} bytes`))
        stream.destroy()
        return
      }
      chunks.push(chunk)
    })
    stream.on('end', () => resolve(Buffer.concat(chunks)))
    stream.on('error', reject)
  })
}

function headersToObject (headers) {
  return Object.fromEntries(Object.entries(headers).filter(([ headerName ]) => !HOP_BY_HOP_HEADERS.has(headerName)))
}

function copyEnvelopeHeaders (headers, response) {
  if (!headers || typeof headers !== 'object' || Array.isArray(headers)) return

  for (const [ headerName, headerValue ] of Object.entries(headers)) {
    const normalizedName = headerName.toLowerCase()
    if (
      headerValue == null ||
      HOP_BY_HOP_HEADERS.has(normalizedName) ||
      normalizedName === 'content-length' ||
      normalizedName === 'content-encoding' ||
      normalizedName === 'set-cookie'
    ) continue

    response.setHeader(headerName, headerValue)
  }
}

function parseJsonBuffer (buffer) {
  try {
    return JSON.parse(buffer.toString())
  } catch (error) {
    return null
  }
}

function isRemoteBffEnvelope (payload) {
  return Boolean(
    payload &&
    typeof payload === 'object' &&
    !Array.isArray(payload) &&
    payload.headers &&
    typeof payload.headers === 'object' &&
    Object.prototype.hasOwnProperty.call(payload, 'status')
  )
}

function writeBuffer (response, statusCode, body, headers) {
  copyEnvelopeHeaders(headers, response)
  response.setHeader('content-length', body.length)
  response.setHeader('cache-control', 'no-store')
  response.writeHead(statusCode)
  response.end(body)
}

function extractSessionCookie (setCookieHeaders) {
  const cookies = Array.isArray(setCookieHeaders) ? setCookieHeaders : []
  const requiredNames = [ 'JSESSIONID', '_WL_AUTHCOOKIE_JSESSIONID' ]
  const values = new Map()

  for (const cookie of cookies) {
    const pair = cookie.split(';', 1)[0]
    const separatorIndex = pair.indexOf('=')
    if (separatorIndex < 1) continue

    values.set(pair.slice(0, separatorIndex), pair.slice(separatorIndex + 1))
  }

  if (!requiredNames.every(name => values.has(name))) return null

  return requiredNames.map(name => `${name}=${values.get(name)}`).join('; ')
}

function writeJson (response, statusCode, payload) {
  const body = Buffer.from(JSON.stringify(payload))
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': body.length,
    'cache-control': 'no-store'
  })
  response.end(body)
}

function requestUpstream (request, config, options) {
  const client = config.target.protocol === 'https:' ? https : http
  const upstreamPath = options.upstreamPath || request.url
  const targetUrl = new URL(upstreamPath, config.target)
  const requestOptions = {
    protocol: targetUrl.protocol,
    hostname: targetUrl.hostname,
    port: targetUrl.port || undefined,
    method: options.method || request.method,
    path: `${targetUrl.pathname}${targetUrl.search}`,
    headers: options.headers,
    timeout: config.requestTimeoutMs
  }

  if (targetUrl.protocol === 'https:') {
    requestOptions.agent = new https.Agent({
      keepAlive: true,
      rejectUnauthorized: config.tlsRejectUnauthorized
    })
  }

  return client.request(requestOptions)
}

function createSessionStore (ttlMs) {
  const sessions = new Map()

  return {
    create (session) {
      const token = crypto.randomBytes(16).toString('hex')
      sessions.set(token, { ...session, expiresAt: Date.now() + ttlMs })
      return token
    },
    get (token) {
      const session = sessions.get(token)
      if (!session) return null
      if (session.expiresAt <= Date.now()) {
        sessions.delete(token)
        return null
      }
      return session
    },
    delete (token) {
      sessions.delete(token)
    }
  }
}

async function handleBffLogin (request, response, context) {
  const { config, log, sessions } = context
  const requestBody = await readStream(request)
  const form = new URLSearchParams(requestBody.toString())
  const locale = getLocale(request)
  form.set('locale', locale)

  const headers = buildForwardHeaders(request, config, 'bff')
  headers['content-type'] = 'application/x-www-form-urlencoded'
  headers['content-length'] = Buffer.byteLength(form.toString())

  const upstreamRequest = requestUpstream(request, config, {
    method: 'POST',
    upstreamPath: `/digx/j_security_check?locale=${encodeURIComponent(locale)}`,
    headers
  })

  upstreamRequest.on('response', async upstreamResponse => {
    try {
      const responseBody = await readStream(upstreamResponse)
      if (upstreamResponse.statusCode !== 303) {
        writeJson(response, 200, {
          success: false,
          statusCode: upstreamResponse.statusCode,
          headers: headersToObject(upstreamResponse.headers),
          ...(responseBody.length > 0 ? { body: responseBody.toString() } : {})
        })
        return
      }

      const cookie = extractSessionCookie(upstreamResponse.headers['set-cookie'])
      if (!cookie) {
        writeJson(response, 502, { message: 'BCO login response is missing required session cookies' })
        return
      }

      const accessToken = sessions.create({
        cookie,
        username: String(form.get('j_username') || '').trim()
      })
      const now = new Date()

      writeJson(response, 200, {
        accessToken,
        loginTime: {
          iso: now.toISOString(),
          display: new Intl.DateTimeFormat('sv-SE', {
            timeZone: 'Asia/Hong_Kong',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
          }).format(now),
          unix: String(Math.floor(now.getTime() / 1000))
        },
        headers: headersToObject(upstreamResponse.headers),
        ...(responseBody.length > 0 ? { body: responseBody.toString() } : {})
      })
    } catch (error) {
      log(`LOGIN response error: ${error.message}`)
      if (!response.headersSent) writeJson(response, 502, { message: 'Failed to read BCO login response' })
    }
  })
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('BCO request timed out')))
  upstreamRequest.on('error', error => {
    log(`LOGIN upstream error: ${error.message}`)
    if (!response.headersSent) writeJson(response, 502, { message: error.message })
  })
  upstreamRequest.end(form.toString())
}

async function handleRemoteBffLogin (request, response, context) {
  const { config, log, sessions } = context
  const requestBody = await readStream(request)
  const headers = buildForwardHeaders(request, config, 'transparent')
  delete headers.cookie
  delete headers['accept-encoding']
  headers['content-length'] = String(requestBody.length)

  const upstreamRequest = requestUpstream(request, config, { headers })
  const startedAt = Date.now()

  upstreamRequest.on('response', async upstreamResponse => {
    try {
      const responseBody = await readStream(upstreamResponse)
      const payload = parseJsonBuffer(responseBody)

      if (payload && typeof payload.accessToken === 'string' && payload.accessToken !== '') {
        const sessionId = sessions.create({
          remoteAccessToken: payload.accessToken,
          remoteRefreshToken: payload.refreshToken || null
        })
        writeSessionCookie(response, sessionId)
        response.setHeader('cache-control', 'no-store')

        // OBDXAuthenticator treats 404 as a successful WebLogic form-login response.
        response.writeHead(404)
        response.end()
        log(`${request.method} ${request.url} [remote-bff login] -> success (${Date.now() - startedAt}ms)`)
        return
      }

      if (payload && payload.success === false) {
        const body = Buffer.from(typeof payload.body === 'string' ? payload.body : '')
        writeBuffer(response, Number(payload.statusCode) || 401, body, payload.headers)
        log(`${request.method} ${request.url} [remote-bff login] -> ${payload.statusCode} (${Date.now() - startedAt}ms)`)
        return
      }

      writeBuffer(
        response,
        upstreamResponse.statusCode || 502,
        responseBody,
        upstreamResponse.headers
      )
    } catch (error) {
      log(`${request.method} ${request.url} [remote-bff login] response error: ${error.message}`)
      if (!response.headersSent) writeJson(response, 502, { message: 'Failed to read remote BFF login response' })
    }
  })
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('BCO request timed out')))
  upstreamRequest.on('error', error => {
    log(`${request.method} ${request.url} [remote-bff login] upstream error: ${error.message}`)
    if (!response.headersSent) writeJson(response, 502, { message: error.message })
  })
  upstreamRequest.end(requestBody)
}

function handleTransparentProxy (request, response, context) {
  const { config, log } = context
  const headers = buildForwardHeaders(request, config, 'transparent')
  const upstreamRequest = requestUpstream(request, config, { headers })
  const startedAt = Date.now()

  upstreamRequest.on('response', upstreamResponse => {
    const localOrigin = `${request.socket.encrypted ? 'https' : 'http'}://${request.headers.host}`
    copyResponseHeaders(upstreamResponse, response, {
      targetOrigin: config.target.origin,
      localOrigin,
      rewriteCookies: config.rewriteCookies
    })
    response.writeHead(upstreamResponse.statusCode || 502)
    pipeline(upstreamResponse, response, error => {
      if (error) log(`${request.method} ${request.url} response error: ${error.message}`)
    })
    log(`${request.method} ${request.url} -> ${upstreamResponse.statusCode} (${Date.now() - startedAt}ms)`)
  })
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('BCO request timed out')))
  upstreamRequest.on('error', error => {
    log(`${request.method} ${request.url} upstream error: ${error.message}`)
    if (!response.headersSent) writeJson(response, 502, { message: error.message })
  })
  pipeline(request, upstreamRequest, error => {
    if (error) log(`${request.method} ${request.url} request error: ${error.message}`)
  })
}

function handleRemoteBffProxy (request, response, context) {
  const { config, log, sessions } = context
  const requestUrl = new URL(request.url, 'http://local')
  const sessionId = readCookie(request.headers.cookie, DEV_BFF_SESSION_COOKIE)
  const session = sessionId ? sessions.get(sessionId) : null
  const headers = buildForwardHeaders(request, config, 'transparent')
  const incomingAuthorization = String(request.headers.authorization || '')
  const accessToken = session && session.remoteAccessToken
    ? session.remoteAccessToken
    : (incomingAuthorization.startsWith('Bearer ') ? incomingAuthorization.slice(7).trim() : '')

  delete headers.cookie
  delete headers['accept-encoding']
  if (accessToken) headers.authorization = `Bearer ${accessToken}`

  const upstreamRequest = requestUpstream(request, config, { headers })
  const startedAt = Date.now()

  upstreamRequest.on('response', async upstreamResponse => {
    try {
      const responseBody = await readStream(upstreamResponse)
      const payload = parseJsonBuffer(responseBody)
      const logoutRequest = request.method === 'DELETE' && requestUrl.pathname.endsWith('/session') && sessionId

      if (logoutRequest) {
        sessions.delete(sessionId)
        clearSessionCookie(response)
      }

      if (isRemoteBffEnvelope(payload)) {
        const body = Buffer.from(typeof payload.body === 'string' ? payload.body : '')
        const statusCode = Number(payload.status) || 502
        writeBuffer(response, statusCode, body, payload.headers)
        log(`${request.method} ${request.url} [remote-bff] -> ${statusCode} (${Date.now() - startedAt}ms)`)
      } else if (
        payload &&
        typeof payload === 'object' &&
        !Array.isArray(payload) &&
        /^\d{3}$/.test(String(payload.status || ''))
      ) {
        const statusCode = Number(payload.status)
        if (statusCode === 401 || statusCode === 419) clearSessionCookie(response)
        writeBuffer(response, statusCode, responseBody, upstreamResponse.headers)
        log(`${request.method} ${request.url} [remote-bff] -> ${statusCode} (${Date.now() - startedAt}ms)`)
      } else {
        writeBuffer(
          response,
          upstreamResponse.statusCode || 502,
          responseBody,
          upstreamResponse.headers
        )
        log(`${request.method} ${request.url} [remote-bff] -> ${upstreamResponse.statusCode} (${Date.now() - startedAt}ms)`)
      }

    } catch (error) {
      log(`${request.method} ${request.url} [remote-bff] response error: ${error.message}`)
      if (!response.headersSent) writeJson(response, 502, { message: 'Failed to read remote BFF response' })
    }
  })
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('BCO request timed out')))
  upstreamRequest.on('error', error => {
    log(`${request.method} ${request.url} [remote-bff] upstream error: ${error.message}`)
    if (!response.headersSent) writeJson(response, 502, { message: error.message })
  })
  pipeline(request, upstreamRequest, error => {
    if (error) log(`${request.method} ${request.url} [remote-bff] request error: ${error.message}`)
  })
}

async function handleBffProxy (request, response, context, forced = false) {
  const { config, log, sessions } = context
  const requestUrl = new URL(request.url, 'http://local')
  const requestedUpstreamPath = forced
    ? `${requestUrl.pathname.replace(/^\/api\/bco/, '') || '/'}${requestUrl.search}`
    : request.url
  const upstreamUrl = new URL(requestedUpstreamPath, config.target)
  upstreamUrl.searchParams.set('locale', getLocale(request))
  const upstreamPath = `${upstreamUrl.pathname}${upstreamUrl.search}`
  const authorization = String(request.headers.authorization || '')
  const token = authorization.startsWith('Bearer ') ? authorization.slice(7).trim() : ''
  const publicEndpoint = isPublicPath(upstreamUrl.pathname, config.publicPaths)
  const session = token ? sessions.get(token) : null

  if (!publicEndpoint && !session) {
    writeJson(response, 200, {
      status: '419',
      code: '419',
      message: 'Session Expired'
    })
    return
  }

  const headers = buildForwardHeaders(request, config, 'bff', session && session.cookie)
  let requestBody = await readStream(request)

  if (
    upstreamUrl.pathname.endsWith('/approve') &&
    String(headers['content-type'] || '').toLowerCase().includes('text/plain') &&
    requestBody.length >= 2 &&
    requestBody[0] === 0x22 &&
    requestBody[requestBody.length - 1] === 0x22
  ) {
    requestBody = requestBody.subarray(1, requestBody.length - 1)
  }

  if (requestBody.length > 0 || headers['content-length'] != null) {
    headers['content-length'] = String(requestBody.length)
  } else {
    delete headers['content-length']
  }

  const upstreamRequest = requestUpstream(request, config, { upstreamPath, headers })
  const startedAt = Date.now()

  upstreamRequest.on('response', async upstreamResponse => {
    try {
      const responseBody = await readStream(upstreamResponse)
      writeJson(response, 200, {
        status: upstreamResponse.statusCode,
        headers: headersToObject(upstreamResponse.headers),
        ...(responseBody.length > 0 ? { body: responseBody.toString() } : {})
      })
      log(`${request.method} ${request.url} [bff] -> ${upstreamResponse.statusCode} (${Date.now() - startedAt}ms)`)
    } catch (error) {
      log(`${request.method} ${request.url} response error: ${error.message}`)
      if (!response.headersSent) writeJson(response, 502, { message: 'Failed to read BCO response' })
    }
  })
  upstreamRequest.on('timeout', () => upstreamRequest.destroy(new Error('BCO request timed out')))
  upstreamRequest.on('error', error => {
    log(`${request.method} ${request.url} upstream error: ${error.message}`)
    if (!response.headersSent) writeJson(response, 502, { message: error.message })
  })
  upstreamRequest.end(requestBody)
}

function serveMock (request, response, config, requestUrl) {
  if (!config.mockEnabled) return false

  const fileName = getMockFileName(requestUrl.pathname, request.method)
  if (!fileName) return false

  const mockPath = path.join(config.mockDir, fileName)
  if (!fs.existsSync(mockPath) || !fs.statSync(mockPath).isFile()) return false

  const body = fs.readFileSync(mockPath)
  response.writeHead(200, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': body.length,
    'cache-control': 'no-store',
    'x-mock-response': 'true'
  })
  response.end(body)
  return true
}

function serveStatic (request, response, config, requestUrl) {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    writeJson(response, 405, { message: 'Method not allowed' })
    return
  }

  if (config.disableServiceWorker && requestUrl.pathname === '/sw.js') {
    const script = Buffer.from(
      "self.addEventListener('install', () => self.skipWaiting());\n" +
      "self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.map(key => caches.delete(key)))).then(() => self.clients.claim())));\n"
    )
    response.writeHead(200, {
      'content-type': 'text/javascript; charset=utf-8',
      'content-length': script.length,
      'cache-control': 'no-store'
    })
    response.end(request.method === 'HEAD' ? undefined : script)
    return
  }

  let filePath = resolveStaticPath(config.frontendDir, requestUrl.pathname)
  if (!filePath) {
    writeJson(response, 400, { message: 'Invalid path' })
    return
  }

  let stat
  try {
    stat = fs.statSync(filePath)
  } catch (error) {
    const fallbackPath = resolveStaticFallback(config.frontendDir, requestUrl.pathname)
    if (!fallbackPath) {
      writeJson(response, 404, { message: 'File not found' })
      return
    }

    try {
      stat = fs.statSync(fallbackPath)
      filePath = fallbackPath
    } catch (fallbackError) {
      writeJson(response, 404, { message: 'File not found' })
      return
    }
  }

  if (!stat.isFile()) {
    writeJson(response, 404, { message: 'File not found' })
    return
  }

  response.writeHead(200, {
    'content-type': MIME_TYPES[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
    'content-length': stat.size,
    'cache-control': 'no-store'
  })
  if (request.method === 'HEAD') {
    response.end()
    return
  }
  pipeline(fs.createReadStream(filePath), response, () => {})
}

function createServer (providedConfig = loadConfig()) {
  const config = { ...providedConfig }
  config.target = config.target instanceof URL ? config.target : new URL(config.target)
  config.targetMode = config.targetMode || 'direct'
  if (!['direct', 'bff'].includes(config.targetMode)) {
    throw new Error(`Unsupported BCO_TARGET_MODE: ${config.targetMode}`)
  }
  const log = createLogger(config)
  const sessions = createSessionStore(config.sessionTtlMs)
  const context = { config, log, sessions }

  return http.createServer(async (request, response) => {
    const requestUrl = new URL(request.url, 'http://local')

    try {
      if (requestUrl.pathname === '/__proxy/health') {
        writeJson(response, 200, {
          status: 'UP',
          target: config.target.origin,
          targetMode: config.targetMode,
          frontendDir: config.frontendDir
        })
      } else if (config.targetMode === 'bff' && requestUrl.pathname === '/digx/j_security_check') {
        await handleRemoteBffLogin(request, response, context)
      } else if (requestUrl.pathname === '/api/login') {
        await handleBffLogin(request, response, context)
      } else if (requestUrl.pathname.startsWith('/api/bco/digx/')) {
        await handleBffProxy(request, response, context, true)
      } else if (requestUrl.pathname === '/digx' || requestUrl.pathname.startsWith('/digx/')) {
        if (serveMock(request, response, config, requestUrl)) return

        if (config.targetMode === 'bff') {
          handleRemoteBffProxy(request, response, context)
          return
        }

        const hasBearerToken = String(request.headers.authorization || '').startsWith('Bearer ')
        if (hasBearerToken) {
          await handleBffProxy(request, response, context)
        } else {
          handleTransparentProxy(request, response, context)
        }
      } else {
        serveStatic(request, response, config, requestUrl)
      }
    } catch (error) {
      log(`${request.method} ${request.url} failed: ${error.stack || error.message}`)
      if (!response.headersSent) writeJson(response, 500, { message: error.message })
    }
  })
}

function start () {
  const config = loadConfig()

  if (!fs.existsSync(config.frontendDir)) {
    console.error(`BCO frontend directory does not exist: ${config.frontendDir}`)
    console.error('Set BCO_FRONTEND_DIR to the consulting/channel directory.')
    process.exitCode = 1
    return
  }

  const server = createServer(config)
  server.listen(config.port, config.host, () => {
    console.log('BCO local proxy started')
    console.log(`Frontend : http://${config.host}:${config.port}`)
    console.log(`BCO API  : ${config.target.origin}`)
    console.log(`Mode     : ${config.targetMode}`)
    console.log(`Source   : ${config.frontendDir}`)
    console.log(`Mocks    : ${config.mockEnabled ? config.mockDir : 'disabled'}`)
  })

  const shutdown = signal => {
    console.log(`\n${signal} received, stopping BCO local proxy`)
    server.close(() => process.exit(0))
  }
  process.once('SIGINT', shutdown)
  process.once('SIGTERM', shutdown)
}

if (require.main === module) start()

module.exports = {
  createServer,
  extractSessionCookie,
  getMockFileName,
  isPublicPath,
  loadConfig,
  readCookie,
  resolveStaticPath,
  resolveStaticFallback,
  rewriteLocation,
  rewriteSetCookie
}
