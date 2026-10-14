# Deep Modules Refactoring for Maintainability

To make the codebase maintainable and AI-navigable, we decompose the monolithic classes (`BlockyUI`, `HttpSearchServer`, `GameEngine`) into deep modules with small interfaces and rich implementations, while strictly preserving EMF metamodel invariants (`src-gen/` untouched) and JavaFX WebView JSBridge lifetime semantics.

## Status

Accepted

## Context

The repository had grown several monolithic source files (`BlockyUI.java` at 2,814 lines, `HttpSearchServer.java` at 2,222 lines, and `GameEngine.java` at 1,997 lines) where user interface logic, network transports, stringified HTML/JS scripts, simulation routines, and persistence were deeply entangled. This hindered automated testing, created duplicate simulation logic across modules, and made changes risky.

## Decision

1. Extract all inlined JavaScript and CSS strings from Java into separate web resource files (`.js`, `.css`), loaded through a dedicated `WebAssetLoader`.
2. Unify simulation execution across `GameEngine`, `ImmediateFeedbackService`, and `BlockySimulator` into a single deep `SimulationEngine` module.
3. Decompose `GameEngine` into focused deep modules behind clear seams:
   - `BlockyAstMapper` for Blockly JSON/XML <-> EMF transformations.
   - `MazeGridService` for GridMap topology and cell mutations.
   - `InteractiveDebugger` for trace stepping and pause/resume logic.
   - `ModelStorageService` for EMF XMI persistence.
   - Keep `GameEngine` as a backwards-compatible facade delegating to these deep modules.
4. Separate `HttpSearchServer` into dedicated HTTP handlers and services:
   - `TelemetryService` for event logging and timing metrics.
   - `AdminReportService` for admin auth, dashboard HTML generation, and JSON/HTML export.
   - `SearchHttpHandler` for REST route registration and JSON request/response handling.
5. Decouple `BlockyUI` into:
   - `BlockyJsBridgeController` for Java <-> JavaScript WebView communication.
   - `MomotUiCoordinator` for background MOMoT synthesis and solution overlays.
   - Retaining a strong reference to the bridge controller on `BlockyUI` to prevent JavaFX WebView garbage collection.

## Consequences

- Line counts dramatically reduced:
  - `GameEngine.java`: reduced from ~2,000 LOC to 335 LOC.
  - `HttpSearchServer.java`: reduced from ~2,200 LOC to 55 LOC.
  - `BlockyUI.java`: reduced from ~2,800 LOC to 250 LOC.
- Strict preservation of repository invariants:
  - `src-gen/` untouched.
  - Case-insensitivity on Blockly block types preserved.
  - Strong `jsBridge` reference retained in `BlockyUI`.
  - Suppress sync protocol respected during state injection.
- Complete regression test suite green (73/73 Maven tests, 46/46 NPM tests, Henshin rule verification passing).

## Constraints

- Never modify `src-gen/` directly.
- Preserve case-insensitivity conventions (`.toLowerCase()`) for Blockly block names.
- Retain the `suppressSync` flag during WebView model synchronization to avoid race conditions.

## Amendment: Interpreter Placement and Session Facade Scope

1. **One Pegman Interpreter in `blocky_momot`**: The Pegman simulation interpreter (`SimulationEngine`) lives in `blocky_momot` because that is the lowest Maven module that both consumers (`blocky_game` desktop/HTTP runtime and `blocky_momot` genetic search fitness evaluation) can see without cyclic dependencies or introducing an artificial intermediate module. `BlockySimulator` delegates execution entirely to `SimulationEngine` and computes MOMoT objective metrics directly from the resulting execution trace without walking the maze grid a second time. `SimUtils` in `blocky_game` delegates its constraint and orientation methods to `SimulationEngine` for backwards source compatibility.
2. **Session Facade Scope for `GameEngine`**: The initial arbitrary 250-line target for `GameEngine` is dropped. `GameEngine` deliberately remains the stateful session facade: it manages the lifetime of `currentGame`, `currentLevel`, and `resource`, and routes commands across subsystem deep modules (`MazeGridService`, `BlockyAstMapper`, `InteractiveDebugger`, `ModelStorageService`). Pegman cell validation/marking and level metadata parsing are consolidated into `MazeGridService`, leaving `GameEngine` to coordinate multi-module session workflows without leaking session state across callers.
