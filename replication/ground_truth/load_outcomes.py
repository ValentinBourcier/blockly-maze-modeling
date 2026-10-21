#!/usr/bin/env python3
"""What participants did with a candidate they loaded from CLEM's solution panel (prototype, see README.md).

Usage: python ground_truth/load_outcomes.py [folder ...]   (from the replication folder)
       (default: data)

Reads <folder>/models/*.csv (from compute_models()) and writes replication/tables/load_outcomes.csv as a timeline: one case per
load (candidate_exploration) on levels 5-10, the same candidate loaded several times in a row counting as one case,
with rows (steps) in time order:
  own run   the last program run of the same participant and level before the load
  load      the loaded candidate, one row per load (loads less than 1 s apart are a double click)
  next run  the first program run of the same participant and level after the load, if no other candidate is
            loaded first
The note of the last step says what happened. "N blocks from load" is a tree edit distance (insert, delete or change
one block costs 1, so does moving a block between the then and else branch of an if):
  ran it unchanged   the next run is the loaded program
  edited             the next run differs, and is closer to the loaded program than to the own run
  dropped            the next run differs, and is at least as close to the own run
  changed            the next run differs, and there is no own run to compare with
  never ran it       no later run on that level
  loaded another candidate before running
The interpretation of the last step says what the case suggests, by fixed rules (intent is never logged):
  own model first    an own goal-reaching run before the load, and the candidate at most SEED_DISTANCE blocks from it
                     (the search edits the program in the workspace, so it started from that run); the candidate is
                     compared with the own model and its label from <folder>/labels.csv (run.py label)
  exploration first  no own run on the level before the load; the candidate's label is given
  next run           unchanged, edited, dropped (closer to the own run), or probably not built from the candidate (as
                     many blocks from it as the next run has blocks)
  searches           the session report counts the searches per level but logs no time, so only the search of the
                     loaded candidate is placed in time
"""
import csv
import json
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT = HERE.parent / "tables"
DEFAULT_FOLDERS = [HERE.parent / "data"]
STUDY_LEVELS = {"5", "6", "7", "8", "9", "10"}  # levels 1-4 are exercise levels in the study

SEED_DISTANCE = 2  # a candidate at most this many blocks from the own run shows the search started from it

# index: the model's index in the participant's <folder>/models/session_*.csv
COLUMNS = ["case", "step", "participant", "index", "level", "time", "program", "goal", "actions", "blocks", "note",
           "interpretation"]

# ---- program text -> tree ----

class Node:
    def __init__(self, label, children=None):
        self.label = label
        self.children = children or []


CONTAINERS = {"prog", "stack"}  # structure only, no block: free to insert or delete (loose stacks run in order)


def branch(statements, name):
    # the branch is part of the label, so moving a block between then and else costs 1
    for s in statements:
        s.label += "@" + name
    return statements


def parse(text):
    """Program text (README: Reading the program syntax) -> tree; '+' separates loose stacks."""
    pos = 0

    def body(stop):
        nonlocal pos
        out = []
        while pos < len(text) and text[pos] not in stop:
            c = text[pos]
            pos += 1
            if c == "W":
                pos += 1  # '('
                out.append(Node("W", body(")")))
                pos += 1  # ')'
            elif c in "alr":
                pos += 1  # '('
                then = body("|")
                pos += 1  # '|'
                other = body(")")
                pos += 1  # ')'
                out.append(Node(c, branch(then, "then") + branch(other, "else")))
            else:
                out.append(Node(c))  # F, L, R, ?
        return out

    stacks = []
    while True:
        stacks.append(Node("stack", body("+")))
        if pos >= len(text):
            break
        pos += 1  # '+'
    return Node("prog", stacks)


# ---- Zhang-Shasha ordered tree edit distance ----

def postorder(root):
    labels, leftmost = [], []

    def walk(n):
        first = None
        for c in n.children:
            lm = walk(c)
            if first is None:
                first = lm
        labels.append(n.label)
        leftmost.append(first if first is not None else len(labels) - 1)
        return leftmost[-1]

    walk(root)
    return labels, leftmost


def keyroots(leftmost):
    seen, roots = set(), []
    for i in range(len(leftmost) - 1, -1, -1):
        if leftmost[i] not in seen:
            seen.add(leftmost[i])
            roots.append(i)
    return sorted(roots)


def indel(label):
    return 0 if label in CONTAINERS else 1


def relabel(a, b):
    # the branch tag only counts between two branches: unwrapping a(F|) to F keeps F, it does not change it
    (a_block, _, a_branch), (b_block, _, b_branch) = a.partition("@"), b.partition("@")
    if a_block != b_block:
        return 1
    return 1 if a_branch and b_branch and a_branch != b_branch else 0


