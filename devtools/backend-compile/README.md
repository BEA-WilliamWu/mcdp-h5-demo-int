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
`consulting/middleware/lib/**/*.jar` files to the compile classpath. It has no remote repositories
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
