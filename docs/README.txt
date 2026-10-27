Top Secret

Current system documentation as of October 23, 2026.

Top Secret is an authenticated command-line application for browsing and
managing intelligence briefs, agents, facilities and mission assignments.
Operational data and the audit log are stored in SQLite. Account credentials and
roles are stored separately in a substitution-ciphered file.


RUNNING THE APPLICATION

From the project root:

  ./gradlew run

Or build and run the bundled jar:

  ./gradlew build
  java -jar build/libs/TopSecret.jar

On the first run, the application initializes its database and imports available
mission, facility and agent seed data. If no credential file exists, it prompts
for the initial username and password and logs that user in as an administrator.
Administrators create later accounts from the authenticated menu.

Usernames require at least three letters, numbers, underscores or hyphens.
Passwords require at least five characters with both uppercase and lowercase
letters.


CHANGING A PASSWORD

Use either command:

  ./gradlew run --args="--change-password"
  java -jar build/libs/TopSecret.jar --change-password

The application verifies the username and current password, confirms the new
password twice, and updates only that account in the ciphered credential file.


AUTHENTICATED MENU

After login, the application presents:

  1) Briefs
  2) Agents
  3) Facilities
  4) Exit

Each content type supports listing, reading by stable database ID and returning
to the main menu. Administrators can also add and update entries, review the audit
log, create accounts, and promote or demote users. Recoverable input and storage errors
return to the menu without ending the session. See interface-design.txt for the
complete interaction contract and manual regression checklist.


DATA AND CREDENTIALS

  mission_briefs.tsv       sample mission records and briefs
  facilities.tsv           sample facilities
  agents.tsv               sample agents
  data/missions.db         generated SQLite database; not committed
  data/credentials.cip     generated ciphered credentials; not committed
  ciphers/key.txt          default substitution-cipher key

Briefs are independent content records. Agents belong to one facility, and every
seeded mission is assigned one facility and at least one agent. Initialization is
idempotent: seed files import only into empty target tables. Missing optional seed
files leave their target tables empty. Passwords and cipher data never enter the
SQLite database.


ARCHITECTURE

  TopSecret
    Initializes dependencies, database storage, credential setup and login.

  UserInterface / ContentMenu / Effects
    Runs the authenticated terminal experience and isolates optional visual
    effects from interaction logic.

  ContentService / SqliteContentService
    Lists, reads and mutates content with role checks; imports sample data;
    maintains mission relationships; records successful reads, creates, and updates.

  Brief / Agent / Facility / MissionAssignment
    Immutable domain models for content and mission relationships.

  Mission / MissionRepository / SqliteMissionRepository
    Represents and persists the searchable mission archive.

  MissionSearch / MissionSearchService
    Performs case-insensitive word and phrase searches over mission briefs.

  AuthenticationService / CredentialStore / FileCredentialStore
    Validates users, manages roles and password changes, and persists the
    ciphered credential file.

  AuditLogRepository / SqliteAuditLogRepository
    Records and retrieves immutable content-access events.

  FileHandler / Cipher
    Provide safe project-file access and substitution-cipher operations.

Interfaces isolate the user interface, database, credentials, audit, file and
cipher boundaries so components can be tested independently.


TESTING

Run the complete JUnit 5 unit and integration suite with:

  ./gradlew test

Run compilation, tests, coverage verification and jar assembly with:

  ./gradlew clean check jar

The suite uses temporary databases and credentials. It covers real seed imports,
relationship constraints, rollback and restart safety, content access, search,
authentication, roles, audit behavior, the interactive menu, file handling and
cipher behavior. The JaCoCo gate enforces 100% method coverage across all
production classes.


DOCUMENTATION

  docs/homework6-member-b-week1.txt  HW6 controller plan, contracts and handoffs
  docs/homework6-controller-classes.puml  new controller API diagram source

Homework 6 Member B week 1 adds controller scaffolding and draft tests only.
The -web startup path and HTTP server are planned for week 2 and are not yet
available through TopSecret. See the member-specific plan for remaining work.

  docs/interface-design.txt  interaction contract and regression checklist
  docs/development-plan.txt  delivery phases, ownership and test responsibilities
  docs/content-model.txt     domain models, import policy and integrity rules
  docs/database.txt          SQLite schema, transactions and persistence tests
  docs/userinterface.txt     command-line and authenticated menu behavior
  docs/useraccounts.txt      credential rules and lifecycle
  docs/useraccess.txt        roles and authorization rules
  docs/auditlog.txt          event schema and recording behavior
  docs/SystemClasses.pdf     current system class diagram
  docs/DatabaseERD.pdf       current database entity-relationship diagram
  docs/changenotes.txt       current release snapshot
