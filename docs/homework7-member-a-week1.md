# Homework 7 - Member A - Week 1

Owner: Aarav (View). Date: November 19, 2026.

Scope: repository review, refactoring priorities, design documents, and tests
with disconnected stubs. Implementation belongs to Week 2. Role A for a
four-member team is the visual presentation of the HTML pages, the countries
entity, and the command line help option. Member B owns the editing workflow,
C owns web messaging, D owns model writes, related-record display, search and
session administration. This is a repository review and a proposed handoff; no
team meeting or agreement is claimed.

## Task 1 - Code review and backlog

Reviewed `HtmlView`, `MessageHtmlView`, `ContentMenu`, `ContentText`,
`HttpWebServer`, `WebEditingHandler`, `ContentController`, `LaunchMode`,
`ContentType`, `SqliteContentService` and the documents under `docs/`.
Baseline on this commit's parent: 572 tests, zero failures, zero missed methods.

| ID | Priority | Finding and evidence | Owner / acceptance |
| --- | --- | --- | --- |
| A1 | P0 | No stylesheet exists anywhere. `HtmlView.document` emits `<head>` with a charset and a title only, and a search for `<style`, `stylesheet` and `.css` across `view` and `controller` returns nothing. | A: every page served with CSS, no Javascript and no images. |
| A2 | P0 | No menu is visible on every page. Log out is rendered only by `HtmlView.menu`; the list, record, audit and form pages each end in a lone `Back to menu` anchor, four occurrences in one file. A signed-in user on a record page cannot log out without navigating away first. | A: one navigation block with log out on every authenticated page. |
| A3 | P0 | No countries entity. `ContentType` declares four types and `createSchema` creates no countries table. `countries.txt` (195 names, added by C in 2dc34a7) is read by no code: `FileHandler.readText` is called only for the three `.tsv` files. | A: countries table, imported from the supplied list at initialization. |
| A4 | P0 | No help option. `LaunchMode.fromArguments` accepts `-web` and `--change-password` only and throws `IllegalArgumentException` for anything else, so `-h` currently exits as a usage error rather than displaying help. | A: `-h` prints help text and exits successfully. |
| A5 | P1 | Facilities have no country. `CREATE_FACILITIES` is `(id, name, abbreviation)`. `CREATE TABLE IF NOT EXISTS` cannot add a column to a database that already exists, so a column alone would pass on a temporary database and fail against a developer's `data/missions.db`. | A: nullable `country_id` plus an `ALTER TABLE` migration, following the `briefs.mission_id` precedent already in `createSchema`. |
| A6 | P1 | `facilities.tsv` carries two columns and no country, and the importer arity is fixed at `tabRows(contents, 2, "Facility")`. Left alone, all six seeded facilities would display no country and the feature would look unimplemented to a reader. | A: accept an optional third column and resolve it against the imported countries by name; keep two-column files valid. |
| A7 | P1 | `ContentController.validateValues` reports every field failure with one message ending `and a positive facility ID`, and the `requireId` branch keys on the literal `facility_id`. Once `country_id` exists this misreports which field was rejected. | A: generalise the identifier branch and the message to any `_id` field. |
| A8 | P1 | `docs/Homework6Wireframes.pdf` is a previous-version document, superseded by `docs/WebWireframes.pdf` and referenced by no file in the repository. The deliverables checklist forbids previous-version documentation by name. | A: delete it. |
| A9 | P1 | `docs/team.txt` records Homework 6 roles only. The role letters keep their owners in Homework 7 but change meaning for a four-member team, so the file now misdescribes who owns what. | A: record the Homework 7 split. |
| A10 | P2 | `HtmlView` rebuilds the page shell and the back link in four methods, and `document` is the only chokepoint but accepts no navigation. Adding a menu per page would duplicate it five times. | A: render navigation once in the page shell; see Task 2. |

No existing runtime defect was reproduced by this review. A1 to A4 are new
Homework 7 requirements, not failures of the delivered Homework 6 work. A5 to
A7 are consequences of adding countries, found by reading the code that the
change touches. A8 and A9 are documentation defects that exist today.
