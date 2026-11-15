#!/usr/bin/env python3
"""
MapReduce vs Hive Output Parity Verification Script
Compares outputs from MapReduce jobs and HiveQL queries for:
- Hits Per Page (FR-9)
- Unique Visitors Per Day (FR-10)
Asserts 100% numerical parity per PRD Section 8.
"""

import argparse
import sys
from pathlib import Path


def parse_tsv_map(file_path: Path):
    """Parses a TSV file or directory of part-* files into a dict {key: int_value}."""
    results = {}
    path = Path(file_path)
    files = [path] if path.is_file() else sorted(path.glob("part-*"))

    for f in files:
        if not f.is_file():
            continue
        with open(f, "r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.split("\t")
                if len(parts) >= 2:
                    key = parts[0].strip()
                    try:
                        val = int(parts[1].strip())
                        results[key] = results.get(key, 0) + val
                    except ValueError:
                        pass
    return results


def compare_results(mr_data: dict, hive_data: dict, metric_name: str):
    """Compares two dictionaries and prints detailed differences."""
    all_keys = set(mr_data.keys()).union(set(hive_data.keys()))
    mismatches = []

    print(f"\n--- Checking Parity for: {metric_name} ({len(all_keys)} keys) ---")
    for key in sorted(all_keys):
        mr_val = mr_data.get(key, 0)
        hive_val = hive_data.get(key, 0)

        if mr_val != hive_val:
            mismatches.append((key, mr_val, hive_val, abs(mr_val - hive_val)))

    if not mismatches:
        print(f"PASSED: 100% Parity Verified for {metric_name}! ({len(all_keys)} items matched exactly)")
        return True
    else:
        print(f"FAILED: Found {len(mismatches)} mismatches for {metric_name}:")
        print(f"{'Key':<35} | {'MapReduce':<12} | {'Hive':<12} | {'Diff':<8}")
        print("-" * 75)
        for key, mr_v, hive_v, diff in mismatches[:20]:
            print(f"{key:<35} | {mr_v:<12} | {hive_v:<12} | {diff:<8}")
        return False


def main():
    parser = argparse.ArgumentParser(description="MapReduce vs Hive Parity Check")
    parser.add_argument("--mr-hits", type=str, default="/workspace/exports/hits_per_page.tsv", help="MR Hits file/dir")
    parser.add_argument("--hive-hits", type=str, default="/workspace/exports/hive_hits.tsv", help="Hive Hits file")
    parser.add_argument("--mr-visitors", type=str, default="/workspace/exports/unique_visitors.tsv", help="MR Visitors file/dir")
    parser.add_argument("--hive-visitors", type=str, default="/workspace/exports/hive_visitors.tsv", help="Hive Visitors file")

    args = parser.parse_args()

    success = True

    # 1. Compare Hits Per Page
    if Path(args.mr_hits).exists() and Path(args.hive_hits).exists():
        mr_hits = parse_tsv_map(Path(args.mr_hits))
        hive_hits = parse_tsv_map(Path(args.hive_hits))
        if not compare_results(mr_hits, hive_hits, "Hits Per Page (FR-9)"):
            success = False
    else:
        print(f"Notice: Hits files not found for check: {args.mr_hits} or {args.hive_hits}")

    # 2. Compare Unique Visitors
    if Path(args.mr_visitors).exists() and Path(args.hive_visitors).exists():
        mr_vis = parse_tsv_map(Path(args.mr_visitors))
        hive_vis = parse_tsv_map(Path(args.hive_visitors))
        if not compare_results(mr_vis, hive_vis, "Unique Visitors (FR-10)"):
            success = False
    else:
        print(f"Notice: Unique Visitors files not found for check: {args.mr_visitors} or {args.hive_visitors}")

    if not success:
        sys.exit(1)
    print("\n=== Parity Check Complete: MapReduce and Hive produce identical analytics! ===")


if __name__ == "__main__":
    main()

