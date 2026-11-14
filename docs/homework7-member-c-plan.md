# Homework 7 — Member C web messaging plan

Status: Member C decisions and first implementation recorded November 14, 2026. The model, web routes, and HTML pages are implemented and pass the project test and coverage gate. Shared navigation/CSS, final diagrams, and a manual system test remain team delivery work. Member C owns messaging for a four-person team.

## Baseline findings and backlog

- `Message`, `MessageService`, and `SqliteMessageService` already send messages to an agent ID and return only the inbox for the agent whose name matches the signed-in username, ignoring case. `MessageMenu` renders terminal sending and reading.
- At the start of this work, the SQLite `messages` table had no read state. The migration now adds nullable `read_at`; the message model and service expose the first-read time.
- At the start of this work, web mode did not receive the message service. `TopSecret` now passes it to `HttpWebServer`, which delegates messaging requests to `WebMessageHandler`; `MessageHtmlView` renders the pages. The main menu now has Messaging for every signed-in user.
- `HttpWebServer` is also Member B's editing integration point. Member A owns shared page styling and navigation. Keep the messaging feature mostly behind its own controller/view classes to minimize simultaneous edits there.
- The existing test suite and 100% method-coverage gate pass on `main` as of November 14, 2026. This establishes a baseline, not proof of web messaging.

## Decisions for Member C

Member C selected all three recommended choices below on November 14, 2026. Record any later change here before changing the integration contract.

### 1. Opening a message

**Recommended: POST to mark read, then redirect to a GET detail page.** Each inbox row has an Open button. The POST verifies that the message belongs to the current username, marks it read, and responds with `303` to the detail page. Refreshing detail is then read-only. This fits the project's POST/redirect/GET pattern and avoids changing data during a crawler, preview, or browser prefetch.

Alternative: an ordinary GET detail link marks the message read. This is simpler HTML, but a GET request would change stored data. Either option must enforce recipient ownership on the server; hiding links is insufficient.

### 2. Persistent read state

**Recommended: nullable `read_at` timestamp in the existing `messages` table.** `NULL` means unread; the first successful open sets the timestamp, and later opens keep that original time. Add an idempotent migration for databases created before Homework 7. New messages start unread. Preserve old messages and terminal reading behavior.

Alternative: an integer `is_read` flag. It satisfies the indicator but cannot show when a message was first opened. Both approaches require migration and tests against an old database.

### 3. Server integration

**Recommended: a dedicated web messaging handler with thin routing in `HttpWebServer`.** Its constructor receives `MessageService`; `TopSecret` passes the already-initialized instance. The handler owns parsing, messaging actions, and page preparation; `HttpWebServer` continues to own authentication, cookie/session handling, redirects, and shared response policy. This lets Member B work on editing routes with a smaller overlap.

Alternative: put all messaging code in `HttpWebServer`. This changes fewer files initially but grows the class that Member B also needs to edit and makes the message flow harder to test independently.

## Implemented feature contract

The exact path names are a Member C choice; reserve these now so the other members can avoid collisions:

| Route | Method | Behavior |
| --- | --- | --- |
| `/messages` | GET | Inbox for signed-in username only; newest first, with sender, date, and read/unread status. Show an empty state if no account name matches an agent or no messages exist. |
| `/messages/new` | GET | Compose form with a dropdown from `MessageService.recipients`; available to every signed-in user. |
| `/messages/send` | POST | Validate recipient ID and bounded, nonblank body; send as the current username; redirect to inbox or a clear success page. |
| `/messages/open` | POST | Validate message ID and recipient ownership, mark it read once, redirect to `/messages/view?id=...`. |
| `/messages/view?id=...` | GET | Read-only detail of one message owned by the current username. |

There is no reply or delete feature, no admin-only rule, and no message audit event. The message body, sender, agent names, dates, errors, and links must be HTML escaped. Only the server-supplied session username determines inbox ownership; never accept a username or agent name from the request for reading. Missing or forged sessions follow the site's login redirect. A message owned by another user should be indistinguishable from a nonexistent message in the web response.

