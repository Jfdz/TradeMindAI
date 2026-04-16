#!/usr/bin/env python3
"""
CSV Export Script for Jira Bulk Import

Parses PLAN_EXECUTION.md and generates a CSV file compatible with Jira's
built-in CSV importer (Administration > System > External System Import > CSV).

Usage:
    python scripts/jira-export-csv.py [--output jira-import.csv] [--epic EPIC-1] [--sprint S1]

The generated CSV can be imported via:
    1. Jira > Settings > System > External System Import > CSV
    2. Upload the CSV file
    3. Map columns to Jira fields
    4. Run import

Column mapping guide:
    Summary       -> Summary
    Description   -> Description
    Issue Type    -> Issue Type
    Priority      -> Priority
    Epic Name     -> Epic Name (custom field)
    Sprint        -> Sprint
    Story Points  -> Story Points (custom field)
    Labels        -> Labels
    Parent ID     -> Parent (for sub-task linking - use 2-pass import)
"""

import argparse
import csv
import re
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Constants: CSV Column Names
# ---------------------------------------------------------------------------

ISSUE_ID = "Issue ID"
PARENT_ID = "Parent ID"
EPIC_ID = "Epic ID"
SUMMARY = "Summary"
DESCRIPTION = "Description"
ACCEPTANCE_CRITERIA = "Acceptance Criteria"
ISSUE_TYPE = "Issue Type"
PRIORITY = "Priority"
STORY_POINTS = "Story Points"
SPRINT = "Sprint"
LABELS = "Labels"
EPIC_NAME = "Epic Name"
EPIC_LINK = "Epic Link"
FEATURE = "Feature"
AGENT = "Agent"
SERVICE = "Service"


# ---------------------------------------------------------------------------
# Regex patterns (same as jira-sync.py)
# ---------------------------------------------------------------------------

APPENDIX_RE = re.compile(
    r"\|\s*(E\d+-F\d+-PBI-\d+)\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*(EPIC-\d+)\s*\|"
    r"\s*(S\d+)\s*\|"
    r"\s*(\d+)\s*\|"
    r"\s*(\w+)\s*\|"
)

PBI_ROW_RE = re.compile(
    r"\|\s*(E\d+-F\d+-PBI-\d+)\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*(\w+)\s*\|"
    r"\s*(\d+)\s*\|"
    r"\s*([\w-]+)\s*\|"
)

EPIC_TABLE_RE = re.compile(
    r"\|\s*(EPIC-\d+)\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*Phase \d+\s*\|"
    r"\s*(.+?)\s*\|"
    r"\s*([\w\-,\s]+)\s*\|"
    r"\s*(S\d+-S\d+)\s*\|"
)


