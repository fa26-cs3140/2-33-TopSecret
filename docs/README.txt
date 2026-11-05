Top Secret

Top Secret is an authenticated terminal and read-only web application for browsing and
managing intelligence briefs, agents, facilities and mission assignments, and for
leaving messages for agents. Operational data, the audit log and messages are
stored in SQLite. Account credentials and roles are stored separately in a
substitution-ciphered file.


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

WEB INTERFACE

Create an account in the terminal first, then run:

  ./gradlew run --args="-web"
  java -jar build/libs/TopSecret.jar -web

Open http://localhost:3002/. The server binds to localhost and refuses to start
without existing valid accounts. No argument preserves terminal mode, and
--change-password preserves the terminal password-change workflow. Unknown or
combined arguments print usage and do not start either interface.

The login form issues a session cookie after valid authentication. It contains
an opaque random token, HttpOnly, Path=/ and SameSite=Lax, with no Expires or
Max-Age. Browser session restoration is controlled by the browser. Logging out
or restarting the server invalidates server-side sessions. Stop with Ctrl+C.
Passwords for newly created accounts must first be set from the terminal.

The web menu lists briefs, agents, facilities and missions. Mission details also
show their facility, assigned agents and attached briefs. Records and the
administrator audit log are read-only. All application routes require a login;
invalid credentials show an error. Only login/logout accept POST. Role checks
use the current account on every request. HTML uses plain forms, links, lists
and tables, with no CSS, JavaScript or frameworks. The controller passes display
data to HtmlView, which escapes dynamic text. Messages and account management
remain terminal workflows.

Run release verification from the project root (JDK 21):

  ./gradlew clean check jar
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-jar.ps1

The smoke script uses isolated generated files under build/, verifies both JAR
modes and private agent messaging, and shuts down its server. Keep port 3002 free.
On Windows, use gradlew.bat in place of ./gradlew. If Gradle's user-home is set
incorrectly by the environment, set GRADLE_USER_HOME to your own .gradle folder.

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
  9) Send a message
  10) My messages
  11) Missions

Administrators also see 5) Review audit log, 6) Create user, 7) Promote user and
8) Demote user.

Each content type supports listing, reading by stable database ID and returning
to the main menu. Administrators can add and update briefs, agents, facilities
and missions. Creating a mission asks for an existing facility and first agent;
all three records are saved together. Mission lists include each mission's date.
Administrators can also review the audit log,
create accounts, and promote or demote users. Recoverable errors return to the
menu without ending the session.


MESSAGES

Any logged-in user can choose 9, pick an agent and leave a message for it. To read
it, someone creates an account whose username matches the agent's name, ignoring
case: an account named Ghost, ghost or GHOST reads the messages sent to the agent
Ghost by choosing 10. No other user can see them, and administrators get no
special access. Messages are shown in the terminal only; the web view does not
display them. Test data is loaded on first start, so the agents already exist.


DATA AND CREDENTIALS

  mission_briefs.tsv       sample mission records and briefs
  facilities.tsv           sample facilities
  agents.tsv               sample agents
  data/missions.db         generated SQLite database; not committed
  data/credentials.cip     generated ciphered credentials; not committed
  ciphers/key.txt          default substitution-cipher key

Briefs may be attached to missions. Agents belong to one facility and can serve
on missions run by any facility. Every seeded mission has a facility and at
least one agent. Initialization is
idempotent: seed files import only into empty target tables. Missing optional seed
files leave their target tables empty. Messages are stored in the messages table
of data/missions.db. Passwords and cipher data never enter the SQLite database.


ARCHITECTURE

  model/ contains domain objects, persistence, authentication and access rules.
  controller/ contains launch parsing, ProgramControl, ContentController and HTTP
  authentication/session/server coordination. view/ contains terminal menus,
  effects, ContentText and HtmlView. TopSecret remains the JAR composition root.
  ContentMenu and HttpWebServer share ContentController read/list/audit operations.
  The controller supplies display data to HtmlView; the view does not access
  storage or decide permissions. ContentText formats terminal text and list titles.
  Existing model methods retain access and audit rules.

  SystemClasses.pdf documents the model/terminal and HTTP architecture in two
  pages. DatabaseERD.pdf reflects the persistence schema. WebWireframes.pdf
  shows login, navigation, lists/detail/audit, and errors in four pages.
  Homework6Wireframes.pdf records six detailed HTML page layouts. Regenerate
  all three with node docs/render-web-docs.cjs using Chrome or Edge.
  Render actual PDF pages for review with scripts/render-pdf-preview.ps1.

  TopSecret
    Initializes dependencies, database storage, credential setup and login.

  UserInterface / ContentMenu / Effects
    Runs the authenticated terminal experience and isolates optional visual
    effects from interaction logic.

  ContentService / SqliteContentService
    Lists, reads and mutates content with role checks; imports sample data;
    maintains mission relationships; records successful reads, creates, and updates.

  Brief / Agent / Facility
    Immutable domain models for content records.

  Mission / MissionRepository / SqliteMissionRepository
    Represents and persists the searchable mission archive.

  MissionSearch / MissionSearchService
    Performs case-insensitive word and phrase searches over mission briefs.

  AuthenticationService / CredentialStore / FileCredentialStore
    Validates users, manages roles and password changes, and persists the
    ciphered credential file.

  AuditLogRepository / SqliteAuditLogRepository
    Records and retrieves immutable content-access events.

  Message / MessageService / SqliteMessageService / MessageMenu
    Message is the immutable domain model. MessageService lists recipients,
    sends a message and returns the logged-in user's inbox; SqliteMessageService
    stores it in the messages table; MessageMenu draws the two terminal screens.

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
authentication, roles, audit behavior, the interactive menu, messaging, file
handling and cipher behavior. The JaCoCo gate enforces 100% method coverage across
all production classes.


DOCUMENTATION

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
  docs/WebWireframes.pdf     login, navigation, content/audit and error wireframes
  docs/Homework6Wireframes.pdf  detailed HTML page wireframes
  docs/changenotes.txt       current release snapshot
