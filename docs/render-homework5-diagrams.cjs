const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const edge = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
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
    box(925, 90, 'ContentService [interface]', ['+ list(type, session)', '+ read(type, id, session)', '+ add(type, values, session)', '+ audit(session)']),
    box(25, 320, 'AuthenticationService', ['- credentialStore: CredentialStore', '+ createUser(username)', '+ sessionFor(username)', '+ promote(actor, target)', '+ demote(actor, target)']),
    box(325, 320, 'Session / AccessControl', ['Session: username, role: Role', '+ canReadContent(session)', '+ canCreateContent(session)', '+ canViewAuditLog(session)', '+ canCreateUsers(session)']),
    box(625, 320, 'ContentEntry / ContentType', ['ContentEntry: id, values (immutable)', 'ContentType: BRIEF, AGENT, FACILITY', 'type defines table and field order']),
    box(925, 320, 'SqliteContentService', ['implements ContentService', '+ initialize()', '- validate(type, values)', '- record(connection, type, id, ...)', 'SQLite + transactional audit']),
    box(25, 560, 'CredentialStore [interface]', ['FileCredentialStore implements', 'Credential: username/password/role', 'Cipher - SubstitutionCipher', 'FileHandler - FileHandlerImpl']),
    box(325, 560, 'ProgramControl [interface]', ['ProgramControlImpl implements', 'MissionSearch - MissionSearchService', 'MissionRepository - SqliteMissionRepository', 'Mission: id, title, date, brief']),
    box(625, 560, 'AuditLogRepository [interface]', ['SqliteAuditLogRepository implements', 'AuditLogEntry: id, event, type,', '  contentId, username, timestamp', 'AuditEventType: READ, CREATE']),
    box(925, 560, 'Effects [interface]', ['AnimatedEffects implements', '+ banner(out)', '+ loading(out, message)', 'Role: ADMIN, STANDARD']),
    arrow(290, 115, 325, 115), arrow(590, 125, 625, 125), arrow(890, 125, 925, 125),
    arrow(1055, 320, 1055, 230, 'implements'), arrow(158, 320, 158, 188, 'used by'),
    arrow(158, 478, 158, 560), arrow(455, 228, 455, 320, 'checks'),
    arrow(760, 208, 760, 320, 'uses'), arrow(1055, 478, 760, 560, 'audit reads'),
    '<path d="M325 200 L308 200 L308 540 L455 540 L455 560" fill="none" stroke="#25476a" marker-end="url(#arrow)"/>',
    '<text x="330" y="534" font-size="12">legacy API</text>',
].join('');

const erd = [
    box(45, 110, 'facilities', ['PK id INTEGER', 'name TEXT NOT NULL', 'abbreviation TEXT NOT NULL'], 310),
    box(470, 110, 'agents', ['PK id INTEGER', 'name TEXT NOT NULL', 'date_of_birth TEXT NOT NULL', 'date_of_death TEXT NOT NULL *', 'notes TEXT NOT NULL *', 'FK facility_id INTEGER NOT NULL'], 330),
    box(865, 110, 'briefs', ['PK id INTEGER', 'title TEXT NOT NULL', 'date TEXT NOT NULL', 'text TEXT NOT NULL'], 310),
    box(45, 450, 'missions (legacy)', ['PK id INTEGER AUTOINCREMENT', 'title TEXT NOT NULL', 'brief TEXT NOT NULL', 'date TEXT NOT NULL'], 340),
    box(610, 450, 'audit_log', ['PK id INTEGER AUTOINCREMENT', 'event_type TEXT NOT NULL', 'content_type TEXT NOT NULL', 'content_id INTEGER NOT NULL', 'username TEXT NOT NULL', 'timestamp TEXT NOT NULL'], 410),
    arrow(470, 225, 355, 225),
    '<text x="380" y="212">1 : many</text>',
    '<text x="50" y="330">Agent facility_id references facilities.id; ON DELETE RESTRICT. Each agent has exactly one facility.</text>',
    '<text x="50" y="357">* Blank strings represent optional death date and notes. Dates are ISO text; adapter validates input.</text>',
    '<text x="50" y="385">Foreign keys must be enabled on every writer connection. Briefs are independent of missions.</text>',
    '<text x="50" y="730">Audit content_id is a historical, polymorphic reference, not a foreign key. Credentials remain in a ciphered file.</text>',
    '<text x="50" y="759">Pending Member C: mission-to-facility and mission-to-agent relationships are absent in the current schema.</text>',
].join('');

for (const [name, title, body] of [
    ['Homework5Classes', 'Homework 5 - Current system class diagram', classes],
    ['Homework5ERD', 'Homework 5 - Current database entity relationship diagram', erd],
]) {
    const html = `<!doctype html><html><head><meta charset="utf-8"><style>
        @page { size: A3 landscape; margin: 8mm; }
        body { margin:0; font-family:Arial,sans-serif; color:#18334f; }
        svg { width:100%; height:auto; } text { font-family:Arial,sans-serif; font-size:14px; fill:#18334f; }
        </style></head><body><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 850">
        <defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#25476a"/></marker></defs>
        <text x="25" y="38" style="font-size:25px;font-weight:bold">${title}</text>
        <text x="25" y="65">Team 2-33 | Member B integration | Current implementation, October 22, 2026</text>
        ${body}<text x="25" y="824">See homework5-member-b.txt for interface contracts, regression steps and outstanding team requirements.</text>
        </svg></body></html>`;
    const source = path.join(directory, `${name}.html`);
    fs.writeFileSync(source, html);
    const common = ['--headless', '--disable-gpu', '--no-pdf-header-footer', `--user-data-dir=${path.join(temporary, name)}`];
    const result = spawnSync(edge, [...common, `--print-to-pdf=${path.join(directory, name + '.pdf')}`, source], { timeout: 45000 });
    if (result.error || !fs.existsSync(path.join(directory, name + '.pdf'))) {
        throw result.error || new Error(result.stderr.toString());
    }
    spawnSync(edge, [...common, '--window-size=1220,850', '--hide-scrollbars', `--screenshot=${path.join(temporary, name + '.png')}`, source], { timeout: 45000 });
    console.log(`Created ${name}.pdf`);
}
