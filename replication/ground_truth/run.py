#!/usr/bin/env python3
"""Pareto analysis of participant models (see README.md in this folder).

Usage: python ground_truth/run.py <mode> [options]   (from the replication folder)

The models of the participants are read from <--sessions folder>/models/*.csv (default: replication/data; levels 5-10),
one CSV per session written by compute_models() of the replication package (replication/compute_models.R), which replays the session reports with the game's own code
(blocky_game.ReportGoalChecker): per model the program text, the index of a loaded candidate it equals, goal reached,
power = Actions, memory = Blocks.

  amin  per level: A_min, the fewest Actions any program can have (fewest moves plus turns from start to goal),
        with the straight-line program along that route checked by BlockySimulator. Writes tables/amin.csv.
  amin-front  per level: the fewest Blocks of a program with A_min Actions (a straight line, or a straight start and
        one loop), with an example checked by BlockySimulator, and checks it against the search tables. Writes
        tables/amin_front.csv, which label uses: a model with A_min Actions is decided whatever its block count.
  loop-search  per level: like search, but only programs of a straight start and one loop whose body has at most
        --max-body blocks, up to --max-blocks blocks, keeping pairs with at most --max-actions Actions. Reaches larger
        programs than search, but not all of them: its programs can show a model dominated, never optimal. Writes
        tables/level{N}_b{max-blocks}_body{max-body}_a{max-actions}.csv, which label uses.
  search  per level: every (Blocks, Actions) pair reached by a goal-reaching program up to --max-blocks (skipping
        useless programs), with one example program, whether it is on the front, and whether BlockySimulator confirms
        the example. Writes tables/level{N}_b{max-blocks}.csv.
  check-sim  checks 1, 2 and 4: generates every program the game can build up to --max-blocks, compares the count with
        the closed form, compares the fast simulator with BlockySimulator on every program, and checks that no
        goal-reaching program has fewer Actions than A_min.
  check-rules  check 3: skipping useless programs keeps the same front as trying all programs, up to --max-blocks.
  check-models  check 5: every goal-reaching model of --sessions (levels given, at most --max-blocks
        blocks) is a generated program, and the search gives it the same Actions and Blocks as the replay.
  label  labels every goal-reaching model of --sessions: dominated if a table program, a loop-search program, the
        amin-front program or another participant's model beats it, optimal if the level's largest table covers its block count or it has
        A_min Actions (with tables/amin_front.csv), else not covered (prints the search
        command that covers it; --extend runs it). Writes <sessions folder>/labels.csv. Needs Java and Maven
        only with --extend.

Compiles the project first and runs with the freshly compiled classes first on the classpath, so the current
BlockySimulator is used (the jars in the local Maven repository may be older).
"""
import argparse
import csv
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TABLES = HERE / "tables"  # the ground truth: search tables and amin files
CP_FILE = ROOT / "blocky_momot" / "target" / "ground-truth-classpath.txt"
STUDY_LEVELS = [5, 6, 7, 8, 9, 10]  # levels 1-4 are exercise levels in the study

MODEL_COLUMNS = ["session", "index", "level", "type", "variant", "timestamp", "program", "matchesLoadedCandidate",
                 "reachesGoal", "status", "power_kW_actions", "memory_kB_blocks", "note"]
LABEL_COLUMNS = MODEL_COLUMNS + ["pareto", "table_max_blocks", "dominatedBy", "dominatedBy_actions",
                                 "dominatedBy_blocks", "dominatedBy_source"]
TABLE_NAME = re.compile(r"level(\d+)_b(\d+)\.csv$")  # action-limited tables (_a{A}) are not used
LOOP_TABLE_NAME = re.compile(r"level(\d+)_b(\d+)_body(\d+)_a(\d+)\.csv$")  # loop-search: dominating programs only


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


def labels_csv(sessions):
    return Path(sessions) / "labels.csv"


def read_models(sessions):
    """The models of the study levels in <sessions>/models/*.csv, one CSV per session (written by compute_models())."""
    files = sorted((Path(sessions) / "models").glob("*.csv"))
    if not files:
        sys.exit(f"no models in {Path(sessions) / 'models'}; run compute_models() of replication/compute_models.R on {sessions} first")
    models = []
    for path in files:
        with open(path, newline="", encoding="utf-8") as f:
            models += [r for r in csv.DictReader(f) if int(r["level"]) in STUDY_LEVELS]
    return models


def largest_tables():
    """Per level: (block limit, [(actions, blocks, example program)]) of its table with the highest block limit."""
    tables = {}
    for path in TABLES.glob("level*_b*.csv"):
        m = TABLE_NAME.fullmatch(path.name)
        if m and int(m[2]) > tables.get(int(m[1]), (0,))[0]:
            tables[int(m[1])] = (int(m[2]), path)
    result = {}
    for level, (limit, path) in tables.items():
        with open(path, newline="", encoding="utf-8") as f:
            result[level] = (limit, [(int(r["actions"]), int(r["blocks"]), r["example_program"]) for r in csv.DictReader(f)])
    return result


