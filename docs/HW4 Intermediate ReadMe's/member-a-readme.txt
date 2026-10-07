MEMBER A - DATABASE HANDOFF

The search feature does not connect to SQLite directly. It asks a
MissionRepository for the missions instead.

Please make your database class implement MissionRepository and provide:

  List<Mission> getAllMissions()

Return missions in the same order that the normal mission list should use.
Return an empty list when the database has no missions; do not return null.

Mission uses an int id, String title, String brief, and LocalDate date. The TSV
columns map as Title -> title, Text -> brief, and Date -> date.

Once this is ready, TopSecret must create your repository and pass a
MissionSearchService into ProgramControlImpl. We should then add an integration
test that searches the real temporary SQLite database.
