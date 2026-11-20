# Homework 7 - Member A - Week 2

Owner: Aarav (View). Date: November 19, 2026.

Scope: implementation of the Week 1 plan, the refactors it prioritised, the
documents, and a system test against the built JAR. The backlog identifiers
below are the ones recorded in `homework7-member-a-week1.md`.

## What was delivered

| Requirement | Where | Backlog |
| --- | --- | --- |
| Every HTML page styled with CSS, no Javascript, no images | `HtmlView.STYLE`, held in the shared page shell | A1 |
| A menu on every page, including log out | `HtmlView.chrome`, wired per request in `HttpWebServer.route` | A2 |
| Countries as a new entity, imported from the supplied list | `ContentType.COUNTRY`, `SqliteContentService.CREATE_COUNTRIES` and `importCountries` | A3 |
| `-h` displays help text | `LaunchMode.HELP`, `TopSecret.HELP_TEXT` | A4 |
| Facilities assigned to a country, a country has zero or more | `facilities.country_id`, nullable, `ON DELETE RESTRICT` | A5 |
| Viewing a facility displays its country | `HttpWebServer.recordPage`, `ContentMenu.showFacilityCountry` | A3 |
| Countries editable, and the new name shows at assigned facilities | the generic update path; the facility reads through the key | A3 |
| The country changeable when editing a facility | `WebEditingHandler.choices` and `selected`, `ContentController.update` | A3 |
| Sample facilities name a country | `facilities.tsv`, third column | A6 |

Each item landed as a failing-test commit followed by the commit that made it
pass, except the terminal menu, where the first attempt committed both together;
that was unwound and recommitted in the right order after confirming the tests
genuinely failed without the implementation.

## Refactoring carried out

1. **One page shell, rendered once.** `HtmlView.document` was the only place
   every page passed through but took no navigation, so a menu would have been
   pasted into five methods. `HtmlView` now holds an immutable `Nav` and
   `withNav` answers a new view carrying it. A per-request copy, not a settable
   field: the view is shared by every thread the HTTP server serves, and a
   mutable field would be a data race between concurrent requests. This also
   retired the four duplicated `Back to menu` anchors (A10).
2. **One shell and one escaper for the messaging pages.** `MessageHtmlView`
   built its own `<!doctype html>` and carried its own copy of `escape`. It now
   renders through `HtmlView.shell`, so C's messaging pages gained the styling
   and the navigation without changes to their content, and the second shell
   and the duplicate escaper are gone.
3. **Record references validated by field name.** `validateValues` keyed its
   identifier check on the literal `facility_id` and ended every failure with
   "and a positive facility ID" whatever the type. It now checks any field whose
   name ends in `_id`, and the field validation is decomposed into named methods
   so the message says which field was rejected (A7).
4. **An unauthenticated page cannot show session controls.** `login` and the
   no-session `error` page render through a separate shell path rather than
   relying on the caller to pass a view without navigation.
5. **Deleted the superseded `Homework6Wireframes.pdf`** and recorded the
   Homework 7 roles in `team.txt` (A8, A9).

## A defect the unit tests could not see

`TopSecretTest` exercised `-h` through `TopSecret.run`, which the test harness
calls directly with an injected credential store. That test passed. Running the
built JAR showed the real behaviour: `main` prepares the mission repository, the
audit log and the content service *before* reaching `run`, so `-h` created
`data/missions.db` in the working directory and only then printed the help text.
The claim in `README.txt` that help touches no storage was therefore false when
written.

Fixed by answering `HELP` in `main` immediately after the arguments are parsed,
before anything is constructed. A new test, `mainAnswersHelpWithoutPreparingAnyStorage`,
goes through `main` with the storage system properties pointed at a temporary
directory and asserts that neither the database nor the credential file is
created. It fails against the previous code and passes now.

This is the second-week system test earning its place: a test that drives a
method below the entry point cannot see what the entry point does first.

## Decisions, including one refined during implementation

**The country is not a field of `ContentType.FACILITY`.** Every field named in
`ContentType.fields` is required non-blank by validation, and `country_id` has
to accept rows written before the column existed. Keeping it outside `fields`
also meant B's generic create and edit forms gained the country control through
the mechanism he already had for relationship choices, with no change to his
rendering code, and it avoided altering twenty-three facility call sites across
sixteen test files.

**The country is optional in a request, although the form always offers it.**
Week 1 recorded that the form would require a choice, so a facility saved
through the application would always have one. Implementation showed that
making the field mandatory in the request allowlist rejected every existing
facility form in B's and C's tests, including ones with no countries in their
fixtures. The field is now required by the form and optional in the request: a
submission that omits it leaves the facility unassigned, which is what the
nullable column already allows. Anything outside the required and optional sets
is still refused, so B's rejection of unexpected fields is intact.

