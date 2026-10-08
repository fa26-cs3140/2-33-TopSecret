Top Secret README for Team 2-33

This program follows the Homework 4 Top Secret requirements in progress. It
requires login before showing the interactive mission menu.

Run the program with one of these commands:

- ./gradlew run
- java TopSecret from build/classes/java/main
- java -jar TopSecret.jar from build/libs

On first run, if data/credentials.cip is missing, the program asks for a new
username and password, writes the credential file, and exits. Run it again to
log in.

Password changes use:

- ./gradlew run --args="--change-password"
- java -jar TopSecret.jar --change-password

Run all tests with ./gradlew test.

Mission files are stored in data/. Cipher keys are stored in ciphers/.
Credentials are stored separately in data/credentials.cip using the same
substitution cipher key as the previous homework. Passwords are not stored in
the database. Command-line options are documented in docs/userinterface.txt.

Project files:

- TopSecret - Starts the program.
- UserInterface - Validates command-line input and displays output.
- ProgramControl - Connects the user interface, file handler, and cipher.
- FileHandler - Safely lists and reads mission and key files.
- Cipher - Validates cipher keys and deciphers .cip file contents.
- AuthenticationService - Validates credentials, checks login, and changes passwords.
- CredentialStore / FileCredentialStore - Owns credential file read/write.

The project builds with Gradle and uses JUnit 5 for testing.

Homework 4 search work:

- Mission represents one database mission record.
- MissionRepository is the boundary Member A's SQLite class will implement.
- MissionSearchService performs Member C's case-insensitive brief search.
- ProgramControl.searchMissions makes search available to Member D's menu.
- Short handoff notes for each member are in docs/member-*-readme.txt.

Homework 4 login work:

- Username must contain only lower-case letters.
- Password must be at least five characters long.
- If data/credentials.cip is missing, setup creates it and exits.
- If data/credentials.cip exists, the stored credential is validated before
  login is allowed.
- Login must succeed before UserInterface.runMenu starts.
- --change-password verifies the current credential, requires the new password
  twice, and overwrites data/credentials.cip.
