# AeroModelLib Ultra Stress Test

The ULTRA profile is a deliberately pathological StationAPI client scene for
finding AeroModelLib's throughput and frame-time limits. It is not a realistic
player-base benchmark and its numbers must not be advertised as normal-game
performance.

## Workload

The default profile creates one qualifying tower every four chunks. Each tower
contains eight complete 16 by 16 layers, or 2,048 animated BlockEntities. The
placement rotates through twelve renderer families so one frame exercises:

- batched skeletal animation;
- high-triangle animated meshes;
- UV animation and direct rendering;
- procedural poses, IK, morphing, graph playback, and easing;
- cell pages and display-list lifecycle;
- solid cobblestone floor compilation;
- native periodic world saves.

Animation LOD is forced to 2,048 blocks and Aero's chunk-visibility and frustum
shortcuts are disabled. This is intentional: the test asks how much raw work
the renderer can survive when its normal protection mechanisms cannot help.

## Run

From `stationapi/test`:

```powershell
./gradlew runClientUltraStress -PultraLayers=8 -PultraSpacing=4 -Pbench=180 -Pwarmup=30
```

To compare the OpenGL 1.1 client-array candidate against the Tessellator
oracle, run the same fixed level twice and change only this property:

```text
./gradlew runClientUltraStress -PultraLayers=8 -PultraSpacing=4 -Pbench=180 -Pwarmup=30 -PultraClientArrays=true
```

The summary reports `maxClientArrayDraws` and `maxClientArrayVertices`; both
must remain zero in the oracle run and become non-zero in the candidate run.

### Isolated client-array A/B (rejected)

The August 29, 2026 isolated run used one central 2,048-machine tower
(`spacing=32`), 1,024 simultaneously animated instances, a 5-second warmup,
and a 15-second measured window. The specialized client-array path submitted
up to 1,026,528 vertices in 170 draws per frame, but lost to Tessellator:

| Metric | Tessellator | Client arrays |
| --- | ---: | ---: |
| Average FPS | 25.73 | 24.16 |
| Average frame | 38.86 ms | 41.39 ms |
| Average Aero flush | 17.03 ms | 19.00 ms |
| Allocation/frame | 2.62 MiB | 3.04 MiB |

The client-array flush was 11.6% slower. The record is therefore rejected and
the flag remains off by default. This is not a GPU-feature gap: Beta's
Tessellator already stages an on-heap integer array and submits client arrays;
the alternative merely duplicated a mature path.

### Isolated exact-pose reuse A/B (promoted)

The same isolated tower also qualifies exact batch-local pose reuse. Set
`-PultraPoseReuse=true|false` and choose synchronized or deterministic
per-position loop phases with `-PultraPhaseSpread=false|true`.

The final counterbalanced 15-second runs kept 1,024 animations and six batches
visible in every frame:

| Workload | Metric | Per-instance pose | Reused pose |
| --- | --- | ---: | ---: |
| Synchronized | Resolved / reused | 1,024 / 0 | 6 / 1,018 |
| Synchronized | Average FPS | 39.02 | 40.72 |
| Synchronized | Average Aero flush | 13.49 ms | 11.43 ms |
| Synchronized | p99 frame | 56.4 ms | 50.5 ms |
| Diverse phases | Resolved / reused | 1,024 / 0 | 515 / 509 |
| Diverse phases | Average FPS | 39.42 | 40.27 |
| Diverse phases | Average Aero flush | 12.88 ms | 12.80 ms |
| Diverse phases | p99 frame | 53.6 ms | 51.7 ms |

The synchronized flush improved 15.3%; the adversarial mixed-phase flush was
neutral (+0.6% faster). Reuse is default-on. It is exact rather than temporal:
bundle and clip identity plus raw float time bits must match. Transitions and
custom `Aero_AnimationPlayback` subclasses are excluded. Use
`-Daero.batchposereuse=false` as the rollback oracle.

### Shared-pose transformed vertex reuse (promoted)

Set `-PultraVertexReuse=true|false` while leaving pose reuse enabled. The
candidate does not change submission order or arithmetic: it stores the first
representative instance's exact local XYZ/UV results and repeats the same
Tessellator calls with each instance translation. Unique pose rows keep the
direct emitter.

