# Member C week-one check-in

Rohan owns the proposed notification and web audit work in the team plan.
Baseline at 6b1cf52 passed 731 tests and covered 436 of 436 methods.

## Review and refactoring priorities

1. Reuse the inbox's case-insensitive recipient match and persisted read time. On each signed-in page or terminal menu display, check for unread mail once. Separate processes can observe deliveries through the shared database.
2. When the terminal prints message bodies, mark only the displayed messages read. Mail arriving during display should still trigger the next notice.
3. The existing audit query orders by insertion ID. For the web filter, bind the exact username, select a fixed date direction, and break timestamp ties by ID in the same direction. Keep the existing administrator check and terminal query.
4. Week two will connect the queries to both interfaces, run the full coverage gate and exercise the packaged application with independent accounts.

## Work check-in

The UML, ERD and wireframe sources and PDFs include the proposed Member C changes. Both service contracts compile. Running `./gradlew --offline check` with the new contracts executed 735 tests. Five failed as expected:

- The unread lookup and filtered audit query reached their Week 1 stubs.
- The web navigation and terminal menu each omitted the proposed recipient notice.
- The admin audit page accepted an unsupported sort direction with HTTP 200. The contract expects HTTP 400.

The other 730 tests passed. Week two will implement these contracts, rerun the coverage gate and record a packaged system test. The baseline coverage result above applies to the starting revision.
