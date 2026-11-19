Top Secret

Top Secret is an authenticated terminal and web application for intelligence
briefs, agents, facilities, missions and agent messages. The terminal lets
administrators add and edit content records. The web interface lets signed-in
users browse records and exchange messages; administrators can create and edit
content records in the browser. Operational data, the audit log and messages are stored in
SQLite. Account credentials and roles are stored separately in a
substitution-ciphered file.


RUNNING THE APPLICATION

From the project root:

  ./gradlew run

Or build and run the bundled jar:

  ./gradlew build
  java -jar build/libs/TopSecret.jar

For a summary of the launch options:

  java -jar build/libs/TopSecret.jar -h

-h and --help print the help text and exit without touching the database or the
credential file. Run the jar from the project root so the seed files beside it
are found.

On the first run, the application initializes its database and imports available
country, mission, facility and agent seed data. If no credential file exists, it prompts
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

Every signed-in page carries the same navigation block above its heading: the
content types, messaging, the audit log for permitted users, the name of the
signed-in user, and a Log out button. Log out is a submitted form rather than a
link, so no prefetch or crafted image request can end a session. The log in and
error pages carry the styling but no navigation, because there is no session to
act in; an error page shown to a signed-in user keeps the menu so a refused
request does not strand them.

The web menu lists briefs, agents, facilities, missions, countries and
messaging. Mission details show their facility, assigned agents and attached
briefs. Facility details show the country the facility is in, by name. A
facility with no country reads as Unassigned. Content records
have admin-only New record links on lists and Edit record links on detail pages.
The administrator audit log is read-only. Content, messaging
and audit pages require a login; invalid credentials show an error. Login, logout,
message sending and message opening use POST. Role checks use the current account
on every request. Pages use forms, links, lists and tables, styled by one
stylesheet held in the shared page shell. There is no separate stylesheet route:
the log in page is styled too, so serving it would have to be reachable before
authentication for a file of a few hundred bytes. There is no JavaScript and
there are no image assets. HtmlView owns the page shell and the escaping;
MessageHtmlView renders through it, so the messaging pages carry the same
styling and the same navigation rather than a shell of their own. Web messaging has a private inbox, read indicators, an
agent dropdown and a compose form. Account management remains a terminal
workflow.

New forms start empty; edit forms load current values. Agent forms offer facility
choices; mission creation requires an existing facility and first agent and saves
them atomically. Mission updates edit title, brief and date; mission relationship
management remains in the terminal. Dates use YYYY-MM-DD, death cannot precede
birth, and death date/notes are optional. Long text uses multiline textareas.
Save uses POST with a session-specific CSRF token, then redirects to the list or
updated detail. Cancel performs no write. Validation failures retain escaped
submitted values; missing records return 404 and unexpected storage errors 500.
Login/editing bodies are limited to 8192 bytes. Direct requests require the current
admin role, even if a previously loaded form belonged to an administrator.

Country forms and facility-country choices await A/D's model contract. Shared
CSS/navigation awaits A. Session expiry and terminal session administration await
D; sessions currently last until logout or server shutdown. Related-record links
and broader search also remain D's work. These are remaining team deliverables.

Run release verification from the project root (JDK 21):

  ./gradlew clean check jar
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-jar.ps1

The smoke script uses isolated generated files under build/, verifies both JAR
modes, private agent messaging, web creation/update for every current content type,
audit events, invalid-input redisplay, CSRF and reader denial, and shuts down its
server. Keep port 3002 free.
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
  12) Countries

Administrators also see 5) Review audit log, 6) Create user, 7) Promote user and
8) Demote user.

Each content type supports listing, reading by stable database ID and returning
to the main menu. Administrators can add and update briefs, agents, facilities,
missions and countries. Reading a facility shows the country it is in, and the
facilities menu offers 6) Set facility country. Creating a mission asks for an existing facility and first agent;
all three records are saved together. Mission lists include each mission's date.
Administrators can also review the audit log,
create accounts, and promote or demote users. Recoverable errors return to the
menu without ending the session.


MESSAGES

Any logged-in terminal user can choose 9, pick an agent and leave a message for it. To read
it, someone creates an account whose username matches the agent's name, ignoring
case: an account named Ghost, ghost or GHOST reads the messages sent to the agent
Ghost by choosing 10. No other user can see them, and administrators get no
special access. In the web view, Messaging is available to every signed-in user.
The inbox lists messages for the agent whose name matches that username, with a
read indicator. Open marks an owned message read and shows its detail. Compose
offers an agent dropdown. Read state persists in SQLite. Test data is loaded on
first start, so the agents already exist.


DATA AND CREDENTIALS

  countries.txt            the supplied country list, one name per line
  mission_briefs.tsv       sample mission records and briefs
  facilities.tsv           sample facilities, each naming its country
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
  ContentController implements WebEditingWorkflow for authorized, validated writes.
  WebEditingHandler coordinates editing requests, relationship choices and CSRF;
  WebFormData shares bounded UTF-8 form parsing between login and editing.
  The controller supplies display data to HtmlView; the view does not access
  storage or decide permissions. ContentText formats terminal text and list titles.
  Existing model methods retain access and audit rules.

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
    stores it in the messages table and saves first-read time; MessageMenu draws
    the two terminal screens. WebMessageHandler and MessageHtmlView provide the
    browser messaging flow.

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
  docs/changenotes.txt       current release snapshot
  docs/homework7-member-c-plan.md    messaging design and team handoff
  docs/homework7-member-b-week2.md   editing delivery, tests and remaining dependencies
  docs/SystemClasses.pdf     architecture and implemented editing workflow
  docs/DatabaseERD.pdf       existing database plus proposed country design
  docs/WebWireframes.pdf     current pages and implemented editing wireframes

The PDFs are design documents; they do not yet show every implemented messaging
detail. Regenerate the existing diagrams with node docs/render-web-docs.cjs,
then apply the editing supplements with node docs/update-hw7-design.cjs. Both
scripts require Chrome or Edge. Render the PDF pages for review with
scripts/render-pdf-preview.ps1.
