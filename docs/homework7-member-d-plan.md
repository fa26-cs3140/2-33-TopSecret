Homework 7 — Member D (Model): records, audit, search, sessions
Scope

Everything the Model owns that Homework 7 still needed after Members A, B and C: record deletion and the audit log, related records, search, and web session administration.

Decisions
Sessions are stored in SQLite (web_sessions). The web server (-web) and the terminal are separate programs. An administrator in the terminal can only list and revoke the web server's sessions if they live in the shared database file, not in the web server's memory.
Sliding 30-minute timeout. SqliteWebSessionStore.usernameFor is called on every request. A session is valid while no more than 30 minutes have passed since its last request, and each request moves that moment forward. A session idle for exactly 30 minutes is still valid.
Administrators never see a token. The list shows the first 8 characters of the token as an id. That is enough to choose a session, and useless for taking one over.
Audit. DELETE is a new event type. The delete and its audit row commit in one transaction. Revoking a web session is audited as DELETE of a SESSION (content id 0, because a session has no numeric id). Listing sessions and timeouts are not audited: listing shows no record content, and a timeout is the system, not a person.
Audit volume. READ is still audited per record returned, including every related-record lookup and every search hit. That keeps one rule ("every record a person sees is a row") at the cost of a longer log. Aggregating is a later option, not needed for this homework.
Search covers every text column of every record type (dates included) and skips *_id columns. It is case-insensitive, % and _ are ordinary characters, and it needs a session.
The old mission search is unchanged. Its tests deliberately say titles are not searched; the new all-field search is ContentService.search.
Test-driven order

Tests and compiling stubs first (red), then implementation (green), then the refactor.

Coordination
Member B's CSRF token is tied to the session token, so it keeps working unchanged with the SQLite store.
Member C's messaging routes use the same login gate in route(), so they get the timeout too.