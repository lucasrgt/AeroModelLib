# CHANGELOG

All notable changes to **AeroModelLib** documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) loosely; versions
correspond to `mod_version` in `stationapi/gradle.properties`.

## [3.x] — Smart LOD + occlusion default-on

### Added
- **Unsafe strict block-entity view culling is now opt-in.** The broad
  conservative cone remains enabled, but `aero.beViewCull` now requires an
  explicit `true`. Worldline's same-client full-frame oracle reproduced
  visible static MegaCrusher models disappearing while the stricter layer was
  active; production defaults therefore fail safe while the projection
  heuristic is redesigned.
- **Adaptive predictive display-list prewarm candidate.** Optional hotness
  admission now keeps merely loaded models out of the GL queue, learns from
  repeated culled/visible render observations, expires cold speculation, and
  suppresses speculative drains after pressured frames while preserving the
  urgent visible lane. Frame-spike telemetry separates admission, rejection,
  expiry, pressure skips, and speculation that missed visible first use. A
  missed prediction now leaves the queue and compiles synchronously instead of
  turning one cold first-use event into several later urgent-drain hitches. The
  earlier bounded queue and loader discovery
  remain available for controlled A/B qualification; production promotion is
  fail-closed on the cross-session M777 repeatability matrix.
- **Camera-aware incremental chunk pre-bake qualified.** The opt-in chunk-work
  scheduler now prioritizes the player's current chunk, adjacent or visible
  chunks, and a bounded look-ahead ring before background work. Priorities are
  recomputed from the current camera after walking, turning, or teleporting;
  age/debt still guarantees background service, and the absolute per-frame
  rebuild budget is unchanged. Frame-spike output now separates speculative
  `chunkWorkPrebake` rebuilds. Worldline AERO-M118 exercised twelve fresh
  clients over entry, walk, turn, mutation, teleport, and drain phases. Every
  backlog drained, the candidate stayed at one rebuild per frame, and 50 ms
  hitches improved from 19,173 ppm to 11,945 ppm versus pages alone. It stays
  opt-in pending the long-soak and promotion gate. `WorldRenderer.setWorld`
  now resets the static scheduler on unload and world transition, immediately
  releasing its retained dirty queue, chunk identities, age, and debt state.
  When visible arrivals and background work are both urgent, debt and age now
  rank before the visual tier, preserving the promised starvation bound under
  sustained chunk arrivals. Worldline AERO-M121 rebuilt 164,096 items in each
  of two fresh Java 8 JVMs, bounded hidden wait to 157 frames, and proved zero
  retained scheduler state after each of 256 world transitions per JVM. The
  real-client promotion gate then exposed a lifecycle flaw in the replacement
  integration: a renderer rebind could enqueue about 1,462 essential rebuilds
  and the absolute one-per-frame budget made the scene appear only in pieces.
  The integration is now vanilla-first and additive: vanilla rebuild selection
  always runs unchanged, and the Aero budget applies only to residual
  speculative work at method return. The corrected Worldline AERO-M122 gate
  then kept the candidate disabled. The first qualification retained only
  54.98% of baseline FPS, raised p99 frame time to 9.70x and allocation to
  1.78x, and one load ended with 380 dirty in-frustum builders. A clean
  published confirmation still retained only 71.51% of baseline FPS, raised
  p99 to 1.11x and allocation to 1.43x, and left as many as 19 visible builders
  pending. Directly
  dirtied camera chunks rebuilt with zero-frame latency, but that local win did
  not justify the broad additive work. The option remains a diagnostic-only,
  default-off experiment and is not recommended for gameplay.
- **Worldline CPU-path consumer adopted.** AeroModelLib now owns an isolated
  Java 8 Worldline TestKit project that compiles maintained core product code
  without Minecraft and differentially verifies morph-array parity, bounded
  visible-first chunk scheduling, and debt-based hidden-work service. Worldline
  AERO-M117 executed all three tests twice with zero failures or skips.
- **Bounded chunk-work scheduler qualified.** The opt-in scheduler now applies
  one absolute non-forced rebuild budget across the whole frame, prioritizes
  visible work, and recovers hidden work through age and debt. Worldline
  AERO-M116 completed four counterbalanced 576-machine pairs with zero final
  backlog, hidden-work service in every candidate, and a hitch-rate delta
  inside the 1,000 ppm equivalence margin. It remains default-off while the
  adoption and matrix milestones proceed. Catalog:
  `aero.chunk.compile-budget` (candidate, default off).
- **Remaining candidate qualification.** Fresh-world evidence rejects the
  chunk-scoped PalettedContainer cache: chunk compilation regressed 11.7%,
  allocation rose 46.8%, and throughput fell 12.7%. The 14-OBJ corpus gave
  hidden-face culling nothing to remove. In the matched at-rest Cell Page
  scene, high-memory flattening improved 145.31 to 165.06 FPS and cut average
  cell flush from 0.57 to 0.20 ms; it remains opt-in because geometry is
  duplicated in driver memory. Per-instance light did not reduce page count,
  membership remained stable, and a bounded auto-flatten experiment was
  neutral and removed.
- **Unique rest-pose Tessellator staging.** Named body groups without an
  active animated bone now write their unchanged scale-and-translate stream
  directly into StationAPI's Tessellator buffer instead of calling `vertex`
  three times per triangle. A diverse-phase 20-second pair increased staged
  coverage from 544,320 to 931,680 vertices/frame, improved 39.09 to 46.18
  FPS, and cut Aero flush from 12.72 to 9.17 ms; a confirmation retained 9.88
  ms flush and full coverage. Disable with
  `-Daero.tessellatorbulk.rest=false` or the global
  `-Daero.tessellatorbulk=false`. Catalog:
  `aero.render.tessellator-bulk-staging` (active, default on).
- **Dead batch-pose reset stores removed.** Reused batch rows now clear their
  activity bits without first writing seventeen default fields into every
  pose: active rows are fully overwritten by `copyPose`, while inactive rows
  are never read. A diverse-phase 20-second pair improved from 39.26 to 40.96
  FPS, cut Aero flush from 11.42 to 10.87 ms, and reduced p95 from 38.7 to
  32.7 ms; a confirming candidate reached 44.84 FPS. Catalog:
  `aero.render.animated-batcher` (active, default on).
- **Unique-pose fused Tessellator staging.** Rotating animation poses that
  cannot share a transformed vertex stream now fuse the existing CPU pose
  transform with direct writes to StationAPI's eight-int Tessellator buffer.
  Unsupported/color-disabled paths retain ordinary vertex calls. In two
  diverse-phase 30-second candidates,
  throughput reached 44.75 and 45.82 FPS versus the prior 43.31-FPS baseline;
  the clean counterbalanced pair measured 45.82 versus 40.52 FPS while Aero
  flush fell from 12.59 to 10.18 ms. Disable all governed bulk staging with
  `-Daero.tessellatorbulk=false`. Catalog:
  `aero.render.tessellator-bulk-staging` (active, default on).
- **Animation curve LUT qualified and rejected.** The opt-in LUT now stores
  samples in one contiguous float array, defaults to 256 samples, validates
  bounded interpolation error at load time, and leaves STEP tracks and unsafe
  Euler wraps on the exact evaluator. A diverse-phase 30-second ULTRA A/B still
  favored exact sampling: 43.31 versus 41.55 FPS and 11.50 versus 12.17 ms of
  Aero flush at 256 samples; 64 samples regressed further. The LUT remains a
  research-only opt-in and is no longer enabled by the high-memory preset.
  Catalog: `aero.animation.curve-lut` (rejected, default off).
