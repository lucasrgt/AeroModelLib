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
