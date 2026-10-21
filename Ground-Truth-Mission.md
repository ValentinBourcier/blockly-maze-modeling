# Ground-Truth Mission: reference Pareto fronts for judging user-obtained programs

Status: plan, nothing run yet for the search (updated 2026-10-16; first version 2026-10-14). The replay of user programs is built (section 7). Follows `Exploration-Proposal.md` (P4, "Optional: exact search for small cases").

## 1. Claim to support

> Users who explore with the tool obtain more of the Pareto-front solutions than users who explore without it.

The yardstick is the Pareto front per level. What is compared with it are the **programs users obtain**, in two conditions: manual exploration (plain Blockly Games) and tool-assisted exploration. The engine's own front is not what is measured.

Two plans, sharing one search (section 5):

| Plan | Question | Supports | Done |
|---|---|---|---|
| **Plan 2: per-model check** | Is this user program Pareto-optimal? | "Group A obtained n distinct Pareto-optimal vectors, group B m" | first |
| **Plan 1: ground truth** | What is the whole Pareto front of the level? | "Group A covered x% of the front"; distance of dominated programs to the front | second, by extending the plan 2 search |

## 2. Decisions taken

| # | Decision | Reason |
|---|---|---|
| 1 | The comparison objectives are **Actions and Blocks** only (power in kW and memory in kB in the game's readout) | Fair to both conditions: a user without the tool has no notion of Edits. The tool keeps exploring with its three objectives |
| 2 | The starting program is **empty** for every user and level | The game always starts empty, so one reference per level is enough |
| 3 | The reference is a set of **(Actions, Blocks) vectors**, not of programs | Two programs with the same vector are one point; program equivalence is not needed |
| 4 | The search is bounded by **K blocks, with K the size of a known straight-line solution** of the level | Provably complete: no program above K blocks is on the front (section 4) |
| 5 | User programs are scored like any other: off the front or not reaching the goal is **not a problem** | We only count which front vectors users obtain |
| 6 | **Between-subject design**: a participant belongs to one condition (manual or tool-assisted) and does all levels in it | Decided 2026-10-14 |
| 7 | The manual condition is **plain Blockly Games** (`maze-original.html`) | Decided 2026-10-14 |
| 8 | Actions and Blocks of a user program are obtained by **replaying the logged program** through the game's own simulator, for both conditions | The logs have the program, not its outcome (section 7) |
| 9 | **Levels 5–10 only** | Levels 1–4 are exercise levels in the study (decided 2026-10-16) |
| 10 | **Two plans**, plan 2 (per-model check) first, then plan 1 (ground truth) | Plan 2 needs much smaller searches and already answers whether a user program is optimal (decided 2026-10-16) |
| 11 | **Candidates loaded from the solution panel count as obtained**, like runs; the CSV column `type` tells them apart (`candidate_exploration` = loaded, `program_run` = run) | Obtaining a model through the tool is what the tool is for (decided 2026-10-16). A candidate loaded and then run unchanged appears twice; measures on distinct vectors count it once |

## 3. The reference: case definition

| Part | Definition | Where it comes from |
|---|---|---|
| Level | `blocky_momot/model/input/N.xmi`, N = 5..10 | the benchmark inputs |
| Starting program | empty | the game |
| Vocabulary | F, L, R, repeat-until-goal, if / if-else on path ahead, left, right | what the game offers when `blocky.levelLimits=false` |
| Constraints | none applied (`maxBlocks`, `allowLoops`, `allowConditionals` ignored) | the reference is the superset of what the UI lets a user build |
| Which programs count | only programs that reach the real goal | as in the gated objectives |
| Actions | `BlockySimulator.simulationSteps` | `blocky_custom._createObjectiveHelper_2` |
| Blocks | `BlockyProgramMetrics.countStatements` | `blocky_custom._createObjectiveHelper_4` |

## 4. What the search can rely on

### How Actions are counted
`BlockySimulator.executeLikeGame` (`BlockySimulator.java:189`):
- a move (also a failing one) and a turn cost 1 action each; the path check of an if block costs 1 action; a loop block costs nothing;
- a loop repeats while Pegman is not on the goal, checked at the start of each iteration;
- the whole program runs, also after the goal is reached; the goal is judged by the final position.

This corrects the premise of the first version: the fewest actions a level needs is **not** the shortest route, it is the shortest route counting **moves plus turns**.

### A_min and the opportunistic solution
**A_min** = the fewest moves plus turns from the start (with its start orientation, `determineStartOrientation`) to the goal: a shortest path over (cell, heading), where a move and a turn cost 1. Computed from the map, no enumeration. The **opportunistic solution** is the straight-line program along that route: A_min actions and A_min blocks. No program has fewer than A_min actions, because an if-check only adds actions.

### Bound K (why the search can stop)
Let L be the size of any known straight-line program that reaches the goal (at best L = A_min). No program P with more than L blocks is on the front:
- if Actions(P) ≥ L, that straight line has fewer blocks and no more actions;
- if Actions(P) < L, the **trace** of P (its executed moves and turns written out as a straight line) reaches the same goal, has at most Actions(P) actions and as many blocks as actions, so fewer than L < Blocks(P).

So **K = A_min** per level. On level 10 a 15-block straight line is known (15 A, 15 B), so K ≤ 15.

### Rules that skip programs without losing a front vector
Each rule replaces a program by one at least as good on both objectives. Argued here; confirmed empirically (section 9, check 2).

| Rule | Argument |
|---|---|
| **R1: no if outside a loop** | Outside a loop an if runs once in a fixed state, so it always takes the same branch. Replacing it by that branch saves 1 action and at least 1 block |
| **R2: nothing after the first top-level loop** | The loop ends only on the goal (or the program crashes and does not count). What follows costs blocks, does not save actions, and must end on the goal again. A loop that never runs (already on the goal) is removed the same way |
| **R3: no redundant turns and no crash in the prefix** | LR, RL, LLL, RRR leave the state unchanged or equal one turn, with 2 more actions and blocks; LL and RR give the same vector, so one is kept. A move into a wall ends the run without the goal |

| **R4: no empty if and no redundant turns anywhere** (also inside loop bodies) | An if with both parts empty, `a(|)`, costs 1 block and 1 action and changes nothing; LR, RL, LLL, RRR as in R3. Removing them inside a loop body keeps every iteration's effect, so the run ends in the same place with fewer actions |

After R1–R3 every candidate has the form **S · [W(B)]**: a straight-line prefix S of moves and turns, optionally followed by one loop with body B. B is unrestricted (full vocabulary, ifs and nested loops allowed); it is the only expensive part.

### Action budget
Once a goal-reaching program with Actions a and Blocks b is known, a program with more than b blocks matters only with fewer than a actions. Its if-checks are at most a − 1 − A_min, since moves and turns take at least A_min. Used in two ways:
- **in the simulation**: a run is cut off as soon as it exceeds the budget (cost only, no effect on the result);
- **in plan 1, to exclude loop bodies with ifs** when the budget leaves fewer if-checks than a loop that goes round twice needs. Level 10 example: (17 A, 11 B) is known from the benchmark, so above 11 blocks only programs with ≤ 16 actions matter, i.e. at most 1 if-check with A_min = 15. **To prove carefully** (edge cases: an if nested in a branch or in an inner loop is not checked every iteration). Until proven, plan 1 reports such a level as "exact except loop bodies with ifs above m blocks", with m the largest size searched and the front compared at m − 1 and m.

### Search size
Programs per block limit, counted in closed form with the game grammar (a loop is last in its sequence). E1 counted without that rule (1,014,161 up to 5 blocks, with programs the game cannot build). "Reduced" is the S · [W(B)] form with 3^|S| as an upper bound for the prefixes (R3, R4 and crashes make it smaller):

| Block limit | Game grammar | Reduced (upper bound) |
|---|---|---|
| 4 | 35,329 | 2,240 |
| 5 | 791,504 | 42,051 |
| 6 | 18.8 million | 0.92 million |
| 7 | 462 million | 21.5 million |
| 8 | 11.7 billion | 527 million |

The count grows about 25 times per block. Speed per program is not yet measured; the 6-block search is timed first and extrapolated.

## 5. The search (shared by both plans)

**Tool** (code in a Maven module, script and results in `tools/ground-truth-analysis/`):
- `blocky_momot/src/blocky_momot/GroundTruthEnumerator.java`: the search, compiled by Maven, built on the maze reading and fast simulator of `LandscapeAnalysis.java`, with its own streaming generator (programs are produced one by one, not stored; `LandscapeAnalysis` keeps them all in memory, which does not scale past a few million);
- `tools/ground-truth-analysis/run.py`: compiles the project and runs the search;
- `tools/ground-truth-analysis/output/level{N}_b{limit}.csv`: one table per level and searched block limit, e.g. `level10_b8.csv` = every program up to 8 blocks on level 10. A table judges only models up to its limit; a larger search writes a new file (plan 1 ends with `level{N}_b{K}.csv`), and labelling uses the file with the largest limit of a level. A search that also stops at an action limit adds it to the name: `level{N}_b{limit}_a{actions}.csv`, e.g. `level10_b15_a15.csv` = every program up to 15 blocks with at most 15 actions; labelling uses such a table only for models with at most that many actions.

Prototype first, clean up afterwards.

**Game grammar:** "repeat until goal" (`maze_forever`) has no next connection, so a loop is always the **last block of its sequence**, and `BlockyProgramMetrics.countStatements` stops counting after a loop. The generator follows this; a program such as `W(F)F` cannot be built in the game and would be executed with a block not counted.

One search per level, up to a block limit (plan 2: section 6; plan 1: K):
1. **Generate** every program up to the limit, in the reduced form of section 4 (for limits ≤ 5, also the full vocabulary, to check the rules). Bodies may be empty; an if without else equals an if with empty else (the E1 convention).
2. **Simulate** each with the fast simulator, cut off at the action budget. Keep goal-reaching programs only.
3. **Record every reachable (Blocks, Actions) pair** with one example program (all pairs, not only the fewest actions per block count, so that plan 2 can report the *closest* dominating program).
4. **Confirm** every recorded example with the real code (build the EMF `Body`, call `BlockySimulator` and `BlockyProgramMetrics`), so the result cannot drift from the game.
5. **Write** `tools/ground-truth-analysis/output/level{N}_b{limit}.csv`: blocks, actions, example program.

The non-dominated entries of this table are the front up to the limit. With two objectives the front has at most one vector per block count. Pairs reached only by programs the rules skip are not in the table; this can make the reported dominating program less close, never the verdict wrong.

**Labelling** (plan 2, cheap, rerun when new participant data arrives): `python tools/ground-truth-analysis/run.py label --models <models CSV>` reads the replay's models CSV and the tables and fills in the plan 2 columns. Per goal-reaching model: dominated if another user's model or a table program beats it; optimal if not and the tables cover its block count; otherwise **not covered**: no verdict, and it prints the level, the model and the exact `search` command (block limit) that would cover it. No "not covered" line means no search is needed. Optional `--extend` runs those searches itself and labels again (off by default, because a large limit can take long).

## 6. Plan 2: is each user program Pareto-optimal?

**Input:** the models CSV of the replay (section 7), goal-reaching programs (`status = WON`) of levels 5–10, both runs and loaded candidates (decision 11). Pilot: 41 goal-reaching runs and 27 loaded candidates.

1. **Shortcut:** a program beaten by another user program of the same level (at least as good on both, better on one) is dominated, with that program as proof.
2. **Search** per level (section 5) up to the largest block count among the remaining programs.
3. **Verdict** for a program with Actions a and Blocks b: **dominated** if the search table has an entry with ≤ b blocks and ≤ a actions, better in one; **Pareto-optimal** otherwise. Because the search covers all programs within these limits, Pareto-optimal means optimal against every program, not only against other users.
4. **Output:** columns added to the models CSV:

| Column | Content |
|---|---|
| `program` | the user's program in program text (below) |
| `matchesLoadedCandidate` | for a `program_run` whose program text equals that of a candidate loaded earlier in the same session and level: the `index` of that candidate's row (the most recent such load); empty otherwise and for loaded candidates. Shows that the run *is* a loaded candidate, not that the participant took it from there; remove if it turns out misleading |
| `pareto` | `optimal`, `dominated`, or empty when the goal is not reached |
| `dominatedBy` | one program that dominates it, in program text |
| `dominatedBy_actions`, `dominatedBy_blocks` | its vector |
| `dominatedBy_source` | `search`, or `<session>#<index>` when another user's program dominates it |

When several programs dominate a user program, **one** is reported: the closest, i.e. the smallest (Actions difference + Blocks difference); ties go to fewer blocks.

**Program text** (the `LandscapeAnalysis` notation, one character per block, so the number of letters is the number of blocks): `F` move forward, `L` / `R` turn left / right, `W(…)` repeat until goal, `a(then|else)` / `l(…|…)` / `r(…|…)` if path ahead / left / right; the `|` is always written, so `a(F|)` is an if without else and `a(|)` an if with nothing inside. Example: `FFLW(a(F|R))` = move, move, turn left, repeat until goal { if path ahead: move, else: turn right }, 7 blocks. A program with loose stacks (section 11, item 2) is written as `stack1 + stack2`.

Every goal-reaching program is labelled, so the rule for the selected program per participant and level (section 11, item 1) can be chosen later without rerunning.

### Pilot points (2026-10-15 data, 4 sessions)
Goal-reaching vectors (Actions, Blocks) not beaten by another pilot program:

| Level | Points to check | Search limit | Expected size |
|---|---|---|---|
| 5 | (8,5), (16,4) | 5 blocks | 0.79 million in the game grammar; seconds |
| 6 | (28,5), (36,4) | 5 blocks | as level 5 |
| 7 | (21,5), (27,4) | 5 blocks | as level 5 |
| 8 | (53,6) | 6 blocks | 0.92 million reduced |
| 9 | (24,4), (12,8) | 8 blocks | up to 527 million reduced; if A_min = 12, only programs without ifs |
| 10 | (110,5), (101,6), (35,8) | 8 blocks | up to 527 million reduced; the hardest case |
| 10 | (15,15) | 15 blocks, ≤ 15 actions | if A_min = 15, only programs without ifs; small |

**Prediction:** on level 10, (15,15) and (110,5) are Pareto-optimal, because the earlier benchmark front also had them. No prediction for (101,6), (35,8) or levels 5–9 before A_min is known.

### A_min (computed 2026-10-16)
`python tools/ground-truth-analysis/run.py amin` → `tools/ground-truth-analysis/output/amin.csv`. Each route program was run with `BlockySimulator`: goal reached, Actions = Blocks = A_min on every level.

| Level | A_min (= K) | Moves + turns | Route program | Fewest actions in the pilot |
|---|---|---|---|---|
| 5 | 8 | 7 + 1 | `FFLFFFFF` | 8 (`FFLW(F)`, 5 blocks) |
| 6 | 20 | 16 + 4 | `FFFFLFFFFLFFFFLFFLFF` | 28 |
| 7 | 15 | 12 + 3 | `FFFFRFFFFRFFRFF` | 21 |
| 8 | 23 | 16 + 7 | `FFLFFLFFRFFRFFFRFLFRFFF` | 53 |
| 9 | 12 | 10 + 2 | `FLFFFLFFFFFF` | 12 (8 blocks) |
| 10 | 15 | 10 + 5 | `FFLFFRFFLFFLFRF` | 15 (the route program) |

Consequences: the pilot points (8,5) on level 5, (12,8) on level 9 and (15,15) on level 10 are at A_min, so a program dominating them needs the same actions with fewer blocks and **no if** (each check costs an action): their searches are small. On levels 6–8 no pilot model came near A_min; there the front reaches up to 20, 15 and 23 blocks, which matters for plan 1, not for plan 2.

## 7. Obtaining the user programs and their vectors

### What the game logs
Per participant, `blocky_game/level_sessions/<user>_<time>/events.jsonl`, with the Blockly XML of each program in `models/N.xml`; the admin page exports the same as session JSON:

| Event | Content | Use |
|---|---|---|
| `program_run` | the program at every press of Run, level, time on level | the programs users built |
| `candidate_exploration` | the candidate loaded from the solution panel, its XMI and `objectiveLine` | which candidate a tool user chose |
| `variant` field | `momot` or `original` | which condition |

The log has **no outcome** per run (no goal reached, Actions or Blocks), and **no "final selected program"** event. The manual page logs `program_run` events with `variant: original` (seen in the pilot, session `player_347`).

### Replay (built)
`python tools/ground-truth-analysis/run.py replay --sessions <folder>` (until 2026-10-16: `tools/report-goal-check/run.py`) runs `blocky_game.ReportGoalChecker`: parse the logged XML (`BlocklyXmlParser`), rebuild the program (`BlockyAstMapper.rebuildProgram`, the game's own conversion) on the clean level `N.xmi`, evaluate with `BlockySimulator` and `BlockyProgramMetrics`. It writes:
- `<name>_levels.csv`: per session and level: variant, runs, average active time of a run;
- `<name>_models.csv`: per program obtained: `program` (program text, section 6), `matchesLoadedCandidate`, reachesGoal, status, power (Actions), memory (Blocks). Both program columns added 2026-10-16; the remaining plan 2 columns come with the search.

The replay runs on the **clean level map**, so "goal" is always the real goal. The tool's own `objectiveLine` is not used as the vector: while a direct-manipulation marker is set, the simulator treats the marked cell as the goal (`BlockySimulator.determineWinCellType`), so a candidate found then is scored against the marker. Users cannot otherwise change the maze (`cycleCellType` has no callers).

## 8. Plan 1: the ground truth

1. Compute A_min per level (section 4); K = A_min.
2. Extend the plan 2 search per level to K blocks, using the reduced form and the action budget. The 12–15 block range on level 10 is expected to leave only straight lines and loops without ifs, which are cheap.
3. Where the budget argument is not proven for a level, report it as "exact up to loop bodies with ifs of m blocks" with the m − 1 vs m comparison (section 4).
4. Output: `tools/ground-truth-analysis/output/level{N}_b{K}.csv`; its non-dominated entries are the front. A summary per level states whether the front is exact or exact up to m.

## 9. Measures and verification

### Measures
| Measure | Plan | Definition |
|---|---|---|
| Goal reached | – | the program reaches the goal |
| Pareto-optimal | 2 | not dominated by any program (section 6) |
| Distinct optimal vectors | 2 | number of different Pareto-optimal vectors obtained per condition |
| Position on the front | 2 | which front vector: the fewest-actions end (opportunistic solution), the fewest-blocks end, or between |
| Front coverage | 1 | share of front vectors obtained by at least one participant, per condition |
| Gap | 1 | for a dominated program: smallest additive increase of Actions and Blocks from a front vector that dominates it (0 if optimal) |

**Caution:** the opportunistic solution is usually Pareto-optimal and needs no exploration (write the route out step by step). A yes/no "obtained an optimal program" can therefore make both conditions look alike; distinct vectors, position and coverage show whether a group explored the trade-off.

Test: unpaired, because the groups are different participants. Fisher's exact test for "Pareto-optimal", Mann-Whitney for the gap and the counts. Group sizes and assignment are **open** (section 11).

Optional diagnostic: the engine's front (saved `model/output` files) against the reference, which separates "the tool did not offer a Pareto solution" from "the tool offered it and the user did not choose it". The claim does not rest on it.

### Verification of the search
1. Program counts of the full vocabulary equal the closed-form count.
2. **Rules R1–R3:** on levels 5–7, the search in the full vocabulary and in the reduced form up to 5 blocks give the same table. A difference means a rule is wrong.
3. Fast simulator against `BlockySimulator` on goal reached and actions: 0 differences (re-run, not assumed).
4. A_min from the shortest path equals the fewest actions found by the search.
5. Benchmark fronts (levels 8–10) and all pilot points: each equals or is dominated by a search entry. A point outside is a falsification and is investigated first.
6. Every proof program (`dominatedBy`) confirmed with the real code.

### Verification of the replay
7. Map: each `N.xmi` has the same cells as the maze the web page shows for that level.
8. Replay of the saved tool candidates (searches without a direct-manipulation marker) gives the Actions and Blocks stored with them.

## 10. Order of work

1. ~~Compute A_min and K per level (levels 5–10); compare with the pilot points.~~ Done 2026-10-16 (section 6).
2. Build the search; run levels 5–7 at 5 blocks in both forms (verification 1–3). **Checks 1 and 2 passed 2026-10-16** (`run.py check-sim --level 5 --level 6 --level 7`): 791,504 programs generated = closed form; fast simulator and `BlockySimulator` agree on goal reached and Actions for every program on levels 5, 6, 7 (0 differences; 203, 205 and 184 programs reach the goal); 9 s. **Check 3 passed 2026-10-16** (`run.py check-rules --level 5 --level 6 --level 7`): the reduced mode (10,463 programs, 1.3% of 791,504) gives the same front as all programs on levels 5, 6, 7; it misses one reached pair per level, reached only by skipped programs. Fronts up to 5 blocks (Blocks, Actions): level 5 (4,15) `W(Fl(L|))`, (5,8) `FFLW(F)`; level 6 (4,36) `W(Fl(L|))`, (5,28) `W(FFl(L|))`; level 7 (4,27) `W(Fr(R|))`, (5,21) `W(FFr(R|))`. No program with fewer than 4 blocks reaches the goal on these levels. **Check 4 passed 2026-10-16** (part of `check-sim`): fewest Actions of a goal-reaching program up to 5 blocks is 8, 28, 21 against A_min 8, 20, 15 (level 5 reaches A_min with 5 blocks). **Check 5 passed 2026-10-16** (`run.py check-models --level 5 --level 6 --level 7`): all 16 goal-reaching pilot models up to 5 blocks on levels 5–7 are generated programs with the same Actions and Blocks as the replay. The other 17 goal-reaching models on these levels (more than 5 blocks) all have at least 8, 28, 21 Actions, so the 5-block fronts already dominate them: levels 5–7 need no larger search. Tables written with `run.py search --level 5 --level 6 --level 7`: `output/level5_b5.csv`, `level6_b5.csv`, `level7_b5.csv` (22, 28, 35 pairs; every example confirmed by `BlockySimulator`). **Step 2 done.** **Streaming generator done 2026-10-16**: programs are produced one by one (only sequences up to 4 blocks are cached), so memory no longer limits the block count. Same order as before: the tables for levels 5–7 at 5 and 6 blocks are byte-identical to those of the in-memory generator, and checks 1–5 give the same results.
3. Time the 6-block search on level 8; extrapolate to 8 blocks.
4. Run levels 9 and 10 at 8 blocks, and (15,15) on level 10.
5. Plan 2: label the pilot models CSV; check verification 4–6.
6. Plan 1: work out the budget argument; extend the searches to K; front per level.
7. Write the results into this file.
8. Write `tools/ground-truth-analysis/README.md`: a short description of how plan 2 is executed, so a reviewer can reproduce the results (prerequisites, commands in order, inputs, outputs and their columns, verification checks with expected outcomes).

## 11. Open decisions

1. **Selected program per participant and level:** the last goal-reaching program, or the best (Actions, Blocks) among all goal-reaching runs on the level. Can be decided after plan 2, since every program is labelled.
2. **Loose blocks:** the replay runs loose (unconnected) block stacks one after another and counts all their blocks, which may differ from what the game runs. **First check what the game runs** when Run is pressed with a loose stack, then make the replay match it (a fact, not a choice). Cases: `sed_20261010T185010Z` level 5 (earlier data, an empty loop); pilot 2026-10-15: `player_347` #45, level 10, goal reached, 112 A / 6 B (its verdict depends on this), and `powidl` #117, level 4 (exercise level).
3. **Map equality** (verification 7): not yet compared.
4. **Replay against stored candidate values** (verification 8): not yet done.
5. **Group assignment and size:** how participants are assigned (random), and how many per group.
6. **Instruction:** both groups told the same goal (few steps, few blocks), otherwise the manual group is not aiming at the front.
7. **Vocabulary per level:** the reference uses the full vocabulary; if `levelLimits=true` is used in the study, the reference must be built in the user's vocabulary.
8. **Budget argument** (section 4): prove it, or accept "exact up to m" for the levels where it does not apply.

## 12. Out of scope

- Levels 1–4 (exercise levels).
- Edits as a compared objective.
- Program equivalence beyond objective vectors.
- Changes to the engine or the objectives.
