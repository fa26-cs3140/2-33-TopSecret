Top Secret README for Team 2-33

This program follows the Homework 3 Top Secret requirements. It lists encrypted
mission files and displays deciphered file contents from the command line.

Run the program with one of these commands:

- ./gradlew run
- java TopSecret from build/classes/java/main
- java -jar TopSecret.jar from build/libs

Run all tests with ./gradlew test.

Mission files are stored in data/. Cipher keys are stored in ciphers/.
Command-line options are documented in docs/userinterface.txt.

Project files:

- TopSecret - Starts the program.
- UserInterface - Validates command-line input and displays output.
- ProgramControl - Connects the user interface, file handler, and cipher.
- FileHandler - Safely lists and reads mission and key files.
- Cipher - Validates cipher keys and deciphers .cip file contents.

The project builds with Gradle and uses JUnit 5 for testing.

Homework 4 search work:

- Mission represents one database mission record.
- MissionRepository is the boundary Member A's SQLite class will implement.
- MissionSearchService performs Member C's case-insensitive brief search.
- ProgramControl.searchMissions makes search available to Member D's menu.
- Short handoff notes for each member are in docs/member-*-readme.txt.

The SQLite repository and interactive menu are not connected yet. The existing
Homework 3 file-based behavior remains available while those parts are built.
