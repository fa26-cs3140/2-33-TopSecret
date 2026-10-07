MEMBER C - SEARCH FEATURE

MissionSearchService searches only mission briefs. It ignores case, accepts a
word or phrase, trims surrounding spaces, and keeps the repository's result
order. A blank search produces a helpful error. No matches produce an empty
list.

ProgramControl exposes searchMissions(query) for the user interface. Unit tests
cover the search rules. The current integration test covers the repository
boundary, search service, and control layer together.

The final SQLite integration test must be added when Member A's database class
is ready.
