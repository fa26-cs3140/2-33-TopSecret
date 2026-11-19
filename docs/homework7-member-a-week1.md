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

## Task 2 - Prioritized refactoring plan

The ordering is by what later work depends on, not by size.

1. **P0 - Render navigation once in the page shell.** `HtmlView.document` is
   already the single place every page passes through, but it takes only a
   title and a body, so the menu required by A2 would have to be pasted into
   five methods. Give `HtmlView` an immutable navigation field and a
   `withNav(username, links)` method that returns a new view carrying it.
   Pages rendered from a view with no navigation (log in, error) stay exactly
   as they are. A per-request copy is used instead of a mutable field because
   `HtmlView` is shared by every thread the HTTP server serves; a settable
   field would be a data race between concurrent requests. This also retires
   the four duplicated `Back to menu` anchors of A10.
2. **P0 - Generalise identifier validation.** `ContentController.validateValues`
   keys its identifier check on the literal string `facility_id` and ends every
   failure with `and a positive facility ID`. Widen the branch to any field
   whose name ends in `_id` and name the rejected field in the message. Without
   this, a bad country selection reports a facility problem. Doing it before
   countries exist keeps the change a pure refactor with existing tests as the
   guard.
3. **P1 - Extend the facility importer rather than duplicating it.** A6 needs a
   third column. `tabRows` takes a fixed arity, so the honest fix is an
   optional trailing column that two-column files still satisfy, not a second
   facility importer beside the first.
4. **P1 - Reuse the existing foreign-key convention for countries.** A facility's
   country travels the path `facility_id` already travels: a field on
   `ContentType`, a dropdown from `WebEditingHandler.choices`, and an
   identifier check in `validateValues`. Reusing it means B's generic create
   and edit forms gain the country control with no change to his rendering
   code, and no parallel country route or store is introduced. This is the
   cheapest correct option and the one B asked for in his B6 handoff.
5. **P1 - Remove the previous-version document and correct the role record.**
   A8 and A9 are small, carry checklist marks, and are independent of code.

### Decisions recorded

**Styling is served in the page shell, not as a separate stylesheet route.**
A `/style.css` route would have to be reachable before login, since the log in
page is styled, which widens the unauthenticated surface of `HttpWebServer`
and adds a path to its route allowlist for a file of a few hundred bytes. Held
in the shell the stylesheet cannot 404, needs no authentication decision, and
stays in the one method every page already shares. No Javascript and no images
are used, as the assignment requires.

**`facilities.country_id` is nullable.** A facility is assigned to one country,
but existing databases already hold six facilities with no country, and
`CREATE TABLE IF NOT EXISTS` cannot add a required column to them. Nullable
with a migration matches how `briefs.mission_id` was added in Homework 6. The
web form still requires a choice, so a facility saved through the application
always has a country; a row predating the migration reads as unassigned rather
than blocking the page.

### Not attempted this cycle

`ContentMenu` is 327 lines and is the longest class in the view package. It is
a genuine decomposition candidate, but it is the terminal menu that every
member's feature reaches through, B is committing to the editing path in the
same week, and splitting it now would collide with that work for no marked
deliverable. Recorded here as the first candidate for Homework 8 rather than
left unmentioned.
