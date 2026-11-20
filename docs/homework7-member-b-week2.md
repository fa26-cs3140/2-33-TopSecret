# Homework 7 - Member B - Week 2

Owner: Mateo (Control). Final integrated verification: November 20, 2026.
Source: `48156dd` on `main`, including Member C PR #8 and Member D PR #7.
This is an agent-executed repository and system-test record. It does not assert
that any teammate personally performed a test or submitted the assignment.

## Delivered editing workflow

- Administrators can create and edit briefs, agents, facilities, missions and
  countries. Lists offer New record; details offer Edit record. New scalar fields
  are empty, edit fields contain stored values, and Cancel performs no write.
- GET `/new` and `/edit` only prepare forms. POST `/create` and `/update` validate
  and save through `ContentController` / `WebEditingWorkflow`, then redirect.
  `WebEditingHandler` coordinates HTTP; `HtmlView` owns escaped form rendering,
  shared CSS and navigation. Handlers contain no SQL or alternate content store.
- Current administrator permission is checked on every request. Missing, expired
  or revoked sessions redirect to login. Standard users receive 403, including
  direct writes from forms loaded before demotion. User/account management stays
  in the terminal; account types and unsupported methods are rejected.
- Session-bound CSRF checks precede writes. Login, editing and messaging share
  `WebFormData`: bounded 8192-byte UTF-8 decoding and strict field checks.
  Validation returns 400 with escaped submitted values, missing records 404,
  oversized forms 413, wrong methods 405 and private storage failures generic 500.
- Agent forms select an existing facility. Mission creation selects a facility
  and first agent and saves atomically. Mission updates edit title, brief and
  date; relationship management remains in the terminal. Multiline text is
  preserved; agent death date/notes can be empty, and dates are validated.
- Facility forms offer countries and preselect the current assignment. Country
  reassignment and field updates share one audited transaction. Renaming a country
  immediately changes the linked name displayed at its facilities.

## Backlog disposition

| Item | Final result |
| --- | --- |
| B1 - routes | Admin new/edit GETs and create/update POSTs implemented and tested. |
| B2 - writes | Existing model writes and atomic mission creation reused. |
| B3 - errors | Typed validation, missing-record checks and private storage errors. |
| B4 - CSRF | Session-specific tokens; stale and cross-session forms cannot save. |
| B5 - forms | Empty/prefilled, escaped, multiline, relationship choices, shared A styling/menu. |
| B6 - countries | A/D contracts integrated; web assignment, reassignment, rename and audit rollback verified. |
| B7 - refactoring | Separate editing handler/workflow; one shared bounded form decoder. |
| B8 - access/relationships | D's persistent timeout/revocation and linked details integrated; editing tested with real credentials. |
| B9 - accounts | Terminal-only management; no editable web account type. |
| B10 - tests | Drafts enabled; unit, HTTP, SQLite and executable JAR checks pass. |

## Verification actually executed

`GRADLE_USER_HOME=C:\Users\mateo\.gradle` and `gradlew.bat clean check jar`:
**719 tests, zero failures/errors/skips; 431 covered methods, zero missed**.
The unchanged JaCoCo gate enforces 100% method coverage per production class.
Method coverage is not a claim of 100% branch or line coverage.

`WebEditingSqliteTest` now runs against real ciphered credentials and D's persistent
SQLite sessions. Seven tests cover all five content types, exact write audit
counts, restart persistence, invalid relationships, country selection/reassignment,
rename propagation, audit-trigger rollback, sliding inactivity timeout, stale CSRF
after re-login, terminal revocation and immediate role demotion. A controllable
clock advances time without a thirty-minute wait. Other unit/HTTP tests retain
authorization, parsing, form escaping, redirects and error-path coverage.

`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-jar.ps1`:
**PASS**. The executable JAR uses isolated disposable storage under `build/`.
It verifies terminal account setup and private messaging; browser-protocol
login/logout; creation/update of all five types; facility-country assignment and
rename propagation; linked facility/agent/mission/country details; shared styling
and logout navigation; CREATE/UPDATE audit entries; escaped validation redisplay;
forged CSRF and standard-user denial; standard-user web messaging, Unread-to-Read
transition and recipient privacy even against an administrator. The server stops
in `finally`. This automated system test does not involve a rendered browser UI.

The JAR script and full suite must run sequentially: both use port 3002. An initial
overlapping run caused two startup failures; the subsequent isolated clean suite
passed all 719 tests. `git diff --check` passes. Local IDE settings are excluded.

## TA deliverables checklist

| Requirement | Evidence / status |
| --- | --- |
| Updated UML | `SystemClasses.html/.pdf`: integrated editing, messaging and sessions. |
| Updated ERD | `DatabaseERD.html/.pdf`: countries, relationships, audit, messages and sessions. |
| Updated wireframe | `WebWireframes.html/.pdf`: current forms, navigation, linked details and messaging. |
| Correct current documentation; no old-version deliverables | Current operational docs reconciled; superseded Homework 6 artifacts removed in the merged release. Homework 7 planning records are labeled historical. |
| 100% method coverage; unit and integration tests | Clean check passes; 719 tests, 431/431 methods. |
| Web add/edit | All five types pass HTTP/SQLite and executable JAR tests. |
| Country property | Imported/editable countries, facility choice, linked names and rename propagation verified. |
| Related data visible/clickable | `HttpWebRelatedRecordsSqliteTest`; JAR verifies links on integrated detail pages. |
| Audit includes new features | CREATE/UPDATE/DELETE and session revocation tests; transactional failure leaves no partial saves. Messaging is exempt. |
| Four-member messaging center | Messaging unit/integration tests plus executable JAR send/open/read/privacy checks. |
| Frequent small commits | Each final task below was committed and pushed before the next task started. |

Canonical diagram command: `node docs/render-hw7-docs.cjs`. The merged current
diagrams already describe the implemented architecture; these final test-only
changes introduce no diagram changes. Previous revisions remain in Git history.

## Final task commits

| Commit | Completed task |
| --- | --- |
| `0546403` | Country web reassignment, rename propagation and audited rollback regressions. |
| `4460262` | Real-account editing with persistent session expiry, revocation and role demotion. |
| `48156dd` | Executable JAR checks for all five types, related links, navigation and web messaging. |
| This check-in | Current Member B report, TA evidence and reconciled release documentation. |

Earlier small implementation commits remain in Git history. All final task
commits are published separately to `origin/main` with descriptive commit titles.

## Remaining human release steps

Each teammate must personally run and record their required system test before
the deadline; automated agent execution cannot certify that personal action.
Member C's manual checklist is in `homework7-member-c-system-test.md`. Member B
can reproduce the isolated JAR test with the command above and record their own
signoff. The team must complete course submission. No live meeting, personal
test signoff or course submission is claimed by this report.
