# Homework 8 - Member B - Week 1

Owner: Mateo. Date: November 23, 2026. Selected block: c and f (six effort
points, one quarter of the allocation in `homework8-plan.md`). This delivery
completes the block's implementation as requested, in addition to week-one
planning, tests and compiling stubs. Other members' features remain planned.

## First-week deliverables

- Code review and prioritized backlog: `homework8-plan.md`, B1-B4 plus A/C/D
  findings. This is a repository review, not an asserted team meeting.
- Refactoring: transactional `addBrief` replaces a prospective two-write save;
  existing form rendering, date validation, CSRF and audited lookups are reused;
  JSON string encoding is isolated in `MissionBriefJson`.
- Class diagram, ERD and wireframes updated in place, both HTML and PDF, using
  the canonical `render-hw7-docs.cjs` generator. Current diagrams describe only
  implemented runtime behavior; no old-version diagram copies were added.
- Tests and stubs committed and pushed before implementation: three model
  tests failed at `addBrief`'s compiling stub; three form contracts failed
  before integration; four export/encoding contracts failed at the serializer
  stub or missing route. Final implementations replace all stubs.
- README, team allocation, development plan, database, content model and
  interface contracts updated for current behavior.

## Completed behavior

Feature c: administrators open Add brief on a mission, receive a preselected
mission in the shared form, and save a brief already linked to that mission.
Validation errors retain entered values and selection; save and cancel return
to the selected mission. Blank selection supports ordinary unlinked briefs.
The brief, mission FK and single CREATE audit entry commit atomically; missing
missions and audit failures cannot leave orphan records. Current role and
session CSRF are checked before saving.

Feature f: signed-in users can download an attached-brief array as
`mission-N-briefs.json`. Each item contains `id`, `mission_id`, `title`, `date`,
and `text`. Export uses UTF-8, correct JSON escaping, ascending brief ID,
`no-store` and the existing audited reads. Unlinked and other-mission briefs
are excluded. Existing missions without briefs return `[]`; missing missions
return 404; malformed/extra parameters return 400; no session redirects to login.

## Verification and work check-in

- `gradlew.bat clean check build`: PASS. 731 tests, zero failures/errors/skips;
  436/436 methods covered, unchanged 100% per-class method gate.
- Focused model, real HTTP/SQLite workflow and JSON unit tests: PASS.
- All three PDFs regenerated and rendered to PNG with the repository Windows
  PDF preview tool; changed class, ERD and wireframe pages visually inspected.
- Executable JAR system test: pending the next verification task.

Week-one check-in: B1-B4 complete. Remaining work is the executable system-test
record, review by the team and the A/C/D blocks. Every teammate must run and
record their own system test before the deadline; this report supplies no
other member's signoff. A/D must coordinate assignment status and history.

Commit history records small separate plan, test/stub and implementation tasks;
each is pushed to origin/main before starting the next task.