- **Governed Tessellator bulk staging.** Dense exact-pose batches now pack
  their already transformed vertices directly into StationAPI's existing
  eight-int Tessellator buffer, preserving its draw path, packed fields, and
  submission order. A per-batch workload gate keeps smaller reuse sets on the
  ordinary `Tessellator.vertex` path. In the isolated synchronized ULTRA
  scene, 919,296 vertices/frame used bulk staging and throughput rose from
  46.95 to 55.78 FPS while Aero flush fell from 9.24 to 5.86 ms/frame. The
  writer hoists its StationAPI accessors and offsets per batch and avoids
  discarded pre-rotation coordinate work. The diverse-phase scene stayed
  below the gate. Disable with
  `-Daero.tessellatorbulk=false`. Catalog:
  `aero.render.tessellator-bulk-staging` (active, default on).
- **Shared-pose transformed vertex reuse.** Once exact pose reuse identifies a
  repeated batch representative, its local XYZ/UV stream is transformed once
  per geometry bucket and replayed with the original per-instance translation
  and Tessellator call order. A synchronized 1,024-animation ULTRA run avoided
  1,020,432 of 1,026,528 vertex transforms per frame and cut Aero flush from
  11.21 to 9.44 ms/frame (15.8%); deterministic diverse phases cut it from
  15.43 to 11.93 ms/frame (22.7%). Disable with
  `-Daero.batchvertexreuse=false`. Catalog:
  `aero.render.batch-transformed-vertex-reuse` (active, default on).
- **Exact pose reuse inside compatible animation batches.** Standard
  non-transitioning playbacks that share the representative bundle, clip, and
  bit-exact interpolated time now share one resolved pose row for the frame.
  Custom playback subclasses and transitions remain per-instance. In the
  isolated 1,024-animation ULTRA A/B this reduced pose resolutions from 1,024
  to 6 and cut the synchronized Aero flush from 13.49 to 11.43 ms/frame
  (15.3%); the diverse-phase case reused 509 poses and stayed neutral at
  12.88 versus 12.80 ms/frame. Disable with
  `-Daero.batchposereuse=false`. Catalog:
  `aero.animation.batch-pose-reuse` (active, default on).
- **Rejected OpenGL 1.1 client-array experiment retained behind an opt-in.**
  `-Daero.clientarrays=true` bypasses `Tessellator.vertex` with a specialized
  reusable `T2F_C4UB_V3F` buffer, but the isolated 1,024-animation A/B made
  the Aero flush 11.6% slower. It remains off by default so the measured
  failure and its telemetry prevent accidental promotion or reinvention.
- **Dense BlockEntity hot-path cleanup.**
  `Aero_RenderDistanceBlockEntity` now shadows its last cell-relevant state
  and only enters the global `IdentityHashMap` index when world, position,
  render state, orientation, paging, or animation membership changes. The
  automatic per-tick dirty detection remains intact. StationAPI also reuses
  the sound-coalescing dispatcher instead of allocating one adapter per world
  flush.
- **Resolved smooth-light cache promoted to default-on (smooth-light-resolved).**
  `Aero_SmoothLightCache` keeps the resolved per-triangle brightness of each
  (world, geometry, block position) smooth-lit instance under a bounded TTL
  (`-Daero.smoothlight.cacheMs=50`,
  `-Daero.smoothlight.cacheMax=1024`). Steady-state draws of static block
  entities skip the world light-grid sampling and the per-triangle bilinear
  blend entirely; a local light change may render at most one TTL late. The
  smooth draw path itself moved into `Aero_MeshSmoothLightRenderer` on both
  runtimes, split into resolve + emit passes that keep the GL stream
  byte-identical when the cache is off. Worldline M780 re-qualified four fresh
  ABBA clients with zero resolved-brightness differences: light samples fell
  from 1,536,000 to 633,725 (-58.7%), aggregate render time improved 12.4%,
  and 50 ms hitches fell from 10 to 1. The cache is now enabled by default;
  `-Daero.smoothlight.cache=false` is the rollback. Selecting a different
  client-world identity immediately clears the previous world's entries, so
  default-on keys cannot retain an unloaded world. Worldline M781 independently
  compiled the pinned production cache with Java 8 and verified the absent,
  explicit-false, and explicit-true property arms in six fresh JVMs, same-world
  array reuse, and two-way world-switch eviction. Catalog:
  `aero.render.smooth-light-resolved-cache` (active, default on).
- **Morph weights in parallel arrays (morph-weight-arrays).**
  `Aero_MorphState` now stores (name, weight) pairs in parallel arrays and
  exposes indexed accessors (`activeCount`/`nameAt`/`weightAt`). Both
  runtimes' `drawGroupsMorph` read weights by index — no map iterator,
  `Map.Entry` views, or `Float` unboxing per morph-active draw.
  `getWeightsView()` becomes a per-call snapshot; validation, zero-eviction,
  and NBT round-trip semantics are unchanged. Catalog:
  `aero.skeletal.morph-weight-arrays` (active, default on).
- **Direct Three.js scene JSON loading.** `Aero_ThreeJsonLoader` imports baked,
  indexed or non-indexed `BufferGeometry` from `Object3D.toJSON()` and
  `BufferGeometry.toJSON()`, including hierarchy transforms, UVs, and named
  groups. `Aero_ModelSpec.mesh(...)` dispatches `.three.json` assets to it.
- **Repository-owned optimization catalog.** Thirty-two stable `aero.*`
  records now live under `worldline/optimizations/catalog`, with status, defaults,
  behavioral deltas, risks, rollback paths, source symbols, and evidence. The
  records use the neutral `worldline.optimization.v1` schema, but Aero owns
  their implementation details and source of truth.
- **Source-wide optimization audit.** Reconciled production code, property
  literals, explicit performance intent, changelog evidence, and performance
  commits against the catalog. Twelve missing decisions expand the inventory
  from 32 to 44 records: eleven active always-on or opt-in implementations and
  the retired coarse raycast-occlusion path. Added
  `tools/optimization-catalog/Audit.java` as a failing source-drift gate.
- **IK scratch scope corrected.** The catalog now states that input-array
  reuse is implemented by the StationAPI renderer; the ModLoader renderer
  still allocates those inputs per call and remains a known platform gap.

### Fixed
- **Animation budget tests now configure the policy they exercise.** Runtime
  defaults to unlimited admission (`aero.maxAnimatedBE=-1`) and hard-cap mode;
  the five finite-budget tests had retained assumptions from the earlier
  `128` default and therefore never entered their target branches. The test
  class now selects `128` plus reserve-enabled mode before the budget class is
  initialized, without changing production defaults.

### Removed
- **`aero.modellib.mixin.WorldSaveSpikeMixin` is gone from the published
  lib.** Cancelling vanilla world saves had no business in a model-rendering
  library — the only purpose was a benchmark convenience flag
  (`-Daero.benchmark.skipNonForcedSaves`). The same mixin lives in the test
  mod now (`aero.modellib.test.mixin.WorldSaveSpikeMixin`), so the dev
  benchmark flag still works when running through `runClient` / `runClientDev`,
  but the published jar no longer ships a save-cancelling mixin.
