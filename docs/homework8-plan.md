# Homework 8 work allocation and week-one review

Date: November 23, 2026. Four-member allocation proposed from the repository
review; individual teammates have not yet confirmed it. Feature letters follow
the order in the assignment. Estimates include implementation, tests and docs.
Each block has six relative effort points; equal feature counts are impossible
with nine features and four members.

| Member | Features | Effort |
| --- | --- | --- |
| A - Aarav | a: Moscow/London rules (1); b: mission status and assignment restrictions (2); g: agent status and assignment restrictions (3) | 6 |
| B - Mateo | c: create a brief from its mission (3); f: download mission briefs as JSON (3) | 6 |
| C - Rohan | d: new-message indicator on web and terminal (3); e: audit user filter and date sorting (3) | 6 |
| D - Jack | h: persistent agent mission history (3); i: portrait upload, disk storage and web display (3) | 6 |

Member B is selected for this work session because the existing responsibility
is web editing. Complete c and f now, together with the first-week review,
refactoring priorities, diagram updates, test contracts and work check-in.
Other blocks remain assigned work, not delivered features.

## Code review and prioritized backlog

| ID | Priority / owner | Finding and acceptance |
| --- | --- | --- |
| B1 | P0 / B | Mission detail has only Edit. Add an admin-only Add brief link; carry the mission through validation and save; redirect back to that mission. |
| B2 | P0 / B | `add` and `attachBrief` commit separately. Add one transactional brief-creation operation, including its mission FK and CREATE audit row; failures must leave no orphan. |
| B3 | P0 / B | No download route. Authenticated GET must return only the requested mission's briefs as UTF-8 JSON attachment, including an empty array, with correct string escaping. |
| B4 | P1 / B | Reuse existing form rendering, CSRF, field/date validation, session checks and audited related-record lookup. Avoid a second brief form or SQL path for exports. |
| A1 | P0 / A | Mission and agent fields lack status/rules. Migrate existing databases with explicit defaults; enforce assignment rules in the model, including status transitions and last-agent triggers. |
| C1 | P0 / C | Read state exists but navigation/terminal lack notifications. Resolve recipient ownership the same way as the existing inbox; verify delivery from another running session. |
| C2 | P0 / C | Audit view has no filter/sort controls. Bind username parameters and allowlist date direction; test malicious parameters and stable ordering. |
| D1 | P0 / D | Current joins are not assignment history. Preserve history across unassignment and status transitions; coordinate the assignment transaction with A. |
| D2 | P0 / D | No portrait storage/upload. Validate image format and size, generate server filenames, prevent traversal, authorize uploads and serve images with an appropriate CSP change. |
| T1 | P1 / all | Run full check and system tests per member; record actual evidence. Do not claim teammate signoffs or a team meeting from this single review. |

## Refactoring order and shared contracts

1. B: add atomic `addBrief` instead of composing two commits. Keep unlinked
   brief creation working for existing terminal/web users.
2. B: extend the existing new-brief form with a mission choice preselected by
   the mission link. Validate it again at submission, retain it on errors.
3. B: isolate JSON encoding from HTTP routing; use existing authorized reads.
4. A/D: agree on current-assignment versus historical-assignment semantics
   before changing joins or deleting assignments. Inactive missions accept briefs.
5. C: reuse the existing recipient/read-state service rather than caching
   unread state in individual browser sessions.

## Week-one design and test contract

Class diagram: `ContentService.addBrief(values, missionId, session)` and its
SQLite implementation; `ContentController.createBrief`; existing editing handler
and mission actions; a dedicated JSON serializer used by the authenticated route.
ERD: reuse nullable `briefs.mission_id -> missions.id`; no B schema migration.
Wireframe: mission detail -> Add brief -> preselected mission form -> save ->
mission detail; Download briefs (JSON) available to signed-in readers.

Tests/stubs precede each implementation in small commits. Cover persistence,
rollback, permissions, CSRF, missing missions, retained invalid form input,
mission isolation, empty export and JSON control/Unicode escaping. Run focused
tests followed by the unchanged 100% per-class method coverage gate. Final
results and week-one check-in belong in `homework8-member-b-week1.md`.
