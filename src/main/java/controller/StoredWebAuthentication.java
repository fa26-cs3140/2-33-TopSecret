package controller;

import model.AuthenticationService;
import model.CredentialException;
import java.io.IOException;
import java.util.Optional;

public final class StoredWebAuthentication implements WebAuthentication {
    private final AuthenticationService authentication;

    public StoredWebAuthentication(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean hasAccounts() throws IOException {
        try {
            if (!authentication.hasAccounts()) {
                return false;
            }
            authentication.verifyStoredCredentialsAreValid();
            return true;
        } catch (CredentialException exception) {
            throw new IOException("Cannot validate stored accounts.", exception);
        }
    }

    @Override
    public Optional<String> authenticate(String username, String password) throws IOException {
        try {
            return authentication.authenticate(username, password) ? Optional.of(username) : Optional.empty();
        } catch (CredentialException exception) {
            throw new IOException("Cannot authenticate account.", exception);
        }
    }
}