- **Diagnostic-only frame-stage hooks moved out of the lib.** The lib's
  `MinecraftFrameStageMixin` previously injected into `Minecraft.tick TAIL`
  and `renderProfilerChart HEAD/TAIL` purely to populate the spike log's
  per-stage breakdown. Those three hooks moved to
  `aero.modellib.test.mixin.MinecraftFrameSpikeMixin` in the test mod. The
  lib's mixin keeps only the three hooks that drive production opt-in
  features (animation tick budget, frame pacer, frame-pacer adaptive
  backoff). When the test mod isn't loaded and `-Daero.spikelog=true` is on,
  the spike log line just shows `clientTickMs=0` / `profilerChartMs=0` —
  zero crash, slightly degraded breakdown.

### Changed
- Relocated optimization metadata to `worldline/optimizations/` and reserved
  `worldline/extensions/` for Worldline extension manifests.
- **`runClient` task is now bare ("modpack user with default Loom JVM"
  simulation).** The previous `runClient` quietly bumped heap to 4 GB and
  cancelled vanilla autosave so dev sessions felt smoother — that hid GC
  spikes and autosave hitches that real users actually hit. The bumped /
  skip-saves combination moved to the new `runClientDev` task. Use
  `runClient` to validate end-user perception, `runClientDev` to focus on
  perf signal without dev-JVM noise.

### Changed (breaking — pre-1.0 internal restructure, no external consumers yet)
- **`core/aero/modellib/` split into 6 subpackages** for navigability. The
  flat package with 52 `Aero_*` classes was a navigability red flag; classes
  are now grouped by responsibility. All `import aero.modellib.<Class>;` in
  consumer code becomes `import aero.modellib.<sub>.<Class>;`. New layout:
  - `aero.modellib.animation` — clip / definition / bundle / loader /
    playback / spec / layer / stack / state-router / predicate / LUT-config /
    pose-resolver / event-listener / event-router / easing
  - `aero.modellib.animation.graph` — graph nodes (`AnimationGraph`,
    `Graph{Additive,Blend1D,Clip,}Node`, `GraphParams`)
  - `aero.modellib.skeletal` — bone FK / IK / morph / quaternion / procedural
    pose / bone render pose / bone page lists / CCD solver
  - `aero.modellib.model` — `MeshModel`, `MeshBlendMode`, `JsonModel`,
    `JsonModelLoader`, `ObjLoader`, `ModelSpec`
  - `aero.modellib.render` — render LOD / culling / budgets / load governor /
    options / entity transform / `CellRenderableBE` interface
  - `aero.modellib.util` — `Profiler`, `SoundCoalesce`, `PerfConfig`
- Promoted several package-private members to `public` that were already
  cross-class API in spirit: `Aero_AnimationBundle.pivotOrZero`,
  `Aero_AnimationClip.boneNames` + `sample{Rot,Pos,Scl,UvOffset,UvScale}Into`
  (cursor overloads), `Aero_BoneRenderPose.{reset,setPivot}`,
  `Aero_BonePageLists` constructor, `Aero_AnimationState` constructor (all
  3 platform variants — modloader, stationapi, test stub),
  `Aero_AnimationLoader.{loadFromString,cacheSize}`,
  `Aero_{Json,Obj}Loader.cacheSize`, `Aero_ObjLoader.parseObjForTest`,
  `Aero_AnimationPoseResolver` and its static `resolve{Clip,Stack}` methods.

### Fixed
- **Chunk-bake registry pre-warm.** `Aero_MeshChunkBaker` now exposes
  `prewarmAll()` and `Aero_RenderDistance.beginRenderFrame()` calls it
  idempotently until every registered entry is baked or has hit a permanent
  bake failure. The previous lazy bake landed mid `BlockRenderManager.render`
  during chunk rebuild — the first chunk-compile to encounter a registered
  block paid for `float[4][][]` + `float[15]` per triangle on the same stack
  as vanilla's Tessellator buffers. The pre-warm pass moves that allocation
  cost out of chunk compile. Atlas-not-ready resolves are deferred (no
  poison) so the pass converges naturally on the first few render frames
  after world load. No consumer changes required.

- **IK chain solver no longer allocates per chain per frame.**
  `Aero_MeshRenderer.runIkChains` now reuses static scratch buffers for the
  `target` (`float[3]`), `boneIdx` (`int[]`) and `pivots` (`float[][]`)
  inputs. `boneIdx` and `pivots` use a same-size cache: chains of identical
  length reuse the buffer; chain-length changes realloc once and steady
  state returns to zero allocations. The CCD solver consumes the inputs
  synchronously and does not retain references, so reuse is safe.

- **PalettedContainer cache is now truly opt-in.** The StationAPI
  `PalettedContainerCacheMixin` is no longer applied when both
  `-Daero.palettedcache` and `-Daero.palettedcache.chunkScope` are off.
  Previously the cache logic returned immediately, but the hot
  `PalettedContainer.get(int)` injection still allocated
  `CallbackInfoReturnable` for every block-state read. Dense tower JFRs showed
  this as the dominant heap churn and FPS degradation source. Default runs now
  skip the mixin entirely; the global and chunk-scoped A/B flags still opt in.

- **MEGA stress terrain no longer dominates the BE benchmark by default.**
  The `-Daero.mega=true` showcase now builds sparse support pads/cross-walks
  instead of full 16×16 cobblestone slabs on every floor. The old solid terrain
  path is still available with `-Daero.mega.solidTerrain=true` when we
  intentionally want to reproduce vanilla `WorldRenderer.renderChunks` /
  driver display-list stalls.

- **StationAPI model texture binds can now avoid per-frame `getTextureId`
  allocation.** Added `Aero_TextureBinder.bind(path)` and migrated the dense
  showcase renderers plus internal batch/cell-page texture binds to the cached
  path. This targets the old rhythmic heap-growth / GC pressure seen near
  large BE towers, where resolving the same model texture string for every
  block entity could dominate allocation samples.

- **BE cell tracking avoids full per-frame revalidation from
  `distanceFrom()`.** `Aero_RenderDistanceBlockEntity` still performs full
  `Aero_BECellIndex.track(...)` validation during tick/lifecycle hooks, but
  render-distance checks now only reattach when the BE changes world or block
  position. This cuts repeated state-hash and cell-index work from the vanilla
  BE dispatcher hot path in dense towers.

- **MEGA spike isolation can now remove benchmark-only autosave/event
  noise.** The StationAPI test mod disables MEGA keyframe side effects by
  default unless `-Daero.mega.sideEffects=true` is set, and the opt-in
  `-Daero.benchmark.skipNonForcedSaves=true` flag cancels non-forced world
  saves during benchmark runs. `runClientMegaSpikeClean` combines those with
  ZGC/C1 and no JFR for visual spike checks.

- **Managed Cell Pages no longer consume the animation budget inside
  `distanceFrom()`.** The distance gate now uses unbudgeted LOD when deciding
  whether a BE is truly far/static enough to skip its individual renderer.
  Budget downgrades stay in the renderer path, so nearby animated blocks can
  still tick and draw correctly instead of getting locked into at-rest pages.

- **UV-animated OBJ groups use the exact Tessellator path again.** The
  animated batcher now detects non-identity `uv_offset` / `uv_scale` poses and
  drains those instances through the unbatched renderer; bone-page display
  lists also fall back per group when UV animation is active. This restores
  conveyor-belt scroll and spell/rune channel effects while preserving the
  optimized paths for ordinary rigid bone animation.

