# Homework 7 - Member B - Week 2

Owner: Mateo (Control). Verification date: November 19, 2026.

Member B's editing workflow is implemented for the current model: briefs, agents,
facilities and missions. This report supersedes the Week 1 proposal's runtime
status. It records repository work and automated system testing, not a team meeting
or a claim that every member's release requirements are complete.

## Delivered behavior

- Administrator lists offer New record; details offer Edit record. New scalar
  fields are empty and edit forms contain current stored values and stable IDs.
- GET `/new?type=...` and `/edit?type=...&id=...` display forms without saving.
  POST `/create` saves then redirects to the list; POST `/update` saves then
  redirects to that record. Cancel follows a GET link without a write.
- Every editing request resolves the current account role. Anonymous requests
  redirect to login; current standard users get 403 before content-service access.
  Account types are excluded; account/password/role management stays terminal-only.
- Editing POSTs require a session-specific CSRF token derived from a server secret
  and the opaque session token. A token from another session fails. Revoking the
  session prevents its form from saving. No extra persistent token store was added.
- Login and editing share an 8192-byte UTF-8 form parser. Wrong content types,
  duplicate/unexpected/missing parameters, malformed encoding and invalid IDs are
  rejected. Oversized bodies return 413; wrong methods return 405 with Allow.
- Validation errors return 400 with escaped submitted values; missing records
  return 404. Unexpected model/storage errors return a generic 500. No database
  exception text is displayed. Optional agent death date/notes may remain blank.
- Agent forms choose an existing facility. New missions choose an existing
  facility and first agent and call `addMission` once for transactional creation.
  Existing mission forms edit title, brief and date; mission relationship management
  remains in the terminal. Long text uses escaped textareas that preserve newlines.
- All content saves reuse model add/update operations and their transactional
  CREATE/UPDATE audit events. HTTP handlers contain no SQL or alternate content store.

## Backlog disposition

| Week 1 item | Week 2 result |
| --- | --- |
| B1 - editing routes | Implemented, guarded, tested, with admin navigation links. |
| B2 - controller writes | `ContentController` implements `WebEditingWorkflow`; audited writes and atomic mission creation reused. |
| B3 - error handling | Controller validation has a typed `WebValidationException`; missing edit/update records are checked before save. Storage failures stay 500. |
| B4 - CSRF | Implemented and tested for missing/forged tokens, cross-session reuse and revoked sessions. |
| B5 - forms | Empty/prefilled, escaped, multiline, relationship choices, save/cancel and menu links implemented. Shared CSS/navigation still awaits A. |
| B6 - countries | Blocked on A/D: supplied file exists but no country entity, ContentType, schema/import or facility-country contract exists yet. |
| B7 - coordination/parsing | Extracted `WebEditingHandler` and shared `WebFormData`; workflow remains independently testable. |
| B8 - sessions/relationships | Current roles and existing revocation are respected. D still owns inactivity timeout, terminal administration and richer linked detail APIs. |
| B9 - no web user management | ContentType allowlist retained; crafted user-type requests rejected. |
| B10 - revised tests | Both disabled editing draft classes enabled; additional security, view, real SQLite and executable JAR tests pass. |

## Validation actually executed

- `GRADLE_USER_HOME=C:\Users\mateo\.gradle` and `gradlew.bat clean check jar`:
  **572 tests passed, 0 skipped, 0 failures, 0 errors**. JaCoCo's unchanged per-class
  100% method gate passes: **380 covered methods, 0 missed**, across 56 classes.
  Method coverage does not imply 100% branch or line coverage.
- Unit/HTTP tests exercise authorization before model access, escaping, input
  parsing, empty/prefilled forms, redirects, CSRF isolation, role demotion,
  revocation, invalid dates/chronology, missing records and private storage errors.
- `WebEditingSqliteTest` runs real loopback HTTP against temporary SQLite files.
  Every current type creates/updates with persisted values and exactly one
  appropriate write audit event. Invalid choices leave no partial content. A
  failing audit trigger rolls back ordinary create/update and mission/relationship
  inserts; reopened services see saved values.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-jar.ps1`:
  **PASS** against the final clean-built executable JAR. Isolated terminal account
  setup, seeded data, private messaging, login/logout, all four types created and
  edited over HTTP, audit entries, escaped validation redisplay, forged CSRF and
  standard-user denial are verified. This is Member B's automated system test;
  other members still need to run their own system tests.
- Updated class diagram, ERD handoff and wireframes were regenerated and their
  changed PDF pages rendered and visually checked. Class diagram has 3 pages,
  ERD 2 and wireframes 6. Previews/build products are ignored, not submitted.
- `git diff --check` passes. The pre-existing `.idea/.name` change is excluded.

## Small, separate commits

Each task below was committed before proceeding to the next task. Changes remain
local; this request did not publish commits to GitHub.

| Commit | Completed task |
| --- | --- |
| `12da907` | Admin workflow, validation and atomic mission creation; enable workflow tests. |
| `88f05cb` | Escaped forms and relationship choices with view tests. |
| `5489bab` | Shared bounded UTF-8 parsing with parser/login regression tests. |
| `9bb4d85` | Protected HTTP routes, CSRF and expanded route acceptance tests. |
| `91388f3` | Admin-only New/Edit navigation and current-role visibility tests. |
| `8a7733f` | Real SQLite persistence, audit and rollback integration tests. |
| `739d214` | Executable JAR editing system test. |
| `1543f50` | Multiline form preservation and textarea escaping regression test. |
| `7f271d1` | Updated UML, ERD handoff and wireframes; rendered PDF verification. |
| `f96f446` | Current README, interaction/access documentation and release notes. |
| This check-in | Week 2 results, backlog disposition and team handoff. |

## Remaining integration and release work

1. A/D must implement the country entity, seed import, schema/backfill and editable
   fields. B can then extend facility-country dropdown preparation and integration
   tests against that contract; country controls cannot safely use an invented
   parallel store. Current generic scalar forms use ContentType field order.
2. A must provide shared page styling/navigation. B's forms use the same HtmlView
   document renderer and semantic controls, ready for common styling. No scripts
   or images were introduced.
3. D must provide the 30-minute inactivity timeout, current-session listing and
   terminal revocation, richer related-record links and broader search. Expiry
   cannot be system-tested until that implementation exists. Existing store
   invalidation and current-role resolution already protect editing routes.
4. D/B should consolidate typed model validation/not-found failures when that
   contract arrives. B currently validates at the workflow boundary for safe 400
   redisplay, while existing model validation remains authoritative. Mission
   relationship editing needs an agreed atomic update contract and rollback tests.
5. C's messaging implementation is present and passes the full suite. Its UI/model
   remains independent of content editing; no messaging audit requirement was added.
6. Final submission packaging still needs team review: remove obsolete historical
   homework artifacts from the submitted version, reconcile all members' diagrams,
   complete the pending features above, and have every member run a system test.
   Historical work records and other members' previous deliverables were retained
   in this development checkout; the TA checklist is not yet complete.