def loop_tables():
    """Per level: [(actions, blocks, example program)] of every loop-search table, all confirmed by BlockySimulator."""
    result = {}
    for path in TABLES.glob("level*_b*_body*_a*.csv"):
        m = LOOP_TABLE_NAME.fullmatch(path.name)
        if not m:
            continue
        with open(path, newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        if any(r["confirmed_by_BlockySimulator"] != "yes" for r in rows):
            sys.exit(f"{path} has unconfirmed rows; run loop-search again")
        result.setdefault(int(m[1]), []).extend((int(r["actions"]), int(r["blocks"]), r["example_program"]) for r in rows)
    return result


def amin_front():
    """Per level: (A_min, fewest Blocks at A_min, example program) from tables/amin_front.csv, or {} without it."""
    path = TABLES / "amin_front.csv"
    if not path.exists():
        return {}
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if any(r["check"] != "ok" for r in rows):
        sys.exit(f"{path} has unconfirmed rows; run amin-front again")
    return {int(r["level"]): (int(r["A_min"]), int(r["min_blocks"]), r["example_program"]) for r in rows}


def check_amin_front():
    """The search tables must agree with tables/amin_front.csv: where a table holds programs with min_blocks, its
    fewest Blocks at A_min is min_blocks; elsewhere it holds no program with A_min Actions. Returns whether all agree."""
    ok = True
    print("Check against the search tables (largest table per level)")
    for level, (a_min, min_blocks, _) in sorted(amin_front().items()):
        limit, pairs = largest_tables().get(level, (0, []))
        at_amin = [b for a, b, _ in pairs if a == a_min]
        expected = min_blocks if min_blocks <= limit else None
        found = min(at_amin) if at_amin else None
        agree = found == expected and all(a >= a_min for a, _, _ in pairs)
        ok &= agree
        print(f"  level {level}: table b{limit}, fewest Blocks at A_min {found if found else 'none'}, "
              f"expected {expected if expected else 'none'}: {'ok' if agree else 'DIFFERENT'}")
    return ok


def label(models, labelled_csv):
    """Labels the goal-reaching models; returns {level: largest block count of a not-covered model}."""
    tables = largest_tables()
    loops = loop_tables()
    amin = amin_front()
    goal = [m for m in models if m["reachesGoal"] == "true"]
    # x beats (a, b): no more Actions and no more Blocks, fewer of at least one
    beats = lambda x, a, b: x[0] <= a and x[1] <= b and (x[0], x[1]) != (a, b)
    fronts = {}  # level -> best known front: (Actions, Blocks, program, source) of table and participant programs
    uncovered = {}
    for m in models:
        if m["reachesGoal"] != "true":
            m["pareto"] = "no goal"
            continue
        level, actions, blocks = int(m["level"]), int(m["power_kW_actions"]), int(m["memory_kB_blocks"])
        limit, pairs = tables.get(level, (0, []))
        m["table_max_blocks"] = limit or ""
        if level not in fronts:
            known = [(a, b, prog, "search") for a, b, prog in pairs]
            known += [(a, b, prog, "loop-search") for a, b, prog in loops.get(level, [])]
            if level in amin:
                known.append((amin[level][0], amin[level][1], amin[level][2], "amin-front"))
            known += [(int(o["power_kW_actions"]), int(o["memory_kB_blocks"]), o["program"], f"{o['session']}#{o['index']}")
                      for o in goal if int(o["level"]) == level]
            fronts[level] = [x for x in known if not any(beats(y, x[0], x[1]) for y in known)]
        # the programs on the best known front (table and participants) that beat the model. Anything that beats the
        # model is beaten by or equal to one of these, so the verdict is the same as with all programs, and the named
        # program is an optimal one.
        beaters = [x for x in fronts[level] if beats(x, actions, blocks)]
        if beaters:
            # closest: smallest total gap; ties go to fewer Actions, then to the table (its source is reproducible)
            a, b, prog, source = min(beaters, key=lambda x: (actions - x[0] + blocks - x[1], x[0], x[3] != "search"))
            m.update(pareto="dominated", dominatedBy=prog, dominatedBy_actions=a, dominatedBy_blocks=b,
                     dominatedBy_source=source)
        elif blocks <= limit:
            # the table holds every goal-reaching program up to the limit, so nothing with at most this many blocks beats it
            m["pareto"] = "optimal"
        elif level in amin and actions == amin[level][0]:
            # nothing has fewer Actions, and amin-front has the fewest Blocks at A_min, which did not beat it
            if blocks != amin[level][1]:
                sys.exit(f"model {m['session']}#{m['index']} has {blocks} Blocks at A_min, amin-front says {amin[level][1]}")
            m["pareto"] = "optimal"
        else:
            m["pareto"] = "not covered"
            uncovered[level] = max(uncovered.get(level, 0), blocks)
    with open(labelled_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=LABEL_COLUMNS, extrasaction="ignore")
        w.writeheader()
        w.writerows(models)

    print(f"{len(models)} rows written to {labelled_csv}")
    print("  Level  Table     Goal models  Optimal  Dominated  Not covered")
    for level in sorted({int(m["level"]) for m in goal}):
        rows = [m for m in goal if int(m["level"]) == level]
        count = lambda p: sum(m["pareto"] == p for m in rows)
        limit = tables.get(level, (0,))[0]
        print(f"  {level:<6} {'b' + str(limit) if limit else 'none':<9} {len(rows):<12} {count('optimal'):<8} "
              f"{count('dominated'):<10} {count('not covered')}")
    return uncovered


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=["amin", "amin-front", "loop-search", "search", "check-sim", "check-rules",
                                         "check-models", "label"])
    parser.add_argument("--sessions", default=str(HERE.parent / "data"),
                        help="label, check-models: folder of session JSONs, with their models in <folder>/models/ "
                             "(default: replication/data)")
    parser.add_argument("--levels-dir", default=str(ROOT / "blocky_momot" / "model" / "input"),
                        help="folder with the clean level files N.xmi (default: blocky_momot/model/input)")
    parser.add_argument("--level", type=int, action="append", help="level to compute (repeatable; default 5-10)")
    parser.add_argument("--max-blocks", type=int, default=5, help="search and checks: block limit (default 5)")
    parser.add_argument("--max-body", type=int, default=5, help="loop-search: block limit of the loop body (default 5)")
    parser.add_argument("--max-actions", type=int, help="loop-search (required): keep pairs with at most this many Actions")
    parser.add_argument("--extend", action="store_true",
                        help="label: run the searches that cover the not-covered models, then label again (10 blocks "
                             "take hours per level)")
    args = parser.parse_args()

    levels = args.level or STUDY_LEVELS
    levels_dir = str(Path(args.levels_dir).resolve())
    if args.mode == "label":
        models, labelled = read_models(args.sessions), labels_csv(args.sessions)
        uncovered = label(models, labelled)
        if uncovered and args.extend:
            java, cp = find_tool("java"), classpath()
            for level, blocks in sorted(uncovered.items()):
                cmd = [java, "-Xmx4g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", "search", levels_dir,
                       str(blocks), str(TABLES), str(level)]
                if subprocess.run(cmd, cwd=ROOT).returncode != 0:
                    sys.exit(f"search failed for level {level}")
            uncovered = label(models, labelled)
        for level, blocks in sorted(uncovered.items()):
            print(f"Level {level}: models up to {blocks} blocks not covered; run: "
                  f"python ground_truth/run.py search --level {level} --max-blocks {blocks}")
        return
    java = find_tool("java")
    cp = classpath()
    if args.mode == "amin":
        tool_args = ["amin", levels_dir, str(TABLES / "amin.csv")]
    elif args.mode == "amin-front":
        cmd = [java, "-Xmx4g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", "amin-front", levels_dir,
               str(TABLES / "amin_front.csv"), *map(str, levels)]
        if subprocess.run(cmd, cwd=ROOT).returncode != 0:
            sys.exit("amin-front failed")
        sys.exit(0 if check_amin_front() else 1)
    elif args.mode == "loop-search":
        if args.max_actions is None:
            parser.error("loop-search needs --max-actions")
        for level in levels:
            cmd = [java, "-Xmx8g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", "loop-search", levels_dir, str(level),
                   str(args.max_blocks), str(args.max_body), str(args.max_actions), str(TABLES)]
            if subprocess.run(cmd, cwd=ROOT).returncode != 0:
                sys.exit(f"loop-search failed for level {level}")
        return
    elif args.mode == "search":
        tool_args = [args.mode, levels_dir, str(args.max_blocks), str(TABLES)]
    elif args.mode == "check-models":
        # the tool reads one models CSV, so the per-session files are combined in a temporary one
        with tempfile.TemporaryDirectory() as tmp:
            models = Path(tmp) / "models.csv"
            with open(models, "w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=MODEL_COLUMNS, extrasaction="ignore")
                w.writeheader()
                w.writerows(read_models(args.sessions))
            cmd = [java, "-Xmx4g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", args.mode, levels_dir,
                   str(args.max_blocks), str(models), *map(str, levels)]
            sys.exit(subprocess.run(cmd, cwd=ROOT).returncode)
    else:
        tool_args = [args.mode, levels_dir, str(args.max_blocks)]
    cmd = [java, "-Xmx4g", "-cp", cp, "blocky_momot.GroundTruthEnumerator", *tool_args, *map(str, levels)]
    sys.exit(subprocess.run(cmd, cwd=ROOT).returncode)


if __name__ == "__main__":
    main()
