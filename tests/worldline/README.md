# Worldline tests

This isolated Gradle 8.14.4 project compiles the minimal AeroModelLib core
slice and runs its Java 8 Worldline TestKit 0.2.0 consumer suite host-only.

Run `gradle -p tests/worldline worldlineDoctor worldlineTest` with Gradle
8.14.4 or newer. This repository's CSM text-audit policy intentionally keeps
the binary Gradle wrapper JAR out of version control.