def tree_distance(a_text, b_text):
    la, lma = postorder(parse(a_text))
    lb, lmb = postorder(parse(b_text))
    td = [[0] * len(lb) for _ in la]
    for i in keyroots(lma):
        for j in keyroots(lmb):
            li, lj = lma[i], lmb[j]
            m, n = i - li + 2, j - lj + 2
            fd = [[0] * n for _ in range(m)]
            for x in range(1, m):
                fd[x][0] = fd[x - 1][0] + indel(la[li + x - 1])
            for y in range(1, n):
                fd[0][y] = fd[0][y - 1] + indel(lb[lj + y - 1])
            for x in range(1, m):
                for y in range(1, n):
                    ai, bj = li + x - 1, lj + y - 1
                    delete = fd[x - 1][y] + indel(la[ai])
                    insert = fd[x][y - 1] + indel(lb[bj])
                    if lma[ai] == li and lmb[bj] == lj:
                        fd[x][y] = min(delete, insert, fd[x - 1][y - 1] + relabel(la[ai], lb[bj]))
                        td[ai][bj] = fd[x][y]
                    else:
                        fd[x][y] = min(delete, insert,
                                       fd[lma[ai] - li][lmb[bj] - lj] + td[ai][bj])
    return td[-1][-1]


# ---- comparison of two models ----

def compare(new, old):
    """new against old in words: goal first, then Actions and Blocks (fewer is better)."""
    new_goal, old_goal = new["status"] == "WON", old["status"] == "WON"
    if new_goal != old_goal:
        return "goal_gained" if new_goal else "goal_lost"
    if not new_goal:
        return "no_goal"
    na, nb = int(new["power_kW_actions"]), int(new["memory_kB_blocks"])
    oa, ob = int(old["power_kW_actions"]), int(old["memory_kB_blocks"])
    if (na, nb) == (oa, ob):
        return "equal"
    if na <= oa and nb <= ob:
        return "better"
    if na >= oa and nb >= ob:
        return "worse"
    return "tradeoff"


COMPARE_TEXT = {"goal_gained": "reaches the goal, {old} does not", "goal_lost": "does not reach the goal, {old} does",
                "no_goal": "does not reach the goal, nor does {old}", "equal": "has the same actions and blocks as {old}",
                "better": "is better than {old}", "worse": "is worse than {old}", "tradeoff": "is a trade-off with {old}"}


def compare_text(new_name, new, old_name, old):
    return f"{new_name} {COMPARE_TEXT[compare(new, old)].format(old=old_name)}"


# ---- timeline ----

def seconds(t):
    # load timestamps carry nanoseconds, which datetime does not take
    date, _, frac = t.rstrip("Z").partition(".")
    return datetime.fromisoformat(date + ("." + frac[:6] if frac else "")).timestamp()


def repeat_note(earlier, later):
    # every click on a row or point of the solution panel loads the candidate, so a double click logs two loads
    gap = seconds(later["timestamp"]) - seconds(earlier["timestamp"])
    if gap < 1:
        return f"double click: loaded again {gap:.1f} s after index {earlier['index']}"
    return f"same candidate loaded again {gap:.0f} s after index {earlier['index']}"

def step(case, name, m, note=""):
    return {"case": case, "step": name, "participant": m["session"].rsplit("_", 1)[0], "index": m["index"],
            "level": m["level"],
            "time": m["timestamp"][11:19], "program": m["program"], "goal": "yes" if m["status"] == "WON" else "no",
            "actions": m["power_kW_actions"], "blocks": m["memory_kB_blocks"], "note": note}


def verdict(load, prev, nxt, following):
    if following is None:
        return "never ran it"
    if nxt is None:
        return "loaded another candidate before running"
    d_load = tree_distance(nxt["program"], load["program"])
    if d_load == 0:
        return "ran it unchanged"
    if prev is None:
        what = f"{d_load} blocks from load: changed (no own run before)"
    else:
        d_prev = tree_distance(nxt["program"], prev["program"])
        what = f"{d_load} blocks from load, {d_prev} from own run: {'edited' if d_load < d_prev else 'dropped'}"
    return f"{what}; {compare_text('the next run', nxt, 'the load', load)}"


def blocks_text(d):
    return "identical to" if d == 0 else f"{d} block{'s' if d > 1 else ''} from"


