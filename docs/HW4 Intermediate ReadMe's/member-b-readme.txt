MEMBER B - LOGIN HANDOFF

After login succeeds, send the user to the interactive menu. Search should not
be available before a successful login.

Mission reports stay as plain text in SQLite. Credentials remain separate and
must not be stored in the mission database.

The search feature does not need to know usernames, passwords, or credential
file details.
