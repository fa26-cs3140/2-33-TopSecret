Top Secret

HOMEWORK 8 - MEMBER B

Work allocation and review backlog: homework8-plan.md.
Week-one delivery and verification: homework8-member-b-week1.md.
Current diagrams: SystemClasses.pdf, DatabaseERD.pdf, WebWireframes.pdf.
Regenerate their HTML and PDF together with node docs/render-hw7-docs.cjs;
the script retains its existing name but generates the current diagrams.

Mission detail offers Add brief to administrators. The shared creation form
preselects that mission; a successful save returns to the selected mission.
Validation errors retain the mission and entered values. A blank mission
selection still creates an unlinked brief. Save and link share one transaction
with the CREATE audit entry, so a failed save cannot leave an orphan brief.
All signed-in readers can Download briefs (JSON) from a mission. The UTF-8
attachment contains an array of {id, mission_id, title, date, text}, ordered by
brief ID and restricted to that mission. No attached briefs returns []. Each
download uses the same audited reads as the record pages. Missing missions
return 404; missing/expired sessions return to login.

The remaining Homework 8 blocks are planned, not implemented by this delivery.

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
Max-Age. Browser session restoration is controlled by the browser. Sessions are
stored in the database and expire after 30 minutes without a request. Logging out,
or an administrator revoking the session from the terminal, also ends it, and
stopping the server clears every session. Stop with Ctrl+C.
Passwords for newly created accounts must first be set from the terminal.

Every signed-in page carries the same navigation block above its heading: the
content types, messaging, the audit log for permitted users, the name of the
signed-in user, and a Log out button. Log out is a submitted form rather than a
link, so no prefetch or crafted image request can end a session. The log in and
error pages carry the styling but no navigation, because there is no session to
act in; an error page shown to a signed-in user keeps the menu so a refused
request does not strand them.

The web menu lists briefs, agents, facilities, missions, countries, Search and
messaging. Record pages link to related records: an agent to its facility and
missions, a facility to its agents and missions, a country to its facilities and a
brief to its mission. The Search page looks through every text field of every
record type. Mission details show their facility, assigned agents and attached
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
management remains in the terminal. A facility's country is optional: Unassigned
on a new form creates a facility without a country. Edit forms preselect the
stored country; choosing Keep current country preserves the assignment while
saving other fields. Choosing another country reassigns the facility. Agent and
new-mission relationship dropdowns still require a selection.
Dates use YYYY-MM-DD, death cannot precede
birth, and death date/notes are optional. Long text uses multiline textareas.
Save uses POST with a session-specific CSRF token, then redirects to the list or
updated detail. Cancel performs no write. Validation failures retain escaped
submitted values; missing records return 404 and unexpected storage errors 500.
Login, editing and messaging form bodies are limited to 8192 bytes. Content
create/update requests require the current admin role, even if a previously
loaded form belonged to an administrator.

Country forms, the facility-country choice and the shared CSS and navigation are
delivered. So are the 30-minute session timeout, terminal session administration,
related-record links, search over every text field, and audited deletes.

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
  13) Search all records

Administrators also see 5) Review audit log, 6) Create user, 7) Promote user,
8) Demote user and 14) Manage web sessions, which lists the web server's active
sessions with username, full issued token and activity times, and revokes one by
its full token. Reading an agent, facility,
country or brief also shows the records connected to it.

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
  WebFormData shares bounded UTF-8 form parsing across login, editing and messaging.
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
    maintains mission relationships; records successful reads, creates, updates and deletes; related
    lookups and search.

  ContentEntry / ContentType / ContentHit
    Immutable ordered record values, the content-type registry and typed search hits.

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

  SessionAdmin / SqliteWebSessionStore / SessionMenu
    The web session store keeps sessions in the web_sessions table with a 30-minute
    sliding timeout. SessionAdmin lets an administrator list and revoke them,
    auditing each revocation; SessionMenu draws the terminal screen.

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
  docs/homework7-member-c-plan.md    messaging design and completed integration
  docs/homework7-member-c-system-test.md  browser evidence and personal test checklist
  docs/homework7-member-d-plan.md    audit, related records, search and sessions
  docs/homework7-member-a-week2.md   styling, countries and help delivery record
  docs/homework7-member-b-week2.md   web editing delivery record
  docs/SystemClasses.pdf     integrated classes, interfaces and dependencies
  docs/DatabaseERD.pdf       all content, message, audit and web-session tables
  docs/WebWireframes.pdf     current login, content, editing and messaging flows

Regenerate the HTML and PDF diagrams with:

  node docs/render-hw7-docs.cjs

Chrome, Edge or Chromium is required. This single generator replaces the older
incremental diagram scripts. Current source files determine the class index.
Render the PDF pages for review with scripts/render-pdf-preview.ps1.
