MEMBER D - USER INTERFACE

Status: complete. TopSecret.main is wired to B's login and A's SQLite
repository, and starts the menu with AnimatedEffects.

What was built
- UserInterface.runMenu(Scanner, PrintStream): interactive menu that runs until
  the user chooses Exit.
    1) List missions   2) Read a mission   3) Search   4) Exit
- Search calls ProgramControl.searchMissions(query) and shows a numbered list of
  matching titles, or "No matches found."
- Listing and reading use the new ProgramControl.listMissions().
- Errors from any layer print as "Error: <message>"; the menu never closes.
- Effects interface (Effects.NONE by default, AnimatedEffects for real runs)
  adds a banner and loading animation without affecting tests.

Tests
- UserInterfaceTest: menu behavior and effects, using FakeProgramControl.
- MenuIntegrationTest: menu + ProgramControlImpl + MissionSearchService +
  repository together.
- ProgramControlTest: listMissions cases.

Full usage details: docs/userinterface.txt