- **UV-channel clips are excluded from animated fast paths up front.** Clips
  now expose `hasUvAnimation()`, letting StationAPI bypass the animated
  batcher and bone-page lists before any frame samples happen. This avoids the
  first-frame/identity-pose case where a UV-only clip could still enter a
  display-list path and appear frozen.

- **Added `Aero_MeshRenderer.renderAnimatedPrecise(...)`.** This StationAPI
  entry point restores the pre-optimization animated Tessellator path for
  UV-sensitive clips. The conveyor and spell/rune showcases now bind their
  texture before direct animated rendering again, matching the old behavior
  that predates the animated batcher queue.

- **Animated batcher opt-out/fallback paths bind their texture.** Disabling
  `-Daero.animatedbatch` or falling back from a non-batchable queued model no
  longer renders against whichever atlas happened to be bound previously.

### Changed
- **Bone-page display lists for rigid animated groups.** Animated renders can
  now cache static geometry plus eligible named groups as per-bone display-list
  pages and replay them under the current bone matrix. This covers nested
  skeleton / procedural / IK / graph / stack paths that do not fit the flat
  animated batcher. Toggle with `-Daero.bonepages=false`; tune the cutoff with
  `-Daero.bonepages.minTris=N` (default `24`). Morph-active renders still use
  the Tessellator fallback.

- **Per-frame animation render budget.** StationAPI LOD now admits only a
  bounded number of animated renders per visual frame and downgrades overflow
  to the at-rest display-list path. This is the first Cell Pages step for dense
  BE scenes where culling cannot help because everything is on screen. Toggle
  with `-Daero.animBudget=false`; tune with `-Daero.maxAnimatedBE=N` (default
  `128`, `-1` = unlimited). Admission is priority-aware: very near / large
  objects can use a small critical reserve, while tiny/low-priority objects
  stop consuming budget early. Tune with `-Daero.animBudget.criticalPx=N`,
  `midPx`, `lowPx`, `nearBlocks`, `criticalExtra`,
  `hysteresisFrames`, and `hysteresisExtra`.

- **BE cell index scaffold.** StationAPI now tracks
  `Aero_RenderDistanceBlockEntity` instances in small world cells, with dirty
  flags for state/orientation/animation-page eligibility and a visible-cell
  snapshot backed by `Aero_ChunkVisibility`. The base class registers on
  tick/render, unregisters on `markRemoved()`, and reattaches on
  `cancelRemoval()`. This does not replace individual BE rendering yet; it
  prepares the Cell Pages renderer without changing visuals. Toggle with
  `-Daero.becell=false`; tune cell size with `-Daero.becell.size=N` (default
  `8`, clamped `1..32`).

- **At-rest BE Cell Pages, opt-in API.** StationAPI renderers can now call
  `Aero_BECellRenderer.queueAtRest(...)` for static / LOD-overflow mesh BEs.
  The flush builds per-cell display-list pages that replay each model's
  existing at-rest display lists under local transforms, with direct-render
  fallback for small groups, translucent options, missing camera cache, or GL
  list failure. Toggle with `-Daero.becell.pages=false`; tune with
  `-Daero.becell.minInstances=N`, `-Daero.becell.pageTtlFrames=N`, and
  `-Daero.becell.rebuildsPerFrame=N` (`8` default, `-1` unlimited).

- **Managed BE Cell Pages before individual renderer dispatch.** StationAPI
  BEs can implement `Aero_CellPageRenderableBE` to expose model, texture,
  brightness, radius, animation distance and render options to the distance
  gate. When LOD resolves to STATIC, `Aero_RenderDistanceBlockEntity` queues
  the BE into the cell page and returns out-of-range to vanilla, so the
  individual BER is skipped. Animated LOD still flows through the renderer
  and existing `Aero_AnimatedBatcher`. Toggle the skip path with
  `-Daero.becell.skipIndividual=false`. Cell page timings now appear in
  `Aero_Profiler` as `aero.becell.flush`, `compile`, `call`, and `direct`.

- **High-memory performance preset and display-list guardrails.**
  `-Daero.perf.memory=high` now raises conservative cache defaults for heavy
  BE scenes: Cell Page TTL becomes `1800`, Cell Page rebuild budget becomes
  `16`, animation LUT defaults on, runtime model caches become unbounded, and
  StationAPI display lists get live-count caps/warnings through
  `Aero_DisplayListBudget`. Explicit `-D` flags still override the preset.

- **Cell Page fragmentation controls.** StationAPI Cell Pages can now reduce
  churn caused by lighting and unstable membership order. New opt-in flags:
  `-Daero.becell.perInstanceLight=true` stores per-BE brightness in the page
  display list, `-Daero.becell.lightBuckets=N` quantizes page-key brightness
  when per-instance light is off, `-Daero.becell.stableMembership=true` sorts
  page members by stable world-position key before hashing, and
  `-Daero.becell.maxCachedPages=N` caps cached pages with LRU-style eviction.
  Defaults remain conservative and visually exact.

- **Render-thread prewarm queue.** `Aero_Prewarm.enqueueModel(model)` lets
  consumers gradually compile at-rest model lists and bone pages on render
  frames instead of paying the full cost on first visibility. The renderer now
  also discovers and prioritizes models automatically. It is opt-in via
  `-Daero.prewarm=true` or enabled by the high-memory preset; tune with
  `-Daero.prewarm.perFrame=N`, `-Daero.prewarm.maxMsPerFrame=N`, and
  `-Daero.prewarm.maxQueued=N`.

- **Benchmark tasks accept extra Aero JVM flags.** The StationAPI test mod's
  `runClient*` Gradle tasks now accept `-PaeroJvmArgs="..."`, making A/B runs
  such as `-Daero.becell.pages=false` or `-Daero.perf.memory=high` possible
  without editing `build.gradle`.

- **Benchmark stress tasks reserve a larger initial heap.** The dense-tower
  spike logger captured frames where committed heap stayed around `240 MB`
  despite a much larger max heap, producing rhythmic F3 memory climb/drop and
  GC pauses near heavy BE scenes. StationAPI benchmark/stress tasks that use
  the 4 GB heap now also pass `-Xms2G`, reducing heap growth/collection churn
  during visual stress runs.

- **Dense-tower spike diagnostics now separate new render paths from legacy
  stalls.** The opt-in spike logger records per-frame CPU time plus
  `renderWorld`, Aero prep, entity dispatch, Aero flush, chunk compile, and
  terrain render timings. Captured `196-272 ms` spikes showed BPDL, Cell
  Pages, animation budget, batch flush, and Aero chunk visibility all below
  `1-2 ms` with no Cell Page rebuilds, shifting the remaining investigation to
  older chunk/terrain/driver stalls rather than the new optimization paths.

- **Reduced near-tower GC spikes from render hot-path allocation.** The
  StationAPI animation budget now uses a primitive long-key path for
  per-frame hysteresis decisions, avoiding `Long`/`Integer` boxing for every
  animated BE near dense towers. Animated batches and Cell Page queues also use
  reusable lookup keys instead of allocating temporary render keys for every
  queued BE. This targets the rhythmic F3 memory climb/drop seen only near
  heavy BE stress scenes.

