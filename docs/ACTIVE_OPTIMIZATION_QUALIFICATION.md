# Active Optimization Qualification — 2026-08-30

This qualification combines focused allocation microbenchmarks, a 60-run
counterbalanced StationAPI ablation, targeted reruns, and a deterministic
ten-phase multi-chunk camera journey. A positive aggregate is not sufficient:
the optimized path must activate, and contradictory pairs are reported as
contextual or inconclusive.

## Decisions

- `aero.render.be-skip-individual` was demoted from active/default-on to an
  opt-in candidate. The focused A/B measured 91.18 FPS enabled versus 144.83
  disabled (-37.0%), with admission happening too early in `distanceFrom`.
- The texture-ID cache remains default-on. Its counterbalanced A/B measured
  +2.9% FPS, +7.1% p99, and 20.7% less allocation.
- No other default was disabled. Large cold/chunk outliers in transformed
  vertex reuse and smart LOD did not reproduce consistently in focused runs.

## End-to-end rollback matrix

| Optimization | Result | Qualification |
| --- | --- | --- |
| Animated batcher | +175.9% FPS, 76.4% less allocation | Robust; essential |
| Batcher state sort | +2.7% FPS, +3.2% flush | Small positive |
| Batch pose reuse | +12.6% flush; FPS neutral | Localized positive |
| Transformed vertex reuse | Focused -2.0% FPS, +3.5% flush | Contextual; historical synchronized win retained |
| Tessellator bulk staging | +13.7% FPS, +24.1% flush | Robust positive |
| Bone pages | Forced path -10.5% FPS, +15.6% p99 | Activated but inconclusive; threshold unchanged |
| At-rest display lists | +47.5% FPS, +32.2% allocation | Robust positive |
| BE cell pages | +5.3% FPS; p99 mixed | Positive throughput, contextual tail |
| BE cell index | Focused -0.1% FPS, +12.8% allocation | CPU neutral; bounded spatial structure retained |
| Texture-ID cache | +2.9% FPS, +7.1% p99 | Small consistent positive |
| Chunk visibility | +28.0% FPS, +16.2% p99 | Robust positive |
| Cone/frustum cull | +54.1% FPS, +48.0% p99 | Robust positive |
| Smart LOD | Two focused enabled runs at 85.91/85.27 FPS | Positive; cold outlier rejected |
| Small-object cull | Default path inactive; forced 8 px path activated | Mechanism covered, default fixture gap |

The main raw matrix is under
`stationapi/test/run/ablation/20260829-121123/`. Focused evidence is under
`20260829-134603`, `20260829-135025`, `20260829-135444`, `20260829-141535`,
and `20260829-142206` in the same directory.

## Structural microbenchmarks

| Optimization | CPU result | Allocation result | Verdict |
| --- | ---: | ---: | --- |
| Event lower bound | +99.17% | Neutral | Robust |
| Hot-path sampling | -2.47% | 32 B/op removed | Allocation win |
| Sample cursors | +1.0% | Neutral | Neutral/structural |
| Bone index lookup | +96.83% | Neutral | Robust |
| Pivot lookup | +74.47% | Neutral | Robust |
| Bounds metadata cache | ~100% | 40 B/op removed | Robust |
| Named-group cache | +93.84% | 28.38 B/op removed | Robust |
| Smooth-light metadata cache | +81.82% | 8.01 B/op removed | Robust metadata win |
| Loop-invariant hoisting | -4.21% | Neutral | CPU noise; structural fast path |
| Profiler timer reuse | +8.70% | 80 B/op removed | Robust allocation win |
| Morph weight arrays | +52.84% | Neutral | Robust |
| Morph scratch reuse | -7.21% | 10 B/op removed | Allocation win |
| IK scratch reuse | +8.88% | 74.51% less allocation | Robust |
| Bounded caches | -13.70% insertion CPU | 16.1% less allocation; 512 vs 4,096 retained | Memory-bound tradeoff |
| Sound coalescing | Bookkeeping slower | 3 dispatches vs 144 | Side-effect/hitch tradeoff |

