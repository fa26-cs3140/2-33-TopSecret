# Homework 7 - Member C delivery and integration

Status as of November 20, 2026: web messaging is implemented and integrated with
Members A, B and D. Member D PR #7 and Member C PR #8 are merged into main.
The final documentation describes the shared implementation. Rohan's personal
system-test signoff and course submission remain; the Member C merge is complete.

## Design decisions and implementation

- **Opening:** POST `/messages/open` checks ownership and CSRF, marks the message
  read once, and redirects to GET detail. Refreshing detail has no write effect.
- **Read state:** nullable `messages.read_at` stores the first web-open timestamp.
  Existing databases gain the column without losing messages. NULL means unread;
  repeated opens keep the first timestamp. Terminal reading leaves it unchanged.
- **Separation:** `HttpWebServer` owns shared authentication and navigation;
  `WebMessageHandler` owns messaging flow and `MessageHtmlView` renders through
  `HtmlView.shell`. The existing model/service also serves terminal messages.
- **Shared parsing:** login, editing and messaging use `WebFormData`, including
  the 8192-byte limit, strict UTF-8 and duplicate-field rejection.
- **Permissions:** every signed-in role may send. Inbox/detail/open join messages
  to agents and compare the current username to the addressee name ignoring case.
  Administrators have no extra inbox access. Missing and foreign IDs both return
  not found. Message sending and reading are excluded from the audit log.
- **Forms:** the recipient dropdown comes from current agents. Body must be nonblank
  and at most 4096 characters. Sender identity comes from the session. All dynamic
  page content is escaped; send/open use a session-bound CSRF token.

## Routes

- GET `/messages`: personal inbox, newest first, Read/Unread and Open buttons.
- GET `/messages/new`: agent dropdown and message textarea for every signed-in role.
- POST `/messages/send`: validate and save, then redirect to the sender's inbox.
- POST `/messages/open`: check ownership, mark read once, redirect to detail.
- GET `/messages/view?id=...`: read-only detail of an owned message.

`MessageService` exposes recipients, send, inbox, message and markRead.
`SqliteMessageService` uses parameterized SQL and performs the read-state migration.
`Message` carries the nullable read time while retaining its original constructor.
Reply/delete and message audit events are outside the assignment's requirements.

## Completed team integration

- A's CSS/navigation applies to all message pages, including errors.
- B's form decoder is shared; content edits remain admin-only while messaging
  remains available to standard users.
- D's SQLite session store applies its inactivity timeout and terminal revocation
  before messaging handlers run. Revoked/expired requests cannot send or mark read.
  A new login requires fresh CSRF forms. Terminal session administration now lists
  the full issued token required by the assignment, rather than an abbreviated ID.
- Repeated shutdown no longer reopens released storage or clears newer sessions.
- The final class diagram, ERD and wireframes cover the integrated team system.
  Generate them with `node docs/render-hw7-docs.cjs`.

## Test evidence and commit sequence

- `f285d3b`: original read-state/web-messaging tests.
- `7b8523e`: read-state migration and ownership-scoped model API.
- `2fc1277`: web handler, pages, menu and startup wiring.
- `5b62b46`, `612f919`: failing form regression, then shared-parser implementation.
- `72aec3e`: initial messaging diagrams and browser evidence.
- `8653b3e`, `b3a837e`: token requirement regression, then correction in D's PR.
- `317b874`: integrate completed main work into Member C.
- `f74edb6`: real messaging/session integration plus failing shutdown regressions.
- `8e3b881`: repeated-shutdown fix; all 715 tests and coverage/build checks pass.

The November 19 browser check and November 20 integration evidence are recorded
in `homework7-member-c-system-test.md`. Automated tests cover session activity,
expiry, terminal revocation, standard-user send, private inboxes, first-read
persistence, old CSRF forms after re-login, and server restart.

## Remaining release work

Rohan must personally run the documented system test and record its outcome.
Member C PR #8 was merged at `018fecb`; the integrated main verification is recorded
in `homework7-member-b-week2.md`. Complete the course submission after each
teammate confirms their own system-test signoff. No missing member implementation
is assumed.
