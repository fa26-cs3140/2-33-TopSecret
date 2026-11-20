# Homework 7 Member C - agent-assisted messaging system test

Date: November 19, 2026. Source revision: `6337195` plus the uncommitted Member C shared-form-parser change. This is an agent-run browser test against the built executable JAR, not a claim that Rohan personally completed the assignment's per-member system-test requirement.

The JAR and four seed files were copied to an isolated temporary directory. The test created three disposable accounts there: administrator `TestAdmin`, standard user `Ghost` (matching a seeded agent), and standard user `Observer` (matching no agent). No repository database or credential file was used. The local web server bound to port 3002 and was stopped after the test. Test passwords and session cookies are not recorded here.

## Browser observations

1. `TestAdmin` signed in, opened Messaging, and saw an empty personal inbox. The New message form listed the seeded agents, including Ghost. Sending to Ghost returned to the inbox; it did not reveal Ghost's mail to the sender.
2. `Ghost` signed in with a standard account. The inbox showed the new message from TestAdmin as **Unread**. Open displayed the sender, recipient, date and full body. Returning to the inbox showed **Read**.
3. `Observer` signed in with a standard account. Its inbox was empty. Opening Ghost's known detail URL (`/messages/view?id=1`) displayed **Message not found**, with the shared menu and log out still available.
4. `Observer` could open the New message form and send a second message to Ghost, confirming that sending does not require administrator permission.
5. The web server was stopped and restarted using the same isolated database. The previous browser session returned to login. After `Ghost` signed in again, the first message was still **Read** and the second was **Unread**. This confirms both persisted read state and new-message default state across a restart.

All five observations passed. The focused form-regression test also reproduced the old oversized-form behavior before the parser change; after the change it passed with 413 for oversized messaging forms and 400 for malformed UTF-8. The full automated suite and coverage gate are recorded separately when run.

## Still required

Rohan should personally run and record a system test before submission, as required for each team member. Member D integration is now checked by the November 20 automated tests below. The personal checklist includes terminal revocation and inactivity.

## November 20 integrated automated verification

Source: `8e3b881` on `hw7-member-c`, incorporating main's Member D merge `383ddc2`.
Command: `./gradlew clean check jar`. Result: 715 tests, zero failures/errors/skips;
100% method-coverage gate passed and executable JAR built. This is automated
evidence, not Rohan's personal system-test signoff.

`HttpWebMessagingSessionsSqliteTest` uses a real HTTP server, ciphered temporary
credentials, content/message SQLite tables and `SqliteWebSessionStore`:

- Standard-user compose/send and repeated message opening work while requests
  spaced 25 minutes apart refresh the session (a controllable test clock is used).
- More than 30 idle minutes blocks all message GETs and send/open POSTs before
  reads or mutations. Re-login requires a fresh CSRF token; old forms fail.
- An administrator using a separate session-store instance revokes Ghost's full
  token. Ghost's messaging requests return to login without saving or marking read;
  Observer's session remains active and cannot read Ghost's message.
- Server stop/restart invalidates the old cookie while first-read time and the
  unread status of new mail survive. Repeated opens preserve the first timestamp.

The two `HttpWebServerLifecycleSqliteTest` regressions failed before `8e3b881` and
pass afterward. A second close no longer accesses deleted storage or removes a
newer server's sessions. The previous shutdown-hook exception is absent from the
full test run.

## Rohan's personal manual system test - not yet signed off

Use the steps below yourself and record the result. Keep real passwords and full
session tokens out of this document and screenshots.

### 1. Build and prepare disposable storage

From the project root, with port 3002 free:

```sh
./gradlew clean check jar
mkdir -p build/hw7-manual
java -Dtopsecret.database=build/hw7-manual/missions.db -Dtopsecret.credentials=build/hw7-manual/credentials.cip -jar build/libs/TopSecret.jar
```

Use this isolated folder for the entire test. Do not run `clean` again during the
test, because it removes build/ including this test data. The project seed files
are read from the project root. If reusing an existing test folder, sign into its
existing accounts rather than expecting the initial-account prompt.

On a fresh test database, create the initial administrator `TestAdmin`, choosing
your own valid password. Use terminal option 6 to create standard accounts `Ghost`
and `Observer`, then option 4 to exit. Run the same command once for each standard
account, choose its initial password and confirmation, then exit. Ghost matches a
seeded agent; Observer does not. Passwords need at least five characters, including
uppercase and lowercase letters.

### 2. Start the browser application

```sh
java -Dtopsecret.database=build/hw7-manual/missions.db -Dtopsecret.credentials=build/hw7-manual/credentials.cip -jar build/libs/TopSecret.jar -web
```

Open http://localhost:3002/ and sign in as TestAdmin. Visit one content list and
follow a related-record link to confirm the integrated navigation is available.
Open Messaging, choose New message, select Ghost and send a unique test message.
Confirm the send returns to TestAdmin's personal inbox. Log out.

### 3. Read, send and check privacy as standard users

1. Sign in as Ghost. Confirm Messaging appears and the message is Unread.
2. Select Open; confirm sender, recipient and full body. Return to the inbox and
   confirm Read. Refresh detail and confirm it still displays correctly.
3. Copy the detail URL for this disposable message. Log out and sign in as Observer.
   The inbox must be empty. Visit the copied URL: expect Message not found.
4. As Observer, choose New message and send another message to Ghost. This must
   work without administrator permission. Try blank text and confirm no blank mail
   is created. Log out and sign in as Ghost; the second message must be Unread.
5. Open Ghost's compose form and leave it open for the next step.

### 4. Revoke a browser session from the terminal

Open a second terminal at the same project root. Run the terminal command from
step 1 using the same test paths. Sign in as TestAdmin and choose option 14.
Confirm Ghost's username, full issued token, creation time and last activity appear.
Enter Ghost's full token to revoke it; do not save that token in your test record.

In the browser, submit the previously opened compose form. Expect the login page,
with no message saved. Sign in as Ghost again and confirm the inbox is unchanged.
Reload compose to obtain a fresh form; sending should work again. Exit the admin
terminal without stopping the separate web server.

### 5. Check inactivity and restart persistence

For the manual inactivity check, stay signed in but make no requests to the app for
31 minutes, then visit Messaging. Expect login. Do not refresh or use another tab
sharing the same login during the wait. The automated test already checks activity
refresh without a real-time wait; only record this manual step as passed if you
actually perform it.

After signing back in, leave one message Read and another Unread. Stop the web
server with Ctrl+C and restart it using step 2's command. Visit Messaging and log
in again. Confirm the read/unread states survived. Log out, try the inbox URL and
confirm access returns to login. Stop the web server and exit any test terminals.

### 6. Record the result before submission

- Tester: Rohan
- Date/time: pending
- Commit tested: pending (run `git rev-parse --short HEAD`)
- Send/read/unread/privacy checks: pending
- Standard-user send and input validation: pending
- Terminal revocation and re-login: pending
- Manual inactivity check: pending
- Restart persistence and logout: pending
- Problems found and any retest: pending
- Overall personal system-test signoff: pending
