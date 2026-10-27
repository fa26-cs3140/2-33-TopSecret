const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const browsers = process.platform === 'win32'
    ? ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe']
    : ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
       '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'];
const browser = browsers.find(fs.existsSync);
if (!browser) {
    throw new Error('Google Chrome or Microsoft Edge is required to render the diagrams.');
}
const directory = __dirname;
const temporary = path.resolve(directory, '../build/diagrams');
fs.mkdirSync(temporary, { recursive: true });

function box(left, top, title, lines, width = 265) {
    const height = 58 + lines.length * 20;
    return `<g><rect x="${left}" y="${top}" width="${width}" height="${height}" rx="5" fill="#f5f8fc" stroke="#25476a"/>
        <path d="M${left} ${top + 35}h${width}" stroke="#25476a"/>
        <text x="${left + 12}" y="${top + 24}" font-weight="bold">${title}</text>
        ${lines.map((line, index) => `<text x="${left + 12}" y="${top + 59 + index * 20}" style="font-size:12px">${line}</text>`).join('')}</g>`;
}

function arrow(startX, startY, endX, endY, label = '') {
    return `<path d="M${startX} ${startY} L${endX} ${endY}" fill="none" stroke="#25476a" marker-end="url(#arrow)"/>
        <text x="${(startX + endX) / 2 + 5}" y="${(startY + endY) / 2 - 7}" font-size="12">${label}</text>`;
}

const classes = [
    box(25, 90, 'TopSecret', ['+ main(args)', '+ run(args, in, out, auth, ui)']),
    box(325, 90, 'UserInterface', ['- session: Session', '- contentMenu: ContentMenu', '+ runMenu(in, out, session)', '+ run(args): String']),
    box(625, 90, 'ContentMenu', ['- content: ContentService', '- authentication: AuthenticationService', '+ run(in, out, session)']),
    box(925, 90, 'ContentService [interface]', ['+ list(type, session)', '+ read(type, id, session)', '+ add(type, values, session)', '+ update(type, id, values, session)', '+ delete(type, id, session)', '+ audit(session)']),
    box(25, 320, 'AuthenticationService', ['- credentialStore: CredentialStore', '+ createUser(username)', '+ sessionFor(username)', '+ promote(actor, target)', '+ demote(actor, target)']),
    box(325, 320, 'Session / AccessControl', ['Session: username, role: Role', '+ canReadContent(session)', '+ canCreateContent(session)', '+ canViewAuditLog(session)', '+ canCreateUsers(session)']),
    box(625, 320, 'Content domain models', ['Brief: id, title, date, text', 'Agent: identity, dates, notes, facilityId', 'Facility: id, name, abbreviation', 'MissionAssignment: mission, facility, agents']),
    box(925, 320, 'SqliteContentService', ['implements ContentService', '+ initialize()', '+ list/read/add/update/delete', '- import seed data + relationships', '- enforce access and validation', '- record transactional audit']),
    box(25, 560, 'CredentialStore [interface]', ['FileCredentialStore implements', 'Credential: username/password/role', 'Cipher - SubstitutionCipher', 'FileHandler - FileHandlerImpl']),
    box(325, 560, 'ProgramControl [interface]', ['ProgramControlImpl implements', 'MissionSearch - MissionSearchService', 'MissionRepository - SqliteMissionRepository', 'Mission: id, title, date, brief']),
    box(625, 560, 'AuditLogRepository [interface]', ['SqliteAuditLogRepository implements', 'AuditLogEntry: id, event, type,', '  contentId, username, timestamp', 'AuditEventType: READ, CREATE']),
    box(925, 560, 'ContentEntry / ContentType / Effects', ['ContentEntry: id, ordered values', 'ContentType: BRIEF, AGENT, FACILITY', 'Effects - AnimatedEffects', 'Role: ADMIN, STANDARD']),
    arrow(290, 115, 325, 115), arrow(590, 125, 625, 125), arrow(890, 125, 925, 125),
    arrow(1055, 320, 1055, 230, 'implements'), arrow(158, 320, 158, 188, 'used by'),
    arrow(158, 478, 158, 560), arrow(455, 228, 455, 320, 'checks'),
    arrow(760, 208, 760, 320, 'uses'), arrow(1055, 498, 760, 560, 'audit reads'),
    '<path d="M325 200 L308 200 L308 540 L455 540 L455 560" fill="none" stroke="#25476a" marker-end="url(#arrow)"/>',
    '<text x="330" y="534" font-size="12">legacy API</text>',
].join('');