**`countries.name` carries no UNIQUE constraint.** The supplied list is
imported once into an empty table, so it holds no duplicates. A constraint
violation on a later rename would reach the user through the generic update path
as an unexplained storage error rather than a message naming the field. Adding
the constraint together with the validation that would explain it is proposed
for Homework 8.

## An independent audit, and six defects it found

After the work was pushed, it was re-audited against the assignment and the
deliverables checklist from the requirements rather than from the author's own
account of what had been built. Everything below was verified before it was
fixed, and each fix landed as a failing test followed by the change.

1. **The response policy blocked the stylesheet.** `HttpWebServer` sends
   `Content-Security-Policy: default-src 'none'; ...` with no `style-src`.
   `style-src` falls back to `default-src` when absent, so `'none'` made the
   browser refuse the inline stylesheet the page shell carries. Every page
   would have rendered unstyled in front of a reader while every assertion
   about the HTML still passed, because no test looked at a response header.
   This was the most serious defect of the cycle and it was invisible from the
   inside: the styling requirement was the one being graded, and it was the one
   broken. The policy now names `style-src 'unsafe-inline'` and keeps
   `default-src 'none'`, so scripts and images stay refused. Two tests now
   assert the policy admits the stylesheet, permits no script source, and is
   present on every response.
2. **Errors raised while handling a request lost the menu.** The catch blocks
   in `handle` rendered from the navigation-less view, because the per-request
   navigation is built inside `route` and is out of scope by then. A signed-in
   reader following a stale record link got a page whose only exit was a log in
   link, contradicting this file's own design note and the test for it. Error
   pages now resolve the reader's view through a helper that falls back to the
   plain view if anything goes wrong, since an error page must never throw.
3. **A missing `countries.txt` stopped the application.** With `facilities.tsv`
   present and naming countries but no country list supplied, the importer
   threw and initialization aborted: the program printed one error and exited,
   where before this cycle the same directory started normally. Every seed file
   is optional, so a facility is now imported unassigned when no list was
   loaded. A name missing from a list that *was* loaded still fails, because
   that is a fault in our own sample data.
4. **The README still said this work was pending.** Two paragraphs read
   "Shared CSS/navigation awaits A" and the ERD was described as "proposed
   country design", in a file edited the same afternoon. `development-plan.txt`
   and `programcontrol.txt` also still presented the Homework 6 plans as
   current, and the latter asserted the controller package was "not yet
   connected to the terminal startup", which has been false since Homework 6.
   All corrected.
5. **Two classes were missing from the class diagram.** `MessageHtmlView` and
   `WebMessageHandler` appeared on no page of it. All 63 classes under
   `src/main/java` are now present, checked by name rather than by eye.
6. **`.idea/.name` still named the project `HW-3-TopSecret`.** Tracked in git,
   four assignments out of date, and exactly the kind of leftover the checklist
   penalises. Now the repository's own name.

Two smaller things the audit raised were also closed: `MessageHtmlView` and
`WebValidationException` had no matching test class, which this team is graded
on, so both now have one. `MessageHtmlView` was refactored in this cycle, so
testing it is this member's responsibility rather than C's.

`WebEditingHandler` still has no matching test class. It is package-private,
reachable only through an `HttpExchange`, and fully covered by B's HTTP tests
and the SQLite editing tests; writing a mock harness for another member's class
seemed worse than naming the gap here for B.

The lesson worth keeping is the shape of defect 1. A header set by one member
silently disabled a feature delivered by another, and every test written from
the inside agreed the feature worked. The tests asserted the HTML contained a
stylesheet; none asked whether a browser would apply it.

## Validation

- `./gradlew clean build`: **652 tests, zero failures, zero skipped**, in 53
  seconds with 29.8 seconds of test time.
- Method coverage: **zero missed methods and zero missed classes** across 413
  methods and 57 classes, computed project-wide from
  `build/reports/jacoco/test/jacocoTestReport.xml` rather than taken from the
  build's verification task.
- A measurement worth recording so nobody chases it: one build of this suite
  took 52 minutes, with two integration tests apparently taking 35 and 10
  minutes. That was contention, not a regression. A second test run and a
  `-web` jar were running against the same build directory, SQLite files and
  port at the same time. Re-timed alone, those two tests take 0.2 and 2.2
  seconds. Do not run two `gradlew test` invocations against this project at
  once; the port 3002 test alone makes the suite unsafe to run in parallel.
- Diagrams and wireframes regenerated from the delivered code by
  `docs/update-hw7-member-a-design.cjs`, which also corrected the eleven
  sentences in the earlier blocks that described this work as pending.