`WebMessageHandler` currently has its own bounded URL-encoded form decoder and a per-server HMAC CSRF token bound to the browser's session token. Member B can reuse or consolidate this parsing during editing integration. Invalid IDs and form fields do not mutate storage. Successful POSTs redirect so refresh does not resend or reopen. All database calls use parameterized SQL.

### Implemented model API extension

Retain the existing `recipients`, `send`, and `inbox` methods for terminal compatibility. Add an ownership-scoped lookup for one message and a mark-read operation. The SQL for both must join `messages.agent_id` to `agents.id` and require a case-insensitive match between `agents.name` and `session.getUsername()`. The mark-read statement should change only an unread message belonging to that user. A separate lookup should distinguish a permitted already-read message from an unknown/foreign ID without revealing which case occurred to the browser.

`Message` exposes nullable first-read time for inbox rendering, while retaining its old constructor for terminal callers. `MessageMenu` continues displaying its current terminal output; terminal reading does not mark messages read because the new requirement names the web view.

### Plain HTML wireframe

```text
Shared navigation: Menu | Briefs | Agents | Facilities | Missions | Messaging | Log out

My messages                         [New message]
Status    From       Received             Action
Unread    admin      2026-11-14 12:00     [Open]
Read      Ghost      2026-11-13 09:00     [Open]

Message from admin
Received: 2026-11-14 12:00
To: Ghost
<full message body>
[Back to my messages]

New message
To: [Select an agent v]
Message: [multiline text area]
[Send message] [Cancel]
```

The shared navigation and CSS belong to Member A. Member C supplies the Messaging destination and page content. No JavaScript or images.

## Implementation order and test evidence

1. **Done:** Active model tests cover unread defaults, first-open persistence, repeated opens, old-database migration, recipient isolation, case-insensitive matching, and unknown IDs. The read-state migration and ownership-scoped API are implemented. Terminal message tests still pass.
2. **Done:** HTTP tests exercise inbox, detail, compose, empty state, and escaping through `MessageHtmlView`. It currently has minimal semantic HTML and messaging navigation; A will add the final shared navigation/CSS.
3. **Done:** HTTP tests cover login guard, standard-user access, recipient dropdown, send validation, forged IDs, ownership isolation, and POST/redirect/GET. The handler and `TopSecret` are wired.
4. **Partly done:** `./gradlew clean check jar` passed on November 14, including the 100% method-coverage gate. A browser system test with two real accounts remains: an agent-matching recipient and another user. Verify send, unread, open, read state after restart, and denied cross-user access.
5. **Remaining:** Update the final class diagram, ERD, wireframe, and user documentation to reflect actual behavior. This plan records the current handoff; B's existing proposed editing supplements must still be reconciled with the final team diagrams.

### Commit sequence

- `f285d3b` — tests for read state and web messaging.
- `7b8523e` — message read-state migration and ownership-scoped model API.
- `2fc1277` — web handler, pages, menu entry, and application wiring.

## Team handoff

- **Member A:** Add Messaging to the menu on every signed-in page, using `/messages`. Provide CSS for inbox status, message detail, compose form, and errors. Member C can supply minimal semantic HTML first. The supplied `countries.txt` belongs to A's country import work; it is currently at the project root.
- **Member B:** Reserve the `/messages` path family. Share bounded URL-encoded form parsing, CSRF handling, and common navigation/rendering helpers where practical. Coordinate the small `HttpWebServer` and `TopSecret` integration edits. Message sending is allowed for standard users even though record editing is admin-only.
- **Member D:** Messaging uses the existing `agents` table and adds read state only to the existing `messages` table. D's 30-minute session timeout and revocation must apply to messaging routes through the server's shared authentication gate. No message audit event is required.

No agreement from A, B, or D is assumed by this document. Member C selected the documented defaults and implemented them so the others have a concrete integration contract.
