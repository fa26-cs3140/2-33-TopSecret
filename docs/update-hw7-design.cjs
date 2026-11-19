const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const output = path.resolve(__dirname, '../build/diagrams/hw7');
fs.mkdirSync(output, { recursive: true });
const browser = [
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/chromium',
].find(fs.existsSync);
if (!browser) {
    throw new Error('Install Edge, Chrome or Chromium to render the design PDFs.');
}

function box(left, top, width, title, lines) {
    return `<g><rect x="${left}" y="${top}" width="${width}" height="${65 + lines.length * 26}" rx="6" fill="#f5f8fc" stroke="#25476a"/>
        <text x="${left + 16}" y="${top + 30}" style="font-size:18px;font-weight:bold">${title}</text>
        ${lines.map((line, index) => `<text x="${left + 16}" y="${top + 65 + index * 26}">${line}</text>`).join('')}</g>`;
}

function page(title, body) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 960" role="img" aria-label="${title}">
        <defs><marker id="hw7arrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#25476a"/></marker></defs>
        <text x="25" y="45" style="font-size:25px;font-weight:bold">${title}</text>
        <text x="25" y="80">Homework 7 / Member B / Week 2 - implementation, November 19, 2026</text>
        ${body}</svg>`;
}

const classes = page('Web editing - implemented controller workflow', `
    ${box(25, 120, 550, 'ContentController : WebEditingWorkflow', [
        '+ newForm(type, session): WebEditForm',
        '+ editForm(type, id, session): WebEditForm',
        '+ create(type, values, facilityId, agentId, session): void',
        '+ update(type, id, values, session): void',
        'All methods may throw ProgramControlException',
    ])}
    ${box(650, 120, 545, 'WebEditForm &lt;&lt;immutable record&gt;&gt;', [
        'type: ContentType; recordId: Integer (null = new)',
        'values: List&lt;String&gt; in ContentType.fields order',
        '+ newRecord(type): empty values',
        '+ editRecord(type, entry): current values and ID',
        'Defensive copy; field count and positive ID checks',
    ])}
    <path d="M575 215H650" stroke="#25476a" marker-end="url(#hw7arrow)"/>
    <text x="583" y="199">returns</text>
    ${box(25, 430, 550, 'HttpWebServer / WebEditingHandler', [
        'Resolve current Session for each protected request',
        'New/edit GET; create/update POST only',
        'Check admin, bounded form input and CSRF',
        'Handler delegates to ContentController workflow',
    ])}
    ${box(650, 430, 545, 'ContentService / AccessControl &lt;&lt;existing&gt;&gt;', [
        'add / update; addMission is atomic with relationships',
        'canCreateContent(session): administrators only',
        'Model validates and records CREATE / UPDATE audit',
        'HtmlView.editForm: escaped inputs and textareas',
    ])}
    <path d="M300 430V315" stroke="#25476a" marker-end="url(#hw7arrow)"/>
    <text x="315" y="387">uses workflow</text>
    <path d="M560 300L660 430" stroke="#25476a" marker-end="url(#hw7arrow)"/>
    <text x="650" y="380">delegates writes and authorization</text>
    ${box(25, 690, 550, 'WebFormData &lt;&lt;shared transport parser&gt;&gt;', [
        'read(exchange): form map; null when over 8192 bytes',
        'parameters(encoded): reject duplicates / bad UTF-8',
        'Used by login and WebEditingHandler',
    ])}
    ${box(650, 690, 545, 'WebValidationException : ProgramControlException', [
        'Controller validates scalar fields before model writes',
        'Handler redisplays submitted form with HTTP 400',
        'Storage failures remain generic 500; missing IDs 404',
    ])}
    <text x="25" y="920">Country forms await A/D. Shared CSS/navigation awaits A. Accounts remain terminal-only.</text>`);

const erd = page('Editing persistence - existing schema and country handoff', `
    ${box(25, 135, 450, 'countries &lt;&lt;proposed; A/D own schema&gt;&gt;', [
        'PK id INTEGER (proposed stable record ID)',
        'name TEXT (proposed editable country label)',
        'Import supplied country list at initialization',
    ])}
    ${box(700, 135, 495, 'facilities &lt;&lt;existing + proposed FK&gt;&gt;', [
        'PK id INTEGER', 'name TEXT NOT NULL',
        'abbreviation TEXT NOT NULL',
        'FK country_id INTEGER (proposed; A/D confirm)',
    ])}
    <path d="M700 230H475" stroke="#25476a" stroke-dasharray="8 5" marker-end="url(#hw7arrow)"/>
    <text x="485" y="210">1 country</text><text x="600" y="210">0..* facilities</text>
    <text x="25" y="390">Each facility is assigned one country in the target design. Existing facilities need a migration decision.</text>
    <text x="25" y="430">Field names, uniqueness, import format and migration/backfill remain A/D handoff decisions.</text>
    ${box(25, 480, 550, 'Member B persistence boundary', [
        'Web forms use existing ContentService operations',
        'Brief / agent / facility: add and update',
        'Mission create: addMission(values, facility, agent)',
        'Mission update: update; relationship edits need D API',
    ])}
    ${box(650, 480, 545, 'audit_log &lt;&lt;existing; unchanged by B&gt;&gt;', [
        'Model writes CREATE / UPDATE transactionally',
        'event_type, content_type, content_id',
        'username, timestamp; id is the primary key',
        'No web-form table; no duplicate content storage',
    ])}
    <text x="25" y="760">Member B adds no schema migration. Dashed relationship is proposed, not present in the database.</text>
    <text x="25" y="800">Country rename should display through the FK lookup, never a copied facility country label.</text>
    <text x="25" y="840">Current complete ERD appears on the preceding page. Session administration belongs to D.</text>`);

const frames = `<section><h2>Homework 7: New and edit workflow</h2>
    <p>Member B Week 2. Implemented November 19, 2026.</p>
    <nav>Back to menu | Cancel | Save</nav>
    <p>Shared CSS/navigation awaits A. Countries await A/D. No JavaScript, images or user-management links.</p>
    <h3>Administrator list/detail</h3><p>[New brief] on list; [Edit] on detail. Standard users see neither.</p>
    <h3>GET /new?type=briefs</h3><p>Title [________________]<br>Date [________________]<br>Text [________________]</p>
    <p>[Save] POST /create (hidden type and CSRF token) | [Cancel: brief list]</p>
    <h3>GET /edit?type=briefs&amp;id=7</h3><p>Title [Signal intercept]<br>Date [2026-11-10]<br>Text [Current stored text...]</p>
    <p>[Save] POST /update (hidden type, ID and CSRF) | [Cancel: record #7]</p>
    <p>Successful create: 303 to list. Successful update: 303 to detail. Refresh performs GET.</p>
    <p>Error: show a safe message beside the form and retain submitted values. Cancel performs no write.</p>
    </section><section><h2>Homework 7: Form fields and access outcomes</h2>
    <table border="1" cellpadding="7"><tr><th>Type</th><th>Fields / choices</th></tr>
    <tr><td>Brief</td><td>title, date, text</td></tr>
    <tr><td>Agent</td><td>name, date_of_birth, optional date_of_death, optional notes, facility choice</td></tr>
    <tr><td>Facility</td><td>name, abbreviation; country choice when A/D supply the model</td></tr>
    <tr><td>Mission</td><td>title, brief, date; facility and first-agent choices required on create</td></tr>
    <tr><td>Country (pending)</td><td>country label; exact fields await A/D contract</td></tr></table>
    <p>New scalar values start empty; relationship choices start unselected. Edit loads current values.
    Store IDs behind relationship labels; escape all labels, values and error text.
    Long text uses textareas; dates use date inputs. Mission update edits scalar fields only.</p>
    <ul><li>Missing/invalid session: 303 to login.</li><li>Current standard role: 403 on all editing routes, including crafted POST.</li>
    <li>Bad fields, duplicate/unexpected parameters or invalid CSRF: 400, no mutation.</li>
    <li>Missing record: 404. Wrong method: 405 + Allow. Oversized body: 413.</li>
    <li>Unexpected storage failure: generic 500; no database details.</li></ul>
    <p>Account creation, passwords and role management remain terminal-only. C's messaging is available.
    Countries, styling and session administration remain team dependencies.</p></section>`;

for (const [name, supplement] of [
    ['SystemClasses', classes], ['DatabaseERD', erd], ['WebWireframes', frames],
]) {
    const source = path.join(__dirname, name + '.html');
    let original = fs.readFileSync(source, 'utf8').replace(/<!-- HW7 START -->[\s\S]*?<!-- HW7 END -->/g, '');
    original = original.replace('Attempt to create/edit over HTTP: 405 + Allow', 'Wrong route method: 405 + Allow; admin create/edit use dedicated forms')
        .replace('Oversized login form: 413', 'Oversized login or editing form: 413')
        .replace('No CSS, JavaScript or web frameworks.', 'No JavaScript or images. Shared styling awaits Member A.')
        .replace('Read-only content...', 'Stored content...')
        .replace('Missing, forged or expired server session', 'Missing, forged or revoked server session')
        .replace('- route / login / parameters / sessionToken', '- route / login / sessionToken')
        .replace('- route / login / sessionToken; WebFormData parses input', '- route / login / sessionToken');
    if (name === 'DatabaseERD' && !original.includes('/* HW7 pagination */')) {
        original = original.replace('</style>', '/* HW7 pagination */ body {height:auto;overflow:visible} svg {break-after:page;break-inside:avoid} svg:last-child {break-after:auto}</style>');
    }
    fs.writeFileSync(source, original.replace('</body>', `<!-- HW7 START -->${supplement}<!-- HW7 END --></body>`));
    const result = spawnSync(browser, [
        '--headless', '--disable-gpu', '--no-pdf-header-footer',
        `--user-data-dir=${path.join(output, name + '-profile')}`,
        `--print-to-pdf=${path.join(__dirname, name + '.pdf')}`, source,
    ], { timeout: 45000, windowsHide: true });
    if (result.error || result.status !== 0) {
        throw result.error || new Error(result.stderr.toString());
    }
    console.log('Updated ' + name + '.html and .pdf');
}
