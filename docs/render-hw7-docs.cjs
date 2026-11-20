/* Canonical Homework 7 diagrams. Run from any directory with Node and Chrome/Edge installed. */
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'build/diagrams/final');
const browser = [
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/chromium', '/usr/bin/chromium-browser',
].find(fs.existsSync);
if (!browser) throw new Error('Install Chrome, Edge or Chromium to render the PDFs.');
fs.mkdirSync(out, { recursive: true });
const escape = s => String(s).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
function box(x, y, w, title, lines) {
    const h = 56 + lines.length * 25;
    return `<g><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="6" fill="#f4f7fb" stroke="#25476a"/>
    <text x="${x + 14}" y="${y + 29}" class="box-title">${escape(title)}</text>
    ${lines.map((s, i) => `<text x="${x + 14}" y="${y + 63 + i * 25}">${escape(s)}</text>`).join('')}</g>`;
}
function arrow(d, label, x, y, realization = false) {
    return `<path d="${d}" fill="none" stroke="#25476a" ${realization ? 'stroke-dasharray="6 4"' : ''} marker-end="url(#arrow)"/>
    ${label ? `<text x="${x}" y="${y}" class="label">${escape(label)}</text>` : ''}`;
}
function diagram(title, subtitle, content, footer) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 880" role="img" aria-label="${escape(title)}">
    <defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0 0L9 5L0 10" fill="white" stroke="#25476a"/></marker></defs>
    <text x="30" y="40" class="title">${escape(title)}</text><text x="30" y="73">${escape(subtitle)}</text>
    ${content}<text x="30" y="850" class="label">${escape(footer)}</text></svg>`;
}
const classes = [
    diagram('System classes - application and content', 'Homework 7 | Current implementation | November 20, 2026',
        box(30, 115, 540, 'TopSecret / LaunchMode', [
            'main: initialize storage, authentication and services',
            '-web -> HttpWebServer; terminal -> UserInterface',
            '-h / --help -> usage without creating storage',
        ]) + box(650, 115, 540, 'UserInterface / ContentMenu', [
            'Uses ContentController for reads, links and search',
            'ContentService: terminal content edits and relations',
            'MessageMenu / SessionMenu: optional menu features',
        ]) + arrow('M570 185H650', 'constructs', 575, 170) +
        box(30, 365, 540, 'ContentController : WebEditingWorkflow', [
            '+ sessionFor / list / read / audit / search',
            '+ newForm / editForm / create / update',
            'Authorizes edits; validates fields and relationships',
            'Builds WebEditForm for the view',
        ]) + box(650, 365, 540, 'ContentService <<interface>>', [
            '+ list / read / add / update / delete / audit',
            '+ addMission / setFacility / assignAgent / attachBrief',
            '+ countryFor / setCountry; related lookups / search',
            'Returns ContentEntry, ContentHit and ContentType',
        ]) + arrow('M570 450H650', 'uses', 591, 435) +
        box(30, 665, 540, 'SqliteContentService : ContentService', [
            'Imports countries, facilities, agents and briefs',
            'Enforces AccessControl and relationship integrity',
            'Content mutation + audit write in one transaction',
        ]) + box(650, 665, 540, 'AuditLogRepository / SqliteAuditLogRepository', [
            'AuditLogEntry: event, content, ID, user, timestamp',
            'READ / CREATE / UPDATE / DELETE events',
            'Content types plus SESSION; no messaging audit',
        ]) + arrow('M570 695L650 500', 'implements', 580, 610, true) +
        arrow('M570 740H650', 'audit', 590, 726),
        'Solid arrows: dependency. Dashed open arrows: interface realization. Selected operations are shown.'),
    diagram('System classes - HTTP, forms and sessions', 'Shared authentication and navigation apply to editing and messaging.',
        box(30, 115, 540, 'HttpWebServer : WebServerController', [
            '+ start / address / close (safe to repeat)',
            'Authenticates every protected route, refreshes session',
            'Delegates to WebEditingHandler / WebMessageHandler',
            'ContentController supplies current role and records',
        ]) + box(650, 115, 540, 'WebAuthentication / StoredWebAuthentication', [
            'StoredWebAuthentication implements WebAuthentication',
            '+ hasAccounts / authenticate',
            'Uses AuthenticationService and ciphered credentials',
            'Login issues token; logout invalidates token',
        ]) + arrow('M570 205H650', 'uses', 590, 190) +
        box(30, 390, 540, 'WebSessionStore / SqliteWebSessionStore', [
            'SqliteWebSessionStore implements WebSessionStore',
            '+ create / usernameFor / invalidate / clear',
            'Also implements SessionAdministration',
            'SQLite token, username, created_at, last_seen',
            'Sliding inactivity timeout: more than 30 minutes',
        ]) + arrow('M300 271V390', 'uses persistent store', 310, 337) +
        box(650, 390, 540, 'SessionAdmin / SessionMenu', [
            'SessionMenu -> SessionAdmin -> SessionAdministration',
            '+ list(session) / revoke(token, session): admin only',
            'WebSessionInfo: full token, username and times',
            'Revocation removes token; audit records SESSION',
            'InMemoryWebSessionStore remains a test alternative',
        ]) + arrow('M650 490H570', 'uses', 590, 475) +
        box(30, 680, 540, 'WebEditingHandler / WebFormData', [
            'Handler -> WebEditingWorkflow -> WebEditForm',
            'WebFormData: UTF-8, duplicates, 8192-byte limit',
            'Login, editing and messaging share the parser',
        ]) + box(650, 680, 540, 'HtmlView / ContentText', [
            'HtmlView: login, menu, records, search, audit, forms',
            'Link / Field / AuditRow / Nav nested display records',
            'Shared CSS and escaped HTML; no JS or images',
        ]), 'Session / Role / AccessControl determine capabilities. Accounts and passwords are managed in the terminal.'),
    diagram('System classes - private web messaging', 'Every signed-in user can send; only the matching addressee can read.',
        box(30, 115, 540, 'WebMessageHandler', [
            'HttpWebServer supplies authenticated Session + token',
            'GET inbox / new / view; POST send / open',
            'Session-bound CSRF; shared WebFormData parser',
            'Successful POST -> 303 redirect -> GET',
        ]) + box(650, 115, 540, 'MessageService <<interface>>', [
            '+ recipients(session) / send(agentId, body, session)',
            '+ inbox(session): List<Message>',
            '+ message(id, session): Optional<Message>',
            '+ markRead(id, session): boolean',
        ]) + arrow('M570 200H650', 'uses', 590, 185) +
        box(30, 405, 540, 'MessageHtmlView', [
            '+ inbox / compose / detail / error / withNav',
            'Uses HtmlView.shell for shared CSS and navigation',
            'Escapes message text, names and form values',
            'Inbox shows Read / Unread and Open buttons',
        ]) + arrow('M300 271V405', 'renders', 313, 344) +
        box(650, 405, 540, 'SqliteMessageService : MessageService', [
            'Scopes reads to username = agents.name (ignore case)',
            'Adds nullable read_at to existing messages tables',
            'First owned POST open sets read_at once',
            'Foreign or missing message IDs both return not found',
        ]) + arrow('M920 405V271', 'implements', 936, 344, true) +
        box(30, 695, 540, 'MessageMenu', [
            'Terminal send and personal inbox use MessageService',
            'Terminal reading does not change web read state',
        ]) + box(650, 695, 540, 'Message <<immutable>>', [
            'id, agentId, agentName, sender, body, createdAt',
            'readAt: nullable timestamp of the first web open',
        ]) + arrow('M920 561V695', 'returns', 936, 644),
        'Expiry and revocation reject messaging before reads or writes. Read state persists across server restarts.'),
];
const names = pkg => fs.readdirSync(path.join(root, 'src/main/java', pkg)).filter(n => n.endsWith('.java')).map(n => n.slice(0, -5)).sort();
const modelNames = names('model');
function catalogColumn(x, title, items) {
    return `<text x="${x}" y="125" class="box-title">${title}</text>` + items.map((n, i) => `<text x="${x}" y="160" transform="translate(0 ${i * 24})">${escape(n)}</text>`).join('');
}
classes.push(diagram('System classes - complete production type index', 'Companion index to the relationship diagrams; generated from src/main/java.',
    catalogColumn(30, 'model (1 of 2)', modelNames.slice(0, 22)) +
    catalogColumn(430, 'model (2 of 2)', modelNames.slice(22)) +
    catalogColumn(830, 'controller', names('controller')) +
    `<text x="430" y="620" class="box-title">view</text>` + names('view').map((n,i) => `<text x="430" y="${651 + i * 22}">${escape(n)}</text>`).join('') +
    '<text x="830" y="670" class="box-title">Application entry point</text><text x="830" y="704">TopSecret</text>',
    'Nested HtmlView records: Link, Field, AuditRow, Nav. Interfaces, enums and exceptions are included in this index.'));
const erd = [
    diagram('Database ERD - content and relationships', 'PK = primary key | FK = foreign key | NN = NOT NULL | arrows point to referenced rows',
        box(30, 115, 350, 'countries', ['PK id INTEGER', 'name TEXT NN']) +
        box(435, 115, 350, 'facilities', ['PK id INTEGER', 'name TEXT NN', 'abbreviation TEXT NN', 'FK country_id INTEGER NULL']) +
        box(840, 115, 350, 'agents', ['PK id INTEGER', 'name TEXT NN', 'date_of_birth TEXT NN', 'date_of_death TEXT NN', 'notes TEXT NN', 'FK facility_id INTEGER NN']) +
        arrow('M435 185H380', '0..1', 387, 171) + arrow('M840 185H785', '1', 805, 171) +
        box(30, 415, 350, 'missions', ['PK id INTEGER AUTOINCREMENT', 'title TEXT NN', 'brief TEXT NN', 'date TEXT NN']) +
        box(435, 415, 350, 'mission_facilities', ['PK/FK mission_id INTEGER', 'FK facility_id INTEGER NN']) +
        box(840, 415, 350, 'mission_agents', ['PK/FK mission_id INTEGER NN', 'PK/FK agent_id INTEGER NN', 'Composite primary key']) +
        arrow('M435 480H380', '1', 405, 468) + arrow('M610 415V271', 'facility', 620, 360) +
        arrow('M1015 415V321', 'agent', 1027, 370) + arrow('M840 530L805 595H400L380 530', 'mission', 595, 585) +
        box(30, 665, 350, 'briefs', ['PK id INTEGER; title TEXT NN', 'date TEXT NN; text TEXT NN', 'FK mission_id INTEGER NULL']) +
        arrow('M205 665V571', '0..1 mission', 220, 625) +
        '<text x="435" y="675">Country 1 -> 0..* facilities; facility 1 -> 0..* agents.</text>' +
        '<text x="435" y="710">Mission has exactly 1 facility and 1..* agents.</text>' +
        '<text x="435" y="745">Agent may serve 0..* missions; mission has 0..* briefs.</text>' +
        '<text x="435" y="780">Triggers protect a mission\'s facility and last agent.</text>',
        'Country/agent facility deletes: RESTRICT. Mission deletion: CASCADE joins, SET NULL on attached briefs.'),
    diagram('Database ERD - messages, audit and web sessions', 'All tables share data/missions.db; credentials and roles remain in data/credentials.cip.',
        box(30, 115, 540, 'messages', ['PK id INTEGER AUTOINCREMENT', 'FK agent_id INTEGER NN -> agents.id', 'sender TEXT NN; body TEXT NN', 'created_at TEXT NN (ISO local date/time)', 'read_at TEXT NULL (first web open)']) +
        box(650, 115, 540, 'agents (referenced content entity)', ['One agent receives zero or more messages', 'Deleting an agent cascades to its messages', 'Inbox ownership: case-insensitive name = username', 'No foreign key to the external credential file']) + arrow('M570 230H650', 'agent', 588, 215) +
        box(30, 435, 540, 'audit_log', ['PK id INTEGER AUTOINCREMENT', 'event_type TEXT NN; content_type TEXT NN', 'content_id INTEGER NN; username TEXT NN', 'timestamp TEXT NN', 'READ / CREATE / UPDATE / DELETE', 'No FK: history survives deletion; SESSION uses ID 0']) +
        box(650, 435, 540, 'web_sessions', ['PK token TEXT (random 256-bit, base64url)', 'username TEXT NN', 'created_at INTEGER NN (epoch milliseconds)', 'last_seen INTEGER NN (epoch milliseconds)', 'No FK: usernames refer to credential file accounts', 'Terminal admins list and revoke full issued tokens']) +
        '<text x="30" y="750">read_at migration preserves existing messages; NULL means unread. Reopening keeps the original time.</text>' +
        '<text x="30" y="785">Sessions expire after >30 idle minutes; activity refreshes last_seen. Logout/revocation/stop remove sessions.</text>',
        'Messages are excluded from audit. Content writes and their audit rows commit atomically; session revocation is audited separately.'),
];
const nav = '<nav>Top Secret | briefs | agents | facilities | missions | countries | Search | Messaging | Audit (admin) | [Log out]</nav>';
function frame(title, body, authenticated = true) {
    return `<section><h1>${title}</h1>${authenticated ? nav : ''}${body}</section>`;
}
const frames = [
    frame('Login and navigation', '<h2>GET /</h2><p>Top Secret</p><div class="mock">Username: [________________]<br>Password: [________________]<br>[Log in]</div><p>POST /login creates a session and redirects to /menu. Invalid credentials show a safe error. Accounts and initial passwords are created through the terminal.</p><h2>GET /menu</h2>' + nav + '<p>Signed in as: Ghost</p><p>Choose any content type, Search or Messaging. Administrators also see Audit. Shared CSS styles login, content, forms and error pages.</p><p>POST /logout ends the session. An expired or revoked session redirects to login before reading or changing content.</p>', false),
    frame('Lists, detail and related records', '<h2>GET /content?type=agents</h2><p>Agents &nbsp; [New record - admin only]</p><div class="mock">[Ghost] &nbsp; [Cardinal] &nbsp; [...]</div><h2>GET /record?type=agents&amp;id=1</h2><div class="mock">Name: Ghost<br>Birth date: 1990-01-01<br>Death date: (blank)<br>Notes: ...<br>Facility: [Langley]<br>Missions: [Nightfall], [...]<br>[Edit record - admin only]</div><p>Each bracketed record name is a link to its detail page.</p><ul><li>Facility: country, agents and missions.</li><li>Country: facilities.</li><li>Mission: facility, assigned agents and briefs.</li><li>Brief: attached mission, or None.</li></ul>'),
    frame('Create and edit content', '<h2>GET /new?type=... or /edit?type=...&amp;id=...</h2><p>Admin only. New forms are empty; edit forms contain current values.</p><div class="mock">Label: [text input]<br>Date: [YYYY-MM-DD]<br>Notes / text / brief: [multiline textarea]<br>Related record: [dropdown v]<br>[Save] &nbsp; [Cancel]</div><ul><li>Brief: title, date, text.</li><li>Agent: name, birth/death dates, notes, facility.</li><li>Facility: name, abbreviation, optional country.</li><li>Country: name.</li><li>Mission: title, brief, date; creation also selects facility and first agent.</li></ul><p>POST /create or /update checks the current admin role and CSRF token. Success redirects to the list/detail; validation errors retain entered values. Mission relationship changes remain in the terminal.</p><p>Standard users cannot access these forms or save through direct requests.</p>'),
    frame('Messaging inbox and detail', '<h2>GET /messages - My messages</h2><p>[New message]</p><table><tr><th>Status</th><th>From</th><th>Received</th><th>Action</th></tr><tr><td>Unread</td><td>admin</td><td>2026-11-20 10:15</td><td>[Open]</td></tr><tr><td>Read</td><td>Observer</td><td>2026-11-19 14:05</td><td>[Open]</td></tr></table><p>Only messages addressed to an agent matching the current username appear. Empty inbox: "No messages available."</p><p>Open submits POST /messages/open, marks the owned message read once, then redirects to detail.</p><h2>GET /messages/view?id=1</h2><div class="mock">From: admin<br>To: Ghost<br>Received: 2026-11-20 10:15<br><br>Meet at dawn.<br><br>[Back to my messages]</div><p>Detail refresh does not change read state. A missing or foreign message shows the same "Message not found" page.</p>'),
    frame('Compose a message', '<h2>GET /messages/new - New message</h2><div class="mock">To: [Select an agent v]<br><br>Message: [multiline text area]<br><br>[Send message] &nbsp; [Cancel]</div><p>The dropdown lists the current agents. Sending is available to standard users and administrators.</p><p>POST /messages/send checks a valid recipient, nonblank text (at most 4096 characters), exact form fields and the session CSRF token. Sender identity comes from the signed-in account.</p><p>Success redirects to the sender\'s inbox. Recipients see the new message as Unread; the sender has no special access to the recipient\'s inbox.</p><p>Expired/revoked sessions return to login without sending or marking a message. After signing in again, reload the form to obtain the new session\'s CSRF token.</p><p>All message text and names are escaped. Messaging creates no audit entries.</p>'),
    frame('Search, audit and request outcomes', '<h2>GET /search</h2><div class="mock">Search every record: [phrase________] [Search]<br>Results: [missions: Nightfall], [briefs: Gate report]</div><p>Search includes every text field. Empty results show "No matches." Each result links to its record.</p><h2>GET /audit - administrators only</h2><p>Rows: timestamp | username | event | content type | ID. Newest first. Includes content access/changes and terminal session revocations.</p><h2>Request outcomes</h2><ul><li>Missing, expired or revoked session: 303 to login.</li><li>Invalid forms or CSRF: 400; admin-only requests by standard users: 403.</li><li>Missing/foreign message or unknown record: 404.</li><li>Wrong method: 405; oversized form: 413; storage failure: generic 500.</li></ul><h2>Terminal session administration</h2><p>Admin menu option 14 lists username, full issued token, creation time and last activity. Enter the full token to revoke it. The next browser request must log in again.</p>'),
];
const css = `@page{size:A3 landscape;margin:0}html,body{margin:0;font-family:Arial,sans-serif;color:#18334f}svg{display:block;width:420mm;height:297mm;break-after:page;break-inside:avoid}svg:last-child{break-after:auto}text{font:17px Arial,sans-serif;fill:#18334f}.title{font-size:27px;font-weight:bold}.box-title{font-size:19px;font-weight:bold}.label{font-size:15px}`;
const frameCss = `@page{size:A4;margin:16mm}body{font:15px/1.5 Arial,sans-serif;color:#18334f;margin:0}section{break-after:page}section:last-child{break-after:auto}h1{font-size:24px;margin:0 0 16px}h2{font-size:18px;margin:22px 0 10px}nav{font-size:12px;background:#edf2f8;padding:10px}p{margin:14px 0}.mock{border:1px solid #25476a;background:#f4f7fb;padding:18px;line-height:1.9}table{border-collapse:collapse;width:100%}td,th{border:1px solid #25476a;padding:9px;text-align:left}li{margin-bottom:8px}`;
for (const [name, body, style] of [['SystemClasses', classes.join(''), css], ['DatabaseERD', erd.join(''), css], ['WebWireframes', frames.join(''), frameCss]]) {
    const source = path.join(__dirname, name + '.html');
    fs.writeFileSync(source, `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Top Secret - ${name}</title><style>${style}</style></head><body>${body}</body></html>\n`);
    const result = spawnSync(browser, ['--headless', '--disable-gpu', '--no-pdf-header-footer',
        `--user-data-dir=${path.join(out, name + '-profile')}`, `--print-to-pdf=${path.join(__dirname, name + '.pdf')}`, source],
        { timeout: 45000, windowsHide: true });
    if (result.error || result.status !== 0) throw result.error || new Error(result.stderr.toString());
    console.log('Updated ' + name + '.html and .pdf');
}
