# Plan: close the remaining refactor gaps

This continues `docs/adr/0001-deep-modules-refactoring.md`. It covers only the gaps left after the seven-phase refactor. Each step keeps `src-gen/` untouched, keeps the strong `jsBridge` field on `BlockyUI`, and stays green on `mvn test -pl blocky_game` before the next step starts.

Vocabulary below follows the codebase-design skill: **module**, **interface**, **seam**, **leverage**, **locality**. A line-count target is not a reason to add a module.

## Order

Do these in order. Later steps depend on earlier ones staying green.

1. Admin report template
2. Remaining WebView scripts
3. HTTP route modules
4. One Pegman interpreter
5. `GameEngine` session facade
6. Verification and the two-axis review

---

## 1. Admin report template

**Gap.** Phase 2 named `blocky_game/src/blocky_game/resources/templates/adminDashboard.html`. `AdminReportService.generateAdminHtmlReport` still builds the whole page as a Java string.

**Seam.** `WebAssetLoader.loadTemplate` already exists. The report module keeps the data; the template keeps the markup.

**Work.**

- Move the static markup, CSS, and the `BlockPreview.renderAll` bootstrap into `resources/templates/adminDashboard.html`.
- Keep session rows, level cells, and event cards as the only values substituted into the template. Build those fragments in `AdminReportService`, then pass them as template replacements (`{{sessions}}`, `{{events}}`, `{{generatedAt}}`, and the header totals).
- Leave `blockPreview.js` loaded at request time the way it is now. The template references it; it does not inline the script source into Java.
- Keep `HttpSearchServerTest` assertions: `<!DOCTYPE html>`, the report title, `No sessions recorded yet.`, `class="block-preview"`, and the `blockPreview.js` script tag.

**Done when.** `generateAdminHtmlReport` no longer contains a `<style>` block or the document skeleton, and the existing admin tests pass without a change to the response contract.

## 2. Remaining WebView scripts

**Gap.** `syncScript.js` and `debugControls.js` are files. The level-apply script in `BlockyJsBridgeController.applyLevelToWebView` and the in-place solution script in `MomotUiCoordinator.loadMomotSolutionInPlace` are still Java string literals. `injectDebugControls` also calls `WebAssetLoader.loadScript("styles/debugControls.css")`, and that file does not exist. The debug script does not use a `{{debugControls.css}}` placeholder, so the call only throws and is swallowed.

**Seam.** Same as phase 2: Java passes data, `WebAssetLoader` loads the script.

**Work.**

- Add `resources/scripts/applyLevel.js` and `resources/scripts/loadMomotSolution.js`.
- Java still computes the injected values (`__injectGridJson`, `__loadXml`, `__momotXml`, direct-manipulation paths) and assigns them on `window` before running the script. The scripts read those globals. They do not receive the maze state as concatenated Java strings.
- Delete the `styles/debugControls.css` load. Styles stay inside `debugControls.js`, where they already are.
- Leave one-line `executeScript` calls that set a single `window` property or call an existing JS function (`__execLogAppend`, `__lvlLoadingHide`, `__momotSetStatus`). Those are calls across the bridge, not embedded programs.

**Done when.** The two multi-line injection programs live in `.js` files, the missing-CSS load is gone, and `BlockyJsBridgeControllerTest` still passes.

## 3. HTTP route modules

**Gap.** `HttpSearchServer` is 54 lines. `SearchHttpHandler` is 1,170 lines and owns every route: session sync, MoMoT, the debugger, direct manipulation, simulation, level timing, and static files.

**Seam.** One module per reason to change. No new Java interface: nothing varies behind these routes except which handler runs. `HttpSearchServer` keeps registering routes.

**Work.** Split the private handler classes, leaving the shared response helpers (`sendJson`, `readRequestBody`, `parseJsonField`, `escapeJson`) on one module both handlers call:

| Module | Routes |
|---|---|
| `SessionHttpHandler` | `/api/session/*` |
| `MomotSearchHttpHandler` | `/api/momot/*` |
| `DebugHttpHandler` | `/api/debug/*` |
| `DirectManipulationHttpHandler` | `/api/dm/*`, `/api/simulation/run` |
| `LevelTimeHttpHandler` | `/api/level-time` |
| `StaticFileHandler` | `/` |

`SearchHttpHandler.registerRoutes` becomes the registrar that constructs these and passes `TelemetryService` only to the handlers that record events. Admin routes stay on `AdminReportService`.

