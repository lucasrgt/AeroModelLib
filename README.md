<p align="center">
  <img src="banner.png" alt="AeroModelLib" width="720">
</p>

<h1 align="center">AeroModelLib</h1>

<p align="center"><strong>Modern model rendering and animation for Minecraft Beta 1.7.3.</strong></p>

<p align="center">
  <a href="#getting-started">Getting Started</a> |
  <a href="#capabilities">Capabilities</a> |
  <a href="#performance-model">Performance</a> |
  <a href="docs/DOC.md">Full Documentation</a>
</p>

<p align="center">
  <a href="https://github.com/lucasrgt/AeroModelLib/actions/workflows/ci.yml"><img src="https://github.com/lucasrgt/AeroModelLib/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/lucasrgt/AeroModelLib/releases"><img src="https://img.shields.io/github/v/release/lucasrgt/AeroModelLib?display_name=tag&sort=semver&style=flat-square" alt="Latest release"></a>
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/license-MIT-2EA44F?style=flat-square" alt="MIT License"></a>
  <img src="https://img.shields.io/badge/Minecraft-Beta%201.7.3-62B47A?style=flat-square" alt="Minecraft Beta 1.7.3">
  <img src="https://img.shields.io/badge/rendering-OpenGL%201.1-5586A4?style=flat-square" alt="OpenGL 1.1">
</p>

AeroModelLib brings a modern content-authoring workflow to Minecraft Beta
1.7.3: Blockbench JSON models, named OBJ and Three.js meshes, GeckoLib-style animation
bundles, skeletal hierarchy, interpolation, events, morph targets, LOD, and
batch-aware rendering on the fixed-function pipeline the game already ships.

The same pure-Java core targets both RetroMCP/ModLoader and StationAPI/Babric.
Mods describe models and animation once; runtime adapters handle the platform
surface.

<table>
<tr><td><b>One model contract</b></td><td><code>Aero_ModelSpec</code> keeps mesh, texture, animation, transform, style, culling, and LOD settings together.</td></tr>
<tr><td><b>One shared core</b></td><td>Loaders, animation, model data, skeletal math, and render policy compile for both supported runtimes.</td></tr>
<tr><td><b>Authoring-first</b></td><td>Use Blockbench, OBJ or Three.js scene JSON, and strict <code>.anim.json</code> files instead of hand-writing long OpenGL transform chains.</td></tr>
<tr><td><b>Fixed-function honest</b></td><td>No shaders, modern instancing, or GPU features that Beta 1.7.3 cannot provide.</td></tr>
<tr><td><b>Performance with rollback</b></td><td>Batching, culling, display-list pages, budgets, and experimental paths expose explicit flags or consumer opt-ins.</td></tr>
</table>

---

## Runtime matrix

| Target | Game/runtime | Toolchain | Distribution model |
| --- | --- | --- | --- |
| ModLoader / Forge 1.0.6 | Minecraft Beta 1.7.3 through RetroMCP | Java 8 | Source bundle integrated into the mod workspace |
| StationAPI / Babric | Minecraft Beta 1.7.3 with StationAPI | JDK 17 build | Loom-built library JAR |

The `main` branch currently declares version `3.0.0` in
`stationapi/gradle.properties`. Published tags remain independently versioned;
pin the exact tag or commit used by a mod.

---

## See it in action