### System test against the built JAR

Run from a directory containing only the JAR and the four seed files.

- `-h` and `--help` print identical help text, exit successfully, and create no
  `data` directory.
- An unrecognised argument still prints the usage line and exits as an error.
- First start creates the account, imports 195 countries, and the main menu
  offers `12) Countries`.
- Reading facility 1 shows `Country: 132. Norway` from the seed file.
- `6) Set facility country` reassigned facility 1 to France and the detail page
  showed it; country `9999` reported "That country does not exist." and changed
  nothing.
- Web mode: the log in page is styled and carries no navigation and no log out
  control. Every signed-in page checked - menu, both content lists, a facility
  record, a country record, the audit log, the new and edit forms, and both
  messaging pages - carried the stylesheet, the navigation block and the log out
  form, and contained no `<script`, `<img`, `javascript:` or `onclick`.
- Facility 1 displayed `country  62. France`; its edit form preselected
  `<option value="62" selected>`; changing the selection to Germany returned 303
  and the record then displayed Germany.
- Renaming that country to "Federal Republic of Germany" changed what facility 1
  displays, confirming the facility reads through the key and stores no copy.
- The audit log contains COUNTRY entries for the country changes.

Re-run against the jar after the audit fixes:

- The response carries `default-src 'none'; style-src 'unsafe-inline';
  form-action 'self'; frame-ancestors 'none'`, so the stylesheet is applied and
  scripts and images stay refused.
- A signed-in reader asking for a malformed record id, an unknown content type
  or an unknown page gets a page that still carries the log out form and no
  "Back to login" link. A reader with no session is still redirected to log in,
  and the log in page still carries no log out control.

## Notes for the team

**For D (audit and model).** `ContentService.list` records one READ audit entry
per returned row. That is existing behaviour for every type, but the country
list is 195 rows, so one visit to the countries page writes 195 audit entries.
A handful of page loads during the system test produced over thirteen hundred
COUNTRY rows. Nothing is wrong, and I have not changed shared audit semantics,
but the log is now noisy enough to be worth a decision that belongs to you:
either record one READ for a list operation, or keep per-row entries and accept
the volume.

**Final documentation cleanup (November 20).** The superseded Homework 6
handoff files and old controller diagram were removed from the submission tree;
their history remains in Git. The class diagram, ERD and wireframes now use one
current generator, `node docs/render-hw7-docs.cjs`. D's implementation retains
per-record READ audit events, including related-record reads and search hits.

**Two of the other members' tests were revised, minimally and for cause.**
`WebEditingSqliteTest` and `ContentMenuSqliteContentServiceTest` iterate
`ContentType.values()` and assume a record of every type exists, so each gained
one country. `WebEditingWorkflowDraftTest` used a facility as its example of an
ordinary type in the test for the generic add and update path; a facility now
carries a country, so that case uses a brief and still tests what it is named
for. No assertion was weakened.

## A related record is now followable

The assignment asks that related items be "visible and clickable for detail".
A facility's country was displayed but rendered as plain text, so it could be
read and not followed. `HtmlView.Field` now carries an optional destination and
the record page renders a link when one is present, so the country on a
facility leads to that country's own page. Fields with no destination are
unchanged, which is every other field on every other type.

This is only the part of that requirement that the country display owns. The
rest of it belongs to D and is listed below.

## Outstanding

Nothing from Member A's scope. The `countries.name` constraint and the audit
volume above are recorded for later, not left silently.

Still open for the team, all in D's scope, and each one a checklist line:

- **The new access control does not exist.** `InMemoryWebSessionStore` is a map
  of token to username with no timestamps, no expiry and no sweep, so a session
  lasts until logout or shutdown. The thirty-minute inactivity timeout, the
  terminal listing of current sessions with their tokens and last activity, and
  the ability to revoke a token are all unimplemented.
- **Related data is incomplete and mostly not clickable.** Viewing an Agent
  shows no missions, and no `missionsFor(agentId)` exists anywhere. Viewing a
  Facility shows its country but not the agents and missions assigned to it.
  Viewing a Country does not list its facilities. Where a mission's
  relationships are shown they are a comma-joined string from
  `HttpWebServer.titles`, not links; `HtmlView.Field` now supports a
  destination, so making them followable is a small change on top of this work.
- **Search covers one field and nothing can reach it.**
  `MissionSearchService` matches only `mission.getBrief()`. More importantly
  the shipped application cannot invoke it: `TopSecret` builds `UserInterface`
  with a `ContentService`, so `runMenu` delegates to `ContentMenu`, which has
  no search option, and there is no web route either.

Messaging is not in the audit log, which is correct: the assignment states it
is not required.
