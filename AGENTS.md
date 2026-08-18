# AeroModelLib Engineering Guide

All repository artifacts must be written in English.

## Behavioral constitution

1. Minecraft Beta 1.7.3 and the supported loader runtimes are the behavioral
   oracles for platform-facing code.
2. Pure-Java model and animation behavior must remain covered by deterministic
   unit tests.
3. Performance changes must preserve observable behavior unless an explicit,
   documented opt-in changes it.
4. Maintained performance changes must reference an Aero-owned stable
   optimization ID with the source-only `OptimizationRef` annotation.

## Engineering constitution

1. Each production source file must remain at or below 200 code lines.
2. Each test, harness, and repository-tool source file must remain at or below
   300 code lines.
3. There is no total line budget. Behavior may not be moved into tests,
   generated files, or harness code to evade a per-file ceiling.
4. Modules follow the dependency order in `harness.properties`. Dependencies
   not declared there and dependency cycles are forbidden.
5. Missing tools, unknown optimization IDs, compilation
   failures, and failed tests fail closed.

## Canonical verification

Run from the repository root:

```text
java tools/harness/Verify.java
```

This gate owns source ceilings, module structure, optimization metadata,
Java 8 core compilation, and the complete pure-Java test suite. Platform
changes additionally require:

```text
java tools/harness/Verify.java --platforms
```
