const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'build', 'diagrams');
fs.mkdirSync(output, { recursive: true });
const browser = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const base = spawnSync(process.execPath, [path.join(__dirname, 'render-diagrams.cjs')], { timeout: 120000 });
if (base.error || base.status !== 0) {
    throw base.error || new Error(base.stderr.toString());
}

function render(name) {
    const source = path.join(__dirname, name + '.html');
    const profile = path.join(output, name + '-web');
    const common = ['--headless', '--disable-gpu', '--no-pdf-header-footer', `--user-data-dir=${profile}`];
    const pdf = spawnSync(browser, [...common, `--print-to-pdf=${path.join(__dirname, name + '.pdf')}`, source], { timeout: 45000 });
    if (pdf.error || pdf.status !== 0 || !fs.existsSync(path.join(__dirname, name + '.pdf'))) {
        throw pdf.error || new Error(pdf.stderr.toString());
    }
    spawnSync(browser, [...common, '--window-size=1600,1100', '--hide-scrollbars',
        `--screenshot=${path.join(output, name + '-web.png')}`, source], { timeout: 45000 });
}

function box(left, top, title, lines, width = 360) {
    return `<g><rect x="${left}" y="${top}" width="${width}" height="${55 + lines.length * 23}" fill="white" stroke="black"/>
        <text x="${left + 12}" y="${top + 27}" font-weight="bold">${title}</text>
        ${lines.map((line, index) => `<text x="${left + 12}" y="${top + 57 + index * 23}">${line}</text>`).join('')}</g>`;
}

function link(startX, startY, endX, endY, label) {
    return `<path d="M${startX} ${startY} L${endX} ${endY}" fill="none" stroke="black" marker-end="url(#arrow)"/>
        <text x="${Math.min(startX, endX) + 5}" y="${(startY + endY) / 2 - 8}">${label}</text>`;
}

