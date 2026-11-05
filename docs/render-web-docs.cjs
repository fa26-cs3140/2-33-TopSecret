const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'build', 'diagrams');
fs.mkdirSync(output, { recursive: true });
const browsers = process.platform === 'win32'
    ? ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe']
    : ['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
       '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'];
const browser = browsers.find(fs.existsSync);
if (!browser) {
    throw new Error('Google Chrome or Microsoft Edge is required to render the documentation.');
}
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
    <text x="25" y="40" style="font-size:25px;font-weight:bold">Top Secret - MVC and HTTP controller classes</text>
    <text x="25" y="70">TopSecret starts terminal or web mode</text>
    ${box(25, 100, 'controller.HttpWebServer', ['implements WebServerController', '+ start(): void; address(): InetSocketAddress', '+ close(): void', '- route / login / parameters / sessionToken'])}
    ${box(435, 100, 'controller.ContentController', ['+ sessionFor(username): Session', '+ canViewAudit(session): boolean', '+ list(type, session): List&lt;ContentEntry&gt;', '+ read(type, id, session): ContentEntry', '+ audit(session): List&lt;AuditLogEntry&gt;', '+ facilityFor / agentsFor / briefsFor'])}
    ${box(845, 100, 'view.HtmlView / ContentText', ['HtmlView: login / menu / list', 'record / audit / error', 'Link / Field / AuditRow display data', 'ContentText: terminal text + list titles'], 350)}
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
    <text x="25" y="795">Model owns access rules; the web controller supplies display data to HtmlView.</text>
    </svg>`;

let classes = fs.readFileSync(path.join(__dirname, 'SystemClasses.html'), 'utf8');
classes = classes.replace('ContentType: BRIEF, AGENT, FACILITY', 'ContentType: BRIEF, AGENT, FACILITY, MISSION');
classes = classes.replace('Top Secret - System class diagram', 'Top Secret - Model and terminal class diagram');
classes = classes.replace('</style>', 'body {height:auto;overflow:visible} svg {break-after:page;break-inside:avoid} svg:last-child {break-after:auto}</style>');
classes = classes.replace('</body>', controller + '</body>');
fs.writeFileSync(path.join(__dirname, 'SystemClasses.html'), classes);

const frames = [
    ['Login and errors', '<p>GET / (public). Application pages require a valid session.</p><h1>Top Secret</h1><p>Enter your credentials.</p><form><p>Username [________________]</p><p>Password [________________]</p><button>Log in</button></form><p>POST /login succeeds: 303 /menu + browser-session cookie.</p><p>Invalid username or password: 401, same form, no cookie.</p><p>New accounts / initial passwords: terminal only.</p>'],
    ['Authenticated navigation', '<p>GET /menu</p><h1>Menu</h1><p>Signed in as agent</p><ul><li><a>briefs</a> - /content?type=briefs</li><li><a>agents</a> - /content?type=agents</li><li><a>facilities</a> - /content?type=facilities</li><li><a>missions</a> - /content?type=missions</li><li><a>Review audit log</a> - /audit (admin only)</li></ul><button>Log out</button><p>POST /logout clears the cookie and redirects to login.</p>'],
    ['List, detail and audit', '<p>GET /content?type=briefs</p><h1>briefs</h1><ul><li><a>1. Sample brief</a> - /record?type=briefs&amp;id=1</li><li><a>2. Another brief</a></li></ul><p>GET /record?type=briefs&amp;id=1</p><h1>briefs #1</h1><table border="1"><tr><th>title</th><td>Sample brief</td></tr><tr><th>date</th><td>2026-11-05</td></tr><tr><th>text</th><td>Read-only content...</td></tr></table><p><a>Back to menu</a></p><p>GET /record?type=missions&amp;id=7 also shows facility, agents and attached briefs.</p><p>GET /audit (admin only)</p><table border="1"><tr><th>Time</th><th>User</th><th>Event</th><th>Content</th></tr><tr><td>timestamp</td><td>agent</td><td>READ</td><td>BRIEF #1</td></tr></table><p>Empty lists and audit logs display a message. Missing records: 404.</p>'],
    ['Access and request errors', '<h1>Request outcomes</h1><ul><li>Missing, forged or expired server session: 303 /</li><li>Invalid form, duplicate parameter or nonpositive ID: 400</li><li>Standard user requesting audit: 403</li><li>Unknown authenticated page or missing record: 404</li><li>Attempt to create/edit over HTTP: 405 + Allow</li><li>Oversized login form: 413</li><li>Storage failure: generic 500; server continues serving</li></ul><p>No passwords, tokens or backend details appear in error pages.</p><p>Application HTML uses forms, lists, links and tables. No CSS, JavaScript or web frameworks.</p>'],
];
const wireframes = `<!doctype html><html><head><meta charset="utf-8"><title>Top Secret web wireframes</title><style>
    @page {size:A4;margin:18mm} body {font:16px Arial,sans-serif;line-height:1.5} section {break-after:page} section:last-child {break-after:auto} h2 {font-size:20px} a {text-decoration:underline} pre {white-space:pre-wrap} button {padding:6px}
    </style></head><body>${frames.map(([title, body], index) => `<section><h2>Wireframe ${index + 1}: ${title}</h2><p>Top Secret web view</p><hr>${body}</section>`).join('')}</body></html>`;
fs.writeFileSync(path.join(__dirname, 'WebWireframes.html'), wireframes);
render('SystemClasses');
render('WebWireframes');
console.log('Rendered current class diagram, ERD and web wireframes.');
