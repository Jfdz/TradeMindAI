#!/usr/bin/env python3
"""
Jira Sync Script for Trading SaaS PLAN_EXECUTION.md

Parses the Agile execution plan and creates Epics, Features (Stories),
and PBIs (Sub-tasks) in Jira using the Jira REST API. Automatically
creates sprints with goals, assigns issues, and optionally starts them.

Usage:
    pip install jira python-dotenv
    cp .env.example .env  # Fill in Jira credentials
    python scripts/jira-sync.py [--dry-run] [--epic EPIC-1] [--sprint S1]
    python scripts/jira-sync.py --start-sprint S1   # Create, populate & start sprint
Usage examples:


# Preview what Sprint 1 will look like
python scripts/jira-sync.py --dry-run --sprint S1 --start-sprint S1

# Sync all issues + create all sprints + start S1
python scripts/jira-sync.py --start-sprint S1

# Sync only Sprint 1 issues + create & start it
python scripts/jira-sync.py --sprint S1 --start-sprint S1

# Sync everything without starting any sprint
python scripts/jira-sync.py

Environment variables (in .env or exported):
    JIRA_URL        - Jira instance URL (e.g., https://yourcompany.atlassian.net)
    JIRA_EMAIL      - Jira account email
    JIRA_API_TOKEN  - Jira API token (generate at https://id.atlassian.net/manage-profile/security/api-tokens)
    JIRA_PROJECT    - Jira project key (e.g., SCRUM)
    JIRA_BOARD_ID   - Board ID for sprint management (default: auto-detected)
"""

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import os


# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------

@dataclass
class PBI:
    id: str
    title: str
    description: str
    acceptance_criteria: str
    priority: str
    story_points: int
    service: str
    epic_id: str
    epic_name: str
    feature_id: str
    feature_name: str
    sprint: str
    agent: str

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "title": self.title,
            "description": self.description,
            "acceptance_criteria": self.acceptance_criteria,
            "priority": self.priority,
            "story_points": self.story_points,
            "service": self.service,
            "epic_id": self.epic_id,
            "epic_name": self.epic_name,
            "feature_id": self.feature_id,
            "feature_name": self.feature_name,
            "sprint": self.sprint,
            "agent": self.agent,
        }


@dataclass
class Feature:
    id: str
    name: str
    epic_id: str
    pbis: list[PBI] = field(default_factory=list)


@dataclass
class Epic:
    id: str
    name: str
    description: str
    sprints: str
    features: list[Feature] = field(default_factory=list)


@dataclass
class SprintInfo:
    id: str              # e.g. "S1"
    number: int          # e.g. 1
    name: str            # e.g. "Foundation"
    goal: str            # e.g. "Monorepo running with all infrastructure..."
    start_date: str      # ISO date: "2026-03-30"
    end_date: str        # ISO date: "2026-04-12"
    deliverable: str     # What ships at the end
    verification: str    # How to verify


# ---------------------------------------------------------------------------
# Markdown parser
# ---------------------------------------------------------------------------

# Mapping from appendix: PBI ID -> (Sprint, SP, Agent)
APPENDIX_RE = re.compile(
    r"\|\s*(E\d+-F\d+-PBI-\d+)\s*\|"     # ID
    r"\s*(.+?)\s*\|"                       # Title
    r"\s*(EPIC-\d+)\s*\|"                  # Epic
    r"\s*(S\d+)\s*\|"                      # Sprint
    r"\s*(\d+)\s*\|"                       # SP
    r"\s*(\w+)\s*\|"                       # Agent
)

PBI_ROW_RE = re.compile(
    r"\|\s*(E\d+-F\d+-PBI-\d+)\s*\|"      # ID
    r"\s*(.+?)\s*\|"                       # Title
    r"\s*(.+?)\s*\|"                       # Description
    r"\s*(.+?)\s*\|"                       # Acceptance Criteria
    r"\s*(\w+)\s*\|"                       # Priority
    r"\s*(\d+)\s*\|"                       # SP
    r"\s*([\w-]+)\s*\|"                    # Service
)

