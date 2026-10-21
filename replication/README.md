# CLEM's user study replication package

This folder contains the scripts used to conduct the analysis of the CLEM paper, from the session reports of the participants (JSON) to the tables and plots. Besides the metrics per level (program runs, time, CLEM usage, best power and memory), it decides for every model a participant obtained whether it is **Pareto-optimal** in power and memory, by comparing it with a ground truth: an exhaustive search over the programs the game can build (see [Pareto analysis](#pareto-analysis)).

| Path | Content |
|---|---|
| `main.R` | the whole analysis, see [How to use](#how-to-use) |
| `compute_models.R`, `compute_labels.R` | replay the models of the sessions (Java) and label them (Python); both are skipped when their results are already in `data/` |
| `read.R`, `group.R`, `plot.R`, `table.R` | read, summarise and draw the metrics per level |
| `pareto.R`, `exploration.R` | count and draw the non-dominated models, and the models loaded from the CLEM exploration |
| `data/` | the session reports, one `.json` per participation, with their models (`data/models/`) and labels (`data/labels.csv`) |
| `ground_truth/` | the ground-truth tool (`run.py`), its tables (`ground_truth/tables/`, provided) and `load_outcomes.py` |
| `examples/` | two example sessions used to develop the package; not part of the study, not read by `main.R` |
| `plots/`, `tables/` | the results |

## How to install?

You need to have installed the `R language` and ensure `Rscript` is present in your path (on Windows it is in `C:\Program Files\R\R-<version>\bin`).
Launch the `R` REPL by typing `R` from a command line terminal and write:

```R
source("dependencies.R")
```

This will install the project dependencies (readr, dplyr ≥ 1.2, tidyr, ggplot2, jsonlite).

To compute the metrics of the models of new sessions, you also need to have installed `Java` (version 21) and `Maven`, and ensure `mvn` and `java` are present in your path.
If several versions of `Java` are installed, select the right one before launching the scripts, for instance:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

To label the models of new sessions, or to rebuild the ground truth, you also need `Python 3` (`python3`, or `python` on Windows).

Neither Java nor Python is needed to replicate the analysis from the models and labels already present in `data/models/` and `data/labels.csv`.

## How to use?

Make sure the `data/` subfolder contains one `.json` file per participation to the study. The variant of each session gives its condition: `original` is the control (the game without CLEM), `momot` is the treatment (the game with the CLEM exploration). The first 4 levels are training levels and are left out of the analysis.

Launch the `main.R` script from this folder using the following command:

```shell
Rscript main.R
```

It runs, in order:

1. **Replay the models** (`compute_models()`, Java). The session reports hold each program only as Blockly XML, so every model is rebuilt and run with the game's own code (`blocky_game.ReportGoalChecker`). Writes one CSV per session in `data/models/`, only for the sessions not computed yet: delete a CSV to compute it again.
2. **Metrics per level** of the sessions and the models (`read.R`, `group.R`, `plot.R`, `table.R`).
3. **Label the models** (`compute_labels()`, Python: `ground_truth/run.py label`). Writes `data/labels.csv`, only if it does not exist yet: delete it to label again, for instance after adding sessions.
4. **Non-dominated models** (`pareto.R`), over all participants and per variant.
5. **Models loaded from the CLEM exploration** (`exploration.R`), treatment only.

The scripts generate the plots supporting the analysis in the `plots/` subfolder and the tables in `tables/`:

| File | Content |
|---|---|
| `plots/per_level.png`, `plots/per_level_compact_*.png` | every metric per level and variant, and the compact version for the paper |
| `plots/distributions/` | the spread of each metric, to choose between mean and median |
| `tables/overall.csv`, `tables/overall.tex` | mean and median of every metric over all levels, per variant (LaTeX for the paper) |
| `plots/models_pareto_all.png`, `tables/models_pareto_all.csv` | goal-reaching runs per level by label, all participants, with a row `all` |
| `tables/models_pareto_all_models.csv` | every goal-reaching run with its label and the program that dominates it |
| `plots/models_pareto.png`, `tables/models_pareto.csv` | the same counts per level and variant |
| `plots/exploration_models.png`, `tables/exploration_models.csv`, `tables/exploration_per_level.csv` | the loaded models, see [Models loaded from the exploration](#models-loaded-from-the-exploration) |

The non-dominated counts cover **program runs** only: a candidate only loaded is not counted, a candidate the participant ran is a program run. Every run counts, also the same program run twice. The columns of the counts are `n_goal_models` = `n_optimal` + `n_dominated` + `n_not_covered`, `percent_optimal`, and `percent_optimal_of_decided` (without the not covered models).

### Models loaded from the exploration

A loaded model is one program loaded by one participant on one level: loading the same program again counts once (`n_loads` counts every load). In `tables/exploration_models.csv`, a participant "ran it after" the load (`run_after`) if they ran the same program later on that level, and "built it before" (`built_before`) if they had already run it earlier; a loaded program that was edited before running counts as not run. `tables/exploration_per_level.csv` adds per level the CLEM searches and loads counted in the session reports; the script stops if these loads differ from the loaded models.

What the participants did after each load is detailed by a separate script, not run by `main.R` yet:

```shell
python ground_truth/load_outcomes.py
```

It writes `tables/load_outcomes.csv` as a timeline: one case per loaded candidate, with the participant's own run before the load, every load (loads less than 1 s apart are a double click) and the next run. `index` points to the row of the model in `data/models/session_*.csv`. The column `note` gives the facts (ran it unchanged, edited, dropped, never ran it, with the tree edit distance in blocks), and `interpretation` what the case suggests, by fixed rules ("own model first" or "exploration first"); the session reports do not log why a participant searched. The rules are described at the top of the script.

## Pareto analysis

Every model a participant obtained on the levels 5–10 is compared in two objectives, both minimised:

- **Power** = actions executed: each move, turn and if-check costs 1; a loop block costs nothing.
- **Memory** = number of blocks.

A model is **dominated** if some program reaches the goal with no more actions *and* no more blocks, and fewer of one. It is **optimal** (non-dominated) if no program the game can build does that. The reference, the ground truth, is an exhaustive search over every program the game can build, up to a block limit, provided in `ground_truth/tables/` (see [Rebuilding and verifying the ground truth](#rebuilding-and-verifying-the-ground-truth)).

### The models and their labels

**Models** (`data/models/session_*.csv`, one per session), one row per model:

| Column | Meaning |
|---|---|
| `session`, `index` | the session, and the model's number in it (from 1) |
| `level`, `timestamp` | level and time of the model |
| `type` | `program_run` (the participant ran it) or `candidate_exploration` (loaded from CLEM's solution panel) |
| `program` | the program as text, see [Reading the program syntax](#reading-the-program-syntax) |
| `matchesLoadedCandidate` | for a run: the index of an earlier loaded candidate with the identical program |
| `status` | `WON` (reaches the goal), `CRASHED` (wall or loop limit), `RUNNING` (ended before the goal) |
| `power_kW_actions`, `memory_kB_blocks` | actions and blocks |

**Labels** (`data/labels.csv`): the columns above for levels 5–10, plus

| Column | Meaning |
|---|---|
| `pareto` | `optimal`, `dominated`, `not covered` (see below) or `no goal` |
| `table_max_blocks` | block limit of the table used for the level |
| `dominatedBy`, `dominatedBy_actions`, `dominatedBy_blocks` | for a dominated model: a program that beats it, on the best known front, with the smallest total gap in actions plus blocks (ties: fewer actions, then the table) |
| `dominatedBy_source` | `search` (from a table), `loop-search` (from a `level{N}_b{B}_body{K}_a{A}.csv` table), `amin-front` (from `amin_front.csv`) or `<session>#<index>` (another participant's model) |

A goal-reaching model is labelled with the table of the highest block limit of its level and all goal-reaching models of the participants on that level:
- **dominated**: a program beats it (from a table, a `loop-search` table, `amin-front` or another participant);
- **optimal**: nothing beats it and the table covers its block count (the table holds every program up to that many blocks, so nothing smaller can exist), or it has A_min actions (nothing has fewer, and `amin-front` holds the fewest blocks at A_min);
- **not covered**: nothing known beats it, but it has more blocks than the table; it is reported as unknown (see [Limitations](#limitations)).

## Rebuilding and verifying the ground truth

The ground truth is the reference every participant model is compared with: for each level, every program the game can build up to a block limit, and the best trade-offs among them. This section explains how it is built and verified. Running it is optional, because the tables are provided in `ground_truth/tables/`. It needs Java, Maven and Python; the commands run from this folder, and each compiles the game first.

**Why a bounded search is enough.** There are infinitely many programs, so the search cannot try them all. Three facts make a finite search sufficient:

1. **Search by blocks.** For a block limit B there are finitely many programs, so the search can try every one of them. A limit on actions would not do: blocks in a branch that never runs cost no actions, so there are infinitely many programs with few actions.
2. **A model is decided by the programs with at most its own number of blocks.** A program that beats a model has no more blocks than the model. So if the table holds every program up to the model's block count, it holds every program that could beat it, and the verdict (optimal or dominated) is exact. A model with more blocks than the table can still be shown dominated by a program in the table, but not shown optimal: it is *not covered*. Example: a model with 6 blocks is decided by a table up to 6 blocks or more; a 12-block model is not covered by a table up to 9 blocks unless a table program beats it.
3. **A_min tells when a level is complete.** Each move and turn costs one action and if-checks only add actions, so no program reaches the goal with fewer actions than A_min, the fewest moves plus turns on a route from start to goal. Once the table holds a goal-reaching program with A_min actions and b blocks, that program beats every program with more than b blocks (no fewer actions, more blocks), so no larger program can be optimal and the table's front is complete. On a level not complete yet, the gap between the table's lowest action count and A_min shows how much of the front is still unknown. A_min is also a check of the search: no program may need fewer actions (`check-sim`).

**A_min**, the lowest possible power (fewest moves plus turns from start to goal):
```
python ground_truth/run.py amin
```
Writes `ground_truth/tables/amin.csv`.

**The fewest blocks at A_min** (the front point with the fewest actions, whatever its block count):
```
python ground_truth/run.py amin-front
```
A program with A_min actions runs no if-check (each check is an action), so what it runs is a shortest route. Nothing follows a "repeat until goal" block, and the loop checks the goal only before each round, so the program is the straight route, or a straight start followed by one loop whose body, repeated a whole number of times, is the rest of the route. The mode tries every shortest route and every such split, keeps the fewest blocks, confirms the programs with `BlockySimulator`, and checks the result against the tables (where a table reaches that block count, its fewest blocks at A_min must be the same; elsewhere it must hold no program with A_min actions). Writes `ground_truth/tables/amin_front.csv` (`level`, `A_min`, `routes`, `min_blocks`, `programs_at_min`, `example_program`, the simulator's result, and `check`, which must be `ok`). Takes seconds. The labels use the file: a model with A_min actions is decided whatever its block count.

**Tables**, once per level and block limit:
```
python ground_truth/run.py search --level N --max-blocks B
```
Tries every program up to B blocks (skipping only programs another program beats, see below); writes `ground_truth/tables/level{N}_b{B}.csv`: every reached (`blocks`, `actions`) pair with an `example_program`, `front` = on the best trade-off, and `confirmed_by_BlockySimulator` (must be `yes`: the example is re-run with the game's simulator). Tables depend only on the level, so they stay valid for new data.

Provided: level 5 up to 6 blocks, level 9 up to 8 blocks, levels 6, 7, 8 and 10 up to 9 blocks. Time per level: 7 blocks about 1.5 s, 8 blocks about 30 s, 9 blocks 16–22 min; each extra block multiplies the number of programs by about 18.

**Larger programs with a small loop**, for a model too large for the tables:
```
python ground_truth/run.py loop-search --level N --max-blocks B --max-body K --max-actions A
```
Tries every program of a straight start followed by one loop whose body has at most K blocks, up to B blocks, and keeps every (`blocks`, `actions`) pair with at most A actions; writes `ground_truth/tables/level{N}_b{B}_body{K}_a{A}.csv` with the same columns as the tables (every example confirmed by `BlockySimulator`). It reaches larger programs than `search` because the body is small (each body is run once per cell and heading a start can end in), but it does not try every program, so its programs can show a model dominated, never optimal. Provided: level 10, `--max-blocks 14 --max-body 5 --max-actions 25` (about 11 s).

**Provided ground truth** (in `ground_truth/tables/`), built with these commands; the labels use, per level, the table with the highest block limit, plus `amin_front.csv` and the `loop-search` tables:
```
python ground_truth/run.py amin
python ground_truth/run.py amin-front
python ground_truth/run.py search --level 5 --max-blocks 6
python ground_truth/run.py search --level 9 --max-blocks 8
python ground_truth/run.py search --level 6 --level 7 --level 8 --level 10 --max-blocks 9
python ground_truth/run.py loop-search --level 10 --max-blocks 14 --max-body 5 --max-actions 25
```
The smaller tables (e.g. `level6_b7.csv`) are earlier steps and are not used. The 9-block searches take 16–22 min per level; everything else takes seconds to a minute.

**Skipped programs.** Only programs that another program beats are skipped: an if outside a loop (runs once; its taken branch alone is cheaper), an if with both parts empty, and the turns LR, RL, LLL, RR (LL does the same as RR). Programs the game cannot build are never generated (nothing can follow a "repeat until goal" block). `check-rules` verifies that skipping them keeps the same front.

**Checks** of the search:
```
python ground_truth/run.py check-sim    --level N ...
python ground_truth/run.py check-rules  --level N ...
python ground_truth/run.py check-models --level N ...
```

| Command | Expected |
|---|---|
| `check-sim` | program count equals the closed-form count; the search's fast simulator and the game's `BlockySimulator` agree on every program (0 differences); no program below A_min |
| `check-rules` | skipping the beaten programs keeps the same best trade-offs: `same front, ok` |
| `check-models` | every goal-reaching participant model up to 5 blocks (in `data/models/`) is generated by the search, with the same actions and blocks: 0 different, except models of loose stacks (below) |

A model reported different because of loose stacks, e.g. `F + W(Fr(R|))`, is correct if the table has the connected program (`FW(Fr(R|))`) with the same actions and blocks.

## Limitations

- **Not covered models.** A model with more blocks than its level's table can only be shown dominated, not optimal, unless it has A_min actions (`amin-front`). For a not covered model, `ground_truth/run.py label` prints the `search` command that would cover it, but tables large enough are out of reach (10 blocks take hours per level), so try `loop-search` first.
- **Loose stacks.** The game runs blocks that are not attached to each other one stack after the other (`+` in the text). The search builds only connected programs. The labels compare actions and blocks, so they are not affected; `check-models` compares the program text and reports such models as different.
- **Loads only.** The session reports log when a candidate is loaded and when a program is run, not the edits in between nor the time of each CLEM search (only their number per level). `load_outcomes.py` therefore compares snapshots, and cannot tell why a participant searched.

## When to run what

| Situation | Run |
|---|---|
| Reproduce the results | `Rscript main.R` |
| New session data | add the `.json` files to `data/`, delete `data/labels.csv`, then `Rscript main.R` (replays the new sessions only, then labels every model) |
| The game's simulator changed | delete `data/models/` and `data/labels.csv`, rebuild the ground truth (all commands under *Provided ground truth*) and run the checks, then `Rscript main.R` |
| New data with not covered models | `loop-search` on that level (block limit = the model's blocks, action limit = its actions), delete `data/labels.csv`, then `Rscript main.R` |

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
