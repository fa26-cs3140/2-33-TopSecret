/*
 * Member A design supplement for Homework 7: the countries entity, the shared
 * page styling and the navigation block.
 *
 * This appends a block marked HW7A to the three shared design documents and
 * re-renders their PDFs. It is a sibling of update-hw7-design.cjs rather than
 * an edit to it: both scripts own a separate marked region of the same files,
 * so either can be re-run without discarding the other member's boxes. Run
 * Member B's script first, then this one.
 *
 * The script also corrects the sentences in the earlier blocks that described
 * Member A's work as pending, because this cycle delivers it. That is the same
 * mechanism update-hw7-design.cjs already uses to correct the Homework 6 text.
 */
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const output = path.resolve(__dirname, '../build/diagrams/hw7a');
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
    return `<g><rect x="${left}" y="${top}" width="${width}" height="${65 + lines.length * 26}" rx="6" fill="#f7fbf5" stroke="#2f6b37"/>
        <text x="${left + 16}" y="${top + 30}" style="font-size:18px;font-weight:bold">${title}</text>
        ${lines.map((line, index) => `<text x="${left + 16}" y="${top + 65 + index * 26}">${line}</text>`).join('')}</g>`;
}

function page(title, body, height) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 ${height || 960}" role="img" aria-label="${title}">
        <defs><marker id="hw7aarrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#2f6b37"/></marker></defs>
        <text x="25" y="45" style="font-size:25px;font-weight:bold">${title}</text>
        <text x="25" y="80">Homework 7 / Member A / countries, page styling and navigation - November 19, 2026</text>
        ${body}</svg>`;
}

const classes = page('Countries, styling and navigation - delivered classes', `
    ${box(25, 120, 560, 'ContentType &lt;&lt;enum, extended&gt;&gt;', [
        'COUNTRY("countries", ["name"]) is the new constant',
        'FACILITY fields become name, abbreviation, country_id',
        'A country travels the path facility_id already travels:',
        'enum field, choices() dropdown, validateValues check',
        'No parallel country store, route or service is added',
    ])}
    ${box(650, 120, 545, 'SqliteContentService &lt;&lt;extended&gt;&gt;', [
        'COUNTRIES_TXT = "countries.txt" at the file boundary',
        '+ CREATE_COUNTRIES runs before facilities (FK order)',
        '+ ALTER TABLE facilities ADD COLUMN country_id',
        '+ importCountries(connection, text) when table empty',
        'Facility import accepts an optional country column',
    ])}
    <path d="M585 215H650" stroke="#2f6b37" marker-end="url(#hw7aarrow)"/>
    <text x="500" y="320">COUNTRY.table names the countries table; the enum stays the only type registry.</text>
    ${box(25, 400, 560, 'HtmlView &lt;&lt;extended; immutable&gt;&gt;', [
        'record Nav(String username, List&lt;Link&gt; links)',
        '+ withNav(username, links): HtmlView (new instance)',
        'document(title, body) renders STYLE and the nav block',
        'A view with no Nav renders log in and error unchanged',
        'Per-request copy: no mutable field shared across threads',
    ])}
    ${box(650, 400, 545, 'LaunchMode &lt;&lt;enum, extended&gt;&gt;', [
        'HELP is the new constant',
        'fromArguments accepts -h and --help',
        'TopSecret prints help text and exits successfully',
        'Unknown arguments still throw with the usage string',
    ])}
    ${box(25, 700, 560, 'Styling and navigation placement', [
        'One stylesheet constant lives in the page shell',
        'No separate /style.css route: nothing new is served',
        'before authentication, and the sheet cannot 404',
        'No Javascript and no images anywhere in the view',
    ])}
    ${box(650, 700, 545, 'ContentController &lt;&lt;refactored&gt;&gt;', [
        'Identifier validation widened from the literal',
        'facility_id to any field whose name ends in _id',
        'The failure message names the rejected field',
        'Country selection no longer reports a facility error',
    ])}
    ${box(25, 880, 560, 'MessageHtmlView &lt;&lt;C; listed here for completeness&gt;&gt;', [
        '+ inbox(messages, csrf) / detail(message)',
        '+ compose(recipients, csrf, error) / error(message)',
        '+ withNav(username, links): MessageHtmlView',
        'Renders through HtmlView.shell: one page shell',
    ])}
    ${box(650, 880, 545, 'WebMessageHandler &lt;&lt;C; listed here for completeness&gt;&gt;', [
        '+ handle(exchange, path, session, token, view)',
        'Routes /messages, /messages/new, open and send',
        'Own CSRF key; only the addressee reads a message',
        'Receives the reader\'s view from HttpWebServer',
    ])}
    <text x="25" y="1090">Delivered by Member A, except the two boxes marked C, which are included because the</text>
    <text x="25" y="1125">class diagram must list every class. The navigation block carries C messaging and the audit link when permitted.</text>`, 1160);

const erd = page('Countries schema - delivered', `
    ${box(25, 135, 470, 'countries &lt;&lt;new&gt;&gt;', [
        'PK id INTEGER',
        'name TEXT NOT NULL',
        'Imported from countries.txt (196 names) when empty',
        'Editable: a rename shows at every assigned facility',
    ])}
    ${box(700, 135, 495, 'facilities &lt;&lt;migrated&gt;&gt;', [
        'PK id INTEGER', 'name TEXT NOT NULL',
        'abbreviation TEXT NOT NULL',
        'FK country_id INTEGER NULL',
        'REFERENCES countries(id) ON DELETE RESTRICT',
    ])}
    <path d="M700 240H495" stroke="#2f6b37" marker-end="url(#hw7aarrow)"/>
    <text x="505" y="220">1 country</text><text x="600" y="220">0..* facilities</text>
    <text x="25" y="400">The relationship is now present in the database; it is no longer proposed.</text>
    <text x="25" y="440">A facility reads its country through the foreign key. No country label is copied onto a facility row.</text>
    ${box(25, 490, 560, 'Migration', [
        'CREATE TABLE IF NOT EXISTS cannot add a column to a',
        'database that already exists, so the column is added',
        'by ALTER TABLE guarded by columnExists, exactly as',
        'briefs.mission_id was added in Homework 6.',
        'Verified against an existing data/missions.db.',
    ])}
    ${box(650, 490, 545, 'Why country_id is nullable', [
        'Six facilities already exist with no country.',
        'A required column cannot be added to them.',
        'The web and terminal forms require a choice, so any',
        'facility saved by the application has a country.',
        'An older row reads as Unassigned, never as an error.',
    ])}
    <text x="25" y="800">ON DELETE RESTRICT: a country that still has facilities cannot be deleted, matching agents.facility_id.</text>
    <text x="25" y="840">name carries no UNIQUE constraint; see the Week 2 notes for that decision and its limitation.</text>
    <text x="25" y="880">Countries are imported only when the table is empty, so a rename is never overwritten on restart.</text>`);

const frames = `<section><h2>Homework 7: Shared styling and navigation</h2>
    <p>Member A. Delivered November 19, 2026. CSS only - no JavaScript and no images.</p>
    <p>Every authenticated page renders the same block above its heading:</p>
    <nav>Top Secret | briefs | agents | facilities | missions | countries | Messaging | Review audit log | [Log out]</nav>
    <p>Signed in as &lt;username&gt; appears beside it. Log out is a POST form, not a link, so it
    cannot be triggered by a prefetch or a crafted image request.</p>
    <p>The audit link appears only for a user whose role may view it. Messaging appears for all users.
    The per-page <em>Back to menu</em> anchors are retired: the menu is now on the page already.</p>
    <p>Log in and error pages carry the styling but no navigation, because there is no session to act in.</p>
    <h3>Styling</h3><p>One stylesheet in the shared page shell: a readable column, a sans-serif
    stack, bordered tables with striped rows, spaced form controls, and a visible focus outline.
    Colours are a dark slate on a light ground with one accent for links and the active nav item.</p>
    </section><section><h2>Homework 7: Countries</h2>
    <p>Member A. A country is an ordinary content type, so it reaches every screen the other types reach.</p>
    <h3>GET /content?type=countries</h3><p>Afghanistan | Albania | Algeria | ... 195 entries, each a link.
    [New country] appears for administrators only.</p>
    <h3>GET /record?type=countries&amp;id=74</h3><p>name: France<br>[Edit] for administrators.</p>
    <h3>GET /edit?type=countries&amp;id=74</h3><p>name [France____________]<br>[Save] POST /update | [Cancel: record #74]</p>
    <p>Renaming a country shows the new name at every facility assigned to it, because the facility
    reads the country through the foreign key rather than storing its own copy.</p>
    <h3>GET /record?type=facilities&amp;id=1</h3>
    <table border="1" cellpadding="7"><tr><th scope="row">name</th><td>Blackwood Annex</td></tr>
    <tr><th scope="row">abbreviation</th><td>BWA</td></tr>
    <tr><th scope="row">country</th><td>United Kingdom</td></tr></table>
    <p>The country is shown by name, not by its identifier. A facility with no country reads Unassigned.</p>
    <h3>GET /edit?type=facilities&amp;id=1</h3><p>name [Blackwood Annex___]<br>abbreviation [BWA___]<br>
    country_id [United Kingdom v] &lt;- select, current country preselected</p>
    <p>The control is the same select Member B's forms already render for a facility or agent choice;
    countries supply its options. Choosing a different country reassigns the facility on save.</p>
    </section><section><h2>Homework 7: Command line help</h2>
    <p>Member A. <code>java -jar TopSecret.jar -h</code> prints the help text and exits successfully.</p>
    <pre>Top Secret - mission archive

Usage: TopSecret [option]

  (no option)         Start the terminal interface.
  -web                Start the web interface on http://localhost:3002/.
  --change-password   Change the password of an existing account.
  -h, --help          Show this help text.

The terminal interface creates the first account. The web interface
refuses to start until an account exists. Sample data is read from
countries.txt, facilities.tsv, agents.tsv and mission_briefs.tsv in
the working directory.</pre>
    <p><code>--help</code> behaves identically. An unrecognised argument still reports the usage line
    and exits as an error, which is unchanged behaviour.</p></section>`;

const corrections = [
    ['Country forms await A/D. Shared CSS/navigation awaits A. Accounts remain terminal-only.',
        'Countries and shared CSS/navigation are delivered by A. Accounts remain terminal-only.'],
    ['No JavaScript or images. Shared styling awaits Member A.',
        'No JavaScript or images. Shared styling and navigation are delivered.'],
    ['Shared CSS/navigation awaits A. Countries await A/D. No JavaScript, images or user-management links.',
        'Shared CSS/navigation and countries are delivered. No JavaScript, images or user-management links.'],
    ['country choice when A/D supply the model', 'country choice, supplied by the countries type'],
    ['<td>Country (pending)</td><td>country label; exact fields await A/D contract</td>',
        '<td>Country</td><td>name; an ordinary content type</td>'],
    ['Countries, styling and session administration remain team dependencies.',
        'Countries and styling are delivered. Session administration remains a team dependency.'],
    ['countries &lt;&lt;proposed; A/D own schema&gt;&gt;', 'countries &lt;&lt;delivered by A&gt;&gt;'],
    ['FK country_id INTEGER (proposed; A/D confirm)', 'FK country_id INTEGER NULL (delivered by A)'],
    ['Each facility is assigned one country in the target design. Existing facilities need a migration decision.',
        'Each facility is assigned one country. Existing rows were migrated by ALTER TABLE; see the Member A page.'],
    ['Field names, uniqueness, import format and migration/backfill remain A/D handoff decisions.',
        'Field names, import format and migration are settled on the Member A page.'],
    ['Member B adds no schema migration. Dashed relationship is proposed, not present in the database.',
        'Member B adds no schema migration. Member A added the countries table and the facility foreign key.'],
];

for (const [name, supplement] of [
    ['SystemClasses', classes], ['DatabaseERD', erd], ['WebWireframes', frames],
]) {
    const source = path.join(__dirname, name + '.html');
    let document = fs.readFileSync(source, 'utf8')
        .replace(/<!-- HW7A START -->[\s\S]*?<!-- HW7A END -->/g, '');
    for (const [from, to] of corrections) {
        document = document.split(from).join(to);
    }
    fs.writeFileSync(source,
        document.replace('</body>', `<!-- HW7A START -->${supplement}<!-- HW7A END --></body>`));
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
