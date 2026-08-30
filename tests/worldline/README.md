# Worldline CPU-path tests

This isolated Java 8 suite is a real external consumer of Worldline TestKit
0.3.1. It compiles Aero's platform-neutral `core/` product and checks optimized
CPU paths against small independent reference models. It does not compile or
replace either legacy loader build and it does not start Minecraft.

The current suite covers morph-array parity, bounded and starvation-safe chunk
scheduling, and camera-relative pre-bake reprioritization after teleport.

Run with Gradle 8.14.4 or newer:

```text
gradle -p tests/worldline worldlineDoctor worldlineTest
```

Before the 0.3.1 plugin is published, Worldline qualification uses its exact
checkout as an included build and supplies the hash-pinned local TestKit
distribution. The suite deliberately contains no Minecraft oracle JAR.