- **PERF roadmap implementation pass.** Animated batches now sort by a
  composite render-state key instead of texture alone; OBJ load can opt into
  conservative hidden-face removal with `-Daero.obj.cullhidden=true`; animated
  mesh renderers can opt into skeletal-chain depth LOD with
  `-Daero.skeletalLod=true`; animation tick LOD has a motion-aware stride
  helper plus `shouldTickAnimation(velocityBlocksPerTick)` overloads; mesh
  specs can declare static/at-rest model LODs via `meshLod(...)`; and the
  StationAPI PalettedContainer cache gained a safer chunk-rebuild scoped mode
  via `-Daero.palettedcache.chunkScope=true`.

- **Smart LOD Y-bias is enabled by default.** `Aero_RenderDistanceCulling`
  now computes LOD and `shouldRenderRelative(...)` distance with vertical
  distance weighted by `-Daero.ybias=N` (default `2.0`). Set
  `-Daero.ybias=1.0` for isotropic euclidean behavior, or
  `-Daero.smartlod=false` to disable the Smart LOD path entirely.

- **`Aero_OcclusionCull` is default-ON again.** Opt out with
  `-Daero.occlusioncull=false`. The per-BE cache now uses hysteresis:
  currently visible BEs re-check after 4 calls, currently occluded BEs
  re-check after 12 calls. This keeps jump-height camera jitter from
  flipping BEs visible/hidden every few frames.

- **Vertical occlusion sample for towers.** When `|dy| > 8`, occlusion
  adds a vertical-only DDA sample from the camera Y at the BE's X/Z up
  to the BE block. If that vertical column crosses 4+ opaque blocks, the
  BE is treated as occluded even when the oblique camera-to-BE line sees
  through side openings.

### Algorithmic micro-pass (v3.x.1)
After the cull-stack landed, a hot-path audit surfaced five algorithmic
hotspots. All five landed together — combined effect on the MEGA
torture world (576 animated BEs/chunk) **doubled the looking-at-tower
FPS** with no visible behaviour change.

- **`Aero_AnimationBundle.resolvePivotsFor(clip)` — pre-resolved pivot
  array per (clip, bundle).** The animated-render hot loop in
  `Aero_MeshRenderer.renderAnimatedInternal` and `renderAnimatedBatch`
  used to call `bundle.pivotOrZero(boneName)` once per clip-bone per
  BE per frame (~345k `HashMap.get`/s under MEGA). Now the bundle
  memoises a `float[][]` keyed by clip identity — array dereference
  replaces the map lookup. Single-entry cache, invalidated when the
  state machine swaps clips.

- **`Aero_ChunkVisibility` last-hit cache.** The BE dispatcher iterates
  in chunk-clustered order, so 100+ BEs in a single chunk would each
  hit `LongOpenHashSet.contains` for the same chunk key. Added a 1-entry
  `(chunkX, chunkZ, result)` cache reset by every `snapshot()`. Hit-rate
  ≈ N-1 / N where N is BEs/chunk.

- **Cone-angle aspect cache.** `refreshCameraForwardFromPlayer()` ran
  `Math.atan(Math.tan(35°)·aspect)` every frame even when display
  dimensions had not changed. Cache `(displayWidth, displayHeight,
  coneHalfDeg)` and recompute only on resize.

- **`Aero_AnimationClip.indexOfBone` reference-equality fast path.**
  Stack/blended sample paths call `indexOfBone(boneName)` per layer
  per channel — usually with the same `boneName` reference for several
  consecutive calls. Added a 1-entry `(lastBoneNameRef, lastIdx)`
  cache compared with `==`, so the follow-up calls skip the `HashMap`.

- **`Aero_AnimationPlayback.fireEvents` binary-search lower bound.**
  Events are sorted by time at clip construction. The per-tick window
  scan was O(n); now does `lowerBound(events, fromBound, includeFrom)`
  binary search and breaks when crossing the upper bound. O(log n + k)
  where k is matches in the window.

### Why the gain compounds
Each fix individually trims a small amount of work, but they all live
inside the same per-frame nested loop (`for each visible BE: for each
bone: ...`). The product N × M × cycles-saved-per-bone is what shows
up at the FPS counter — that's why algorithmic wins beat the much
larger-looking constant-factor ones at this scale.

## [3.0.0] — 2026-04-29

The "máxima performance" release. Targets 500+ FPS sustained on the
StationAPI stress-test world (3×3 motor grid + 14 showcases per qualifying
chunk). Three culling layers stacked, plus chunk-baked static rendering
from 0.2.5 carried forward. Major version bump signals the architectural
shift from "BlockEntity dispatch with cone heuristic" to
"BlockEntity dispatch with real frustum + coarse occlusion + chunk-bake
opt-in".

### Added
- **`Aero_Frustum6Plane`** — wraps Beta 1.7.3's vanilla
  {@code Frustum.getInstance() / FrustumData.intersects(...)}, the same
  6-plane AABB test vanilla uses to cull world chunks. Replaces the cone
  heuristic for tight, projection-correct culling. The {@code FrustumData}
  is computed by vanilla once per frame during chunk culling; we cache the
  reference and reuse it for every BE / entity visibility query that
  frame. Cone stays in the path as a forgiving back-up (see "How it
  composes" below). Toggle: {@code -Daero.frustum6=false}.

- **`Aero_OcclusionCull`** — coarse occlusion via voxel-step DDA. Walks
  the camera-to-BE line, counting opaque-block crossings via
  {@code Block.BLOCKS_OPAQUE[id]} (boolean array, O(1), no Block instance
  dereference). 4+ opaque crossings → "occluded" → BE skipped entirely.
  Catches the underground spawn case where 200 BEs above the surface keep
  rendering through 8 blocks of dirt. False-cull risk in glass corridors /
  windowed geometry is mitigated by requiring multiple opaque crossings.
  Toggle: {@code -Daero.occlusioncull=false}.

- **Camera world-position cache** in {@code Aero_RenderDistance} —
  populated alongside the per-frame forward-vector update. Lets the
  6-plane and occlusion paths reconstruct world coords from the
  camera-relative {@code (dx, dy, dz)} that {@code BlockEntityRenderer.render}
  receives, without each BER having to plumb the camera world position.

### Changed
- **`Aero_RenderDistance.shouldRenderRelative` / `lodRelative`**: now
  layered cull. Distance → cone → 6-plane. Each layer is independent
  and toggleable, but in production all three run. The 6-plane test
  catches narrow-FOV / wide-aspect cases the cone over-includes.

- **`Aero_RenderDistance.blockEntityDistanceFrom`** (the chokepoint
  used by {@code Aero_RenderDistanceBlockEntity.distanceFrom}, which
  ALL showcase BEs delegate to): now applies cone + 6-plane + occlusion
  in series. {@code distanceFrom} returns {@code Double.POSITIVE_INFINITY}
  for any BE failing any of the three. Vanilla's
  {@code BlockEntityRenderDispatcher} treats Infinity as out-of-range
  and skips the BER call entirely — so an occluded BE pays zero cost
  past the three cull tests.

- **`Aero_RenderDistanceBlockEntity.shouldTickAnimation`**: occluded
  BEs freeze on their last pose. Saves keyframe-interpolation CPU for
  entities the player can't see anyway. The pose snaps to the moment
  occlusion ended — invisible to the player on showcase animations,
  but downstream mods doing gameplay-critical timing should override
  {@code shouldTickAnimation()} to opt out.

### How it composes
Three cull layers in order of cost (cheapest first):

1. **Distance** — squared-distance check. Already present pre-3.0.
2. **Cone** ({@code Aero_FrustumCull}) — 1 dot product. Catches behind-camera
   bulk. Forgiving by design (75° floor, 8-block behind tolerance).
3. **6-plane frustum** ({@code Aero_Frustum6Plane}) — vanilla
   {@code FrustumData.intersects} call. Tight, projection-correct.
4. **Occlusion** ({@code Aero_OcclusionCull}) — DDA block lookups along
   camera→BE line, plus the v3.x vertical sample for tower cases. Catches
   "in frustum but behind solid terrain".

The order isn't accidental: each subsequent test is more expensive but
strictly more selective, so most BEs reject early. In stress-test
profiling the occlusion check fires for ~20-30% of BEs in surface
worlds, ~80%+ in cave / dungeon scenarios.

### Why we didn't go further (yet — v3.1 candidates)
- **BE batching for shared models** (motor grid → 1 draw cycle) was
  scoped but skipped — the cull layers above already eliminate enough
  per-frame work that batching is no longer the dominant bottleneck.
  Will revisit if profile shows otherwise on user hardware.
- **Real chunk-visibility query** from {@code WorldRenderer.cullChunks} —
  could replace our occlusion raycast with vanilla's per-chunk
  {@code inFrustum} flag. Not done because (a) the raycast already wins
  the underground case at acceptable cost, and (b) the chunk flag
  doesn't capture sub-chunk occlusion (BE behind a wall in the same
  chunk as the camera).

