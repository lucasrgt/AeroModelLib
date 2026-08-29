# Active Optimization Qualification — 2026-08-29

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
