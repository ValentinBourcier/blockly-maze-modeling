# Pareto analysis of participant models

Decides for every model a participant obtained (levels 5–10) whether it is **Pareto-optimal** in two objectives, both minimised:

- **Power** = actions executed: each move, turn and if-check costs 1; a loop block costs nothing.
- **Memory** = number of blocks.

A model is **dominated** if some program reaches the goal with no more actions *and* no more blocks, and fewer of one. It is **optimal** if no program the game can build does that.

**Requirements:** Java 17+, Maven 3.9, Python 3. Run all commands from the repository root; each compiles the project first.

## Steps

**1. Replay the models** (whenever new session data arrives). Input: a folder with the session JSONs from the admin page (default `raw_user_report/`). Each model is rebuilt and run with the game's own code, because the logs hold only the Blockly XML.
```
python tools/ground-truth-analysis/run.py replay [--sessions <folder>]
```
Writes `output/<folder>_levels.csv` (per session and level: `variant`, `runs`, `active_avg_s`) and `output/<folder>_models.csv` (per model: `type`, `program`, `matchesLoadedCandidate`, `status`, `power_kW_actions`, `memory_kB_blocks`). `type` is `program_run` (run) or `candidate_exploration` (loaded from the tool's panel).

**2. Compute A_min** (once). A_min is the lowest possible power: the fewest moves plus turns from start to goal.
```
python tools/ground-truth-analysis/run.py amin
```
Writes `output/amin.csv`. Expected A_min for levels 5–10: 8, 20, 15, 23, 12, 15.

**3. Verify the search** (once, and after any code change).
```
python tools/ground-truth-analysis/run.py check-sim    --level N ...
python tools/ground-truth-analysis/run.py check-rules  --level N ...
python tools/ground-truth-analysis/run.py check-models --level N ... [--sessions <folder>]
```

| Command | Expected |
|---|---|
| `check-sim` | program count equals the closed-form count (791,504 up to 5 blocks); fast simulator and the game's `BlockySimulator` agree on every program (0 differences); no program below A_min |
| `check-rules` | skipping useless programs (below) keeps the same best trade-offs: `same front, ok` |
| `check-models` | every goal-reaching participant model up to 5 blocks is generated, with the same values as the replay: 0 different |

Passed on levels 5–10 (`check-sim` and `check-models` up to 5 blocks, `check-rules` up to 6 blocks).

**4. Build the tables** (once per level and limit).
```
python tools/ground-truth-analysis/run.py search --level N --max-blocks B
```
Tries every program up to B blocks; writes `output/level{N}_b{B}.csv`: every reached (`blocks`, `actions`) pair with an `example_program`, `front` = best trade-off, `confirmed_by_BlockySimulator` (must be `yes`). A table also limited to A actions is named `level{N}_b{B}_a{A}.csv`. Tables depend only on the level, so they stay valid for new data.

Built: level 5 up to 6 blocks, levels 6–10 up to 8 blocks. Levels 5 and 9 are complete (A_min is reached with 5 and 8 blocks, so no larger program can be optimal). Time per level: 7 blocks about 1.5 s (2.9 million programs), 8 blocks about 30 s (53 million), plus about 5 s start-up; each extra block multiplies the cost by about 18.

**5. Label the models** *(not yet implemented)* (after every step 1).
```
python tools/ground-truth-analysis/run.py label [--sessions <folder>] [--extend]
```
Replays the sessions (step 1), then per goal-reaching model: **dominated** if another participant's model or a table program beats it; else **optimal** if the tables cover its block count; else **not covered**, printing the `search` command that would cover it (`--extend` runs it). Adds `pareto`, `dominatedBy` (closest program that beats it), `dominatedBy_actions`, `dominatedBy_blocks`, `dominatedBy_source` (`search` or `<session>#<index>`).

## When to run what

| Situation | Run |
|---|---|
| From scratch | steps 1–5 |
| New session data | 5 (it replays first) |
| Step 5 prints "not covered" | the printed command, then 5 (or 5 with `--extend`) |
| Code changed | 2–5 |

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

## Skipped programs
Only programs that another program beats are skipped: an if outside a loop (runs once; its taken branch alone is cheaper), an if with both parts empty, and the turns LR, RL, LLL, RR (LL does the same as RR). Programs the game cannot build are never generated (nothing can follow a "repeat until goal" block).