### Measured (dev workstation, RTX 3070 / R7-5800X / 1440p windowed)
- **Vanilla terrain, no showcases visible**: 200 FPS (vanilla
  baseline)
- **Stress mode, motors + showcases visible**: 160 FPS sustained
  (vs ~80-120 pre-3.0 baseline = ~2× sustained gain)
- **Underground (-10 blocks), all surface BEs occluded**: **800 FPS**
  — occlusion cull eliminates the entire BE render pass for
  entities behind multiple opaque blocks. The 4-7× boost is the proof
  the cull layer is doing real work; FPS recovers to a clean
  vanilla baseline because the BE dispatch never fires.
- **Lag spikes**: resolved by the per-BE occlusion cache (see
  "Spike fixes" below). Pre-3.0 had a sub-version that landed
  the cull stack but produced 80→160 FPS oscillation — the
  uncached 8-step DDA per BE per frame compounded with chunk-
  boundary getBlockId throws.

### Spike fixes (mid-3.0 iteration)
- **Y-bounds early-out** in {@code Aero_OcclusionCull}: skips
  block lookup when sample Y is outside the world (0..127).
  Cuts the worst-case JNI cost when the ray walks above/below
  the world.
- **No try/catch around getBlockId**: relied on the BLOCKS_OPAQUE
  bounds check + Y-bounds for safety. Java exception throw was
  ~100µs each on chunk-boundary samples — noise at 200 BEs.
- **Per-BE occlusion cache** on
  {@code Aero_RenderDistanceBlockEntity}: 4-frame stride between
  recomputes. Cuts 75% of the voxel-step lookups; staleness ≤
  ~67 ms at 60 FPS, invisible to the player. Subclasses get this
  automatically; non-subclass BEs fall back to uncached path.
- **Sign fix in shouldTickAnimation**: the {@code dx, dy, dz}
  passed to occlusion was {@code player − BE} instead of the
  BER-convention {@code BE − camera}. Tick-LOD occlusion was
  raycasting from the wrong origin. Fixed.

### Reproduce
{@code runClientBenchmark -Pbench=N -Daero.stresstest=true}.
Drop {@code run/aero-benchmark.jfr} into JMC. Compare:
- {@code BlockEntityRenderDispatcher.render} — should drop
  ~50% in stress mode, ~95% underground
- {@code Aero_OcclusionCull.isOccluded} — should be present
  but small (cache + Y-bounds keep it cheap)
- {@code Aero_MeshRenderer.renderModelAtRest} — chunks of this
  vanish for occluded scenes

### PalettedContainer cache (added then reverted to opt-in — full story)
JFR profile during world-entry showed 34% of CPU samples concentrated in
{@code PalettedContainer.get(int)} (StationAPI's chunk block-state
storage). Theory: a small per-instance cache could shave that down to
~17% by exploiting spatial locality in chunk meshing's neighbor reads.

Implemented as `PalettedContainerCacheMixin` — 4-entry FIFO cache,
flush on `Data<T>` swap, gated through `AeroMixinPlugin` for clean
startup logs and graceful degradation when StationAPI is absent /
refactored.

**Result: net regression in steady-state benchmark.** Stress mode
dropped from **160 → 120-130 FPS sustained** with the cache on.
Diagnosis: `@Inject` HEAD/RETURN add ~30 ns of dispatch overhead
per call. With millions of calls per frame in steady-state, the
overhead × volume outweighs the savings × hit-rate. The cache *is*
faster during chunk-meshing burst (where call rate spikes), but
that's a one-time cost; steady-state pays it forever.

Decision: **default-OFF** in 3.0. Opt in with
`-Daero.palettedcache=true` to A/B test on your scene. The mixin
infrastructure (plugin + diagnostic logs + getResource probe to
avoid `MixinTargetAlreadyLoadedException`) stays in place for a
future smarter implementation.

Future direction: cache at the **caller** level (chunk-mesh entry
point, e.g. `WorldRenderer.compileChunks`) instead of the per-call
get site. Pay the overhead once per chunk build instead of once per
neighbor lookup. Tracked as 3.x candidate.

### Animated BE batching (mid-3.0 iteration)
**`Aero_AnimatedBatcher`** — per-frame collector that coalesces
multiple animated BE renders sharing the same `Aero_MeshModel` into
a single `Tessellator` cycle per bone. BERs replace
`Aero_MeshRenderer.renderAnimated(...)` with
`Aero_AnimatedBatcher.queueAnimated(...)`. At end-of-entity-pass
(via `WorldRendererBatchFlushMixin` TAIL on
`renderEntities(Vec3d, Culler, F)V`), `Aero_MeshRenderer.renderAnimatedBatch`
drains the batch in one Tessellator session per bone with per-vertex
CPU matrix transforms (`T(-pivot) → S → Rx → Ry → Rz → T(pivot+offset)`
inlined per vertex, identical to `applyPose` but composed in CPU
instead of via GL matrix stack).

Constraints (first cut): single-bone-depth models only — flat
skeletons batch cleanly, nested skeletons fall back to per-instance
rendering at flush time. Procedural pose / IK / morph paths route
to the non-batched overload. Toggle: `-Daero.animatedbatch=false`.

Bug fix mid-iteration: named groups present in the OBJ but **not** in
the active clip's `boneNames` (e.g. MegaCrusher's `wall_*`, `chamber`,
`pillar_*` — static body parts whose only animated subgroups are the
turbines / shredders) were being skipped with `continue` in the
batched render loop because their resolved pose was null. Manifested
visually as "fans floating in air" — only the named-bone parts
rendered. Fixed by adding `emitBoneInstanceBatchedRest` which mirrors
the unbatched path's behavior of drawing those groups at their
model-space rest position when `applyPoseChain` returns null.

BERs migrated in this iteration:
- Motor, Pump, Conveyor, Crystal, CrystalChaos
- EasingShowcase 1/2/3, SpellCircle, AnimatedMegaModel

