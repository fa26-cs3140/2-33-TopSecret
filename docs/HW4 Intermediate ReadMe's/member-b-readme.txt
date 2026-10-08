MEMBER B - LOGIN HANDOFF

After login succeeds, send the user to the interactive menu. Search should not
be available before a successful login.

Mission reports stay as plain text in SQLite. Credentials remain separate and
must not be stored in the mission database.

The search feature does not need to know usernames, passwords, or credential
file details.

Implementation completed:

- AuthenticationService validates credential rules and verifies login.
- CredentialStore is the interface between authentication and storage.
- FileCredentialStore stores credentials in data/credentials.cip.
- The file contains two ciphered lines: username, then password.
- The file is ciphered with ciphers/key.txt using the existing substitution key.
- TopSecret handles first-run setup, normal login, and --change-password.

Operation:

- First run with no data/credentials.cip prompts for a new username/password,
  writes the file, and exits.
- Normal run validates the credential file, prompts for username/password, then
  starts UserInterface.runMenu only after successful authentication.
- --change-password prompts for username, current password, new password, and a
  repeated confirmation before overwriting the credential file.

Tests:

- AuthenticationServiceTest covers username/password validation, login, and
  password changes.
- FileCredentialStoreTest covers ciphered file storage and deciphered reads.
- TopSecretTest covers first-run setup, login gating, and password-change
  integration with the entry point.
