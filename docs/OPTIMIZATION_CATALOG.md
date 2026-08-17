# Aero Optimization Catalog

This repository owns the canonical metadata for AeroModelLib optimizations.
The 32 records under `optimizations/catalog` describe Aero implementation
details, defaults, risks, rollback paths, source symbols, and evidence using
the neutral `worldline.optimization.v1` schema.

The initial audit was performed at revision
`436d65b38c53346b465e5e793bd943177ebfaa32`. A record's optional
`source.revision` is its last audited source snapshot, not a dependency on a
Worldline checkout. Existing records use `tracking=symbol` so the catalog adds
no annotations or runtime dependency to Aero. Future owned sites may adopt a
source-only annotation without changing runtime behavior.

Worldline can validate and experimentally evaluate these IDs, but it does not
own or duplicate their definitions. Other consumers should treat this
repository as the source of truth for every `aero.*` optimization ID.

## Classification

| Status | Count | Meaning in this inventory |
| --- | ---: | --- |
| Active | 16 | Shipped implementation with a supported production path; some still require consumer adoption. |
| Candidate | 14 | Opt-in, adoption-gated, or awaiting representative benchmark evidence. |
| Rejected | 2 | A known current implementation is unsafe or a measured regression. |

The `default.enabled` field records the source-level default, not proof of a
performance win. For adoption-gated APIs such as `Aero_TextureBinder`, false
means existing consumers are not intercepted automatically. The record itself
remains active because the supported implementation is shipped.

### Active families

- Allocation and cache control: bounded loader caches, IK scratch reuse,
  texture-ID caching, and chunk-bake prewarm.
- Render submission: animated batching, composite-state sorting, bone pages,
  block-entity cell indexing/pages, and individual-render skipping.
- Visibility and detail: smart LOD, conservative cone culling, small-object
  culling, chunk visibility, and animation admission control.
- Side-effect pressure: same-name sound coalescing.

### Candidate families

- Animation LUT, motion-aware tick LOD, dense tick budget, and skeletal LOD.
- OBJ hidden-face removal and consumer-authored mesh LODs.
- Cell-page fragmentation controls, prewarm, display-list budget, and the
  aggregate high-memory preset.
- Chunk-scoped paletted cache, chunk compile budget, frame pacing, and the
  adaptive render-load governor.

### Rejected implementations

- `aero.chunk.paletted-cache-global`: applying the injection to the hot
  `PalettedContainer.get(int)` path allocated `CallbackInfoReturnable` per
  read even while the cache was logically off. Aero reports roughly 20%
  lower steady-state FPS. The mixin plugin now omits it unless explicitly
  opted in.
- `aero.render.six-plane-frustum`: the current lazy plane capture can read
  stale or uninitialized data and over-cull visible block entities.

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
