package controller;

import java.io.IOException;
import java.util.Optional;

public interface WebAuthentication {
    boolean hasAccounts() throws IOException;

    Optional<String> authenticate(String username, String password) throws IOException;
}