def parse_and_export(plan_path: Path, output_path: Path,
                     filter_epic: str = None, filter_sprint: str = None):
    """Parse plan and export to Jira-compatible CSV."""
    text = plan_path.read_text(encoding="utf-8")
    lines = text.split("\n")

    # --- Parse appendix ---
    appendix_map = {}
    for match in APPENDIX_RE.finditer(text):
        appendix_map[match.group(1)] = {
            "sprint": match.group(4),
            "sp": int(match.group(5)),
            "agent": match.group(6),
        }

    # --- Parse epics ---
    epics = {}
    for match in EPIC_TABLE_RE.finditer(text):
        epics[match.group(1)] = {
            "name": match.group(2).strip(),
            "description": match.group(3).strip(),
            "sprints": match.group(5).strip(),
        }

    # --- Parse PBIs ---
    current_epic_id = ""
    current_epic_name = ""
    current_feat_id = ""
    current_feat_name = ""
    rows = []

    for line in lines:
        epic_match = re.match(r"^###\s+(EPIC-\d+):\s*(.+)", line)
        if epic_match:
            current_epic_id = epic_match.group(1)
            current_epic_name = epic_match.group(2).strip()
            continue

        feat_match = re.match(r"^####\s+(FEAT-\d+):\s*(.+)", line)
        if feat_match:
            current_feat_id = feat_match.group(1)
            current_feat_name = feat_match.group(2).strip()
            continue

        pbi_match = PBI_ROW_RE.match(line)
        if pbi_match:
            pbi_id = pbi_match.group(1)
            appendix_info = appendix_map.get(pbi_id, {})
            sprint = appendix_info.get("sprint", "Backlog")
            agent = appendix_info.get("agent", "Unassigned")

            if filter_epic and current_epic_id != filter_epic:
                continue
            if filter_sprint and sprint != filter_sprint:
                continue

            # Clean up markdown bold from acceptance criteria
            ac = pbi_match.group(4).strip()
            ac = re.sub(r"\*\*(.+?)\*\*", r"\1", ac)

            rows.append({
                ISSUE_ID: pbi_id,
                PARENT_ID: current_feat_id,
                EPIC_ID: current_epic_id,
                SUMMARY: f"[{pbi_id}] {pbi_match.group(2).strip()}",
                DESCRIPTION: pbi_match.group(3).strip(),
                ACCEPTANCE_CRITERIA: ac,
                ISSUE_TYPE: "Sub-task",
                PRIORITY: pbi_match.group(5).strip(),
                STORY_POINTS: int(pbi_match.group(6)),
                SPRINT: sprint,
                LABELS: f"{pbi_match.group(7).strip()},{agent.lower()}",
                EPIC_NAME: current_epic_name,
                EPIC_LINK: current_epic_id,
                FEATURE: f"[{current_feat_id}] {current_feat_name}",
                AGENT: agent,
                SERVICE: pbi_match.group(7).strip(),
            })

    # --- Build CSV rows: Epics + Features (Stories) + PBIs (Sub-tasks) ---
    csv_rows = []

    # Collect unique epics and features from filtered PBIs
    seen_epics = set()
    seen_features = set()
    for row in rows:
        seen_epics.add(row["Epic ID"])
        seen_features.add(row["Parent ID"])

    # Add Epic rows
    for epic_id in sorted(seen_epics):
        epic = epics.get(epic_id, {})
        csv_rows.append({
            ISSUE_ID: epic_id,
            PARENT_ID: "",
            EPIC_ID: "",
            SUMMARY: f"[{epic_id}] {epic.get('name', epic_id)}",
            DESCRIPTION: epic.get("description", ""),
            ACCEPTANCE_CRITERIA: "",
            ISSUE_TYPE: "Epic",
            PRIORITY: "Highest",
            STORY_POINTS: "",
            SPRINT: epic.get("sprints", ""),
            LABELS: "epic",
            EPIC_NAME: epic.get("name", ""),
            EPIC_LINK: "",
            FEATURE: "",
            AGENT: "",
            SERVICE: "",
        })

    # Add Feature/Story rows
    feature_names = {}
    for row in rows:
        feat_id = row["Parent ID"]
        if feat_id not in feature_names:
            feature_names[feat_id] = {
                "name": row["Feature"],
                "epic_id": row["Epic ID"],
            }

    for feat_id in sorted(seen_features):
        info = feature_names.get(feat_id, {})
        csv_rows.append({
            ISSUE_ID: feat_id,
            PARENT_ID: info.get("epic_id", ""),
            EPIC_ID: info.get("epic_id", ""),
            SUMMARY: info.get("name", feat_id),
            DESCRIPTION: f"Feature under {info.get('epic_id', '')}",
            ACCEPTANCE_CRITERIA: "",
            ISSUE_TYPE: "Story",
            PRIORITY: "High",
            STORY_POINTS: "",
            SPRINT: "",
            LABELS: "feature",
            EPIC_NAME: "",
            EPIC_LINK: info.get("epic_id", ""),
            FEATURE: "",
            AGENT: "",
            SERVICE: "",
        })

    # Add PBI rows (Sub-tasks)
    csv_rows.extend(rows)

    # --- Write CSV ---
    if not csv_rows:
        print("ERROR: No data to export. Check filters.")
        sys.exit(1)

    fieldnames = [
        ISSUE_ID, PARENT_ID, SUMMARY, DESCRIPTION,
        ACCEPTANCE_CRITERIA, ISSUE_TYPE, PRIORITY, STORY_POINTS,
        SPRINT, LABELS, EPIC_NAME, EPIC_LINK, FEATURE,
        AGENT, SERVICE,
    ]

    with open(output_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(csv_rows)

    # Stats
    type_counts = {}
    for row in csv_rows:
        t = row["Issue Type"]
        type_counts[t] = type_counts.get(t, 0) + 1

    total_sp = sum(r.get("Story Points", 0) for r in csv_rows if isinstance(r.get("Story Points"), int))

    print(f"\nExported to: {output_path}")
    print(f"  Epics:     {type_counts.get('Epic', 0)}")
    print(f"  Stories:   {type_counts.get('Story', 0)}")
    print(f"  Sub-tasks: {type_counts.get('Sub-task', 0)}")
    print(f"  Total SP:  {total_sp}")
    print(f"  Total:     {len(csv_rows)} rows")

    print("\n--- Jira Import Instructions ---")
    print("1. Go to Jira > Settings > System > External System Import > CSV")
    print("2. Upload this CSV file")
    print("3. Map columns:")
    print("   - Summary       -> Summary")
    print("   - Description   -> Description")
    print("   - Issue Type    -> Issue Type")
    print("   - Priority      -> Priority")
    print("   - Story Points  -> Story Points (custom field)")
    print("   - Sprint        -> Sprint (custom field)")
    print("   - Labels        -> Labels")
    print("   - Epic Name     -> Epic Name (for Epics)")
    print("   - Epic Link     -> Epic Link (for Stories/Sub-tasks)")
    print("4. For Sub-task parent linking: do a 2-pass import")
    print("   Pass 1: Import Epics and Stories")
    print("   Pass 2: Import Sub-tasks with Parent ID mapped to parent Story keys")


def main():
    parser = argparse.ArgumentParser(
        description="Export Trading SaaS plan to Jira-compatible CSV"
    )
    parser.add_argument(
        "--output", "-o", type=str, default="jira-import.csv",
        help="Output CSV file path (default: jira-import.csv)"
    )
    parser.add_argument(
        "--epic", type=str, default=None,
        help="Filter to a specific epic (e.g., EPIC-1)"
    )
    parser.add_argument(
        "--sprint", type=str, default=None,
        help="Filter to a specific sprint (e.g., S1)"
    )
    parser.add_argument(
        "--plan", type=str, default=None,
        help="Path to PLAN_EXECUTION.md (auto-detected if omitted)"
    )
    args = parser.parse_args()

    if args.plan:
        plan_path = Path(args.plan)
    else:
        plan_path = Path(__file__).parent.parent / "PLAN_EXECUTION.md"

    if not plan_path.exists():
        print(f"ERROR: Plan file not found: {plan_path}")
        sys.exit(1)

    output_path = Path(args.output)
    print(f"Parsing: {plan_path}")
    parse_and_export(plan_path, output_path,
                     filter_epic=args.epic, filter_sprint=args.sprint)


if __name__ == "__main__":
    main()