**Done when.** `HttpSearchServerTest` passes with no URL or JSON change, and no handler file owns a route outside its table.

## 4. One Pegman interpreter

**Gap.** `SimulationEngine` in `blocky_game` runs the in-app trace. `BlockySimulator` in `blocky_momot` (854 lines) still walks the program itself, including its own `determineStartOrientation` and `violatesLevelConstraints`. `blocky_game` depends on `blocky_momot`, and `blocky_momot` depends on `blocky_model`. The shared interpreter cannot live in `blocky_game`.

**Seam.** One implementation, two callers (the desktop/HTTP game, and MoMoT fitness). That is a real seam. Do not add a `SimulationEngine` interface: the two walks must not vary. A second Maven module would be a module with only one adapter.

**Work.**

- Add characterization tests first, before moving code. For each level XMI under `blocky_game/model/input/`, run today's `BlockySimulator.run` and today's `SimulationEngine.simulate` on the same level and record final `GameStatus` plus the position sequence. These tests must pass on the current, duplicated code. They are the lock that the move did not change fitness.
- Move `SimulationEngine` into `blocky_momot`. Move the constraint and start-orientation rules it needs with it (`SimUtils.violatesLevelConstraints`, `SimUtils.determineStartOrientation`). `blocky_game.SimUtils` keeps the same methods and delegates, so existing game callers stay source-compatible.
- Replace `BlockySimulator.executeBody` and its private sensor walk with a call to that engine. Keep `BlockySimulator`'s fitness interface (`run`, `stepsToGoalOrPenalty`, `closestToGoalOrPenalty`, `distanceToGoalOrPenalty`, `simulationSteps`). Those methods read the trace the engine returns. They do not walk the grid again.
- Point `GameEngine`, `ImmediateFeedbackService`, and `DebuggingService` at `blocky_momot.SimulationEngine`.

**Done when.** The characterization tests still match, `BlockySimulator` no longer contains its own step loop, and `mvn test` passes for `blocky_game` and `blocky_momot`.

**ADR.** Amend `docs/adr/0001-deep-modules-refactoring.md` with one paragraph: the interpreter lives in `blocky_momot` because that is the lowest module both callers can see.

## 5. `GameEngine` session facade

**Gap.** The plan asked for `GameEngine` under 250 lines. It is 415. Most of the extra lines are one-line delegates to modules that already exist (`debugStart`, `getStartCell`, `solutionToBlocklyXml`, `getAdjacent`). Extracting those again would make `GameEngine` a middle man and spread the session object (`currentGame`, `currentLevel`, `resource`) across more callers.

**Decision.** Drop the 250-line target. `GameEngine` stays the session facade: it holds the current game and forwards. Record that in the same ADR amendment.

**The two clusters that still contain rules:**

- `teleportPegman`: cell-type rejection, DMG marking, initial `GameState`, and the XMI snapshot. Move the cell rule and DMG mark into `MazeGridService`. `GameEngine` keeps the sequence: ask the grid, tell `InteractiveDebugger`, write the trace, save the snapshot.
- `syncLevelMeta`: hand-rolled JSON field extraction and level-flag assignment. Move the parsing and the flag write into one method on the module that already owns level fields (alongside `MazeGridService` level metadata, or a package-visible helper used only by `GameEngine`). Callers still call `GameEngine.syncLevelMeta`.

Leave `initializeGame`, `loadFromFile`, `rebuildProgram`, `setMapFromJson`, and `simulateUserProgramWithLogs` on the facade. They coordinate several modules around the current session.

**Done when.** Pegman placement rejects a wall in one place, level-meta parsing has one implementation, and `GameEngineFeatureTest` plus `HttpSearchServerTest` pass. Line count is not the exit check.

## 6. Verification and review

After step 5:

- `mvn clean test`
- `npm test` in `blocky_game/src/blocky_game/blockly-games-web`
- `bash tools/henshin-prototype/run.sh verify` and `verify-wrap`

Then run the code-review skill on the refactor diff. The spec is this file plus `docs/adr/0001-deep-modules-refactoring.md`. The fixed point is the commit that precedes the refactor (the review needs that ref; if the work is still uncommitted, review the working tree against that commit). The review reports Standards and Spec separately and does not merge them.

## Out of scope

- Editing anything under `src-gen/`.
- Changing REST URLs, admin HTML test strings, or MoMoT fitness numbers.
- Splitting `GameEngine` until it is under 250 lines.
- A new Maven module for the interpreter.
- A Java interface with one implementation.
