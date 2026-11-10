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
