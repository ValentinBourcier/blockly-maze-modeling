# Pareto analysis of participant models

This folder reproduces the analysis of the participants' models in the user study, from the session reports (JSON) to the tables and plots. For every model a participant obtained on the study levels 5–10 (levels 1–4 are exercises), it decides whether the model is **Pareto-optimal** in two objectives, both minimised:

- **Power** = actions executed: each move, turn and if-check costs 1; a loop block costs nothing.
- **Memory** = number of blocks.

A model is **dominated** if some program reaches the goal with no more actions *and* no more blocks, and fewer of one. It is **optimal** (non-dominated) if no program the game can build does that. The reference ("ground truth") is an exhaustive search over every program the game can build, up to a block limit.

## Requirements

- Java 17+ and Maven 3.9 (`java` and `mvn` on the PATH)
- Python 3
- R 4.x (`Rscript` on the PATH; on Windows it is in `C:\Program Files\R\R-<version>\bin`)

Once, install the R packages (readr, dplyr ≥ 1.2, tidyr, ggplot2, jsonlite), from the repository root:
```
cd replication
Rscript dependencies.R
```

## Input

One folder per condition, each holding the session reports (one `session_*.json` per participant, as downloaded from the game's admin page):

| Folder | Condition | `variant` in the JSON |
|---|---|---|
| `control_user_report/` | control: the game without CLEM | `original` |
| `treatment_user_report/` | treatment: the game with the CLEM exploration | `momot` |

The folders are set in `reports/study.R` (`session_folders`); the scripts stop if a folder holds a session of the other condition.

## Rebuilding and verifying the ground truth

The ground truth is the reference every participant model is compared with: for each level, every program the game can build up to a block limit, and the best trade-offs among them. This section explains how it is built and verified. Running it is optional, because the tables are provided in `output/`. Commands from the repository root; each compiles the game first.

**Why a bounded search is enough.** There are infinitely many programs, so the search cannot try them all. Three facts make a finite search sufficient:

1. **Search by blocks.** For a block limit B there are finitely many programs, so the search can try every one of them. A limit on actions would not do: blocks in a branch that never runs cost no actions, so there are infinitely many programs with few actions.
2. **A model is decided by the programs with at most its own number of blocks.** A program that beats a model has no more blocks than the model. So if the table holds every program up to the model's block count, it holds every program that could beat it, and the verdict (optimal or dominated) is exact. A model with more blocks than the table can still be shown dominated by a program in the table, but not shown optimal: it is *not covered*. Example: a model with 6 blocks is decided by a table up to 6 blocks or more; a 12-block model is not covered by a table up to 9 blocks unless a table program beats it.
3. **A_min tells when a level is complete.** Each move and turn costs one action and if-checks only add actions, so no program reaches the goal with fewer actions than A_min, the fewest moves plus turns on a route from start to goal. Once the table holds a goal-reaching program with A_min actions and b blocks, that program beats every program with more than b blocks (no fewer actions, more blocks), so no larger program can be optimal and the table's front is complete. Example: on level 5, A_min = 8 and `FFLW(F)` reaches the goal with 8 actions and 5 blocks, so the table up to 5 blocks already holds the complete front. On a level not complete yet, the gap between the table's lowest action count and A_min shows how much of the front is still unknown. A_min is also a check of the search: no program may need fewer actions (`check-sim`).

**A_min**, the lowest possible power (fewest moves plus turns from start to goal):
```
python tools/ground-truth-analysis/run.py amin
```
Writes `output/amin.csv`. Expected for levels 5–10: 8, 20, 15, 23, 12, 15.

**The fewest blocks at A_min** (the front point with the fewest actions, whatever its block count):
```
python tools/ground-truth-analysis/run.py amin-front
```
A program with A_min actions runs no if-check (each check is an action), so what it runs is a shortest route. Nothing follows a "repeat until goal" block, and the loop checks the goal only before each round, so the program is the straight route, or a straight start followed by one loop whose body, repeated a whole number of times, is the rest of the route. The mode tries every shortest route and every such split, keeps the fewest blocks, confirms the programs with `BlockySimulator`, and checks the result against the tables (where a table reaches that block count, its fewest blocks at A_min must be the same; elsewhere it must hold no program with A_min actions). Writes `output/amin_front.csv` (`level`, `A_min`, `routes`, `min_blocks`, `programs_at_min`, `example_program`, the simulator's result, `check` = `ok`). Takes seconds. Expected for levels 5–10: 5, 18, 13, 22, 8, 15 blocks; each level has one shortest route. Example: on level 7 the straight route `FFFFRFFFFRFFRFF` (15 actions, 15 blocks) becomes `FFFFRFFFFW(RFF)` (15 actions, 13 blocks); on level 10 no loop shortens it, so the straight route (15 blocks) is the fewest. `label` uses the file: a model with A_min actions is decided whatever its block count.

**Tables**, once per level and block limit:
```
python tools/ground-truth-analysis/run.py search --level N --max-blocks B
```
Tries every program up to B blocks (skipping only programs another program beats, see below); writes `output/level{N}_b{B}.csv`: every reached (`blocks`, `actions`) pair with an `example_program`, `front` = on the best trade-off, and `confirmed_by_BlockySimulator` (must be `yes`: the example is re-run with the game's simulator). Tables depend only on the level, so they stay valid for new data.

Provided: level 5 up to 6 blocks, level 9 up to 8 blocks, levels 6, 7, 8 and 10 up to 9 blocks. Levels 5 and 9 are complete (A_min is reached with 5 and 8 blocks, so no larger program can be optimal). Time per level: 7 blocks about 1.5 s (2.9 million programs), 8 blocks about 30 s (53 million), 9 blocks 16–22 min (992 million); each extra block multiplies the number of programs by about 18.

**Larger programs with a small loop**, for a model too large for the tables:
```
python tools/ground-truth-analysis/run.py loop-search --level N --max-blocks B --max-body K --max-actions A
```
Tries every program of a straight start followed by one loop whose body has at most K blocks, up to B blocks, and keeps every (`blocks`, `actions`) pair with at most A actions; writes `output/level{N}_b{B}_body{K}_a{A}.csv` with the same columns as the tables (every example confirmed by `BlockySimulator`). It reaches larger programs than `search` because the body is small (each body is run once per cell and heading a start can end in), but it does not try every program, so its programs can show a model dominated, never optimal. Provided: level 10, `--max-blocks 14 --max-body 5 --max-actions 25` (11 s): best pairs 11 blocks/19 actions, 13/18, 14/17; nothing up to 10 blocks reaches 25 actions or fewer, which agrees with the table up to 9 blocks (28 actions at best).

**Provided ground truth** (in `output/`), built with these commands; the label uses, per level, the table with the highest block limit, plus `amin_front.csv` and the `loop-search` tables:
```
python tools/ground-truth-analysis/run.py amin
python tools/ground-truth-analysis/run.py amin-front
python tools/ground-truth-analysis/run.py search --level 5 --max-blocks 6
python tools/ground-truth-analysis/run.py search --level 9 --max-blocks 8
python tools/ground-truth-analysis/run.py search --level 6 --level 7 --level 8 --level 10 --max-blocks 9
python tools/ground-truth-analysis/run.py loop-search --level 10 --max-blocks 14 --max-body 5 --max-actions 25
```
The smaller tables in `output/` (e.g. `level6_b7.csv`) are earlier steps and are not used. The 9-block searches take 16–22 min per level; everything else takes seconds to a minute.

**Skipped programs.** Only programs that another program beats are skipped: an if outside a loop (runs once; its taken branch alone is cheaper), an if with both parts empty, and the turns LR, RL, LLL, RR (LL does the same as RR). Programs the game cannot build are never generated (nothing can follow a "repeat until goal" block). `check-rules` verifies that skipping them keeps the same front.

**Checks** of the search:
```
python tools/ground-truth-analysis/run.py check-sim    --level N ...
python tools/ground-truth-analysis/run.py check-rules  --level N ...
python tools/ground-truth-analysis/run.py check-models --level N ... --sessions <folder>
```

| Command | Expected |
|---|---|
| `check-sim` | program count equals the closed-form count (791,504 up to 5 blocks); the search's fast simulator and the game's `BlockySimulator` agree on every program (0 differences); no program below A_min |
| `check-rules` | skipping the beaten programs keeps the same best trade-offs: `same front, ok` |
| `check-models` | every goal-reaching participant model up to 5 blocks (replayed in step 1 of [Reproducing the results](#reproducing-the-results)) is generated by the search, with the same actions and blocks: 0 different, except models of loose stacks (below) |

A model reported different because of loose stacks, e.g. `F + W(Fr(R|))`, is correct if the table has the connected program (`FW(Fr(R|))`) with the same actions and blocks.

## Reproducing the results

The ground truth (`output/level{N}_b{B}.csv`, `output/amin_front.csv` and `output/level10_b14_body5_a25.csv`, see [Rebuilding and verifying the ground truth](#rebuilding-and-verifying-the-ground-truth)) is provided, so three steps suffice. They take a few minutes in total, most of it compiling the game once.

**Step 1. Replay the models** (R, from `tools/ground-truth-analysis/reports/`). The JSON logs hold each program only as Blockly XML, so every model is rebuilt and run with the game's own code (`blocky_game.ReportGoalChecker`, through `compute_models()` of the replication package in `replication/`).
```
cd tools/ground-truth-analysis/reports
Rscript build_models.R
```
Writes `<folder>/models/session_*.csv`, one per session (all levels). Sessions that already have their CSV are skipped; delete a CSV to replay that session again. Other folders: `Rscript build_models.R ../<folder>`.

**Step 2. Label the models** (Python, from the repository root). Needs only Python and takes about a second per folder.
```
cd ../../..
python tools/ground-truth-analysis/run.py label --sessions tools/ground-truth-analysis/control_user_report
python tools/ground-truth-analysis/run.py label --sessions tools/ground-truth-analysis/treatment_user_report
```
Writes `output/<folder>_labelled.csv` and prints per level the goal-reaching models (program runs and loaded candidates) by label. It uses every ground-truth file in `output/` (see above). If a model is not covered, it prints the `search` command that would cover it; such searches are usually out of reach, so try `loop-search` first (see [Limitations](#limitations)). On the study data no model is not covered.

**Step 3. Build the reports** (R, from `tools/ground-truth-analysis/reports/`).
```
cd tools/ground-truth-analysis/reports
Rscript models_pareto_all.R    # goal-reaching runs of all participants: non-dominated, dominated, not covered
Rscript models_pareto.R        # the same per condition (control, treatment)
```
Each script writes `reports/tables/<name>.csv` and `reports/plots/<name>.png` (see [Outputs](#outputs)).

**Expected results** on the study data: 82 goal-reaching program runs, none of them not covered.

All participants together (`models_pareto_all.R`, `reports/tables/models_pareto_all.csv`):

| Level | 5 | 6 | 7 | 8 | 9 | 10 | All |
|---|---|---|---|---|---|---|---|
| Non-dominated | 8 | 2 | 2 | 0 | 4 | 9 | 25 |
| Dominated | 8 | 8 | 11 | 14 | 6 | 10 | 57 |
| Not covered | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

Per condition (`models_pareto.R`, `reports/tables/models_pareto.csv`), non-dominated / dominated:

| Level | 5 | 6 | 7 | 8 | 9 | 10 | All |
|---|---|---|---|---|---|---|---|
| Control (43 runs) | 2 / 4 | 1 / 4 | 1 / 5 | 0 / 7 | 1 / 4 | 5 / 9 | 10 / 33 |
| Treatment (39 runs) | 6 / 4 | 1 / 4 | 1 / 6 | 0 / 7 | 3 / 2 | 4 / 1 | 15 / 24 |

## Outputs

**Models** (`<folder>/models/session_*.csv`, step 1), one row per model:

| Column | Meaning |
|---|---|
| `session`, `index` | the session, and the model's number in it (from 1) |
| `level`, `timestamp` | level and time of the model |
| `type` | `program_run` (the participant ran it) or `candidate_exploration` (loaded from CLEM's solution panel) |
| `program` | the program as text, see [Reading the program syntax](#reading-the-program-syntax) |
| `matchesLoadedCandidate` | for a run: the index of an earlier loaded candidate with the identical program |
| `status` | `WON` (reaches the goal), `CRASHED` (wall or loop limit), `RUNNING` (ended before the goal) |
| `power_kW_actions`, `memory_kB_blocks` | actions and blocks |

**Labels** (`output/<folder>_labelled.csv`, step 2): the columns above for levels 5–10, plus

| Column | Meaning |
|---|---|
| `pareto` | `optimal`, `dominated`, `not covered` (see below) or `no goal` |
| `table_max_blocks` | block limit of the table used for the level |
| `dominatedBy`, `dominatedBy_actions`, `dominatedBy_blocks` | for a dominated model: a program that beats it, on the best known front, with the smallest total gap in actions plus blocks (ties: fewer actions, then the table) |
| `dominatedBy_source` | `search` (from the table), `loop-search` (from a `level{N}_b{B}_body{K}_a{A}.csv` table), `amin-front` (from `output/amin_front.csv`) or `<session>#<index>` (another participant's model) |

How a goal-reaching model is labelled, with the table of the highest block limit of its level and all goal-reaching models of the folder on that level:
- **dominated**: a program beats it (from a table, a `loop-search` table, `amin-front` or another participant);
- **optimal**: nothing beats it and the table covers its block count (the table holds every program up to that many blocks, so nothing smaller can exist), or it has A_min actions (nothing has fewer, and `amin-front` holds the fewest blocks at A_min);
- **not covered**: nothing known beats it, but it has more blocks than the table; it is reported as unknown (see [Limitations](#limitations)).

**Reports** (step 3). They count **program runs** only: a candidate only loaded is not counted, a candidate the participant ran is a program run. Every run counts, also the same program run twice.

| Script | Table columns | Plot |
|---|---|---|
| `models_pareto_all.R` | per level (all participants) and a row `all`: `n_goal_models` = `n_optimal` + `n_dominated` + `n_not_covered`; `percent_optimal`; `percent_optimal_of_decided` (without not covered). `models_pareto_all_models.csv` lists every goal-reaching run with its label and the `dominated_by` program | goal-reaching runs by label, per level |
| `models_pareto.R` | the same columns per level and condition | goal-reaching runs by label, one panel per condition |

## Limitations

- **Not covered models.** A model with more blocks than its level's table can only be shown dominated, not optimal, unless it has A_min actions (`amin-front`). On the study data no run is left not covered: the straight routes are decided by `amin-front`, and `FFLFFRFW(l(LF|a(F|R)))` on level 10 (25 actions, 14 blocks) is dominated by `FFLFFRFFW(LFa(|R)F)` (17 actions, 14 blocks) from `loop-search`. New data may bring not covered models again; tables large enough are out of reach (10 blocks take hours per level), so try `loop-search` first.
- **Loose stacks.** The game runs blocks that are not attached to each other one stack after the other (`+` in the text). The search builds only connected programs. The label compares actions and blocks, so it is not affected; `check-models` compares the program text and reports such models as different.

## When to run what

| Situation | Run |
|---|---|
| Reproduce the results | steps 1–3 |
| New session data | steps 1–3 |
| Other folder names | edit `session_folders` in `reports/study.R`, then steps 1–3 |
| The game's simulator changed | delete `<folder>/models/`, rebuild the ground truth (all commands under *Provided ground truth*) and the checks, then steps 1–3 |
| New data with not covered models | `loop-search` on that level (block limit = the model's blocks, action limit = its actions), then steps 2–3 |

## Reading the program syntax
The columns `program`, `example_program` and `dominatedBy` write a program as text, read left to right like the blocks top to bottom. Each letter is one block, so **the number of letters is the memory (blocks)**.

| Text | Block |
|---|---|
| `F` | move forward |
| `L` / `R` | turn left / turn right |
| `W(body)` | repeat until goal, with the blocks inside it |
| `a(then\|else)` | if path ahead: *then* blocks, else: *else* blocks |
| `l(then\|else)` / `r(then\|else)` | if path to the left / to the right |

- The `|` is always written; an empty part means no blocks there: `a(F|)` is an if without else, `a(|L)` does nothing when the path is free.
- Brackets nest: blocks inside a bracket belong to that loop or if.
- Nothing follows a `W(…)` in the same sequence (the game's repeat block has no connection below it).
- `+` separates loose stacks (blocks not attached to each other), which run one after another.

Examples:

| Text | Reads as | Blocks |
|---|---|---|
| `FFLW(F)` | move, move, turn left, repeat until goal { move } | 5 |
| `W(Fl(L\|))` | repeat until goal { move; if path to the left { turn left } } | 4 |
| `W(a(F\|L))` | repeat until goal { if path ahead { move } else { turn left } } | 4 |
| `W(l(LF\|)F)` | repeat until goal { if path to the left { turn left, move }; move } | 5 |