def interpretation(load, prev, nxt, following, labels, searches):
    """What the case suggests, by fixed rules; the telemetry shows the order of the steps, not why."""
    parts = []
    d_own = tree_distance(load["program"], prev["program"]) if prev is not None else None
    own_first = prev is not None and prev["status"] == "WON" and d_own <= SEED_DISTANCE
    if prev is None:
        parts.append("exploration first: no own run on this level before the load")
    elif own_first:
        parts.append(f"own model first: explored from their own goal-reaching model "
                     f"(candidate {blocks_text(d_own)} it)")
    elif prev["status"] != "WON":
        parts.append("own run first, but it does not reach the goal")
    else:
        parts.append(f"own run first, but the exploration did not clearly start from it "
                     f"(candidate {blocks_text(d_own)} it)")

    d_next = tree_distance(nxt["program"], load["program"]) if nxt is not None else None
    if following is None:
        parts.append("did not run the candidate")
    elif nxt is None:
        parts.append("loaded another candidate before running")
    elif d_next == 0:
        parts.append("ran the candidate unchanged")
    elif d_next >= int(nxt["memory_kB_blocks"]):
        parts.append(f"next run is {blocks_text(d_next)} the candidate, as many as building it from scratch, "
                     f"so probably not built from it")
    elif prev is not None and tree_distance(nxt["program"], prev["program"]) <= d_next:
        parts.append(f"next run is closer to their own run than to the candidate, so the candidate was dropped")
    else:
        parts.append(f"next run is {blocks_text(d_next)} the candidate, so they edited it")

    if own_first:
        own_label = labels.get((prev["session"], prev["index"]), "")
        already = {"optimal": ", which was already optimal", "dominated": ", which was dominated"}.get(own_label, "")
        parts.append(compare_text("the candidate", load, "their own model", prev) + already)
    else:
        load_label = labels.get((load["session"], load["index"]), "")
        if load_label in ("optimal", "dominated", "not covered"):
            parts.append(f"the candidate is {load_label}")
        if nxt is not None and d_next > 0:
            parts.append(compare_text("the next run", nxt, "the candidate", load))
        elif load_label == "no goal":
            parts.append("the candidate does not reach the goal")

    if searches > 1:
        parts.append(f"{searches} searches on this level, only this one placed in time")
    elif searches == 1:
        parts.append("the only search on this level")
    parts.append("intent not logged")
    return "; ".join(parts)


def read_labels(folder):
    """(session, index) -> Pareto label from run.py label (<folder>/labels.csv), empty if not labelled yet."""
    path = Path(folder) / "labels.csv"
    if not path.exists():
        return {}
    with open(path, newline="", encoding="utf-8") as f:
        return {(m["session"], m["index"]): m["pareto"] for m in csv.DictReader(f)}


def searches_on_level(folder, session, level):
    """Searches the session report counts on the level; it logs no time for them."""
    path = Path(folder) / f"session_{session}.json"
    if not path.exists():
        return 0
    with open(path, encoding="utf-8") as f:
        return json.load(f).get("levels", {}).get(level, {}).get("momotSearches", 0)


def timeline(folders):
    rows = []
    for folder in folders:
        labels = read_labels(folder)
        for path in sorted((Path(folder) / "models").glob("*.csv")):
            with open(path, newline="", encoding="utf-8") as f:
                models = sorted(csv.DictReader(f), key=lambda m: int(m["index"]))
            for k, load in enumerate(models):
                if load["type"] != "candidate_exploration" or load["level"] not in STUDY_LEVELS:
                    continue
                before = [m for m in models[:k] if m["level"] == load["level"]]
                after = [m for m in models[k + 1:] if m["level"] == load["level"]]
                following = after[0] if after else None
                if following is not None and following["type"] == "candidate_exploration" \
                        and following["program"] == load["program"]:
                    continue  # loaded again right away: the case is the last load of the row
                times = 1
                while times < len(before) + 1 and before[-times]["type"] == "candidate_exploration" \
                        and before[-times]["program"] == load["program"]:
                    times += 1
                prev = next((m for m in reversed(before) if m["type"] == "program_run"), None)
                nxt = following if following is not None and following["type"] == "program_run" else None

                case = len({r["case"] for r in rows}) + 1
                note = verdict(load, prev, nxt, following)
                later = next((m for m in after[1:] if m["type"] == "program_run" and m["program"] == load["program"]),
                             None)
                if later is not None:
                    note += f"; ran it unchanged later, at {later['timestamp'][11:19]}"
                if prev is not None:
                    rows.append(step(case, "own run", prev))
                chain = before[len(before) - times + 1:] + [load]  # every click on this candidate, one row each
                for i, m in enumerate(chain):
                    notes = [repeat_note(chain[i - 1], m)] if i > 0 else []
                    if m is load and nxt is None:
                        notes.append(note)
                    rows.append(step(case, "load", m, "; ".join(notes)))
                if nxt is not None:
                    rows.append(step(case, "next run", nxt, note))
                rows[-1]["interpretation"] = interpretation(
                    load, prev, nxt, following, labels, searches_on_level(folder, load["session"], load["level"]))
    return rows


def main():
    rows = timeline(sys.argv[1:] or DEFAULT_FOLDERS)
    OUTPUT.mkdir(exist_ok=True)
    out = OUTPUT / "load_outcomes.csv"
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(rows)
    print(f"{rows[-1]['case'] if rows else 0} loads -> {out}")


if __name__ == "__main__":
    main()
