/* Add Member C's implemented messaging design to the shared diagrams.
 * Run after the Member B and A update scripts. Re-running replaces only HW7C.
 */
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const browser = [
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
    '/usr/bin/chromium',
].find(fs.existsSync);
if (!browser) {
    throw new Error('Chrome, Edge or Chromium is required to render the design PDFs.');
}
const output = path.resolve(__dirname, '../build/diagrams/hw7c');
fs.mkdirSync(output, { recursive: true });

function box(x, y, width, title, lines) {
    return `<g><rect x="${x}" y="${y}" width="${width}" height="${64 + lines.length * 27}" rx="6" fill="#f5f8fc" stroke="#25476a"/>
        <text x="${x + 16}" y="${y + 29}" style="font-size:19px;font-weight:bold">${title}</text>
        ${lines.map((line, index) => `<text x="${x + 16}" y="${y + 66 + index * 27}">${line}</text>`).join('')}</g>`;
}
function page(title, body) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1220 960" role="img" aria-label="${title}">
        <defs><marker id="hw7carrow" markerWidth="8" markerHeight="8" refX="8" refY="4" orient="auto"><path d="M0 0L8 4L0 8" fill="none" stroke="#25476a"/></marker></defs>
        <text x="25" y="48" style="font-size:26px;font-weight:bold">${title}</text>
        <text x="25" y="82">Homework 7 / Member C / implemented web messaging</text>
        ${body.trim()}</svg>`;
}

const classes = page('Messaging - implemented class and route relationships', `
    ${box(25, 130, 550, 'MessageService &lt;&lt;interface&gt;&gt;', [
        '+ recipients(session) / send(agentId, body, session)',
        '+ inbox(session): List&lt;Message&gt;',
        '+ message(id, session): Optional&lt;Message&gt;',
        '+ markRead(id, session): boolean',
    ])}
    ${box(650, 130, 545, 'SqliteMessageService &lt;&lt;implements&gt;&gt;', [
        'Owns messages table and read_at migration',
        'Joins agents to scope reads to the username',
        'markRead only changes an unread owned message',
        'SQL statements use parameters; no message audit',
    ])}
    <path d="M650 250H575" stroke="#25476a" marker-end="url(#hw7carrow)"/>
    ${box(25, 390, 550, 'Message &lt;&lt;immutable model&gt;&gt;', [
        'id, agentId, agentName, sender, body',
        'createdAt; readAt is nullable until first open',
        'The old constructor still serves terminal callers',
    ])}
    ${box(650, 390, 545, 'WebMessageHandler &lt;&lt;controller&gt;&gt;', [
        'Receives current Session from HttpWebServer',
        'GET inbox, compose and owned detail',
        'POST send and open; redirect after success',
        'Uses shared WebFormData and session-bound CSRF',
    ])}
    <path d="M650 430L575 302" stroke="#25476a" marker-end="url(#hw7carrow)"/>
    ${box(25, 680, 550, 'MessageHtmlView &lt;&lt;view&gt;&gt;', [
        'Inbox, detail, compose and error pages',
        'HtmlView.shell supplies shared CSS and navigation',
        'Escapes all dynamic labels and message text',
    ])}
    <path d="M650 530L565 680" stroke="#25476a" marker-end="url(#hw7carrow)"/>
    <text x="25" y="905">Every signed-in role can send. Only the addressee can list, open or view a message.</text>`);

const erd = page('Messaging - current database schema and read state', `
    ${box(25, 150, 500, 'agents &lt;&lt;existing&gt;&gt;', [
        'PK id INTEGER',
        'name TEXT NOT NULL',
        'User inbox matches agents.name to username',
        'Case-insensitive match; no account foreign key',
    ])}
    ${box(650, 150, 545, 'messages &lt;&lt;current schema&gt;&gt;', [
        'PK id INTEGER AUTOINCREMENT',
        'FK agent_id INTEGER NOT NULL',
        'sender TEXT NOT NULL; body TEXT NOT NULL',
        'created_at TEXT NOT NULL',
        'read_at TEXT NULL (first read time)',
    ])}
    <path d="M650 280H525" stroke="#25476a" marker-end="url(#hw7carrow)"/>
    <text x="420" y="390">One agent has zero or more messages.</text>
    ${box(25, 540, 550, 'Migration and read semantics', [
        'Existing messages gain nullable read_at by ALTER TABLE',
        'Old and new messages start unread (NULL)',
        'First owned open sets read_at once; later opens keep it',
        'The column remains after server restart',
    ])}
    ${box(650, 540, 545, 'Access and audit boundary', [
        'Lookup joins messages.agent_id to agents.id',
        'Only username = agent name can read or mark read',
        'Foreign and missing IDs both return not found',
        'Messaging is excluded from the audit log',
    ])}
    <text x="25" y="900">This page completes the messages table shown on the original ERD page.</text>`);

const frames = `<section><h2>Homework 7: Messaging inbox</h2>
    <p>Member C. Implemented in the web view for all signed-in users.</p>
    <nav>Top Secret | briefs | agents | facilities | missions | countries | Messaging | [Log out]</nav>
    <h3>GET /messages - My messages</h3><p>[New message]</p>
    <table border="1" cellpadding="8"><tr><th>Status</th><th>From</th><th>Received</th><th>Action</th></tr>
    <tr><td>Unread</td><td>TestAdmin</td><td>2026-11-19 17:13</td><td>[Open]</td></tr>
    <tr><td>Read</td><td>Observer</td><td>2026-11-18 14:05</td><td>[Open]</td></tr></table>
    <p>Only messages addressed to the agent whose name matches the signed-in username are listed.
    An account with no matching agent or messages sees "No messages available."</p>
    <p>[Open] submits POST /messages/open with message ID and CSRF token, marks the message read once,
    then redirects to GET /messages/view?id=... . Refreshing the detail page does not change read_at.</p>
    <h3>GET /messages/view?id=1</h3>
    <p>From: TestAdmin<br>To: Ghost<br>Received: 2026-11-19 17:13</p>
    <p>Meet at dawn.</p><p>[Back to my messages]</p>
    <p>A foreign or missing ID shows the same "Message not found" page. The menu and log out remain visible.</p>
    </section><section><h2>Homework 7: Compose and send</h2>
    <p>Member C. Messaging requires a valid session but does not require administrator permission.</p>
    <nav>Top Secret | briefs | agents | facilities | missions | countries | Messaging | [Log out]</nav>
    <h3>GET /messages/new - New message</h3>
    <p>To: [Select an agent v] - options come from the current agents table.</p>
    <p>Message: [multiline text area, up to 4096 characters]</p>
    <p>[Send message]&nbsp;&nbsp;&nbsp;[Cancel]</p>
    <p>POST /messages/send checks recipient, nonblank text, size, form fields and CSRF token,
    then redirects to /messages. The sender is the session username, not a form field.</p>
    <p>Blank text or an unknown recipient returns a safe error; malformed or forged input cannot
    create a message. A standard user sees the same compose form as an administrator.</p>
    <p>All dynamic text is escaped. Shared CSS and navigation come from HtmlView.shell.
    No JavaScript or images.</p></section>`;

for (const [name, supplement] of [
    ['SystemClasses', classes], ['DatabaseERD', erd], ['WebWireframes', frames],
]) {
    const source = path.join(__dirname, name + '.html');
    let document = fs.readFileSync(source, 'utf8')
        .replace(/<!-- HW7C START -->[\s\S]*?<!-- HW7C END -->/g, '');
    if (name === 'SystemClasses') {
        document = document.replace(/<text x="697" y="791" style="font-size:12px">[^<]*<\/text>/,
            '<text x="697" y="791" style="font-size:12px">+ send / inbox / message / markRead</text>');
        document = document.replace(/<text x="697" y="811" style="font-size:12px">[^<]*<\/text>/,
            '<text x="697" y="811" style="font-size:12px">Message: body, createdAt, readAt</text>');
    }
    fs.writeFileSync(source,
        document.replace('</body>', `<!-- HW7C START -->${supplement}<!-- HW7C END --></body>`));
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
