# Backend compile tool

The OBDX consulting backend is a collection of Ant/Eclipse projects. It is not a standalone Spring
Boot application, so this tool deliberately supports compilation and code navigation only.

## Recommended: IntelliJ Gradle project

1. In IntelliJ IDEA select **File > Open**.
2. Select `devtools/backend-compile/build.gradle`.
3. Choose **Open as Project** and trust the project.
4. Set the Gradle JVM/Project SDK to the JDK used by the target OBDX environment. For this source
   tree the language level is Java 8.
5. Wait for Gradle sync, then run **Build > Build Project**, or run the `classes` Gradle task.

The Gradle project discovers all `consulting/middleware/projects/**/src` directories and adds all
`consulting/middleware/lib/**/*.jar` files to the compile classpath. The legacy
`module/com.ofss.digx.cz.bea.datatype` source copy is excluded because its DTOs are provided by
`common/com.ofss.digx.cz.bea.app.xface`, as in the official Ant project list. It has no remote repositories
and does not download application dependencies.

If IDEA reports an unresolved class, first verify that the required proprietary JAR exists under
`consulting/middleware/lib`. Do not replace proprietary OBDX classes with public Maven artifacts.

## Official Ant compile from IDEA

Use this when the compile result must follow the same project order as the delivery build:

1. Open **View > Tool Windows > Ant**.
2. Add `devtools/backend-compile/build.xml` as an Ant build file.
3. Run the `compile` target.

The target delegates to `consulting/ant/build_backend.xml:compile-all`. It compiles and creates
intermediate JARs under `consulting/dist`, but it does not package EARs or start WebLogic.

## What this tool does not do

- It does not start the backend locally.
- It does not emulate WebLogic, OBDX runtime services, approval processing, or database adapters.
- A successful compile does not replace deployment/integration testing against the real backend.

## Local dependency versions

The current source requires `h2h-ms-support-client-0.1.0.jar`,
`h2h-ms-support-api-0.1.0.jar`, and an `LMEngine.jar` exposing `getCity`/`setCity` on the
LM instruction models. Keep these libraries aligned with the delivered middleware source.

The standalone SnakeYAML 2.0 JAR is placed first on the local compile classpath because
`redisson-all-3.11.0.jar` also bundles older SnakeYAML classes. The old standalone 1.26 JAR
is excluded from this local compile. Deployment classloading still follows the server configuration.

`gradle.properties` gives the compiler a 2 GB Gradle heap for the complete source tree.

## HTH API Password transaction regression

Set `JAVA_HOME` to the local JDK and `H2_JAR` to a local H2 1.4.200 JAR, then run:

```sh
python3 devtools/backend-compile/tests/verify_hth_password_transactions.py
```

The test executes the service's transaction/Code-validation methods and repository SQL through the
project's real OBDX ORM wrappers and EclipseLink against an isolated H2 database. It checks that
failed Code attempts survive an outer rollback, five failures invalidate the Code, SETUP and RESET
commit the Code/credential/state/operation together, a final-step failure rolls all four writes back,
and expiry/session-open failure restore the outer transaction and close independent sessions.
The fixture applies the three user foreign keys from the deployment SQL. Production identity
resolution and database call sites run against profiles whose CLOSE_ID is either the complete
login ID or a legacy short ID. Code lookups accept both username formats, while credential/state/
operation writes, status reads and successful-request lookups retain the resolved profile key.
The test also deserializes the existing generate request with no `purpose`, resolves SETUP/RESET
from the target user's selected credential store, activates pending Codes through the approval
transaction method, and verifies that the old password stops matching after RESET. UAM status
calls are fixtures, including unavailable/unknown responses with no local-store fallback.
The STATE MERGE fixture adds explicit types to two bind parameters for H2, and approval expiry
arithmetic uses H2 DATEADD; production SQL is unchanged.
WebLogic suspension, user-profile repository bootstrap, management eligibility and application
configuration use test fixtures. Passwords are synthetic inputs hashed and checked by the production
PBKDF2 implementation; the separate frontend transport regression exercises the actual session RSA protocol.

`python3 devtools/backend-compile/tests/verify_hth_code_lookup.py` checks that the newest Code
is selected across both short and full usernames, without revealing another user's or company's Code.

UAT must still verify WebLogic's NONXA datasource/Oracle permissions and a complete HTTP SETUP/RESET
with a current approved Code. For a wrong Code, expect `_002` with ATTEMPT_COUNT incremented; for an
expired Code with matching input, expect `_003`; incorrect input remains `_002` and counts
towards the attempt limit even when the stored Code has expired. An expired Code never
reserves an operation or changes the password. For success, check the credential is ACTIVE
and Code is USED.

### API Password 500 diagnostics

`DIGX_CO_0003` with HTTP 500 is a generic RuntimeException/FatalException response.
The existing server log prefixes are `RuntimeException thrown by a REST service` and
`FatalException thrown by a REST service`; inspect the matching request's cause chain.
HTH-specific logs use `HTH_API_PASSWORD lifecycle` for the service and
`HTH_API_PASSWORD endpoint` for the REST/channel boundary. They record only stages and
exception class names. `TX_BEGIN_NONXA` also records the actual ORM transaction wrapper
class, distinguishing the resource-local wrapper from a JTA wrapper in the deployed environment.
Stages include `CODE_RESERVE`, `DATABASE_COMPLETE`, `NOTIFICATION`, `INTERACTION_CLOSE`,
and `CHANNEL_CLOSE`. These logs diagnose a failure; they do not change its response or transaction outcome.

Generation logs `HTH_API_PASSWORD code: stage=PURPOSE_RESOLVED` with SETUP or RESET. Code
verification logs `input: stage=CODE_LOOKUP` when no usable or latest expired Code is available,
`CODE_COMPARE` for incorrect input, `CODE_EXPIRY` only after matching an expired Code, and
`CODE_DECRYPT` for a stored Code cipher/key failure. A decryption failure
returns `_009` without incrementing the user's failed-attempt count. Input transport errors
remain `_010`; Code values, ciphertext and keys are never included in these diagnostics.
