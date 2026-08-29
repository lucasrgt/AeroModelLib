# Active optimization ablation

`AblationMatrix.java` measures default-on Aero optimizations against their
documented rollback switches. It changes one switch at a time and keeps the
world seed, deterministic ten-phase camera journey, machine population, heap,
warmup, and measurement window fixed. Non-forced periodic saves are suppressed because their 20-26 ms cost
is unrelated to the optimization under test; forced save and exit behavior
remain intact.

Each round is counterbalanced:

```text
enabled -> disabled -> disabled -> enabled
```

This reduces first-run, JIT, and thermal ordering bias. The generated report
keeps raw summaries beside the aggregate so an apparent FPS win can be
rejected when chunk compilation, saves, GC, or another unrelated stage
contaminates one run.

List the supported end-to-end experiments:

```text
java tools/perf/AblationMatrix.java --list
```

Run one experiment:

```text
java tools/perf/AblationMatrix.java --only=batcher-state-sort --warmup=15 --bench=30
```

Run several or all experiments:

```text
java tools/perf/AblationMatrix.java --only=animated-batcher,bone-pages --rounds=2
java tools/perf/AblationMatrix.java
```

Outputs land under `stationapi/test/run/ablation/<timestamp>/`:

- `report.md` contains enabled-versus-rollback deltas;
- `results.csv` keeps every run and metric;
- `phases.csv` separates FPS and tail latency for every camera phase;
- each raw JSON preserves stage totals and activation counters;
- `runner.log` contains the complete Gradle and client log.

Positive report deltas mean the shipped optimization won. A result is not
catalog evidence merely because its average is positive. The optimization's
activation counter must be non-zero, both sides need valid measured windows,
and a suspected regression or large win must survive a longer confirmation.
The runner also requires `journeyCoverageComplete=1`; missing phases fail the
experiment instead of silently producing a static-scene result.

The runner covers mechanisms with safe runtime rollback switches and an
existing ULTRA scene that activates them. Structural optimizations such as
sample cursors, lookup caches, metadata caches, scratch reuse, event lower
bounds, and loop-invariant hoisting require focused allocation/algorithm
benchmarks or temporary source ablation. Adding permanent hot-path branches
only to make those mechanisms switchable would itself distort the result.

The bone-page fixture disables the animated batcher and lowers `minTris` to 1
so the alternate renderer is actually exercised. The small-object fixture uses
an 8 px threshold to prove activation. These are mechanism-coverage oracles;
they do not change or directly qualify the production thresholds of 24
triangles and 2 px.
The full phase contract and Worldline visual-oracle boundary are documented in
[`docs/ULTRA_JOURNEY.md`](../../docs/ULTRA_JOURNEY.md).

## Multi-chunk density envelope

`UltraDensityMatrix.java` keeps the same ten-phase journey and progressively
reduces tower spacing. It reports the number of tower chunks and machines that
were actually generated, rather than inferring coverage from configuration.

```text
java tools/perf/UltraDensityMatrix.java --list
java tools/perf/UltraDensityMatrix.java --warmup=10 --bench=15
java tools/perf/UltraDensityMatrix.java --only=field-animated,dense-animated
```

The isolated profile is the causal control. Field and dense profiles qualify
normal culling transitions across several chunks. Saturation places one tower
in every generated chunk and is protected by a per-profile timeout; a timeout
or missing summary is retained as the workload limit, not silently reported as
a successful zero-FPS run.
