Top Secret - Team 2-33

Top Secret is an interactive command-line program for viewing and searching
mission briefs. Users must log in before accessing the mission menu. Mission
records are stored as plain text in SQLite, while credentials are kept in a
separate ciphered file.


RUNNING THE PROGRAM

From the project root:

  ./gradlew run

Or build and run the bundled jar:

  ./gradlew build
  java -jar build/libs/TopSecret.jar

On the first run, the program creates and imports the mission database. If no
credential file exists, it asks for a new username and password and then exits.
Run the program again to log in.

Usernames may contain only lower-case letters. Passwords must contain at least
five characters.


CHANGING THE PASSWORD

Use either command:

  ./gradlew run --args="--change-password"
  java -jar build/libs/TopSecret.jar --change-password

The program verifies the current username and password, asks for the new
password twice, and then updates the ciphered credential file.


INTERACTIVE MENU

After a successful login, the program continues showing this menu until Exit is
selected:

  1) List missions
  2) Read a mission
  3) Search
  4) Exit

List shows numbered mission titles in database order. Read displays the chosen
mission's title, date, and brief. Search accepts a word or phrase, searches only
mission briefs, ignores case, and displays matching titles. If nothing matches,
the program displays "No matches found." Invalid input or a recoverable error
returns the user to the menu instead of closing the program.


DATA AND CREDENTIALS

  mission_briefs.tsv       100 sample mission records imported on first run
  data/missions.db         generated SQLite database; not committed
  data/credentials.cip     generated ciphered credentials; not committed
  ciphers/key.txt          default substitution-cipher key

The mission table contains an integer id, title, brief, and ISO-formatted date.
Database setup is safe to run repeatedly: the TSV is imported only when the
mission table is empty. Passwords and cipher data are never stored in SQLite.


PROJECT STRUCTURE

  TopSecret
    Starts the application, initializes SQLite, handles credential setup and
    login, and connects the program components.

  UserInterface / Effects
    Runs the persistent terminal menu and displays mission results and optional
    visual effects.

  ProgramControl / ProgramControlImpl
    Connects the user interface to mission listing and search operations.

  Mission / MissionRepository / SqliteMissionRepository
    Represents mission records and owns SQLite table creation, TSV importing,
    connections, and reads.

  MissionSearch / MissionSearchService
    Performs case-insensitive word and phrase searches against mission briefs.

  AuthenticationService / CredentialStore / FileCredentialStore
    Validates users, performs login and password changes, and manages the
    ciphered credential file.

  FileHandler / Cipher
    Provide safe project-file access and substitution-cipher operations.

Interfaces separate the UI, search, database, credentials, file access, and
cipher so each part can be tested independently.


TESTING

Run the complete JUnit 5 unit and integration suite with:

  ./gradlew test

The tests include temporary SQLite databases, the real TSV import, search,
authentication, credential storage, the interactive menu, control-layer
wiring, file handling, and the original cipher behavior. Test databases and
credential files use temporary locations and do not alter normal user data.


MORE DOCUMENTATION

  docs/database.txt       SQLite schema, importing, connections, and tests
  docs/userinterface.txt  login, password changes, and menu behavior
  docs/changenotes.txt    changes from Homework 3 to Homework 4
  docs/TopSecretUML.*     class and responsibility diagram