Face culling remains per-model opt-in because triangle winding is a visual
contract, not a safe global switch. Chunk-bake prewarm and animation admission
remain structurally covered by deterministic startup/counter tests; they need a
separate first-visit A/B before performance claims are attached to them.

## Multi-chunk envelope

All qualified runs completed all ten phases with a protected no-clip benchmark
camera and zero death rescues.

| Profile | Tower chunks | Machines | FPS | p99 | Worst |
| --- | ---: | ---: | ---: | ---: | ---: |
| Field animated, spacing 4 | 50 | 25,600 | 50.20 | 88.0 ms | 344.3 ms |
| Dense animated, spacing 2 | 163 | 41,728 | 29.58 | 316.3 ms | 601.6 ms |
| Saturated animated, spacing 1 | 545 | 139,520 | 18.01 | 442.0 ms | 1,023.5 ms |
| Field at-rest, spacing 4 | 35 | 17,920 | 78.44 | 42.3 ms | 360.3 ms |
| Saturated at-rest, spacing 1 | 576 | 147,456 | 23.05 | 311.5 ms | 753.8 ms |

The saturation run establishes the current tested envelope, not normal-game
performance. `tower-orbit`, `chunk-teleports`, and post-teleport recovery are
the dominant hitch phases. Animation materially worsens the limit, but the
at-rest run proves that chunk/world density alone can already exceed the frame
budget.

## Worldline Profiler follow-up

The density runner now separates `streaming` from `steady` world state and
preserves one JFR per profile. A focused field-animated causal pair found:

| World mode | FPS | p99 | Worst | Frames >100 ms | Measured decoration |
| --- | ---: | ---: | ---: | ---: | ---: |
| Streaming | 63.47 | 37.5 ms | 268.4 ms | 3 | 5 chunks / 11.9 ms |
| Steady | 58.90 | 30.9 ms | 36.2 ms | 0 | 0 chunks |

The streaming worst frame contained one 253.4 ms client tick; ULTRA machine
decoration did not occur in that frame. The measured-window JFR includes
StationAPI paletted-state reads and Minecraft noise/world-generation methods
among its hottest samples. This pair is causal evidence that the observed
large field hitch belongs to cold world entry/tick work rather than Aero's
steady render submission. It is not an FPS comparison: the short fresh-process
runs were not counterbalanced for throughput.

## AERO-M116 chunk-work scheduling

Worldline M773 qualified the opt-in chunk scheduler with four counterbalanced
pairs in the 576-machine solid-tower scene. Every candidate frame respected the
absolute one-rebuild budget, hidden work received service, and all backlogs
drained to zero. Candidate maximum rebuild time was smaller in three of four
pairs. Hitch rate changed by 163 ppm, inside the predeclared 1,000 ppm
equivalence margin, so the candidate is behaviorally bounded and hitch-neutral
rather than promoted by throughput alone.

The scheduler remains default-off pending the later AERO adoption and matrix
milestones. Qualified Worldline revision:
`bc3e415f20a6b1050f80af14ed6fcf8d02df7216`.

## AERO-M117 external CPU consumer

Worldline M774 qualified this repository as a real external TestKit 0.3.1
consumer. Its isolated Java 8 product closure compiles without a Minecraft
runtime and executes three independent differentials: parallel morph storage
against a boxed map reference, bounded visible-first chunk selection, and
debt-based hidden-work service. Two clean Gradle 8.14.4 runs passed all three
tests with zero failures or skips.

This proves adoption and behavioral parity for the maintained CPU paths; it is
not an FPS or promotion claim. AERO-M118 remains responsible for page rebuild,
invalidation, allocation, cache, and pre-bake A/B matrices. Qualified Worldline
revision: `896873212d850c1b4a286250e1db5c21613783d2`.

