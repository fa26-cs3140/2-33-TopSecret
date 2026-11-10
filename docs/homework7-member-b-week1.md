# Homework 7 - Member B - Week 1

Owner: Mateo (Control). Date: November 10, 2026.

Scope: review, refactoring priorities, design documents, tests and disconnected
editing contracts. HTTP create/edit implementation belongs to Week 2. This is a
repository review and a proposed handoff; no team meeting or agreement is claimed.
Member A owns styling/countries, C owns web messaging, D owns model changes,
related-record display, search and session administration (four-member team).

## Task 1 - Code review and backlog

Reviewed `HttpWebServer`, `ContentController`, `HtmlView`, `ContentType`,
`ContentService`, `SqliteContentService`, `AccessControl` and their existing tests.

| ID | Priority | Finding and evidence | Owner / acceptance |
| --- | --- | --- | --- |
| B1 | P0 | HTTP route allowlist only permits GET for content; no create/edit routes or forms exist. | B: admin GET new/edit forms and POST create/update; unauthenticated requests redirect, standard users receive 403 without model calls. |
| B2 | P0 | `ContentController` only delegates reads; `ContentService` already exposes add/update and atomic `addMission`. | B + D: reuse model writes/audit, never SQL in HTTP handlers; create missions with facility and first agent in one transaction. |
| B3 | P0 | `HttpWebServer.handle` maps all `ProgramControlException` failures to 500. | B + D: distinguish validation, forbidden, not-found and storage errors for writes; invalid submissions retain input and never partially save. |
| B4 | P0 | No write-specific cross-site request protection exists (web currently read-only). | B: add per-session CSRF token to editing forms; validate before mutation, including direct POST attempts. |
| B5 | P1 | `HtmlView` exposes read-only rendering; it escapes values but has no form display contract. | B + A: empty new forms, populated edit forms, escaped labels/values/errors, cancel links and shared CSS/navigation; no scripts/images. |
| B6 | P1 | `ContentType.fields` has four types; no country entity yet. | A + D dependency: agree country field/ID and facility-country choices; B extends generic forms when model contract lands. Do not invent a parallel country store. |
| B7 | P1 | `route` mixes dispatch, authentication, parsing and display assembly. | B: extract form coordination and share bounded UTF-8 form parsing before enabling writes; preserve login behavior. |
| B8 | P1 | Current sessions do not expire; relationship details are only assembled for missions. | D dependency: inactivity timeout/revocation and related-detail APIs; B resolves current account role on every write. |
| B9 | P1 | Users/passwords/roles are terminal workflows today. | B: keep account types out of form type allowlist; no account-management HTTP routes or links. |
| B10 | P2 | Existing tests assert read-only HTTP behavior. | B: retain regression tests while scaffold is disconnected, then revise only relevant route expectations in Week 2. Add HTTP + SQLite write/audit/rollback tests then. |

No existing runtime defect has yet been reproduced by this review. New Homework 7
features above are planned work, not claims that Homework 6 requirements failed.
Baseline and final validation evidence will be recorded in the check-in.

## Task 2 - Prioritized refactoring plan

1. P0 - Separate form preparation from transport. Introduce a disconnected
   `WebEditingWorkflow` interface and immutable `WebEditForm` display data in
   `controller`. The contract takes a freshly resolved model `Session`; it never
   authenticates users or trusts a browser role. This makes new/edit preparation
   independently testable before altering the running server.
2. P0 - Reuse persistence and authorization. The Week 2 implementation delegates
   ordinary writes to `ContentService.add/update`, and mission creation to
   `addMission`. Do not implement generic mission creation with `add(MISSION)`:
   that path does not create the required relationships. Model owns validation,
   transactions and CREATE/UPDATE audit events. Agree typed error results with D
   before mapping validation to 400, role denial to 403 and missing IDs to 404.
3. P1 - Extract a shared form decoder from `HttpWebServer.login` when wiring writes.
   Preserve the 8192-byte limit, strict URL decoding and duplicate-key rejection.
   Permit only expected fields and positive record/relationship IDs. Reject
   unknown content types before model access. Verify CSRF before save delegation.
4. P1 - Keep HTML in `HtmlView`. A supplies common navigation/CSS; B supplies
   ordered field values, relationships, errors and form targets. Never concatenate
   unescaped input into an HTTP response or copy terminal Scanner loops into it.
5. P2 - Reduce route complexity after contract tests pass. Dispatch editing routes
   through a small handler using the workflow interface. Keep read/login/logout
   behavior under existing HTTP regression tests and avoid moving unrelated code.

Definition of done for refactoring: existing terminal and HTTP tests still pass;
new contract tests cover empty/prefilled forms and defensive copies; Week 2 tests
cover authorization before service calls, validation without partial writes,
escaped redisplay, atomic mission creation and audit events. Preserve the existing
100% method-coverage gate; do not relax it to make new code pass.

### Proposed HTTP contract (Week 2 only)

| Route | Inputs | Success |
| --- | --- | --- |
| GET /new | type | 200 empty editable fields; relationship choices as needed |
| GET /edit | type, positive id | 200 fields populated from the current record |
| POST /create | type, csrf, type fields, required relationship choices | 303 to the content list (ordinary add does not return an ID) |
| POST /update | type, positive id, csrf, type fields | 303 to the updated record |

All four routes require login and a current administrator role. Missing session:
303 to `/`. Standard user: 403 even for a crafted URL/body. Wrong method: 405
with Allow. Invalid fields: 400 with submitted values and safe error text;
missing record: 404; oversized body: 413; unexpected storage failure: generic
500. GET requests never mutate. Cancel returns to the list/detail without a write.
After success, use POST/Redirect/GET to prevent refresh resubmission. Country
support waits for A/D's schema contract; accounts are never editable web content.

## Task 3 - Associated design documents

Updated the existing `SystemClasses.html/.pdf`, `DatabaseERD.html/.pdf` and
`WebWireframes.html/.pdf` with clearly labeled Homework 7 design supplements.
Existing runtime diagrams remain accurate on preceding pages. The class design
shows the workflow interface, form data and planned dependencies. The ERD shows
the proposed country relationship and explicitly leaves migration details to A/D;
Member B adds no database table. Wireframes show empty/populated forms, all type
fields, admin controls, cancel/save destinations and invalid-request outcomes.

Regenerate the supplements and PDFs with `node docs/update-hw7-design.cjs`.
The script replaces its own supplement on repeated runs. It requires local Edge,
Chrome or Chromium. If rebuilding the older base diagrams with the existing
render scripts, run this supplement script last. Preview with the existing
`scripts/render-pdf-preview.ps1`. PDF outputs were rendered and visually checked.
Team-wide removal of historical homework documents is a final release task;
this Week 1 contribution does not delete other members' delivery records.