Not migrated (incompatible with the first-cut batcher):
- Turret IK, PlasmaCrystal (procedural pose), MorphCrystal (morph state),
  GraphPowered (animation graph), MegaModel (uses display-list cache
  for static path already)

### Updated measurement (factory test, post-batcher + body-fix)
- **Looking away from tower (tiny render-distance)**: 2000+ FPS
- **Looking at tower (tiny)**: stable 700+ FPS
- **Looking at tower (far)**: 250-300+ FPS
- **JIT warmup**: first ~60 s shows 120-130 FPS during C2
  compilation; sustained values above are post-warmup.

### Known follow-ups (3.x candidates)
- **World-entry trava** — vanilla chunk meshing is single-threaded
  and synchronous; loading 25-100 chunks at world-entry stalls the
  main thread. JFR confirms 47% combined in `PalettedContainer.get` +
  HashMap traversals during chunk meshing. modellib contributes
  <1%. Killing this requires either upstream changes to StationAPI
  or off-thread chunk meshing — candidate for 4.0.
- **Nested-skeleton batching** — current first cut rejects multi-bone
  ancestor chains and drains them through the unbatched path.
  Composing parent poses in CPU is doable; deferred until a
  user-mod hits the limit.
- **6-plane frustum default-on** — currently opt-in via
  `-Daero.frustum6=true`. Lazy `Frustum.getInstance()` fetch reads
  stale planes in the current capture timing. 3.x will mixin into
  `WorldRenderer.cullChunks` TAIL to grab the `FrustumData`
  reference at a known-good moment.

## [0.2.5] — 2026-04-29