| Workload | Metric | Pose reuse only | + Vertex reuse |
| --- | --- | ---: | ---: |
| Synchronized (30 s, 15 s warmup) | Average FPS | 42.43 | 47.87 |
| Synchronized (30 s, 15 s warmup) | Average Aero flush | 11.21 ms | 9.44 ms |
| Synchronized (30 s, 15 s warmup) | p95 / p99 frame | 29.7 / 52.1 ms | 25.9 / 44.6 ms |
| Diverse phases (15 s, 10 s warmup) | Average FPS | 39.80 | 40.38 |
| Diverse phases (15 s, 10 s warmup) | Average Aero flush | 15.43 ms | 11.93 ms |
| Diverse phases (15 s, 10 s warmup) | p95 / p99 frame | 33.8 / 53.1 ms | 31.2 / 50.0 ms |

In the synchronized run, only 6,096 local vertices were transformed and
1,020,432 repeated transforms were avoided per frame. The feature is
default-on; use `-Daero.batchvertexreuse=false` for rollback.

### Governed Tessellator bulk staging (promoted)

Set `-PultraTessellatorBulk=true|false` with pose and transformed-vertex reuse
enabled. The candidate writes the same XYZ, UV, and packed color fields into
StationAPI's existing eight-int Tessellator buffer, then uses the unchanged
`Tessellator.draw()` path. A batch must contain at least 262,144 eligible
vertices before bulk staging activates; smaller workloads retain ordinary
vertex calls.

| Metric | Vertex reuse | + Governed bulk staging |
| --- | ---: | ---: |
| Average FPS | 46.95 | 54.08 |
| Average frame | 21.30 ms | 18.49 ms |
| Average Aero flush | 9.24 ms | 8.30 ms |
| p95 / p99 frame | 26.3 / 45.6 ms | 23.7 / 42.6 ms |
| Bulk-staged vertices/frame | 0 | 919,296 |

The synchronized candidate gained 15.2% throughput and reduced Aero flush by
10.2%. Under deterministic diverse phases, the largest eligible batch stayed
below the workload gate and `maxTessellatorBulkVertices` remained zero. The
feature is default-on; use `-Daero.tessellatorbulk=false` for rollback.

The task creates a uniquely named fixed-seed world, loads the central tower,
pins the camera beside it, waits for every central machine, warms up, measures
for the requested benchmark duration, and exits without menu interaction. It
writes:

- `run/aero-ultra-summary.json`: complete-frame distribution and counters;
- `run/aero-ultra-spikes.log`: at most one human-readable spike per second;
- `run/aero-ultra.jfr`: CPU, allocation, GC, safepoint, and I/O evidence;
- epoch bounds in the JSON for isolating the measured window in JFR.

The default keeps the complete census and JFR enabled but disables the
per-call `Aero_Profiler`. This avoids timing millions of animation calls and
logging every slow flush inside the workload being measured. After locating a
limit, rerun that exact level with `-PultraProfiler=true` for detailed Aero
section attribution; treat that diagnostic run as instrumented, not as the
throughput score.

The JSON embeds its `stageOrder`: client tick, world save, maximum chunk
compile, terrain render, Aero preparation, cell-page rebuild, entity render,
Aero world flush, and `Display.update`. Stage timers can overlap: for example,
a native world save is part of the client tick rather than additional time.

## Find the limit

Change one dimension at a time and use a fresh world/process for every run:

1. Hold spacing at 8 and run 2, 4, 8, 16, 32, then 48 layers.
2. Keep the highest acceptable layer count and reduce spacing through 8, 4,
   2, then 1 chunk.
3. Define the knee as the first level where p99 exceeds the chosen frame
   budget or where backlog, GC, display-list pressure, or one-second frames
   no longer return to the prior level.
4. Repeat the two levels around that knee in counterbalanced order before
   treating it as a library limit.

The explicit envelope is 48 layers and spacing 1: 12,288 mixed animated
BlockEntities in every loaded chunk. This can produce seconds-per-frame,
multi-gigabyte worlds, or an out-of-memory exit. Start with the defaults. The
bounded defaults are already substantially harsher than the older MEGA scene,
which places 576 machines once every twelve chunks.

## Interpretation

`aeroFlush` is the direct batched mesh-submission bucket; `entityRenderNanos`
covers vanilla BlockEntity traversal before that flush. A slow frame whose
material time is instead in `worldSaveNanos`, `chunkCompileMaxNanos`,
`displayUpdateNanos`, or GC is still a valid workload failure, but it is not
direct proof that mesh submission caused the stall. Use JFR and the profiler
sections to establish causality before changing production code.

For a production-like comparison, use `runClientEmpty`, `runClientRealistic`,
and `runClientMegaDefault`. Never compare ULTRA with a differently tuned JVM
and call the difference an Aero optimization.
