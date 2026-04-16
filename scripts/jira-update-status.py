#!/usr/bin/env python3
"""
Jira Issue Status Updater for Trading SaaS

Finds a Jira issue by PBI/Feature/Epic ID tag (e.g., E1-F01-PBI-01)
and transitions it to the specified status.

Usage:
    python scripts/jira-update-status.py E1-F01-PBI-01 "In Progress"
    python scripts/jira-update-status.py E1-F01-PBI-01 "Done"
    python scripts/jira-update-status.py E1-F01-PBI-02 "In Development"

Common status names (vary by project workflow):
    "To Do" | "In Progress" | "In Development" | "In Review" | "Done"

Environment variables (in .env or exported):
    JIRA_URL        - e.g., https://yourcompany.atlassian.net
    JIRA_EMAIL      - Jira account email
    JIRA_API_TOKEN  - API token from https://id.atlassian.net/manage-profile/security/api-tokens
    JIRA_PROJECT    - Project key (e.g., SCRUM)
    JIRA_BOARD_ID   - Board ID (optional, auto-detected)
"""

import argparse
import os
import re
import sys

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import requests


def find_issue_by_tag(url: str, auth: tuple, board_id: int, tag: str) -> str | None:
    """Search board issues for one whose summary starts with [<tag>]."""
    tag_re = re.compile(r"^\[(.+?)\]")
    start_at = 0
    page_size = 100

    while True:
        resp = requests.get(
            f"{url}/rest/agile/1.0/board/{board_id}/issue",
            params={"startAt": start_at, "maxResults": page_size, "fields": "summary"},
            auth=auth,
        )
        resp.raise_for_status()
        data = resp.json()
        issues = data.get("issues", [])

        for issue in issues:
            summary = issue["fields"]["summary"]
            match = tag_re.match(summary)
            if match and match.group(1) == tag:
                return issue["key"]

        total = data.get("total", 0)
        start_at += page_size
        if start_at >= total:
            break

    return None


def detect_board_id(url: str, auth: tuple, project_key: str) -> int:
    resp = requests.get(
        f"{url}/rest/agile/1.0/board",
        params={"projectKeyOrId": project_key},
        auth=auth,
    )
    resp.raise_for_status()
    boards = resp.json().get("values", [])
    if not boards:
        raise RuntimeError(f"No board found for project {project_key}")
    return boards[0]["id"]


def get_transitions(url: str, auth: tuple, issue_key: str) -> list[dict]:
    resp = requests.get(
        f"{url}/rest/api/2/issue/{issue_key}/transitions",
        auth=auth,
    )
    resp.raise_for_status()
    return resp.json().get("transitions", [])


def do_transition(url: str, auth: tuple, issue_key: str, transition_id: str):
    resp = requests.post(
        f"{url}/rest/api/2/issue/{issue_key}/transitions",
        json={"transition": {"id": transition_id}},
        auth=auth,
    )
    resp.raise_for_status()


def main():
    parser = argparse.ArgumentParser(description="Transition a Jira issue by PBI ID tag")
    parser.add_argument("tag", help="PBI/Feature/Epic ID, e.g. E1-F01-PBI-01")
    parser.add_argument("status", help="Target status name, e.g. 'In Progress'")
    parser.add_argument("--list-transitions", action="store_true",
                        help="List available transitions for this issue and exit")
    args = parser.parse_args()

    jira_url = os.environ.get("JIRA_URL", "").rstrip("/")
    jira_email = os.environ.get("JIRA_EMAIL", "")
    jira_token = os.environ.get("JIRA_API_TOKEN", "")
    jira_project = os.environ.get("JIRA_PROJECT", "")
    jira_board_id_env = os.environ.get("JIRA_BOARD_ID", "")

    missing = [v for v, k in [
        (jira_url, "JIRA_URL"), (jira_email, "JIRA_EMAIL"),
        (jira_token, "JIRA_API_TOKEN"), (jira_project, "JIRA_PROJECT"),
    ] if not v]
    if missing:
        print(f"ERROR: Missing env vars: {missing}")
        sys.exit(1)

    auth = (jira_email, jira_token)

    # Detect board ID
    board_id = int(jira_board_id_env) if jira_board_id_env else detect_board_id(jira_url, auth, jira_project)
    print(f"Using board ID: {board_id}")

    # Find issue
    print(f"Searching for issue with tag [{args.tag}]...")
    issue_key = find_issue_by_tag(jira_url, auth, board_id, args.tag)
    if not issue_key:
        print(f"ERROR: No issue found with tag [{args.tag}] on board {board_id}")
        sys.exit(1)
    print(f"Found issue: {issue_key}")

    # List or apply transition
    transitions = get_transitions(jira_url, auth, issue_key)

    if args.list_transitions:
        print(f"\nAvailable transitions for {issue_key}:")
        for t in transitions:
            print(f"  [{t['id']}] {t['name']}")
        return

    # Find matching transition (case-insensitive)
    target = args.status.strip().lower()
    match = next((t for t in transitions if t["name"].strip().lower() == target), None)
    if not match:
        available = [t["name"] for t in transitions]
        print(f"ERROR: Transition '{args.status}' not found. Available: {available}")
        sys.exit(1)

    do_transition(jira_url, auth, issue_key, match["id"])
    print(f"[OK] {issue_key} transitioned to '{match['name']}'")


if __name__ == "__main__":
    main()