const erd = [
    box(25, 100, 'facilities', ['PK id INTEGER', 'name TEXT NOT NULL', 'abbreviation TEXT NOT NULL'], 280),
    box(365, 100, 'agents', ['PK id INTEGER', 'name TEXT NOT NULL', 'date_of_birth TEXT NOT NULL', 'date_of_death TEXT NOT NULL *', 'notes TEXT NOT NULL *', 'FK facility_id INTEGER NOT NULL'], 330),
    box(815, 100, 'briefs', ['PK id INTEGER', 'title TEXT NOT NULL', 'date TEXT NOT NULL', 'text TEXT NOT NULL'], 340),
    box(25, 400, 'missions', ['PK id INTEGER AUTOINCREMENT', 'title TEXT NOT NULL', 'brief TEXT NOT NULL', 'date TEXT NOT NULL'], 300),
    box(370, 350, 'mission_facilities', ['PK/FK mission_id INTEGER', 'FK facility_id INTEGER NOT NULL'], 330),
    box(370, 540, 'mission_agents', ['PK/FK mission_id INTEGER', 'PK/FK agent_id INTEGER'], 330),
    box(815, 400, 'audit_log', ['PK id INTEGER AUTOINCREMENT', 'event_type TEXT NOT NULL', 'content_type TEXT NOT NULL', 'content_id INTEGER NOT NULL', 'username TEXT NOT NULL', 'timestamp TEXT NOT NULL'], 360),
    arrow(365, 215, 305, 215),
    '<text x="313" y="204">1</text><text x="349" y="204">*</text>',
    arrow(370, 405, 325, 450),
    '<text x="330" y="438">1</text><text x="357" y="409">1</text>',
    arrow(535, 350, 165, 218),
    '<text x="180" y="236">1</text><text x="510" y="342">*</text>',
    arrow(370, 590, 325, 500),
    '<text x="330" y="514">1</text><text x="357" y="576">*</text>',
    '<path d="M700 590 L750 590 L750 300 L650 300 L650 278" fill="none" stroke="#25476a" marker-end="url(#arrow)"/>',
    '<text x="660" y="298">1</text><text x="710" y="580">*</text>',
    '<text x="25" y="700">mission_facilities permits one facility per mission. mission_agents permits one or more unique agents per mission.</text>',
    '<text x="25" y="728">RESTRICT protects referenced facilities/agents; CASCADE removes relationship rows when a mission is deleted.</text>',
    '<text x="25" y="756">* Blank strings represent optional death date and notes. All stored dates use ISO text.</text>',
    '<text x="25" y="784">audit_log content_id is historical and polymorphic, not a foreign key. Credentials remain in a ciphered file.</text>',
].join('');

for (const [name, title, body] of [
    ['SystemClasses', 'Top Secret - System class diagram', classes],
    ['DatabaseERD', 'Top Secret - Database entity-relationship diagram', erd],
]) {
    const html = `<!doctype html><html><head><meta charset="utf-8"><style>
        @page { size: A3 landscape; margin: 0; }
        html, body { margin:0; width:420mm; height:297mm; overflow:hidden; font-family:Arial,sans-serif; color:#18334f; }
        svg { display:block; width:420mm; height:297mm; } text { font-family:Arial,sans-serif; font-size:14px; fill:#18334f; }
        </style></head><body><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 850">
        <defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#25476a"/></marker></defs>
        <text x="25" y="38" style="font-size:25px;font-weight:bold">${title}</text>
        <text x="25" y="65">Current implementation snapshot | October 23, 2026</text>
        ${body}<text x="25" y="824">See interface-design.txt and content-model.txt for contracts, integrity rules and regression steps.</text>
        </svg></body></html>`;
    const source = path.join(directory, `${name}.html`);
    fs.writeFileSync(source, html);
    const common = ['--headless', '--disable-gpu', '--no-pdf-header-footer', `--user-data-dir=${path.join(temporary, name)}`];
    const result = spawnSync(browser, [...common, `--print-to-pdf=${path.join(directory, name + '.pdf')}`, source], { timeout: 45000 });
    if (result.error || !fs.existsSync(path.join(directory, name + '.pdf'))) {
        throw result.error || new Error(result.stderr.toString());
    }
    spawnSync(browser, [...common, '--window-size=1220,850', '--hide-scrollbars', `--screenshot=${path.join(temporary, name + '.png')}`, source], { timeout: 45000 });
    console.log(`Created ${name}.pdf`);
}
