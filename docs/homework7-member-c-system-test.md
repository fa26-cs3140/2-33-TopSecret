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

Rohan should personally run and record a system test before submission, as required for each team member. Repeat the messaging path after Member D's session timeout and revocation work lands, including an expired or revoked session on a messaging request.
