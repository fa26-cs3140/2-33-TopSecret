Top Secret

Top Secret is an authenticated application for browsing intelligence briefs,
agents, facilities and missions. Its terminal interface can create and update
supported records and send messages to agents. Operational data, audit events and
messages are stored in SQLite. Credentials and roles are stored separately in a
substitution-ciphered file. A separate HTML view renders read-only pages from
display data; the HTTP server is not connected to the executable yet.


RUNNING THE TERMINAL APPLICATION

From the project root:

  ./gradlew run

Or build and run the bundled JAR:

  ./gradlew jar
  java -jar build/libs/TopSecret.jar

On first launch, the program imports available sample data and prompts for an
initial administrator account. Later launches prompt for a username and password.
Administrators can create additional user accounts from the terminal menu.
New users choose a password at first login.

To change a password:

  java -jar build/libs/TopSecret.jar --change-password

The -web switch is not available in the current executable. The web controller
contracts and HTML renderer exist, but the server has not been wired into startup.


TERMINAL MENU

  1) Briefs
  2) Agents
  3) Facilities
  4) Exit
  5) Review audit log       administrator only
  6) Create user            administrator only
  7) Promote user           administrator only
  8) Demote user            administrator only
  9) Send a message         when messaging is connected
  10) My messages           when messaging is connected
  11) Missions

All signed-in users can list and read records by database ID. Administrators can
add and update briefs, agents and facilities, and update missions. Mission lists
include each mission's date. Mission creation is unavailable through the menu
until a new mission can be assigned its required facility in the same operation.
Record creation and editing take place in the terminal, not in the HTML view.


MESSAGES

Any logged-in user can choose 9, pick an agent and leave a message for that
agent. A user whose account name matches the agent's name, ignoring case, can
read those messages by choosing 10. No other user can see them, including an
administrator with a different account name. Messages appear only in the
terminal, not in the HTML view.


HTML VIEW

The view.HtmlView class renders a login form, navigation menu, record list,
record detail, audit log and error page as plain HTML. It receives display data
from its caller, escapes displayed text, and does not access the database or
check permissions. It contains no CSS or JavaScript, and record pages have no
editing controls. The controller must authenticate requests and choose which
links and records a user may see.

The proposed routes are / for login, /menu, /content for lists, /record for
details and /audit for the audit log. These are view and controller contracts;
they are not working HTTP endpoints until the server implementation is added.
The page layout is illustrated in docs/Homework6Wireframes.pdf.


DATA AND STORAGE

  mission_briefs.tsv       sample mission records and briefs
  facilities.tsv           sample facilities
  agents.tsv               sample agents
  data/missions.db         generated SQLite database; not committed
  data/credentials.cip     generated ciphered credentials; not committed
  ciphers/key.txt          default substitution-cipher key

Briefs are independent content records. Each agent belongs to one facility, and
an agent may serve on multiple missions. Seeded missions have a facility and at
least one agent. Missions may use agents from any facility. Seed files import
only into empty target tables. Messages are stored in the messages table in the
SQLite database. The database and credential paths can be overridden with the
topsecret.database and topsecret.credentials Java system properties for isolated
runs.


CODE ORGANIZATION

TopSecret assembles storage, authentication, messaging and terminal interaction.
ContentMenu handles terminal content prompts; MessageMenu handles message
screens. ContentService and SqliteContentService handle content operations and
access checks. Message, MessageService and SqliteMessageService provide message
storage and retrieval. The view package contains the HTML renderer. The
controller package currently contains web contracts and command-line mode
parsing, but no active HTTP server. Most older application classes still live
in Java's unnamed package; the planned MVC package migration is not complete.


TESTING

Run the complete unit and integration suite, method-coverage check and JAR build:

  ./gradlew clean check jar

Tests use temporary databases and credential files. The suite covers seed
imports, content relationships and access, authentication, audit events,
terminal menus and messaging. HTTP draft tests are currently disabled until a
server implementation exists; browser login, cookies and protected routes
cannot be verified yet.


DOCUMENTATION

  docs/interface-design.txt    terminal interaction and manual regression steps
  docs/content-model.txt       content models and relationship rules
  docs/database.txt            SQLite schema, imports and persistence behavior
  docs/useraccounts.txt        credential rules and account lifecycle
  docs/useraccess.txt          roles and authorization rules
  docs/auditlog.txt            audit events and recording behavior
  docs/Homework6Wireframes.pdf web page wireframes
  docs/SystemClasses.pdf       current class diagram
  docs/DatabaseERD.pdf         current database diagram
  docs/render-diagrams.cjs     generates the class and database diagrams

The diagrams and package locations need a final update after the MVC package
migration and web server integration.