const controller = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 850">
    <defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="black"/></marker></defs>
    <text x="25" y="40" style="font-size:25px;font-weight:bold">Homework 6 - MVC and HTTP controller classes</text>
    <text x="25" y="70">November 5, 2026 | TopSecret remains the default-package composition root</text>
    ${box(25, 100, 'controller.HttpWebServer', ['implements WebServerController', '+ start(): void; address(): InetSocketAddress', '+ close(): void', '- route / login / parameters / sessionToken'])}
    ${box(435, 100, 'controller.ContentController', ['+ sessionFor(username): Session', '+ canViewAudit(session): boolean', '+ list(type, session): List&lt;ContentEntry&gt;', '+ read(type, id, session): ContentEntry', '+ audit(session): List&lt;AuditLogEntry&gt;'])}
    ${box(845, 100, 'view.HtmlView / ContentText', ['HtmlView: page/login/menu/navigation', 'list/record/audit/escape', 'ContentText: title/record/audit', 'Shared terminal and HTML text'], 350)}
    ${box(25, 400, 'controller.StoredWebAuthentication', ['implements WebAuthentication', '+ hasAccounts(): boolean', '+ authenticate(user, password): Optional&lt;String&gt;'])}
    ${box(435, 400, 'controller.InMemoryWebSessionStore', ['implements WebSessionStore', '+ create(user): String', '+ usernameFor(token): Optional&lt;String&gt;', '+ invalidate(token): void; clear(): void', 'SecureRandom + ConcurrentHashMap'])}
    ${box(845, 400, 'model services / view adapters', ['AuthenticationService / Session / AccessControl', 'ContentService / SqliteContentService', 'view.ContentMenu uses ContentController', 'ContentNotFoundException extends', 'controller.ProgramControlException'], 350)}
    ${link(385, 180, 435, 180, 'uses')}
    <path d="M205 100 L205 88 L1020 88 L1020 100" fill="none" stroke="black" marker-end="url(#arrow)"/>
    <text x="800" y="85">renders HTML</text>
    ${link(205, 247, 205, 400, 'uses auth')}
    ${link(385, 270, 435, 400, 'uses sessions')}
    ${link(700, 292, 1020, 400, 'uses model')}
    <text x="25" y="690">controller: LaunchMode {TERMINAL, WEB, CHANGE_PASSWORD}; fromArguments(args): LaunchMode</text>
    <text x="25" y="725">WebServerController: PORT=3002; SESSION_COOKIE=TopSecretSession; extends AutoCloseable</text>
    <text x="25" y="760">Session cookies have no Expires/Max-Age at login. Logout/restart invalidate in-memory sessions.</text>
    <text x="25" y="795">All previous domain/persistence classes are in model; terminal menus/effects are in view.</text>
    </svg>`;

let classes = fs.readFileSync(path.join(__dirname, 'SystemClasses.html'), 'utf8');
classes = classes.replace('Current implementation snapshot | November 4, 2026', 'Current implementation snapshot | November 5, 2026');
classes = classes.replace('ContentType: BRIEF, AGENT, FACILITY', 'ContentType: BRIEF, AGENT, FACILITY, MISSION');
classes = classes.replace('Top Secret - System class diagram', 'Top Secret - Model and terminal class diagram');
classes = classes.replace('</style>', 'body {height:auto;overflow:visible} svg {break-after:page;break-inside:avoid} svg:last-child {break-after:auto}</style>');
classes = classes.replace('</body>', controller + '</body>');
fs.writeFileSync(path.join(__dirname, 'SystemClasses.html'), classes);

const frames = [
    ['Login and errors', '<p>GET / (public). All application pages require a valid session.</p><h1>Top Secret login</h1><p>Enter your credentials.</p><form><p>Username [________________]</p><p>Password [________________]</p><button>Log in</button></form><p>POST /login succeeds: 303 /menu + browser-session cookie.</p><p>Invalid username or password: 401, same form, no cookie.</p><p>New accounts / initial passwords: terminal only.</p>'],
    ['Authenticated navigation', '<p>GET /menu</p><h1>Main menu</h1><ul><li><a>briefs</a> - /content?type=briefs</li><li><a>agents</a> - /content?type=agents</li><li><a>facilities</a> - /content?type=facilities</li><li><a>missions</a> - /content?type=missions</li><li><a>Review audit log</a> - /audit (admin only)</li></ul><p><a>Main menu</a></p><button>Log out</button><p>POST /logout clears the cookie and redirects to login.</p>'],
    ['List, detail and audit', '<p>GET /content?type=briefs</p><h1>briefs</h1><ul><li><a>1. Sample brief</a> - /record?type=briefs&amp;id=1</li><li><a>2. Another brief</a></li></ul><p>GET /record?type=briefs&amp;id=1</p><h1>briefs #1</h1><pre>title: Sample brief\ndate: 2026-11-05\ntext: Read-only content...</pre><p><a>Back to list</a> | <a>Main menu</a></p><p>GET /audit (admin only)</p><pre>timestamp | username | READ | BRIEF #1</pre><button>Log out</button><p>Empty lists and audit logs display a message. Missing records: 404.</p>'],
    ['Access and request errors', '<h1>Request outcomes</h1><ul><li>Missing, forged or expired server session: 303 /</li><li>Invalid form, duplicate parameter or nonpositive ID: 400</li><li>Standard user requesting audit: 403</li><li>Unknown authenticated page or missing record: 404</li><li>Attempt to create/edit over HTTP: 405 + Allow</li><li>Oversized login form: 413</li><li>Storage failure: generic 500; server continues serving</li></ul><p>No passwords, tokens or backend details appear in error pages.</p><p>Application HTML uses forms, lists, links and preformatted shared text. No CSS, JavaScript or web frameworks.</p>'],
];
const wireframes = `<!doctype html><html><head><meta charset="utf-8"><title>HW6 web wireframes</title><style>
    @page {size:A4;margin:18mm} body {font:16px Arial,sans-serif;line-height:1.5} section {break-after:page} section:last-child {break-after:auto} h2 {font-size:20px} a {text-decoration:underline} pre {white-space:pre-wrap} button {padding:6px}
    </style></head><body>${frames.map(([title, body], index) => `<section><h2>Wireframe ${index + 1}: ${title}</h2><p>Top Secret - Homework 6 - November 5, 2026</p><hr>${body}</section>`).join('')}</body></html>`;
fs.writeFileSync(path.join(__dirname, 'WebWireframes.html'), wireframes);
render('SystemClasses');
render('WebWireframes');
console.log('Rendered current class diagram and web wireframes. ERD remains unchanged: no persistent web tables.');
