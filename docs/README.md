# AeroModelLib Documentation

- [`DOC.md`](DOC.md) is the complete user and API guide, including quick
  starts, architecture, animation schemas, public APIs, integration patterns,
  examples, troubleshooting, and profiling.
- [`PERF_ROADMAP.md`](PERF_ROADMAP.md) records implemented, experimental,
  deferred, and rejected performance work with benchmark context.
- [`OPTIMIZATION_CATALOG.md`](OPTIMIZATION_CATALOG.md) defines the canonical
  Aero optimization IDs, ownership, status, risks, defaults, and rollback
  paths backed by `worldline/optimizations/catalog`. The source reconciliation and
  repeatable drift gate are in
  [`worldline/optimizations/AUDIT.md`](../worldline/optimizations/AUDIT.md).
- [`ULTRA_STRESS_TEST.md`](ULTRA_STRESS_TEST.md) documents the deliberately
  pathological StationAPI limit-discovery scene, its bounded controls, and
  the interpretation of its complete-frame/JFR evidence.

Repository-level `README.md`, `CHANGELOG.md`, and `LICENSE.md` remain at the
root because hosting platforms and release tooling expect them there.