EPIC_TABLE_RE = re.compile(
    r"\|\s*(EPIC-\d+)\s*\|"               # Epic ID
    r"\s*(.+?)\s*\|"                       # Name
    r"\s*Phase \d+\s*\|"                   # Phase
    r"\s*(.+?)\s*\|"                       # Business Value
    r"\s*(.+?)\s*\|"                       # Services
    r"\s*(S\d+(?:-S\d+)?)\s*\|"           # Sprints (S1 or S1-S2)
)

# Sprint header: ### Sprint 1 (Mar 30 - Apr 12): Foundation
SPRINT_HEADER_RE = re.compile(
    r"^### Sprint (\d+) \((.+?)\):\s*(.+)"
)

# Gantt chart dates: S1: ... :s1, 2026-03-30, 14d
GANTT_DATE_RE = re.compile(
    r"S(\d+):.+?:\s*s\d+,\s*(\d{4}-\d{2}-\d{2}),\s*(\d+)d"
)

MONTH_MAP = {
    "Jan": 1, "Feb": 2, "Mar": 3, "Apr": 4, "May": 5, "Jun": 6,
    "Jul": 7, "Aug": 8, "Sep": 9, "Oct": 10, "Nov": 11, "Dec": 12,
}


def _parse_header_dates(date_str: str, year: int = 2026) -> tuple[str, str]:
    """Parse 'Mar 30 - Apr 12' into ISO dates."""
    match = re.match(r"(\w+)\s+(\d+)\s*-\s*(\w+)\s+(\d+)", date_str)
    if not match:
        return "", ""
    start_month = MONTH_MAP.get(match.group(1), 1)
    start_day = int(match.group(2))
    end_month = MONTH_MAP.get(match.group(3), 1)
    end_day = int(match.group(4))
    return (
        f"{year}-{start_month:02d}-{start_day:02d}",
        f"{year}-{end_month:02d}-{end_day:02d}",
    )


