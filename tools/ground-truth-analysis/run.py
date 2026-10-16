#!/usr/bin/env python3
"""Pareto analysis of participant models (see README.md in this folder).

Usage: python tools/ground-truth-analysis/run.py <mode> [options]

  replay  replays every model of the session reports in --sessions (default: raw_user_report) with the game's own code
        (blocky_game.ReportGoalChecker) and writes output/<sessions folder>_levels.csv (per session and level: variant,
        runs, average active time of a run) and output/<sessions folder>_models.csv (per model: program text, the index
        of a loaded candidate it equals, goal reached, power = Actions, memory = Blocks). Levels >= --from-level only.
  amin  per level: A_min, the fewest Actions any program can have (fewest moves plus turns from start to goal),
        with the straight-line program along that route checked by BlockySimulator. Writes output/amin.csv.
  search  per level: every (Blocks, Actions) pair reached by a goal-reaching program up to --max-blocks (skipping
        useless programs), with one example program, whether it is on the front, and whether BlockySimulator confirms
        the example. Writes output/level{N}_b{max-blocks}.csv.
  check-sim  checks 1, 2 and 4: generates every program the game can build up to --max-blocks, compares the count with
        the closed form, compares the fast simulator with BlockySimulator on every program, and checks that no
        goal-reaching program has fewer Actions than A_min.
  check-rules  check 3: skipping useless programs keeps the same front as trying all programs, up to --max-blocks.
  check-models  check 5: every goal-reaching model of the replay's models CSV (levels given, at most --max-blocks
        blocks) is a generated program, and the search gives it the same Actions and Blocks as the replay.

Compiles the project first and runs with the freshly compiled classes first on the classpath, so the current
BlockySimulator is used (the jars in the local Maven repository may be older).
"""
import argparse
import csv
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUTPUT = HERE / "output"
CP_FILE = ROOT / "blocky_momot" / "target" / "ground-truth-classpath.txt"
STUDY_LEVELS = [5, 6, 7, 8, 9, 10]  # levels 1-4 are exercise levels in the study

LEVEL_COLUMNS = ["session", "variant", "level", "runs", "active_avg_s"]
MODEL_COLUMNS = ["session", "index", "level", "type", "variant", "timestamp", "program", "matchesLoadedCandidate",
                 "reachesGoal", "status", "power_kW_actions", "memory_kB_blocks", "note"]


def find_tool(name):
    # shutil.which honours PATHEXT, so it also finds mvn.cmd on Windows
    path = shutil.which(name)
    if path is None:
        sys.exit(f"{name} not found on PATH")
    return path


def run(cmd):
    result = subprocess.run(cmd, cwd=ROOT)
    if result.returncode != 0:
        sys.exit(f"command failed ({result.returncode}): {' '.join(cmd)}")


def classpath():
    mvn = find_tool("mvn")
    run([mvn, "-q", "compile"])
    run([mvn, "-q", "-f", "blocky_momot/pom.xml", "dependency:build-classpath", f"-Dmdep.outputFile={CP_FILE}"])
    classes = [str(ROOT / m / "target" / "classes") for m in ("blocky_game", "blocky_momot", "blocky_model")]
    return os.pathsep.join(classes + [CP_FILE.read_text(encoding="utf-8").strip()])


def output_csv(sessions, kind):
    return OUTPUT / f"{Path(sessions).resolve().name}_{kind}.csv"


def replay(java, cp, sessions, levels_dir, from_level):
    """Runs ReportGoalChecker on the session reports and splits its one-row-per-event CSV into the two output CSVs."""
    with tempfile.TemporaryDirectory() as tmp:
        events_csv = Path(tmp) / "events.csv"
        cmd = [java, "-cp", cp, "blocky_game.ReportGoalChecker", str(Path(sessions).resolve()), "--csv", str(events_csv),
               "--levels", levels_dir]
        result = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.DEVNULL)
        if result.returncode != 0 or not events_csv.is_file():
            sys.exit(f"replay failed ({result.returncode})")
        with open(events_csv, newline="", encoding="utf-8") as f:
            rows = [r for r in csv.DictReader(f) if int(r["level"]) >= from_level]

    levels = {}  # (session, level) -> row, in order of first appearance
    for r in rows:
        # level_runs and level_active_avg_s are per session and level, so every event of the level carries the same values
        levels.setdefault((r["session"], r["level"]), {"session": r["session"], "variant": r["variant"], "level": r["level"],
                                                       "runs": r["level_runs"], "active_avg_s": r["level_active_avg_s"]})
    OUTPUT.mkdir(exist_ok=True)
    for kind, columns, table in (("levels", LEVEL_COLUMNS, levels.values()), ("models", MODEL_COLUMNS, rows)):
        path = output_csv(sessions, kind)
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
            w.writeheader()
            w.writerows(table)
        print(f"{len(table)} rows written to {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=["replay", "amin", "search", "check-sim", "check-rules", "check-models"])
    parser.add_argument("--sessions", default=str(HERE / "raw_user_report"),
                        help="replay, check-models: folder of session JSONs from the admin page (default: raw_user_report)")
    parser.add_argument("--from-level", type=int, default=5,
                        help="replay: first level written (default 5, because levels 1-4 are exercise levels)")
    parser.add_argument("--models", help="check-models: models CSV (default: the replay output of --sessions)")
    parser.add_argument("--levels-dir", default=str(ROOT / "blocky_momot" / "model" / "input"),
                        help="folder with the clean level files N.xmi (default: blocky_momot/model/input)")
    parser.add_argument("--level", type=int, action="append", help="level to compute (repeatable; default 5-10)")
    parser.add_argument("--max-blocks", type=int, default=5, help="search and checks: block limit (default 5)")
    args = parser.parse_args()

    levels = args.level or STUDY_LEVELS
    java = find_tool("java")
    cp = classpath()
    levels_dir = str(Path(args.levels_dir).resolve())
    if args.mode == "replay":
        replay(java, cp, args.sessions, levels_dir, args.from_level)
        return
    if args.mode == "amin":
        tool_args = ["amin", levels_dir, str(OUTPUT / "amin.csv")]
    elif args.mode == "search":
        tool_args = [args.mode, levels_dir, str(args.max_blocks), str(OUTPUT)]
    elif args.mode == "check-models":
        models = Path(args.models).resolve() if args.models else output_csv(args.sessions, "models")
        if not models.is_file():
            sys.exit(f"{models} not found; run the replay first")
        tool_args = [args.mode, levels_dir, str(args.max_blocks), str(models)]
    else:
        tool_args = [args.mode, levels_dir, str(args.max_blocks)]
    cmd = [java, "-Xmx4g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", *tool_args, *map(str, levels)]
    sys.exit(subprocess.run(cmd, cwd=ROOT).returncode)


if __name__ == "__main__":
    main()
