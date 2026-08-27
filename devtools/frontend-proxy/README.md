# Frontend local server and backend proxy

This zero-dependency Node service serves the local OBDX frontend and proxies backend requests on
the same origin. The browser opens the frontend at `http://127.0.0.1:8850`; requests to `/digx/*`
are forwarded to the configured real backend. Because the browser only talks to the local origin,
the proxy avoids browser CORS restrictions.

Node.js 18 or newer is required.

## Start through the remote BFF bridge

Use this mode when the workstation cannot connect to the backend/VPN but can reach the remote BFF
bridge at `http://8.210.128.44:8850`.

macOS/Linux, including the VS Code integrated terminal:

```bash
cd devtools/frontend-proxy
npm test
BCO_TARGET=http://8.210.128.44:8850 BCO_TARGET_MODE=bff npm start
```

PowerShell:

```powershell
cd devtools/frontend-proxy
npm test
$env:BCO_TARGET = "http://8.210.128.44:8850"
$env:BCO_TARGET_MODE = "bff"
npm start
```

The `bff` mode unwraps the bridge's `status/headers/body` response, keeps its access token in a
local HTTP-only development session, and exposes the normal `/digx/*` contract expected by the
unmodified OBDX frontend. Do not use the default `direct` mode with this bridge.

### Remote Go BFF development bypass

`main.go` is the remote BFF program intended to run on the host that can reach the real backend.
For isolated debugging, BCO token validation is disabled by default, so the program can be started
without an additional environment variable:

```bash
./frontend-proxy
```

The Go program must be rebuilt and the remote process/container restarted after changing
`main.go`. This variable belongs on the remote BFF host, not on the local Node frontend process.
In this default debug mode, anonymous `/digx/*` requests are forwarded without enforcing the BFF
Bearer token. Requests carrying a valid token still receive their stored backend session cookie.
To restore token enforcement, start the Go BFF with
`BFF_DEBUG_DISABLE_BCO_AUTH=false`. Never use the default bypass in a shared or production
environment.

## Start

macOS/Linux:

```bash
cd devtools/frontend-proxy
npm test
BCO_TARGET=https://backend.example.internal npm start
```

PowerShell:

```powershell
cd devtools/frontend-proxy
npm test
$env:BCO_TARGET = "https://backend.example.internal"
npm start
```

Open [http://127.0.0.1:8850](http://127.0.0.1:8850). The default frontend source is
`../../consulting/channel`, relative to this directory. Files are served with `Cache-Control:
no-store`, so a normal browser refresh picks up source changes. The local service worker is also
replaced with a cache-clearing implementation by default.

Before logging in, open [http://127.0.0.1:8850/__proxy/health](http://127.0.0.1:8850/__proxy/health)
and verify that `frontendDir` and `target` are correct.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Local listen address |
| `PORT` | `8850` | Local listen port |
| `BCO_TARGET` | `https://210.177.116.65` | Real backend origin |
| `BCO_TARGET_MODE` | `direct` | `direct` for a real backend; `bff` for the remote BFF bridge |
| `BCO_FRONTEND_DIR` | repository `consulting/channel` | Frontend source or built channel directory |
| `BCO_TLS_REJECT_UNAUTHORIZED` | `false` | Verify the backend TLS certificate |
| `BCO_REWRITE_COOKIES` | `true` | Remove backend cookie Domain/Secure attributes for local HTTP |
| `BCO_DISABLE_SERVICE_WORKER` | `true` | Disable stale service-worker caches during debugging |
| `BCO_LOG_REQUESTS` | `true` | Log method, path, response status and duration |
| `BCO_REQUEST_TIMEOUT_MS` | `60000` | Backend request timeout |
| `BCO_MOCK_ENABLED` | `false` | Enable optional OHS-style JSON mocks |
| `BCO_MOCK_DIR` | `./mocks` | Mock JSON directory |

Only set `BCO_TLS_REJECT_UNAUTHORIZED=true` when the internal backend certificate is trusted by
the local Node runtime.

## Proxy modes

- Direct browser mode: `/digx/*` is forwarded transparently and browser session cookies are preserved.
- Remote BFF bridge mode: `/digx/*` responses are unwrapped and the bridge token is managed by the local proxy.
- Optional BFF mode: `/api/login` returns a short-lived local token. `/digx/*` requests carrying
  that Bearer token use the server-side cookie mapped to the token.

Normal OBDX browser debugging uses browser mode; no frontend API URL modification is required.

## Optional mock responses

Mock file naming follows the existing OHS convention:

```text
GET /digx/v1/me               -> me_GET.json
GET /digx/cz/v1/accounts/123 -> accounts_123_GET.json
```

Place files under `BCO_MOCK_DIR` and start with `BCO_MOCK_ENABLED=true`.

## Local endpoints

| Endpoint | Description |
| --- | --- |
| `/` | Local OBDX frontend |
| `/digx/*` | Transparent backend proxy |
| `/__proxy/health` | Effective frontend directory and backend target |
| `/api/login` | Optional BFF-compatible login |
| `/api/bco/digx/*` | Optional forced BFF envelope mode |