[![AeroModelLib demonstration](https://img.youtube.com/vi/ewJ0XgnOSHE/maxresdefault.jpg)](https://www.youtube.com/watch?v=ewJ0XgnOSHE)

The demonstration covers animated machines authored outside the renderer,
including moving named parts, interpolation, texture animation, and
render-distance behavior.

---

## Getting started

### 1. Obtain the runtime target

Tagged artifacts are published through
[GitHub Releases](https://github.com/lucasrgt/AeroModelLib/releases).

- StationAPI consumers use the versioned library JAR with StationAPI present at
  runtime.
- RetroMCP/ModLoader consumers use the source bundle, which contains the shared
  `core/` tree and the `modloader/` adapter.
- To build the current branch, use the commands in
  [Build and contribute](#build-and-contribute).

### 2. Add model assets

Put model, texture, and animation resources on the mod classpath:

```text
assets/
  models/
    crusher.obj
    robot.three.json
    crusher.anim.json
  block/
    crusher.png
```

Named `o` or `g` groups in the OBJ become animation bone names. Animation
pivots use Blockbench pixels and are converted by the loader.

### 3. Declare animation once

```java
public static final int STATE_IDLE = 0;
public static final int STATE_WORKING = 1;

public static final Aero_AnimationSpec ANIMATION =
    Aero_AnimationSpec.builder("/models/crusher.anim.json")
        .state(STATE_IDLE, "idle")
        .state(STATE_WORKING, "working")
        .defaultTransitionTicks(4)
        .build();

public static final Aero_MeshModel MODEL =
    Aero_ObjLoader.load("/models/crusher.obj");

public final Aero_AnimationState animation = ANIMATION.createState();
```

Advance playback before selecting the state:

```java
public void updateEntity() {
    animation.tick();
    ANIMATION.applyState(animation, isRunning ? STATE_WORKING : STATE_IDLE);
}
```

### 4. Render the current pose

```java
bindTextureByName("/block/crusher.png");

Aero_RenderLod lod =
    Aero_RenderDistance.lodRelative(x, y, z, 2.0d, 48.0d);

if (lod.shouldAnimate()) {
    Aero_MeshRenderer.renderAnimated(
        MODEL, animation, x, y, z, brightness, partialTick);
} else if (lod.isStaticOnly()) {
    Aero_MeshRenderer.renderModelAtRest(
        MODEL, x, y, z, 0.0f, brightness);
}
```

For entities, put the shared configuration in an `Aero_ModelSpec` and delegate
to `Aero_EntityModelRenderer`:

```java
public static final Aero_ModelSpec MODEL =
    Aero_ModelSpec.mesh("/models/robot.obj")
        .texture("/mob/robot.png")
        .animations(Aero_AnimationSpec.builder("/models/robot.anim.json")
            .state(0, "idle")
            .state(1, "walk")
            .state(2, "attack")
            .defaultTransitionTicks(4)
            .build())
        .offset(-0.5f, 0.0f, -0.5f)
        .cullingRadius(2.0f)
        .animatedDistance(48.0d)
        .build();
```

The complete block, entity, NBT, multiplayer, and renderer recipes live in
[` 1 Quick Start](docs/DOC.md#1-quick-start) and
[` 14 End-to-end example](docs/DOC.md#14-full-end-to-end-example).

---

## Authoring workflow

```text
Blockbench
   |
   +-- Blockbench JSON --------------------> Aero_JsonModelLoader
   |
   +-- OBJ with named groups --+
   |                           +-----------> Aero_ObjLoader
   +-- .bbmodel animations ----+
              |
              +-- tools/convert.{sh,bat} --> .anim.json
                                                  |
                                                  v
                             animation state + model spec
                                      /                 \
                         ModLoader / RetroMCP      StationAPI / Babric
```

Three.js scenes can join the same mesh path after their geometries are baked:

```text
Three.js Object3D.toJSON() --> robot.three.json --> Aero_ThreeJsonLoader
```

`Aero_ModelSpec.mesh("/models/robot.three.json")` selects the Three.js loader
automatically. The resulting `Aero_MeshModel` uses the existing mesh renderer,
LOD, named-group animation, and texture binding APIs.

The converter is a standalone Java tool. It turns Blockbench `.bbmodel`
animation data into Aero's strict animation schema:

```bash
tools/convert.sh path/to/crusher.bbmodel
```

On Windows:

```bat
tools\convert.bat path\to\crusher.bbmodel
```

---

## Capabilities

### Models and rendering

| Capability | What it provides |
| --- | --- |
| Blockbench JSON | Cached element-model loading and world/inventory rendering |
| Wavefront OBJ | Flattened mesh loading with named groups for animated parts |
| Three.js scene JSON | Baked BufferGeometry loading with hierarchy transforms and named groups |
| Declarative model specs | One source of truth for model, texture, animation, transform, style, and LOD |
| Per-call styling | Tint, alpha, alpha clipping, additive blending, depth test, and face culling |
| Inventory thumbnails | Centralized auto-scale and vertical alignment |
| Real mesh LOD | Consumer-supplied alternate meshes selected by distance |

### Animation

| Capability | What it provides |
| --- | --- |
| Strict animation bundles | Versioned `.anim.json` with explicit failures for unsupported schema and interpolation names |
| Pose channels | Rotation, position, scale, UV offset, and UV scale |
| Interpolation | Linear, step, Catmull-Rom, quaternion slerp, and 30 GeckoLib-style easing curves |
| State transitions | State IDs, crossfades, play-once, hold-last-frame, and looping clips |
| Layering | Replace and additive `Aero_AnimationStack` layers |
| Animation graph | Clip, Blend1D, and additive graph nodes driven by runtime parameters |
| Hierarchy and IK | Parent-child bone composition, forward kinematics, and CCD chains |
| Morph targets | Vertex-level blend shapes from topology-compatible OBJ variants |
| Procedural pose | Runtime bone deltas for turrets, steering, suspension, and other input-driven motion |
| Keyframe events | Sound, particle, and custom events anchored to moving bone locators |

### Runtime scale

| Capability | What it provides |
| --- | --- |
| Smart LOD | Animated, at-rest, and culled bands derived from model size and distance |
| Animated batching | Compatible StationAPI draws share texture and fixed-function state |
| Cell pages | Eligible at-rest block entities compile into reusable per-cell display lists |
| Bone pages | Rigid named groups compile into reusable bone display lists |
| Visibility layers | Conservative view-cone, small-object, and vanilla chunk visibility checks |
| Admission budgets | Optional animation render/tick caps preserve visibility by degrading detail |
| Observability | Zero-cost-when-disabled profiler and spike logger with renderer/chunk/GC stages |

---

## Animation bundle example

`Aero_AnimationLoader` accepts format `1.0` and the additive `1.1` schema.
Version 1.1 adds morph-target declarations; existing 1.0 bundles remain valid.

```json
{
  "format_version": "1.1",
  "pivots": {
    "fan": [8.0, 8.0, 8.0]
  },
  "morph_targets": {
    "expanded": "/models/crystal_expanded.obj"
  },
  "animations": {
    "working": {
      "loop": "loop",
      "length": 2.0,
      "bones": {
        "fan": {
          "rotation": {
            "0.0": { "value": [0, 0, 0], "interp": "linear" },
            "1.0": { "value": [0, 180, 0], "interp": "easeInOutSine" },
            "2.0": { "value": [0, 360, 0], "interp": "linear" }
          },
          "uv_offset": {
            "0.0": { "value": [0, 0, 0], "interp": "linear" },
            "2.0": { "value": [1, 0, 0], "interp": "linear" }
          }
        }
      },
      "keyframes": {
        "sound": {
          "1.0": { "name": "random.click", "locator": "fan" }
        },
        "particle": {
          "0.5": { "name": "smoke", "locator": "fan" }
        }
      }
    }
  }
}
```

Important format rules:

- Pivots and position values use Blockbench pixels.
- Rotation values are Euler degrees; the runtime composes hierarchical poses
  and uses quaternion interpolation where the segment is unambiguous.
- `o`/`g` names in the OBJ must match animation bone and locator names.
- Morph variants must preserve base-mesh topology and vertex order.
- Unknown versions, loop types, or interpolation names fail during loading.

See [` 5 Animations](docs/DOC.md#5-animations) and
[` 9 File Formats](docs/DOC.md#9-file-formats) for the complete schema.

---

## Performance model

AeroModelLib does not treat every optimization as universally safe. Production
defaults favor conservative culling and batching; scene-dependent or invasive
paths remain opt-in.

### Default production path

| Area | Default behavior | Rollback |
| --- | --- | --- |
| Animated batches | Enabled on StationAPI | `-Daero.animatedbatch=false` |
| Batch state sorting | Enabled | `-Daero.batcher.sort=false` |
| Smart LOD | Enabled | `-Daero.smartlod=false` |
| View-cone culling | Enabled with close/turn safeguards | `-Daero.frustumcull=false` |
| Small-object culling | Enabled at a conservative pixel threshold | `-Daero.smallobj=false` |
| Chunk visibility | Enabled using vanilla chunk visibility | `-Daero.chunkvisibility=false` |
| Cell pages | Infrastructure enabled; consumer adoption required | `-Daero.becell.pages=false` |
| Bone pages | Enabled for eligible rigid groups | `-Daero.bonepages=false` |

### Explicit experiments

| Feature | Enable with | Main trade-off |
| --- | --- | --- |
| Animation curve LUT (rejected research path) | `-Daero.anim.lut=true` | Bounded approximation, more memory, and a measured dense-scene regression |
| OBJ hidden-face removal | `-Daero.obj.cullhidden=true` | Load-time geometry decision needs asset validation |
| Skeletal LOD | `-Daero.skeletalLod=true` | Distant poses become less detailed |
| Prewarm queue | `-Daero.prewarm=true` | Earlier CPU/driver work and cache allocation |
| High-memory preset | `-Daero.perf.memory=high` | Higher heap and display-list retention |
| Chunk-scoped palette cache | `-Daero.palettedcache.chunkScope=true` | Experimental injection during chunk rebuild |
| Chunk work scheduler | `-Daero.chunkCompileBudget=true` | Bounded non-forced rebuilds; current/adjacent/visible and camera look-ahead first, then age/debt recovery |
| Frame pacing | `-Daero.framePacing=true` | Caps submission rate and may add latency |

Do not enable every experiment at once. Change one family at a time and record
frame-stage evidence. The old global `-Daero.palettedcache=true` mode remains
an A/B diagnostic path, not a recommended gameplay default: the hot-method
injection measured as a steady-state regression.

The chunk scheduler's speculative look-ahead defaults to three chunks and can
be adjusted with `-Daero.chunkCompileBudget.lookAheadRadius=1..8`. It does not
create a second cache or block world entry: priorities are recomputed from the
current camera every frame, and the existing rebuild budget remains absolute.

The canonical optimization IDs, ownership, status, defaults, risks, and
rollback paths are in
[`docs/OPTIMIZATION_CATALOG.md`](docs/OPTIMIZATION_CATALOG.md). Detailed
benchmark history and the investigation protocol remain in
[`docs/PERF_ROADMAP.md`](docs/PERF_ROADMAP.md). The source-wide reconciliation
and drift gate are documented in
[`worldline/optimizations/AUDIT.md`](worldline/optimizations/AUDIT.md).

### Profiling

Enable the lightweight section profiler:

```text
-Daero.profiler=true
```

For spike attribution on StationAPI:

```text
-Daero.spikelog=true
```

The spike logger separates animation preparation, entity rendering, cell-page
rebuilds, chunk compilation/rendering, world save, display update, and GC
signals. This distinction matters: a long frame near many machines is not
automatically an Aero renderer or heap-capacity problem.

---

## Architecture

```text
core/
  aero/modellib/
    animation/   platform-neutral clips, playback, graphs, and events
    model/       JSON/OBJ loaders, mesh data, model specifications
    render/      LOD, culling, render policy, and animation budgets
    skeletal/    quaternions, FK, IK, morph state, and bone pages
    util/        profiler, performance configuration, sound coalescing

modloader/
  Java 8 / RetroMCP adapter and source-integration tooling

stationapi/
  Loom project, Minecraft-facing renderers, batching, cell pages, and mixins
```

The dependency direction is deliberate: shared core code does not import a
Minecraft runtime. Runtime adapters bridge world, texture, entity, block
entity, and OpenGL lifecycle details.

The complete class map and diagrams are in
[` 2 Architecture](docs/DOC.md#2-architecture) and
[` 8 API Reference](docs/DOC.md#8-api-reference).

---

## Documentation

| Document | Use it for |
| --- | --- |
| [Documentation index](docs/README.md) | Entry point for all maintained docs |
| [Complete guide](docs/DOC.md) | Quick starts, architecture, API, formats, patterns, examples, troubleshooting |
| [Optimization catalog](docs/OPTIMIZATION_CATALOG.md) | Canonical Aero optimization IDs, status, risks, defaults, and rollback paths |
| [Optimization audit](worldline/optimizations/AUDIT.md) | Source-wide coverage, platform drift, and the repeatable catalog-to-code gate |
| [Performance roadmap](docs/PERF_ROADMAP.md) | Implemented optimizations, experiments, evidence, flags, and known regressions |
| [Changelog](CHANGELOG.md) | Version history and compatibility notes |

Useful direct links:

- [Static and animated quick starts](docs/DOC.md#1-quick-start)
- [Animation schema and sampling](docs/DOC.md#5-animations)
- [State machine and transitions](docs/DOC.md#6-state-machine)
- [Advanced animation](docs/DOC.md#7-advanced-animation)
- [Asset workflow and converter](docs/DOC.md#10-asset-workflow--converter)
- [Patterns, multiplayer, and best practices](docs/DOC.md#11-patterns--best-practices)
- [Troubleshooting](docs/DOC.md#13-troubleshooting)
- [Full end-to-end example](docs/DOC.md#14-full-end-to-end-example)
- [Tests, benchmarks, and profiling](docs/DOC.md#15-development-tests--benchmarks)

---

## Compatibility and scope

AeroModelLib targets:

- Minecraft Beta 1.7.3.
- RetroMCP with ModLoader / Forge 1.0.6.
- StationAPI / Babric.
- Java 8 for the shared core and ModLoader source target.
- JDK 17 for the StationAPI Loom build.
- LWJGL 2 and the OpenGL 1.1 fixed-function pipeline.

It is not a shader framework, a modern GPU instancing layer, a world scheduler,
or a replacement for gameplay state. Animation state remains consumer-owned,
and consumers decide which block entities, entities, assets, and experimental
performance paths adopt Aero behavior.

---

## Build and contribute

### Canonical repository gate

```text
java tools/harness/Verify.java
```

The gate enforces module order and per-file source ceilings, audits optimization
metadata and `OptimizationRef` annotations, compiles the Java 8 shared core,
and runs the full pure-Java test suite. Platform-facing changes additionally
run `java tools/harness/Verify.java --platforms`.

### Core and ModLoader tests

```powershell
powershell -ExecutionPolicy Bypass -File modloader/tests/run.ps1
```

### Pure-Java microbenchmarks

```powershell
powershell -ExecutionPolicy Bypass -File modloader/tests/bench.ps1
```

### StationAPI library

```powershell
cd stationapi
.\gradlew.bat build
```

### StationAPI integration test mod

```powershell
cd stationapi/test
.\gradlew.bat build
```

### In-game test client

```powershell
cd stationapi/test
.\gradlew.bat runClient
```

### Release artifacts

```bash
bash scripts/release.sh
```

The release script produces a ModLoader source ZIP plus StationAPI binary and
source JARs under `dist/`. Passing `--gh` also creates a GitHub release when
the GitHub CLI is authenticated.

CI runs core tests, StationAPI builds, integration-mod builds, dependency
review, CodeQL, Gitleaks, Trivy, and Gradle wrapper validation. GitHub Actions
are pinned by commit.

Contributions should preserve both runtime targets, fail loudly on malformed
assets, keep platform-neutral logic in `core/`, satisfy the 200-code-line
production-file ceiling, and include focused tests for behavioral changes.

---

## Project transparency

AeroModelLib is developed with substantial AI assistance. Architecture,
product decisions, review, and release responsibility remain with the
maintainer. The repository keeps source, tests, performance evidence, flags,
and rollback paths public so claims can be inspected rather than inferred from
the development process.

Maintainer: [lucasrgt](https://github.com/lucasrgt)

---

## License

[MIT](LICENSE.md)
