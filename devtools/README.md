# Local development tools

This directory contains local development tools only. It does not change the OBDX deployment
layout under `consulting`.

## Backend compile check

Open [`backend-compile/build.gradle`](backend-compile/build.gradle) as a Gradle project in an IDE
with Java/Gradle support, or invoke it from a terminal. The module
adds every `consulting/middleware/projects/**/src` directory as a Java source root and every JAR
under `consulting/middleware/lib` as a local compile dependency.

The backend tool is intentionally compile-only. It does not create a local OBDX/WebLogic
runtime and does not connect to a database.

See [`backend-compile/README.md`](backend-compile/README.md) for the IDEA and Ant instructions.

## Frontend local runtime

The frontend proxy serves `consulting/channel` at `http://127.0.0.1:8850` and forwards
same-origin `/digx/*` requests to a configurable real backend.

For a VS Code workstation without backend VPN access, use the reachable remote BFF bridge:

```bash
cd devtools/frontend-proxy
npm test
BCO_TARGET=http://8.210.128.44:8850 BCO_TARGET_MODE=bff npm start
```

Then open `http://127.0.0.1:8850`. Editing files under `consulting/channel` only requires a browser
refresh; there is no separate frontend build step for this source tree.

```bash
cd devtools/frontend-proxy
npm test
BCO_TARGET=https://backend.example.internal npm start
```

On PowerShell:

```powershell
cd devtools/frontend-proxy
$env:BCO_TARGET = "https://backend.example.internal"
npm start
```

See [`frontend-proxy/README.md`](frontend-proxy/README.md) for proxy and TLS options.