def parse_plan(plan_path: Path) -> tuple[list[Epic], list[PBI], dict[str, SprintInfo]]:
    """Parse PLAN_EXECUTION.md and extract Epics, Features, PBIs, and Sprints."""
    text = plan_path.read_text(encoding="utf-8")
    lines = text.split("\n")

    # --- Parse Gantt chart dates ---
    gantt_dates: dict[int, tuple[str, str]] = {}
    for match in GANTT_DATE_RE.finditer(text):
        sprint_num = int(match.group(1))
        start = match.group(2)
        days = int(match.group(3))
        from datetime import datetime, timedelta
        start_dt = datetime.strptime(start, "%Y-%m-%d")
        end_dt = start_dt + timedelta(days=days - 1)
        gantt_dates[sprint_num] = (start, end_dt.strftime("%Y-%m-%d"))

    # --- Parse sprint sections ---
    sprints: dict[str, SprintInfo] = {}
    i = 0
    while i < len(lines):
        header_match = SPRINT_HEADER_RE.match(lines[i])
        if header_match:
            sprint_num = int(header_match.group(1))
            sprint_id = f"S{sprint_num}"
            date_str = header_match.group(2).strip()
            sprint_name = header_match.group(3).strip()

            # Dates from Gantt (more reliable) or header
            if sprint_num in gantt_dates:
                start_date, end_date = gantt_dates[sprint_num]
            else:
                start_date, end_date = _parse_header_dates(date_str)

            # Parse goal, deliverable, verification from subsequent lines
            goal = ""
            deliverable = ""
            verification = ""
            j = i + 1
            while j < len(lines) and not lines[j].startswith("### "):
                line = lines[j]
                if line.startswith("**Goal**:"):
                    goal = line.replace("**Goal**:", "").strip()
                elif line.startswith("**Deliverable**:"):
                    deliverable = line.replace("**Deliverable**:", "").strip()
                    deliverable = re.sub(r"`(.+?)`", r"\1", deliverable)
                elif line.startswith("**Verification**:"):
                    verification = line.replace("**Verification**:", "").strip()
                    verification = re.sub(r"`(.+?)`", r"\1", verification)
                j += 1

            sprints[sprint_id] = SprintInfo(
                id=sprint_id,
                number=sprint_num,
                name=sprint_name,
                goal=goal,
                start_date=start_date,
                end_date=end_date,
                deliverable=deliverable,
                verification=verification,
            )
        i += 1

    # --- Parse appendix for sprint/agent mapping ---
    appendix_map: dict[str, dict] = {}
    for match in APPENDIX_RE.finditer(text):
        pbi_id = match.group(1)
        appendix_map[pbi_id] = {
            "sprint": match.group(4),
            "sp": int(match.group(5)),
            "agent": match.group(6),
        }

    # --- Parse epics table ---
    epics: dict[str, Epic] = {}
    for match in EPIC_TABLE_RE.finditer(text):
        epic_id = match.group(1)
        epics[epic_id] = Epic(
            id=epic_id,
            name=match.group(2).strip(),
            description=match.group(3).strip(),
            sprints=match.group(5).strip(),
        )

    # --- Parse features and PBIs ---
    current_epic_id = ""
    current_epic_name = ""
    current_feat_id = ""
    current_feat_name = ""
    all_pbis: list[PBI] = []

    for line in lines:
        # Detect epic headers: ### EPIC-N: Name
        epic_match = re.match(r"^###\s+(EPIC-\d+):\s*(.+)", line)
        if epic_match:
            current_epic_id = epic_match.group(1)
            current_epic_name = epic_match.group(2).strip()
            continue

        # Detect feature headers: #### FEAT-NN: Name
        feat_match = re.match(r"^####\s+(FEAT-\d+):\s*(.+)", line)
        if feat_match:
            current_feat_id = feat_match.group(1)
            current_feat_name = feat_match.group(2).strip()
            continue

        # Detect PBI rows in feature tables
        pbi_match = PBI_ROW_RE.match(line)
        if pbi_match:
            pbi_id = pbi_match.group(1)
            appendix_info = appendix_map.get(pbi_id, {})

            pbi = PBI(
                id=pbi_id,
                title=pbi_match.group(2).strip(),
                description=pbi_match.group(3).strip(),
                acceptance_criteria=pbi_match.group(4).strip(),
                priority=pbi_match.group(5).strip(),
                story_points=int(pbi_match.group(6)),
                service=pbi_match.group(7).strip(),
                epic_id=current_epic_id,
                epic_name=current_epic_name,
                feature_id=current_feat_id,
                feature_name=current_feat_name,
                sprint=appendix_info.get("sprint", "Backlog"),
                agent=appendix_info.get("agent", "Unassigned"),
            )
            all_pbis.append(pbi)

    print(f"Parsed: {len(epics)} epics, {len(all_pbis)} PBIs, {len(sprints)} sprints")
    return list(epics.values()), all_pbis, sprints


# ---------------------------------------------------------------------------
# Jira client
# ---------------------------------------------------------------------------