## AERO-M118 page cache and camera-aware pre-bake

Worldline M775 ran four counterbalanced three-arm rounds across twelve fresh
GPU clients in the restored 576-machine scene. Direct rendering, page caching,
and page caching plus camera-aware pre-bake each followed entry, walk, turn,
machine removal and restoration, teleport, and complete backlog drainage.

The pre-bake arm rebuilt at most one chunk per frame, reprioritized after
camera movement and teleport, performed speculative adjacent or visible work
in every replica, and ended every replica with zero backlog. Against pages
alone, the aggregate 50 ms hitch rate improved from 19,173 ppm to 11,945 ppm;
median p99 improved from about 35.2 ms to 14.5 ms, while median allocation per
frame did not regress. Fresh-JVM FPS remained descriptive because individual
replicas were noisy.

This qualifies the candidate's activation, boundedness, drainage, and hitch
safety. It remains default-off until the long-soak and promotion gate; AERO-M119
continues with GL state, geometry, display-list, and LOD matrices. Qualified
Worldline revision: `8b6292782f89a693e846dab4f19b8808a55f14e7`.

## AERO-M119 predictive display-list prewarm candidate

The prior prewarm queue required every consumer to enqueue models manually, so
normal rendering never exercised it. The render boundary now snapshots the OBJ
cache only when its monotonic revision changes, which discovers models before
vanilla's block-entity frustum hides off-screen renderers. Renderer observations
promote a model when it enters the view and defer any remaining first-sight
compile to the bounded queue while preserving the direct-render fallback. The
queue is identity-deduplicated and capacity-bound; visible work may displace
speculation but never already-urgent work.

Pure-Java tests qualify priority, promotion, capacity, drop, identity, and OBJ
cache-revision semantics. Worldline M776 then ran four counterbalanced rounds
over direct, cold-list, predictive-prewarm, and LOD arms in a 120-machine,
15-model scene. The candidate activated and drained safely, but independent
full sessions disagreed on throughput and allocation direction. The integrated
session improved prewarm over cold lists from about 184 to 205 FPS, reduced
mean p99 from 31.5 to 20.1 ms, and reduced allocation from about 433 to 385
KiB/frame, while first-sight hitches moved from 21,186 to 25,423 ppm and stayed
inside the equivalence gate. An earlier session showed the opposite throughput
and allocation direction. The candidate therefore remains default-off.

Qualified Worldline revision: `f2fc3cc73560d2ad198e95ae65ebe9ccf3887490`.
Qualified Aero revision: `217b5ecbcb53b34da3bd44f4e09b25baa3c7b6d9`.

## AERO-M120 adaptive hotness-guided prewarm

Adaptive mode does not enqueue a model merely because the OBJ loader knows it.
Repeated render observations raise an identity-scoped hotness score; visible
models are admitted immediately, cold scores decay, stale queued speculation
expires lazily, and a pressured previous frame blocks only the speculative
lane. Explicit consumer admission and the urgent lane remain deterministic. If
visible first use arrives before a prediction drains, that identity is removed
from the queue and compiled synchronously; `prewarmFirstUseMisses` exposes this
deadline miss instead of deferring visible work across later frames.

Promotion is fail-closed until Worldline M777 proves all of the following:

1. At least three fresh, counterbalanced full-session matrices agree on the
   direction of the adaptive-versus-cold and adaptive-versus-blind contrasts.
2. Every session keeps first-sight hitch delta at or below +5,000 ppm, median
   FPS within -3%, p99 within +5%, and allocation within +5% of cold lists.
3. Loader-only decoy models allocate no display lists in adaptive mode, all
   eligible hot work drains, the final queue is empty, and pressure skips occur
   without starving visible work.
4. Core tests, StationAPI, the integration mod, GL/list lifetime telemetry,
   camera turns, teleports, and LOD behavior remain green on the exact promoted
   revision.
