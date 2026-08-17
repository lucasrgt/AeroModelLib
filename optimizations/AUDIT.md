# Optimization Source Audit

This audit reconciles Aero's production source and performance history with
the repository-owned optimization catalog. It is deliberately broader than a
feature-flag inventory: always-on caches, allocation removal, loop-invariant
work, and retired experiments are optimization decisions too.

## Audited baseline

- Source baseline: `258a22bdbbd6102657de83a34fd1b5c9cf1a0e87`
- Production roots: `core/aero`, `modloader/aero`, and
  `stationapi/src/main/java`
- Production Java files: 90
- Stable records after reconciliation: 44
- Current source symbols checked: 100
- Historical source symbols checked: 1

The inventory examined property literals, explicit performance-intent
comments, performance-related commit history, `CHANGELOG.md`, and
`docs/PERF_ROADMAP.md`. It then traced each independent decision to current or
historical source.

## Findings

The original 32-record import covered the configurable optimization families
well but missed always-on implementation work. The audit added eleven active
records:

- `aero.animation.event-lower-bound`
- `aero.animation.hot-path-sampling`
- `aero.animation.lookup-caches`
- `aero.animation.sample-cursors`
- `aero.diagnostics.profiler-timer-reuse`
- `aero.model.render-metadata-caches`
- `aero.render.at-rest-display-lists`
- `aero.render.face-culling`
- `aero.render.loop-invariant-hoisting`
- `aero.render.morph-scratch-reuse`
- `aero.render.smooth-light-cache`

It also added `aero.render.raycast-occlusion` as retired. The implementation
was removed when the cell-page and chunk-visibility path replaced it, but its
false-cull and spike history remains useful evidence against repeating the
same coarse-DDA design without a controlled experiment.

Three existing records gained missing implementation detail: animated batch
plan reuse, chunk-visibility last-hit caching, and cone-aspect caching.

## Platform drift discovered

`aero.animation.ik-scratch-reuse` is active in the StationAPI renderer only.
The ModLoader renderer still allocates `target`, `boneIdx`, and `pivots` in
`runIkChains`. The record now names the StationAPI source path and explicitly
states this divergence. The audit does not treat the ModLoader path as
optimized until its implementation changes and passes its own tests.

## Repeatable gate

Run from the Aero repository root:

```text
java tools/optimization-catalog/Audit.java
```

The StationAPI Java 17 CI job runs the same command before either Gradle
build, so source/catalog drift blocks the repository gate.

The gate fails when:

- a non-retired `source.symbols` entry no longer resolves;
- a referenced source member disappears;
- a `source.paths` platform constraint drifts;
- `source.revision` is missing or malformed;
- a Java type containing an Aero property literal or explicit performance
  intent has neither catalog ownership nor a documented exclusion; or
- an exclusion becomes stale or overlaps a catalog-owned type.

`Aero_FrameSpikeLogger` is the only current exclusion. It is diagnostic
instrumentation, not an optimization implementation.

The generic `worldline.optimization.v1` validator remains owned by Worldline.
Run it against this repository when schema fields or annotations change. This
Aero gate adds implementation-aware source resolution; it does not fork the
schema or make Aero depend on the Worldline runtime.

## Limits

No static heuristic can prove that a future optimization was documented.
The gate catches property-bearing and explicitly described performance code,
while review must still classify new algorithmic changes. A performance
change is complete only when the same commit updates its catalog record,
source revision, risks, rollback path, and evidence.
