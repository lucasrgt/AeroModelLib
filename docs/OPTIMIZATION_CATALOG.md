# Aero Optimization Catalog

This repository owns the canonical metadata for AeroModelLib optimizations.
The 51 records under `worldline/optimizations/catalog` describe Aero implementation
details, defaults, risks, rollback paths, source symbols, and evidence using
the neutral `worldline.optimization.v1` schema.

The initial import was performed at revision
`436d65b38c53346b465e5e793bd943177ebfaa32`; the source-wide reconciliation
was performed against `258a22bdbbd6102657de83a34fd1b5c9cf1a0e87`. A
record's optional `source.revision` is its last audited source snapshot, not a
dependency on a Worldline checkout. Maintained implementation sites migrate
from `tracking=symbol` to Aero's source-only `OptimizationRef` annotation as
they are touched. Source retention adds no runtime metadata or Worldline
dependency; the audit rejects unknown IDs and missing annotations for every
record already marked `tracking=annotation`.

The audit method, missing decisions found, platform drift, and repeatable
source gate are documented in
[`worldline/optimizations/AUDIT.md`](../worldline/optimizations/AUDIT.md).

Worldline can validate and experimentally evaluate these IDs, but it does not
own or duplicate their definitions. Other consumers should treat this
repository as the source of truth for every `aero.*` optimization ID.

## Classification

| Status | Count | Meaning in this inventory |
| --- | ---: | --- |
| Active | 31 | Shipped implementation with a supported production path; some still require consumer adoption. |
| Candidate | 14 | Opt-in, adoption-gated, or awaiting representative benchmark evidence. |
| Rejected | 5 | A known current implementation is unsafe or a measured regression. |
| Retired | 1 | Historical implementation removed from current production source. |

The `default.enabled` field records the source-level default, not proof of a
performance win. Adoption-gated APIs may remain active when their supported
implementation is shipped but consumers must call them explicitly.

### Active families

- Allocation and cache control: bounded loader caches, hot-path sampling,
  lookup and metadata caches, morph/IK scratch reuse, morph weight arrays,
  texture-ID caching, and chunk-bake prewarm.
- Render submission: animated batching, composite-state sorting, bone pages,
  at-rest display lists, and block-entity cell indexing/pages.
- Visibility and detail: smart LOD, conservative cone culling, small-object
  culling, chunk visibility, and animation admission control.
- Inner-loop work: animation event lower bounds and cursors, smooth-light grid
  and resolved-brightness reuse, loop-invariant hoisting, and opt-in back-face
  culling.
- Side-effect pressure: same-name sound coalescing.

### Candidate families

- Motion-aware tick LOD, dense tick budget, and skeletal LOD.
- OBJ hidden-face removal and consumer-authored mesh LODs.
- Cell-page fragmentation controls, prewarm, display-list budget, and the
  aggregate high-memory preset.
- Early individual-render skipping, demoted after a focused -37.0% A/B.
- Chunk compile budget, frame pacing, and the adaptive render-load governor.
- Incremental non-forced autosave batches with an unchanged forced-save drain.

### Rejected implementations

- `aero.animation.curve-lut`: bounded, contiguous 64- and 256-sample tables
  both regressed the representative diverse-phase 30-second ULTRA workload.
  The exact evaluator remains the default, including in the high-memory preset.
- `aero.chunk.paletted-cache-global`: applying the injection to the hot
  `PalettedContainer.get(int)` path allocated `CallbackInfoReturnable` per
  read even while the cache was logically off. Aero reports roughly 20%
  lower steady-state FPS. The mixin plugin now omits it unless explicitly
  opted in.
- `aero.chunk.paletted-cache-scope`: limiting that cache to
  `ChunkBuilder.rebuild()` still requires the injected callback on every
  mixed-in read. In a fresh-world A/B it made chunk compilation 11.7% slower,
  raised measured allocation 46.8%, and reduced throughput 12.7%. The flag is
  retained only as a research oracle.
- `aero.render.six-plane-frustum`: the current lazy plane capture can read
  stale or uninitialized data and over-cull visible block entities.
- `aero.render.client-vertex-arrays`: duplicating Beta's already mature client
  array submission path made the isolated Aero flush 11.6% slower.

### Retired implementation

- `aero.render.raycast-occlusion`: coarse voxel DDA was removed in favor of
  chunk-visibility snapshots and the cell-page path. Its cost, false-cull,
  hysteresis, and chunk-boundary spike history remain cataloged.

## Known platform divergence

`aero.animation.ik-scratch-reuse` currently names only the StationAPI
renderer. The equivalent ModLoader `runIkChains` path still performs per-call
input allocation. This is a known implementation gap, not catalog ambiguity.

## Lag-spike evidence

The Aero evidence does not support one universal "RAM spike" diagnosis.
It separates at least four mechanisms:

1. The pathological historical regression was allocation inside the
   `PalettedContainer.get(int)` injection. This was a code-path problem, not
   simply exhaustion of the configured maximum heap.
2. One early dense-scene capture did show GC pressure with only about 240 MB
   committed despite a high maximum heap. Raising the initial heap addresses
   that specific mechanism, but does not explain later captures.
3. Later 196-272 ms frames had negligible Aero preparation, entity render,
   world flush, cell rebuild, and GC time. One 284 ms sample instead reported
   about 111.7 ms in chunk rendering and 159.5 ms in display update.
4. Rhythmic 30-40 ms spikes in the sparse benchmark were mostly non-forced
   world saves taking about 20-26 ms inside the tick. Save suppression is a
   benchmark isolation control and is not safe as a gameplay optimization.

Texture string lookup allocation and repeated cell-index reattachment were
also found by JFR and optimized, but neither should be treated as proof that
every remaining spike originates in Aero rendering.

## Differential investigation order

A Worldline experiment should keep the map, camera path, machine population,
heap start/maximum, render distance, and warmup fixed, then compare:

1. Vanilla-compatible baseline without Aero consumers.
2. Aero source defaults.
3. Default-on families disabled one family at a time: batching, cell pages,
   bone pages, culling/LOD, and sound coalescing.
4. Opt-in candidates enabled one at a time. Never combine the paletted cache,
   chunk scheduler, frame pacer, load governor, and high-memory preset in the
   first attribution pass.
5. Spike-correlated traces that distinguish GC, world save, chunk compile,
   chunk render, entity render, Aero flush/rebuild, display update, and an
   unmeasured driver/swap interval.

A feature moves from candidate to active only after a representative
differential or invariant test records its evidence and rollback path.

Default-on features are now eligible for the same retroactive differential.
Run `java tools/perf/AblationMatrix.java --list` for the end-to-end mechanisms
that already have safe rollback switches and activating ULTRA scenes. The
runner uses counterbalanced enabled/disabled order and preserves raw stage
summaries; its method and interpretation rules live in
[`tools/perf/README.md`](../tools/perf/README.md). Structural cache and
inner-loop records without switches require focused allocation/algorithm
benchmarks rather than permanent diagnostic branches in production paths.
The end-to-end scene is now the ten-phase
[`ULTRA journey`](ULTRA_JOURNEY.md), so camera-turn, movement, floor-occlusion,
chunk-teleport, and post-transition recovery regressions are reported
separately instead of being averaged into one stationary-wall number.