Render-perf pass for the StationAPI runtime — third response to calmilamsy's
review ("Maybe overriding some core rendering methods with StationAPI ones
can give better results"). The architectural shift in this release is the
new chunk-bake path; the smaller fixes in §A are bundled in to clean the
baseline before measuring it.

### Added
- **`Aero_MeshChunkBaker`** — public registry that lets a static block's
  mesh be emitted directly into the chunk vertex buffer at chunk-rebuild
  time. Per-frame cost for registered blocks goes to **zero** (vs. the
  ~800 GL/JNI calls per frame the old `BlockEntity` dispatch produced
  for the stress-test world's ~200 visible BEs). v0.2.5 first cut
  supports a single terrain-atlas tile per registered block — model UVs
  are remapped into the tile at registration time, no per-frame UV
  math. Animated / morph / IK paths stay on the existing `BlockEntity`
  renderer; chunk-bake is opt-in for genuinely static geometry only.

- **`BlockRenderManagerChunkBakeMixin`** — Mixin priority 500
  (lower-than-default = applied first → runs first at HEAD) on
  `BlockRenderManager.render(Block, int, int, int)`. Cancels with
  `setReturnValue(true)` when the block is registered with the chunk
  baker, before Arsenic's mixin or vanilla dispatch get a chance to
  walk the BakedModel pipeline. The implementation is a single
  `Aero_MeshChunkBaker.get(blockId) != null` branch — no allocation,
  no map lookup, just an array index. For unregistered blocks the
  mixin falls through cleanly.

- **`ChunkBakedShowcaseBlock` + `ChunkBakedShowcase.obj`** — new test-mod
  showcase block (no BlockEntity), wired into the stress-mode worldgen
  as a 3×3 grid sitting next to the existing 3×3 motor grid. Lets the
  player see both paths side-by-side and lets a JFR snapshot count
  CPU time for "block X via BE renderer" vs. "block X via chunk
  emit" in the same frame budget. Existing showcases were left
  untouched — they remain regression coverage for the BE animated
  path.

### Why the architectural shift
The display-list cache from 0.2.3 cut the *per-call* cost of a static
BE render (4 `glCallList` vs. N `Tessellator.draw` per frame), but the
*number of calls* is still proportional to visible BEs. Vanilla
blocks dodge this entirely: their faces live in the chunk's pre-baked
vertex buffer and replay for free until the chunk is dirtied. Chunk-bake
puts modellib's static meshes in the same buffer — once at chunk
rebuild, replayed for free. That's what calmilamsy was pointing at:
the StationAPI render hook chain (Arsenic's mixin on `BlockRenderManager.render`)
already provides the entry point; we just needed our own mixin to
intercept it before Arsenic's BakedModel pipeline takes over.

### Why we didn't use `RendererAccess.registerRenderer`
The original plan was to register a `Renderer` plug-in via
`RendererAccess.INSTANCE.registerRenderer(...)`. That slot is owned by
Arsenic when the StationAPI stack includes `station-renderer-arsenic`
(which it almost always does), and `RendererAccess` rejects a second
registration. Going through Mixin instead leaves Arsenic alone and
lets both work — Arsenic for everything that has a `BakedModel`,
modellib's chunk-bake for blocks registered via `Aero_MeshChunkBaker`.

### Changed
- **`Aero_MorphState.isEmpty()`** is now O(1) — `set(name, 0f)` already
  removes the entry, so the previous "iterate values, unbox each Float,
  return false on first non-zero" defense was unnecessary work. The
  HashMap-emptiness check is the only path now. The morph showcase is
  the only stress-test BE to hit this path, but it's the kind of
  hygiene that compounds when a downstream mod attaches morphs to many
  entities.
- **`Aero_MeshRenderer.drawGroupsMorph`** scratch arrays
  (`activeTargets`, `activeWeights`) are now pooled in static fields
  (`SCRATCH_MORPH_TARGETS`, `SCRATCH_MORPH_WEIGHTS`) that grow on
  demand. Previous code allocated two arrays per render of a
  morph-active block — fine for the current showcase (one BE) but
  noise as soon as a downstream mod uses morphs broadly. Both runtimes
  (modloader + stationapi) updated.

### Skipped (planned but not done)
- **`Aero_AnimationPoseResolver.lookupStackPivotInto` pre-merge** — the
  v0.2.5 plan flagged this as a hot path (linear scan across stack
  layers per bone per render). After grep through the test mod and
  every consumer, no showcase BE actually uses
  `Aero_AnimationStack` — every animated showcase calls `renderAnimated(model, state, ...)`
  with a single `Aero_AnimationPlayback`, which goes through `resolveClip`
  (already memoized via `model.boneRefsFor`). The "10 000 String comparisons
  per frame" estimate was hypothetical. Skipped to keep the diff minimal
  and avoid touching code without a benchmarked motivation.

### Methodology unchanged
Same caveat as 0.2.3 / 0.2.4: numbers vary per workstation. Reproduce
with `runClientBenchmark -Pbench=N -Daero.stresstest=true`, drop the
JFR (`run/aero-benchmark.jfr`) into JMC. Compare hot-method self-time
on `BlockRenderManager.render` (chunk-bake should hide the
chunk-baked block from this profile entirely; only animated BEs
should remain).

## [0.2.4] — 2026-04-28

Documentation honesty pass — second response to calmilamsy's review.
No runtime behavior changes; the perf paths from 0.2.3 are unchanged.
What changed is what we say about them.

### Changed
- **CHANGELOG perf claims** are now bracketed by an explicit
  methodology block (workstation, GPU, view distance, repro task) and
  the per-feature `33.6% → 7.5%` / `45 ms → 34 ms` figures were
  dropped in favor of describing the change and pointing the reader
  at `runClientBenchmark -Pbench=N` to verify on their own hardware.
  Numbers without an attached artifact were noise; the artifact you
  can now produce locally is the artifact.
- **`Aero_FrustumCull` class javadoc** now opens by stating *this is
  not a real view-frustum check* — it's a forward-vector cone with a
  fixed 20° margin and 75° floor. The constants are explicitly
  frozen; the documented escape hatch for false-cull cases is the
  `-Daero.frustumcull=false` toggle, not a wider cone.
- **`Aero_MeshRenderer.disposeModel` javadoc** dropped the "shutdown
  hook" framing (Beta has no stable client-shutdown hook for mods,
  and the GL driver releases all lists on context destruction). The
  three real call sites — resource-pack reload, dev-time hot-swap,
  tooling/CI — are now spelled out instead.
- **`Aero_AnimationTickLOD.recommendedAnimatedDistance` javadoc**
  now says the per-tier numbers are an empirical heuristic from the
  stress-mode dev workstation, not a derivation, and points scenes
  with different cost mixes to the lower-level `tickStride(...)`
  overload.

### Fixed
- 210 unit tests still green on the modloader runtime.

External feedback pass — addresses calmilamsy's review (April 2026) on
build script structure and rendering performance.

> **About the perf claims below.** Numbers in this changelog are from
> a single development workstation (Windows 11 / RTX 3070 / R7-5800X /
> 1440p windowed) running `runClientBenchmark -Pbench=60` against the
> stress-mode worldgen (3×3 mega-model tower per qualifying chunk,
> animated LOD 256). They are reproducible — the exact `runClientBenchmark`
> task and stress worldgen ship in this repo — but they are not a
> claim about your hardware, your scene, or your driver. Run the
> benchmark, drop the JFR into JMC, and verify on your own setup
> before quoting these numbers anywhere.

### Added
- **Display-list cache** for `renderModelAtRest`: geometry + UV baked
  into 4 GL lists per model (one per brightness bucket); brightness ×
  tint applied via `glColor4f` outside. Replaces a per-frame
  Tessellator emission with 4 `glCallList` invocations. On the
  reference scene, `nglDrawArrays` dropped from the dominant samples
  in CPU profile to a small fraction; verify locally with
  `runClientBenchmark` if the number matters to you.
- **Aero_FrustumCull**: cheap, dot-product-based cone heuristic against
  the cached camera forward. **Not a real frustum check** — it's a wide
  cone whose half-angle is derived from MC's hardcoded 70° vertical FOV
  + window aspect, with a fixed 20° safety margin and 75° minimum
  half-angle. Catches the bulk of behind-camera + far-side-of-vision
  entities; doesn't catch entities outside the projection's near/far
  planes. Toggle off with `-Daero.frustumcull=false` if it culls too
  aggressively for your case.
- **Aero_AnimationTickLOD**: distance-tiered animation tick stride
  (1× / ½× / ¼× / skip beyond 256). `recommendedAnimatedDistance(viewDistance)`
  returns a stride radius scaled to the player's render-distance setting.
- **Adaptive LOD swap inside `renderAnimatedInternal`**: far entities
  auto-route to the at-rest display-list path. Threshold is
  `Aero_AnimationTickLOD.recommendedAnimatedDistance()` — same call the
  tick-LOD policy uses, so render-LOD and tick-LOD always agree on the
  switchover distance.
- **`Aero_RenderOptions.cullFaces(boolean)`**: opt-in `GL_CULL_FACE`
  for models with verified consistent triangle winding. Default off —
  many OBJ exporters produce inconsistent winding and silent
  invisibility is worse than the perf hit.
- **`Aero_MeshRenderer.disposeModel(model)`**: releases cached display
  lists via `glDeleteLists`. Beta has no stable client-shutdown hook
  for mods, so this is intended for tooling / hot-reload / model-swap
  workflows where the consumer controls model lifecycle. The driver
  releases all lists on context destruction anyway, so calling this on
  game exit is mostly a no-op.
- **`Aero_RenderDistanceBlockEntity.shouldTickAnimation()` /
  `Aero_RenderDistanceTileEntity.shouldTickAnimation()`**: drop-in
  tick-LOD test for BE / TE update methods. Uses the per-entity age
  counter for phase-stable thinning.
- **`runClientStress` gradle task**: `-Daero.stresstest=true`, every
  qualifying chunk gets the showcase + 3×3 mega-model tower;
  animated LOD bumped to 256 for max stress.
- **`runClientBenchmark -Pbench=N` gradle task**: same as stress, but
  auto-exits after `N` seconds via `Aero_TestProfilerHook` and dumps
  JFR to `run/aero-benchmark.jfr`. **Use this to verify any perf claim
  in this changelog on your own hardware** — open the JFR in JMC,
  sort by Self Time, and you'll have your own numbers in 60 seconds.

### Changed
- **`Aero_Profiler.start` is now zero-allocation**: start time stored on
  the cached `Section` object instead of a parallel `Map<String, Long>`
  (which was boxing `Long.valueOf(System.nanoTime())` per call). The
  hot-path allocation is gone; whether that translates to a visible
  GC-pause reduction depends on what else your scene allocates.
- **Build script**: composite build via `includeBuild('..')`; the test
  project's `modImplementation "dev.aerocoding:aero-model-lib:${mod_version}"`
  resolves through the composite instead of via `files('../build/libs/...jar')`.
- **`modRuntimeOnly`** for AMI / glass-networking / GCAPI; `downloadAMI`
  task removed. Pinned to mod versions built with the same Loom minor
  (AMI 1.9.0 → 1.6.0, GCAPI 3.2.5 → 3.2.2). modmenu has no Loom-1.9.2
  release, off the stack until a wider Loom bump.
- **`babric:fabric-loader:0.15.6-babric.1`** explicit pin — the upstream
  `net.fabricmc:fabric-loader` collides with StationAPI's transitive
  babric loader at runtime (`duplicate fabric loader classes`).
  `fabric.mod.json` `depends.fabricloader` lowered from `>=0.16.0` to
  `>=0.15.0` to accept the babric fork (Fabric's semver treats
  `-babric.1` as a pre-release, so `>=0.15.6` rejects it).

### Docs
- **README**: AI disclaimer at the top.
- **docs/DOC.md** § 15 Profiling: VisualVM walkthrough alongside JFR + JMC.
- **CHANGELOG.md** (this file): introduced.

## [0.2.1] — 2026-04-26

- Graph render path follow-up + 5 StationAPI test showcases.

## [0.2.0] — 2026-04-25

- Quaternion slerp on rotation channels.
- UV animation channels (`uv_offset`, `uv_scale`).
- Skeletal IK with CCD solver + hierarchical rendering.
- Morph targets (`format_version "1.1"`).
- Animation graph (Blend1D + Additive nodes).
- Five visual showcases per runtime.

## [0.1.0] — initial release

- Static + animated OBJ rendering on ModLoader and StationAPI.
- Strict format-1.0 `.anim.json` validation.
- Aero_AnimationStack (multi-layer animation).
- Aero_AnimationStateRouter (predicate-based state machine).
- Aero_RenderOptions (per-call tint / alpha / blend / depth).
- Aero_RenderDistance LOD (animated / static / culled).
- Locator-anchored sound + particle keyframe events.
- 33 easing curves + smooth state transitions.
- Multiplayer-ready (server/client side gating).
- Aero_Profiler (always-on, zero-cost when off).
- 210 pure-Java unit tests + JMH-style benchmark harness.