class JiraSync:
    """Syncs parsed plan data to Jira."""

    def __init__(self, url: str, email: str, token: str, project_key: str,
                 board_id: Optional[int] = None):
        from jira import JIRA
        self.jira = JIRA(server=url, basic_auth=(email, token))
        self.url = url.rstrip("/")
        self.auth = (email, token)
        self.project_key = project_key
        self.board_id = board_id or self._detect_board_id()
        self.created_issues: dict[str, str] = {}  # local_id -> jira_key
        self.sprint_cache: dict[str, int] = {}     # sprint_name -> jira_sprint_id
        self.errors: list[str] = []                # accumulated errors
        self._existing_issues: dict[str, str] = {} # summary_prefix -> jira_key
        self._load_existing_issues()

    def _detect_board_id(self) -> int:
        """Auto-detect the board ID for this project."""
        boards = self.jira.boards(projectKeyOrID=self.project_key)
        if boards:
            return boards[0].id
        raise RuntimeError(f"No board found for project {self.project_key}")

    def _load_existing_issues(self):
        """Fetch ALL project issues via the Board API and index by ID tag.

        Uses /rest/agile/1.0/board/{id}/issue instead of jira.search_issues()
        because Jira Cloud has deprecated /rest/api/2/search and the old
        endpoint returns truncated results (caps at 100).

        Summaries follow the pattern '[EPIC-1] Name' or '[FEAT-01] Name' or
        '[E1-F01-PBI-01] Name'. We extract the bracketed ID and map it to
        the Jira issue key for O(1) duplicate detection.
        """
        import requests
        print(f"Loading existing issues from board {self.board_id}...")
        start_at = 0
        page_size = 100
        tag_re = re.compile(r"^\[(.+?)\]")
        total_fetched = 0

        while True:
            resp = requests.get(
                f"{self.url}/rest/agile/1.0/board/{self.board_id}/issue",
                params={"startAt": start_at, "maxResults": page_size, "fields": "summary"},
                auth=self.auth,
            )
            resp.raise_for_status()
            data = resp.json()
            api_total = data.get("total", 0)
            issues = data.get("issues", [])

            for issue in issues:
                summary = issue["fields"]["summary"]
                match = tag_re.match(summary)
                if match:
                    tag = match.group(1)
                    # Keep the FIRST (oldest) issue for each tag — ignore duplicates
                    if tag not in self._existing_issues:
                        self._existing_issues[tag] = issue["key"]

            total_fetched += len(issues)
            if start_at + page_size >= api_total:
                break
            start_at += page_size

        if total_fetched > 0:
            print(f"  Found {total_fetched} issues on board ({len(self._existing_issues)} unique ID tags)")
        else:
            print(f"  No existing issues found — fresh sync")

    def _find_existing(self, local_id: str) -> Optional[str]:
        """Look up a local ID (EPIC-1, FEAT-01, E1-F01-PBI-01) in the index."""
        return self._existing_issues.get(local_id)

    # --- Sprint management ---

    def _get_existing_sprints(self) -> dict[str, dict]:
        """Get all sprints on the board, keyed by name."""
        result = {}
        sprints = self.jira.sprints(self.board_id, extended=True)
        for s in sprints:
            result[s.name] = {
                "id": s.id,
                "state": s.state,
                "goal": getattr(s, "goal", None),
            }
        return result

    def get_or_create_sprint(self, sprint_info: SprintInfo) -> int:
        """Create or find a Jira sprint. Returns the sprint ID."""
        sprint_name = f"Sprint {sprint_info.number}: {sprint_info.name}"

        # Check cache
        if sprint_name in self.sprint_cache:
            return self.sprint_cache[sprint_name]

        # Check existing sprints
        existing = self._get_existing_sprints()
        if sprint_name in existing:
            sprint_id = existing[sprint_name]["id"]
            print(f"  [EXISTS] Sprint: {sprint_name} (id={sprint_id})")
            self.sprint_cache[sprint_name] = sprint_id
            return sprint_id

        # Also check by number pattern (e.g. "SCRUM Sprint 1" auto-created)
        for name, info in existing.items():
            if f"Sprint {sprint_info.number}" in name:
                sprint_id = info["id"]
                # Rename to our convention and update goal/dates
                self._update_sprint(sprint_id, sprint_name, sprint_info)
                print(f"  [UPDATED] Sprint: {name} -> {sprint_name} (id={sprint_id})")
                self.sprint_cache[sprint_name] = sprint_id
                return sprint_id

        # Create new sprint
        sprint_id = self._create_sprint(sprint_name, sprint_info)
        print(f"  [CREATED] Sprint: {sprint_name} (id={sprint_id})")
        self.sprint_cache[sprint_name] = sprint_id
        return sprint_id

    def _create_sprint(self, name: str, info: SprintInfo) -> int:
        """Create a sprint via the Agile REST API."""
        import requests
        payload = {
            "name": name,
            "originBoardId": self.board_id,
            "goal": info.goal,
        }
        if info.start_date:
            payload["startDate"] = f"{info.start_date}T09:00:00.000Z"
        if info.end_date:
            payload["endDate"] = f"{info.end_date}T18:00:00.000Z"

        resp = requests.post(
            f"{self.url}/rest/agile/1.0/sprint",
            json=payload,
            auth=self.auth,
        )
        resp.raise_for_status()
        return resp.json()["id"]

    def _update_sprint(self, sprint_id: int, name: str, info: SprintInfo):
        """Update an existing sprint's name, goal, and dates."""
        import requests
        payload = {"name": name, "goal": info.goal}
        if info.start_date:
            payload["startDate"] = f"{info.start_date}T09:00:00.000Z"
        if info.end_date:
            payload["endDate"] = f"{info.end_date}T18:00:00.000Z"

        resp = requests.post(
            f"{self.url}/rest/agile/1.0/sprint/{sprint_id}",
            json=payload,
            auth=self.auth,
        )
        resp.raise_for_status()

    def _move_issues_to_sprint(self, sprint_id: int, issue_keys: list[str]):
        """Move issues into a sprint via the Agile REST API."""
        if not issue_keys:
            return
        import requests
        resp = requests.post(
            f"{self.url}/rest/agile/1.0/sprint/{sprint_id}/issue",
            json={"issues": issue_keys},
            auth=self.auth,
        )
        resp.raise_for_status()

    def start_sprint(self, sprint_id: int, info: SprintInfo):
        """Start a sprint (move from 'future' to 'active')."""
        import requests
        payload = {
            "state": "active",
            "startDate": f"{info.start_date}T09:00:00.000Z",
            "endDate": f"{info.end_date}T18:00:00.000Z",
        }
        resp = requests.post(
            f"{self.url}/rest/agile/1.0/sprint/{sprint_id}",
            json=payload,
            auth=self.auth,
        )
        if resp.status_code == 200:
            print(f"  [STARTED] Sprint {info.id}: {info.start_date} -> {info.end_date}")
        else:
            error = resp.json().get("errorMessages", [resp.text])
            print(f"  [WARN] Could not start sprint: {error}")

    # --- Issue creation ---

    def get_or_create_epic(self, epic: Epic) -> str:
        """Create a Jira Epic. Returns the Jira issue key."""
        existing_key = self._find_existing(epic.id)
        if existing_key:
            print(f"  [EXISTS] Epic: {existing_key} - {epic.name}")
            self.created_issues[epic.id] = existing_key
            return existing_key

        issue = self.jira.create_issue(
            project=self.project_key,
            summary=f"[{epic.id}] {epic.name}",
            description=(
                f"*Business Value:* {epic.description}\n\n"
                f"*Sprints:* {epic.sprints}\n\n"
                f"_Auto-created from PLAN_EXECUTION.md_"
            ),
            issuetype={"name": "Epic"},
        )
        key = issue.key
        print(f"  [CREATED] Epic: {key} - {epic.name}")
        self.created_issues[epic.id] = key
        return key

    def create_story(self, feature_id: str, feature_name: str, epic_key: str) -> str:
        """Create a Jira Story for a Feature. Returns the Jira issue key."""
        if feature_id in self.created_issues:
            return self.created_issues[feature_id]

        existing_key = self._find_existing(feature_id)
        if existing_key:
            print(f"  [EXISTS] Story: {existing_key} - {feature_name}")
            self.created_issues[feature_id] = existing_key
            return existing_key

        issue = self.jira.create_issue(
            project=self.project_key,
            summary=f"[{feature_id}] {feature_name}",
            description=f"Feature under Epic {epic_key}.\n\n_Auto-created from PLAN_EXECUTION.md_",
            issuetype={"name": "Historia"},
            parent={"key": epic_key},
        )

        key = issue.key
        print(f"  [CREATED] Story: {key} - {feature_name}")
        self.created_issues[feature_id] = key
        return key

    def create_pbi(self, pbi: PBI, parent_key: str) -> str:
        """Create a Jira Sub-task for a PBI. Returns the Jira issue key."""
        existing_key = self._find_existing(pbi.id)
        if existing_key:
            print(f"  [EXISTS] Task: {existing_key} - {pbi.title}")
            self.created_issues[pbi.id] = existing_key
            return existing_key

        # Map priority
        priority_map = {
            "Critical": "Highest",
            "High": "High",
            "Medium": "Medium",
            "Low": "Low",
        }

        description = (
            f"*Description:* {pbi.description}\n\n"
            f"*Acceptance Criteria:*\n{pbi.acceptance_criteria}\n\n"
            f"*Service:* {pbi.service}\n"
            f"*Sprint:* {pbi.sprint}\n"
            f"*Agent:* {pbi.agent}\n"
            f"*Story Points:* {pbi.story_points}\n\n"
            f"_Auto-created from PLAN_EXECUTION.md_"
        )

        issue = self.jira.create_issue(
            project=self.project_key,
            summary=f"[{pbi.id}] {pbi.title}",
            description=description,
            issuetype={"name": "Subtask"},
            parent={"key": parent_key},
            priority={"name": priority_map.get(pbi.priority, "Medium")},
        )

        # Set story points if the field exists
        try:
            issue.update(fields={"story_points": pbi.story_points})
        except Exception:
            pass  # Story points field may have a different name

        key = issue.key
        print(f"  [CREATED] Task: {key} - {pbi.title}")
        self.created_issues[pbi.id] = key
        return key

    # --- Full sync ---

    def sync_all(self, epics: list[Epic], pbis: list[PBI],
                 sprint_infos: dict[str, SprintInfo],
                 filter_epic: Optional[str] = None,
                 filter_sprint: Optional[str] = None,
                 start_sprint_id: Optional[str] = None):
        """Sync all epics, features, PBIs, and sprints to Jira.

        Resilient: each operation is wrapped in try/except so failures
        are logged but don't stop the rest of the sync. Re-running the
        script will skip already-created items ([EXISTS]) and retry
        only what failed.
        """
        # Group PBIs by feature
        feature_pbis: dict[str, list[PBI]] = {}
        for pbi in pbis:
            if filter_epic and pbi.epic_id != filter_epic:
                continue
            if filter_sprint and pbi.sprint != filter_sprint:
                continue
            feature_pbis.setdefault(pbi.feature_id, []).append(pbi)

        # Create epics
        print("\n=== Creating Epics ===")
        for epic in epics:
            if filter_epic and epic.id != filter_epic:
                continue
            has_pbis = any(p.epic_id == epic.id for feat_pbis in feature_pbis.values() for p in feat_pbis)
            if filter_sprint and not has_pbis:
                continue
            try:
                self.get_or_create_epic(epic)
            except Exception as e:
                msg = f"Epic {epic.id} ({epic.name}): {e}"
                print(f"  [ERROR] {msg}")
                self.errors.append(msg)

        # Create features as stories and PBIs as sub-tasks
        print("\n=== Creating Features & PBIs ===")
        for feat_id, feat_pbis in feature_pbis.items():
            if not feat_pbis:
                continue

            sample = feat_pbis[0]
            epic_key = self.created_issues.get(sample.epic_id)
            if not epic_key:
                for epic in epics:
                    if epic.id == sample.epic_id:
                        try:
                            epic_key = self.get_or_create_epic(epic)
                        except Exception as e:
                            msg = f"Epic {epic.id} ({epic.name}): {e}"
                            print(f"  [ERROR] {msg}")
                            self.errors.append(msg)
                        break

            if not epic_key:
                msg = f"Skipped {feat_id}: parent epic {sample.epic_id} not created"
                print(f"  [SKIP] {msg}")
                self.errors.append(msg)
                continue

            try:
                story_key = self.create_story(feat_id, sample.feature_name, epic_key)
            except Exception as e:
                msg = f"Story {feat_id} ({sample.feature_name}): {e}"
                print(f"  [ERROR] {msg}")
                self.errors.append(msg)
                continue

            for pbi in feat_pbis:
                try:
                    self.create_pbi(pbi, story_key)
                except Exception as e:
                    msg = f"PBI {pbi.id} ({pbi.title}): {e}"
                    print(f"  [ERROR] {msg}")
                    self.errors.append(msg)

        # --- Sprint management ---
        sprint_ids_needed: set[str] = set()
        for feat_pbis in feature_pbis.values():
            for pbi in feat_pbis:
                if pbi.sprint != "Backlog":
                    sprint_ids_needed.add(pbi.sprint)

        if sprint_ids_needed:
            print("\n=== Managing Sprints ===")
            for sid in sorted(sprint_ids_needed):
                info = sprint_infos.get(sid)
                if not info:
                    print(f"  [SKIP] No sprint info found for {sid}")
                    continue

                # Create/find sprint
                try:
                    jira_sprint_id = self.get_or_create_sprint(info)
                except Exception as e:
                    msg = f"Sprint {sid} ({info.name}) create/find: {e}"
                    print(f"  [ERROR] {msg}")
                    self.errors.append(msg)
                    continue

                # Collect issue keys for this sprint
                sprint_issue_keys: list[str] = []
                for feat_pbis in feature_pbis.values():
                    for pbi in feat_pbis:
                        if pbi.sprint == sid:
                            pbi_key = self.created_issues.get(pbi.id)
                            if pbi_key:
                                sprint_issue_keys.append(pbi_key)
                            story_key = self.created_issues.get(pbi.feature_id)
                            if story_key and story_key not in sprint_issue_keys:
                                sprint_issue_keys.append(story_key)

                # Assign issues to sprint
                if sprint_issue_keys:
                    try:
                        self._move_issues_to_sprint(jira_sprint_id, sprint_issue_keys)
                        print(f"  [ASSIGNED] {len(sprint_issue_keys)} issues -> Sprint {info.number}: {info.name}")
                    except Exception as e:
                        msg = f"Sprint {sid} assign issues: {e}"
                        print(f"  [ERROR] {msg}")
                        self.errors.append(msg)

                # Start sprint if requested
                if start_sprint_id and sid == start_sprint_id:
                    try:
                        self.start_sprint(jira_sprint_id, info)
                    except Exception as e:
                        msg = f"Sprint {sid} start: {e}"
                        print(f"  [ERROR] {msg}")
                        self.errors.append(msg)

        # --- Summary ---
        print(f"\n=== Sync Complete ===")
        print(f"  Issues synced: {len(self.created_issues)}")
        if self.errors:
            print(f"  Errors:        {len(self.errors)}")
            print(f"\n--- Errors (re-run to retry) ---")
            for err in self.errors:
                print(f"  - {err}")
            print(f"\nTip: Re-run the same command. Already-created items will be"
                  f" skipped ([EXISTS]) and only failed operations will be retried.")
        else:
            print(f"  Errors:        0")


