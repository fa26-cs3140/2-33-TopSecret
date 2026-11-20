package controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpWebServerLifecycleSqliteTest {
    @TempDir Path temporary;

    @Test
    void repeatedCloseDoesNotReopenAnAlreadyReleasedDatabase() throws Exception {
        Path database = temporary.resolve("sessions.db");
        SqliteWebSessionStore sessions = new SqliteWebSessionStore(database);
        sessions.initialize();
        HttpWebServer server = server(sessions);
        server.start();
        server.close();
        Files.delete(database);

        assertDoesNotThrow(server::close, "A later shutdown hook must not access disposed storage");
        assertFalse(Files.exists(database), "Repeated close must not recreate the database");
    }

    @Test
    void anOldShutdownHookDoesNotRevokeSessionsCreatedByAnotherServer() throws Exception {
        Path database = temporary.resolve("sessions.db");
        SqliteWebSessionStore sessions = new SqliteWebSessionStore(database);
        sessions.initialize();
        HttpWebServer oldServer = server(sessions);
        oldServer.start();
        String oldToken = sessions.create("Ghost");
        oldServer.close();
        assertEquals(Optional.empty(), sessions.usernameFor(oldToken));

        SqliteWebSessionStore nextSessions = new SqliteWebSessionStore(database);
        nextSessions.initialize();
        try (HttpWebServer nextServer = server(nextSessions)) {
            nextServer.start();
            String newToken = nextSessions.create("Ghost");
            oldServer.close();
            assertEquals(Optional.of("Ghost"), nextSessions.usernameFor(newToken));
        }
    }

    private HttpWebServer server(SqliteWebSessionStore sessions) throws java.io.IOException {
        WebAuthentication auth = mock(WebAuthentication.class);
        when(auth.hasAccounts()).thenReturn(true);
        return new HttpWebServer(auth, sessions, mock(ContentController.class),
                new InetSocketAddress("localhost", 0));
    }
}
