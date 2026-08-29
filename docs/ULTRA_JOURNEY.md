# ULTRA Journey

The ULTRA journey turns the dense mixed-machine fixture into a deterministic
camera sequence. One client process now covers stationary rendering, camera
rotation, translation, occlusion between factory floors, chunk transitions,
and recovery after teleport-induced loading. This removes repeated game
startup from comparisons between scenes and exposes transition-only hitches.

## Phase contract

| Index | Phase | Primary pressure |
| ---: | --- | --- |
| 0 | `front-static` | Stable dense front view and warm caches |
| 1 | `yaw-sweep` | View culling, visibility history, and turn recovery |
| 2 | `pitch-sweep` | Vertical frustum boundaries |
| 3 | `lateral-strafe` | Side-entry visibility and chunk rebuild pressure |
| 4 | `tower-dolly` | Near/far transitions and LOD |
| 5 | `tower-orbit` | Continuous chunk and view-set changes |
| 6 | `vertical-scan` | Multi-floor entry/exit and vertical page boundaries |
| 7 | `floor-occlusion` | Cobblestone-floor partial occlusion |
| 8 | `chunk-teleports` | Abrupt near/far and chunk-set replacement |
| 9 | `post-teleport-recovery` | Deferred loading, cache recovery, and return stability |

The warmup is held at the canonical front pose. Measurement time is divided
equally between the ten phases. The summary fails its coverage contract if any
phase receives zero frames.

Multi-chunk profiles use a protected no-clip drone camera above the highest
tower floor. This prevents suffocation or death from contaminating frame data
and avoids placing the view inside a neighboring tower. The summary records
`benchmarkCameraProtected` and `benchmarkCameraRescues`; a qualified run must
report protection enabled and zero rescues.

## Running it

From `stationapi/test` on Windows:

```text
gradlew.bat runClientUltraStress -PultraCulls=true -PultraLayers=4 -Pbench=120 -Pwarmup=30
```

`-PultraCulls=false` preserves the original raw-throughput envelope. Use true
for visibility qualification. `-PultraLayers` controls density; `-Pbench`
and `-Pwarmup` control measured and warmup seconds.

For a frozen visual checkpoint, pass the zero-based phase index as a JVM
property, for example `-Daero.ultra.journeyCheckpoint=7` for
`floor-occlusion`. After warmup, the camera remains at the phase midpoint so
Worldline can wait for render readiness and capture a stable framebuffer.

The JSON summary includes aligned arrays for phase frame counts, average,
p95, p99, worst frame, allocation, queued/immediate render counts, view culls,
and visible chunks. `tools/perf/AblationMatrix.java` rejects incomplete
journeys and writes both whole-run and per-phase CSV reports.

## Worldline binding

`worldline/extensions/ultra-journey/manifest.properties` publishes the two
test-mod control hooks as Worldline semantic roles. Worldline remains the
owner of exact seed/plan/nonce orchestration and frozen RGBA framebuffer
oracles. `AeroUltraJourney.phaseIndex()` and `name(int)` form the public
test-consumer checkpoint surface. A performance journey should keep moving; a
visual oracle should freeze at a declared phase checkpoint, wait for render readiness, and compare
the complete framebuffer against its paired rollback arm.

Performance and visual equivalence are separate gates. A faster phase is not
accepted when its checkpoint image differs, and a matching image does not
prove that the optimization activated. Activation counters and scene coverage
must both be non-zero before a result is interpreted.

## Deliberate non-claim

The floor-occlusion phase exercises whole-model and chunk visibility, not
triangle-level occlusion. The depth buffer hides covered pixels, but partially
covered models can still transform and submit their complete geometry. A
future hierarchical sub-group culler needs its own visual oracle and cannot
reuse the retired per-entity raycast implementation.