# ---------------------------------------------------------------------------
# Dry-run printer
# ---------------------------------------------------------------------------

def dry_run(epics: list[Epic], pbis: list[PBI],
            sprint_infos: dict[str, SprintInfo],
            filter_epic: Optional[str] = None,
            filter_sprint: Optional[str] = None,
            start_sprint_id: Optional[str] = None):
    """Print what would be created without touching Jira."""
    print("\n=== DRY RUN - No Jira changes will be made ===\n")

    filtered_pbis = pbis
    if filter_epic:
        filtered_pbis = [p for p in filtered_pbis if p.epic_id == filter_epic]
    if filter_sprint:
        filtered_pbis = [p for p in filtered_pbis if p.sprint == filter_sprint]

    # Group by epic -> feature
    tree: dict[str, dict[str, list[PBI]]] = {}
    for pbi in filtered_pbis:
        tree.setdefault(pbi.epic_id, {}).setdefault(pbi.feature_id, []).append(pbi)

    total_sp = 0
    total_pbis = 0
    for epic in epics:
        if epic.id not in tree:
            continue
        print(f"EPIC: [{epic.id}] {epic.name}")
        for feat_id, feat_pbis in tree[epic.id].items():
            feat_name = feat_pbis[0].feature_name if feat_pbis else "Unknown"
            print(f"  STORY: [{feat_id}] {feat_name}")
            for pbi in feat_pbis:
                print(
                    f"    SUB-TASK: [{pbi.id}] {pbi.title} "
                    f"| {pbi.sprint} | {pbi.story_points}SP | {pbi.agent} | {pbi.priority}"
                )
                total_sp += pbi.story_points
                total_pbis += 1
        print()

    # Sprint summary
    sprint_ids_needed: set[str] = set()
    for pbi in filtered_pbis:
        if pbi.sprint != "Backlog":
            sprint_ids_needed.add(pbi.sprint)

    if sprint_ids_needed:
        print("--- Sprints ---")
        for sid in sorted(sprint_ids_needed):
            info = sprint_infos.get(sid)
            if not info:
                print(f"  {sid}: (no sprint info found in plan)")
                continue

            sprint_pbis = [p for p in filtered_pbis if p.sprint == sid]
            sprint_sp = sum(p.story_points for p in sprint_pbis)
            features_in_sprint = set(p.feature_id for p in sprint_pbis)

            marker = " *** WILL START ***" if start_sprint_id == sid else ""
            print(f"  Sprint {info.number}: {info.name}{marker}")
            print(f"    Dates:        {info.start_date} -> {info.end_date}")
            print(f"    Goal:         {info.goal}")
            print(f"    Deliverable:  {info.deliverable}")
            print(f"    Verification: {info.verification}")
            print(f"    Issues:       {len(sprint_pbis)} PBIs + {len(features_in_sprint)} Stories = {len(sprint_pbis) + len(features_in_sprint)} total")
            print(f"    Story Points: {sprint_sp}")
            print()

    print(f"Total: {total_pbis} PBIs | {total_sp} Story Points")
    print("\nRun without --dry-run to create these in Jira.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Sync Trading SaaS PLAN_EXECUTION.md to Jira"
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Print what would be created without touching Jira"
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
        "--start-sprint", type=str, default=None, dest="start_sprint",
        help="Start the specified sprint after syncing (e.g., S1)"
    )
    parser.add_argument(
        "--plan", type=str, default=None,
        help="Path to PLAN_EXECUTION.md (auto-detected if omitted)"
    )
    parser.add_argument(
        "--json", type=str, default=None,
        help="Export parsed data to JSON file instead of syncing"
    )
    args = parser.parse_args()

    # Find plan file
    if args.plan:
        plan_path = Path(args.plan)
    else:
        plan_path = Path(__file__).parent.parent / "PLAN_EXECUTION.md"
    if not plan_path.exists():
        print(f"ERROR: Plan file not found: {plan_path}")
        sys.exit(1)

    print(f"Parsing: {plan_path}")
    epics, pbis, sprint_infos = parse_plan(plan_path)

    if not pbis:
        print("ERROR: No PBIs found in plan file. Check the markdown format.")
        sys.exit(1)

    # Validate --start-sprint
    if args.start_sprint and args.start_sprint not in sprint_infos:
        print(f"ERROR: Sprint {args.start_sprint} not found in plan. Available: {', '.join(sorted(sprint_infos.keys()))}")
        sys.exit(1)

    # JSON export mode
    if args.json:
        output = {
            "epics": [{"id": e.id, "name": e.name, "description": e.description, "sprints": e.sprints} for e in epics],
            "pbis": [p.to_dict() for p in pbis],
            "sprints": {k: {"id": v.id, "number": v.number, "name": v.name, "goal": v.goal,
                            "start_date": v.start_date, "end_date": v.end_date,
                            "deliverable": v.deliverable, "verification": v.verification}
                        for k, v in sprint_infos.items()},
        }
        Path(args.json).write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"Exported {len(pbis)} PBIs + {len(sprint_infos)} sprints to {args.json}")
        return

    # Dry run mode
    if args.dry_run:
        dry_run(epics, pbis, sprint_infos,
                filter_epic=args.epic, filter_sprint=args.sprint,
                start_sprint_id=args.start_sprint)
        return

    # Live sync mode - requires Jira credentials
    jira_url = os.environ.get("JIRA_URL")
    jira_email = os.environ.get("JIRA_EMAIL")
    jira_token = os.environ.get("JIRA_API_TOKEN")
    jira_project = os.environ.get("JIRA_PROJECT")
    jira_board_id = os.environ.get("JIRA_BOARD_ID")

    missing = []
    if not jira_url:
        missing.append("JIRA_URL")
    if not jira_email:
        missing.append("JIRA_EMAIL")
    if not jira_token:
        missing.append("JIRA_API_TOKEN")
    if not jira_project:
        missing.append("JIRA_PROJECT")

    if missing:
        print(f"ERROR: Missing environment variables: {', '.join(missing)}")
        print("Set them in .env or export them. See .env.example for details.")
        print("\nTip: Use --dry-run to preview without Jira credentials.")
        sys.exit(1)

    try:
        board_id = int(jira_board_id) if jira_board_id else None
        syncer = JiraSync(jira_url, jira_email, jira_token, jira_project, board_id)
        syncer.sync_all(epics, pbis, sprint_infos,
                        filter_epic=args.epic, filter_sprint=args.sprint,
                        start_sprint_id=args.start_sprint)
    except ImportError:
        print("ERROR: 'jira' package not installed. Run: pip install jira")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: Jira sync failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
