"""
Tidies up Level 10 elapsed time across all participant session files by
capping elapsedMs at the participant's last active interaction (final program run),
removing artificial idle screen-on time while filling out the paper questionnaire.
"""

import glob
import json
import os
import csv
import pandas as pd

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
MODELS_DIR = os.path.join(DATA_DIR, "models")
TABLES_DIR = os.path.join(os.path.dirname(__file__), "tables")


def tidy_level_10(dry_run=False):
    pattern = os.path.join(DATA_DIR, "session_*.json")
    files = sorted(glob.glob(pattern))
    if not files:
        print(f"No session JSON files found matching {pattern}")
        return

    print(f"Found {len(files)} session files in {DATA_DIR}.\n")
    print(f"{'User':15} | {'Variant':9} | {'Old L10':10} | {'New L10':10} | {'Idle Removed':12} | {'Old Total':10} | {'New Total':10}")
    print("-" * 90)

    for f in files:
        with open(f, "r", encoding="utf-8") as fp:
            d = json.load(fp)

        user = d.get("userId", "Unknown")
        l10 = d.get("levels", {}).get("10")
        if not l10:
            print(f"Skipping {user}: no Level 10 record found.")
            continue

        variant = l10.get("variant", "original")
        old_l10_ms = l10.get("elapsedMs", 0)
        old_total_ms = d.get("totalElapsedMs", 0)

        # Find the last active program_run event on level 10
        events_l10 = [
            e for e in d.get("events", [])
            if e.get("level") == 10 and e.get("type") == "program_run" and e.get("levelElapsedMs") is not None
        ]

        if not events_l10:
            print(f"Warning: No program_run event on Level 10 for {user}. Keeping original time.")
            continue

        last_event = events_l10[-1]
        new_l10_ms = last_event["levelElapsedMs"]
        idle_removed_ms = old_l10_ms - new_l10_ms

        # Update Level 10 elapsedMs and updatedAt
        l10["elapsedMs"] = new_l10_ms
        if "timestamp" in last_event:
            l10["updatedAt"] = last_event["timestamp"]

        # Recalculate totalElapsedMs
        new_total_ms = sum(lvl.get("elapsedMs", 0) for lvl in d.get("levels", {}).values())
        d["totalElapsedMs"] = new_total_ms

        print(
            f"{user:15} | {variant:9} | {old_l10_ms/60000:6.2f} min | {new_l10_ms/60000:6.2f} min | "
            f"{idle_removed_ms/60000:6.2f} min ({idle_removed_ms:7d} ms) | {old_total_ms/60000:6.2f} min | {new_total_ms/60000:6.2f} min"
        )

        if not dry_run:
            with open(f, "w", encoding="utf-8") as fp:
                json.dump(d, fp, indent=2)

    print("\n" + "=" * 90)
    print("Level 10 tidying completed successfully.")


def regenerate_all_levels_csv():
    """Regenerates replication/tables/participants_all_levels.csv across all levels 1-10."""
    models_dict = {}
    for f in sorted(glob.glob(os.path.join(MODELS_DIR, "*.csv"))):
        with open(f, encoding="utf-8") as fp:
            reader = csv.DictReader(fp)
            for r in reader:
                if r.get("type") == "program_run" and r.get("status") == "WON":
                    key = (r["session"], int(r["level"]))
                    p = int(r["power_kW_actions"])
                    m = int(r["memory_kB_blocks"])
                    if key not in models_dict:
                        models_dict[key] = (p, m)
                    else:
                        models_dict[key] = (min(models_dict[key][0], p), min(models_dict[key][1], m))

    rows = []
    for f in sorted(glob.glob(os.path.join(DATA_DIR, "session_*.json"))):
        with open(f, encoding="utf-8") as fp:
            d = json.load(fp)
        sess_id = d["sessionId"]
        user_id = d["userId"]
        for lvl_str, ldata in d["levels"].items():
            lvl = int(lvl_str)
            variant = "treatment" if ldata["variant"] == "momot" else "control"
            runs = ldata["programRuns"]
            elapsed_min = round(ldata["elapsedMs"] / 60000.0, 4)
            searches = ldata["momotSearches"]
            loaded = ldata["solutionsLoaded"]
            best = models_dict.get((sess_id, lvl), (None, None))
            rows.append({
                "session": sess_id,
                "user": user_id,
                "level": lvl,
                "variant": variant,
                "level_runs": runs,
                "level_elapsed_min": elapsed_min,
                "clem_searches": searches,
                "solutions_loaded": loaded,
                "min_power_kW": best[0] if best[0] is not None else "",
                "min_memory_kB": best[1] if best[1] is not None else ""
            })

    df = pd.DataFrame(rows)
    df = df.sort_values(by=["session", "level"])
    out_path = os.path.join(TABLES_DIR, "participants_all_levels.csv")
    df.to_csv(out_path, index=False)
    print(f"Wrote {out_path} with {len(df)} rows.")


if __name__ == "__main__":
    tidy_level_10(dry_run=False)
    regenerate_all_levels_csv()
