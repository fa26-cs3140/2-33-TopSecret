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
        <text x="25" y="80">Homework 7 / Member B / Week 1 - design supplement, November 10, 2026</text>
        ${body}</svg>`;
}

const classes = page('Web editing - controller contracts and planned wiring', `
    ${box(25, 120, 550, 'WebEditingWorkflow &lt;&lt;interface; Week 1 stub&gt;&gt;', [
        '+ newForm(type, session): WebEditForm',
        '+ editForm(type, id, session): WebEditForm',
        '+ create(type, values, facilityId, agentId, session): void',
        '+ update(type, id, values, session): void',
        'All methods may throw ProgramControlException',
    ])}
    ${box(650, 120, 545, 'WebEditForm &lt;&lt;immutable record; Week 1&gt;&gt;', [
        'type: ContentType; recordId: Integer (null = new)',
        'values: List&lt;String&gt; in ContentType.fields order',
        '+ newRecord(type): empty values',
        '+ editRecord(type, entry): current values and ID',
        'Defensive copy; field count and positive ID checks',
    ])}
    <path d="M575 215H650" stroke="#25476a" marker-end="url(#hw7arrow)"/>
    <text x="583" y="199">returns</text>
    ${box(25, 430, 550, 'HttpWebServer &lt;&lt;existing; wiring in Week 2&gt;&gt;', [
        'Resolve current Session for each protected request',
        'New/edit GET; create/update POST only',
        'Check admin, bounded form input and CSRF',
        'Delegate to workflow; redirect after successful write',
    ])}
    ${box(650, 430, 545, 'ContentService / AccessControl &lt;&lt;existing&gt;&gt;', [
        'add / update; addMission is atomic with relationships',
        'canCreateContent(session): administrators only',
        'Model validates and records CREATE / UPDATE audit',
        'HtmlView renders escaped fields with A\'s CSS/nav',
    ])}
    <path d="M300 430V315" stroke="#25476a" stroke-dasharray="8 5" marker-end="url(#hw7arrow)"/>
    <text x="315" y="387">planned use</text>
    <path d="M560 300L660 430" stroke="#25476a" stroke-dasharray="8 5" marker-end="url(#hw7arrow)"/>
    <text x="650" y="380">planned implementation delegates</text>
    <text x="25" y="725">Interface has no implementation yet; running HTTP server remains read-only.</text>
    <text x="25" y="765">Country support depends on A/D. No user-account editing or new credential/session store.</text>
    <text x="25" y="805">Solid arrow: return type. Dashed arrows: planned runtime dependencies, not active code.</text>
    <text x="25" y="845">Existing system classes and relationships are retained on the preceding pages.</text>`);

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
    <text x="25" y="760">Week 1 adds no schema migration. Dashed relationship is proposed, not present in the database.</text>
    <text x="25" y="800">Country rename should display through the FK lookup, never a copied facility country label.</text>
    <text x="25" y="840">Current complete ERD appears on the preceding page. Session administration belongs to D.</text>`);

const frames = `<section><h2>Homework 7: New and edit workflow (proposed)</h2>
    <p>Member B Week 1. These routes and controls are design targets for Week 2.</p>
    <nav>Menu | Briefs | Agents | Facilities | Missions | Countries (pending A/D) | Log out</nav>
    <p>Use A's shared CSS/navigation. No JavaScript or images. No user-management links.</p>
    <h3>Administrator list/detail</h3><p>[New brief] on list; [Edit] on detail. Standard users see neither.</p>
    <h3>GET /new?type=briefs</h3><p>Title [________________]<br>Date [________________]<br>Text [________________]</p>
    <p>[Create] POST /create (hidden type and CSRF token) | [Cancel: brief list]</p>
    <h3>GET /edit?type=briefs&amp;id=7</h3><p>Title [Signal intercept]<br>Date [2026-11-10]<br>Text [Current stored text...]</p>
    <p>[Save changes] POST /update (hidden type, ID and CSRF) | [Cancel: record #7]</p>
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
    Store IDs behind relationship labels; escape all labels, values and error text.</p>
    <ul><li>Missing/invalid session: 303 to login.</li><li>Current standard role: 403 on all editing routes, including crafted POST.</li>
    <li>Bad fields, duplicate/unexpected parameters or invalid CSRF: 400, no mutation.</li>
    <li>Missing record: 404. Wrong method: 405 + Allow. Oversized body: 413.</li>
    <li>Unexpected storage failure: generic 500; no database details.</li></ul>
    <p>Account creation, passwords and role management remain terminal-only. Countries, styling,
    messaging and session administration are team dependencies, not Member B implementations.</p></section>`;

for (const [name, supplement] of [
    ['SystemClasses', classes], ['DatabaseERD', erd], ['WebWireframes', frames],
]) {
    const source = path.join(__dirname, name + '.html');
    let original = fs.readFileSync(source, 'utf8').replace(/<!-- HW7 START -->[\s\S]*?<!-- HW7 END -->/g, '');
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
