package model;

import controller.ContentController;
import controller.HttpWebServer;
import controller.SqliteWebSessionStore;
import controller.StoredWebAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Member C messaging exercised with real credentials, content, messages and D's session store. */
class HttpWebMessagingSessionsSqliteTest {
    @TempDir Path temporary;
    private final HttpClient client = HttpClient.newHttpClient();
    private final MutableClock clock = new MutableClock();
    private AuthenticationService auth;
    private SqliteContentService content;
    private SqliteMessageService messages;
    private SqliteAuditLogRepository audit;
    private SqliteWebSessionStore sessions;
    private HttpWebServer server;
    private Path database;
    private Session ghost;

    @BeforeEach
    void start() throws Exception {
        database = temporary.resolve("missions.db");
        audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        content = new SqliteContentService(database, audit, mock(FileHandler.class));
        content.initialize();
        FileHandler files = new FileHandlerImpl();
        auth = new AuthenticationService(new FileCredentialStore(temporary.resolve("credentials.cip"),
                files, new SubstitutionCipher(files), "key.txt"));
        auth.createCredentials("admin", "Secret1");
        for (String name : List.of("Ghost", "Observer")) {
            auth.createUser(name);
            auth.setInitialPassword(name, "Secret1", "Secret1");
        }
        Session admin = auth.sessionFor("admin");
        ghost = auth.sessionFor("Ghost");
        content.add(ContentType.FACILITY, List.of("Test base", "TST"), admin);
        content.add(ContentType.AGENT, List.of("Ghost", "1990-01-01", "", "", "1"), admin);
        messages = new SqliteMessageService(database);
        messages.initialize();
        messages.send(1, "Original message", admin);
        sessions = new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
        sessions.initialize();
        server = new HttpWebServer(new StoredWebAuthentication(auth), sessions,
                new ContentController(content, auth), new InetSocketAddress("localhost", 0), messages);
        server.start();
    }

    @Test
    void unreadLookupObservesAnotherSessionAndClearsAfterRead() throws Exception {
        messages.markRead(1, ghost);
        assertFalse(messages.hasUnread(ghost));
        new SqliteMessageService(database).send(1, "Delivery", auth.sessionFor("admin"));
        assertTrue(messages.hasUnread(ghost));
        assertFalse(messages.hasUnread(auth.sessionFor("Observer")));
        messages.markRead(messages.inbox(ghost).get(0).getId(), ghost);
        assertFalse(messages.hasUnread(ghost));
    }

    @Test
    void nextAuthenticatedPageShowsDeliveryOnlyToRecipient() throws Exception {
        messages.markRead(1, ghost);
        String cookie = login("Ghost");
        String observer = login("Observer");
        String label = "Messaging (new message)";
        assertFalse(request("GET", "/menu", cookie, null).body().contains(label));
        messages.send(1, "Delivery", auth.sessionFor("admin"));
        assertTrue(request("GET", "/search", cookie, null).body().contains(label));
        assertFalse(request("GET", "/search", observer, null).body().contains(label));
        messages.markRead(messages.inbox(ghost).get(0).getId(), ghost);
        assertFalse(request("GET", "/search", cookie, null).body().contains(label));
    }

    @Test
    void terminalInboxClearsNoticeOnNextMenuDisplay() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        new view.ContentMenu(content, auth, messages).run(new java.util.Scanner("10\n"),
                new java.io.PrintStream(output), ghost);
        String transcript = output.toString();
        String notice = "You have a new message.";
        assertTrue(transcript.contains(notice));
        assertTrue(transcript.indexOf(notice) < transcript.indexOf("Original message"));
        assertEquals(transcript.indexOf(notice), transcript.lastIndexOf(notice));
        assertNotNull(messages.message(1, ghost).orElseThrow().getReadAt());
    }

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void standardUserMessagingActivityRefreshesTheSessionAndReadTimeSurvivesRestart() throws Exception {
        String cookie = login("Ghost");
        clock.advance(Duration.ofMinutes(25));
        String csrf = csrf(request("GET", "/messages/new", cookie, null).body());
        clock.advance(Duration.ofMinutes(25));
        assertRedirect(request("POST", "/messages/send", cookie,
                "agentId=1&body=Sent+by+standard+user&csrf=" + csrf), "/messages");
        clock.advance(Duration.ofMinutes(25));
        assertRedirect(request("POST", "/messages/open", cookie, "id=1&csrf=" + csrf),
                "/messages/view?id=1");
        var firstRead = messages.message(1, ghost).orElseThrow().getReadAt();
        assertNotNull(firstRead);
        assertEquals("Ghost", messages.inbox(ghost).get(0).getSender());
        assertNull(messages.inbox(ghost).get(0).getReadAt());
        assertRedirect(request("POST", "/messages/open", cookie, "id=1&csrf=" + csrf),
                "/messages/view?id=1");
        assertEquals(firstRead, messages.message(1, ghost).orElseThrow().getReadAt());

        server.close();
        server.start();
        assertRedirect(request("GET", "/messages", cookie, null), "/");
        cookie = login("Ghost");
        assertEquals(200, request("GET", "/messages/view?id=1", cookie, null).statusCode());
        SqliteMessageService reopened = new SqliteMessageService(database);
        reopened.initialize();
        assertEquals(firstRead, reopened.message(1, ghost).orElseThrow().getReadAt());
        assertNull(reopened.inbox(ghost).get(0).getReadAt());
    }

    @Test
    void expiredSessionsCannotReadSendOrMarkMessagesAndOldFormsFailAfterLogin() throws Exception {
        String cookie = login("Ghost");
        String csrf = csrf(request("GET", "/messages", cookie, null).body());
        clock.advance(Duration.ofMinutes(31));
        assertMessagingBlocked(cookie, csrf);
        String freshCookie = login("Ghost");
        assertEquals(400, request("POST", "/messages/open", freshCookie,
                "id=1&csrf=" + csrf).statusCode());
        assertNull(messages.message(1, ghost).orElseThrow().getReadAt());
        String freshCsrf = csrf(request("GET", "/messages", freshCookie, null).body());
        assertRedirect(request("POST", "/messages/open", freshCookie,
                "id=1&csrf=" + freshCsrf), "/messages/view?id=1");
    }

    @Test
    void terminalRevocationBlocksAllMessagingRoutesButDoesNotEndOtherUsersSessions() throws Exception {
        String cookie = login("Ghost");
        String csrf = csrf(request("GET", "/messages", cookie, null).body());
        String observerCookie = login("Observer");
        SqliteWebSessionStore terminal = new SqliteWebSessionStore(database, clock, Duration.ofMinutes(30));
        terminal.initialize();
        SessionAdmin administration = new SessionAdmin(terminal, audit);
        Session admin = auth.sessionFor("admin");
        String token = administration.list(admin).stream().filter(s -> s.username().equals("Ghost"))
                .findFirst().orElseThrow().id();
        assertTrue(administration.revoke(token, admin));
        assertMessagingBlocked(cookie, csrf);
        assertEquals(200, request("GET", "/messages", observerCookie, null).statusCode());
        assertEquals(404, request("GET", "/messages/view?id=1", observerCookie, null).statusCode());
        String freshCookie = login("Ghost");
        assertEquals(200, request("GET", "/messages/view?id=1", freshCookie, null).statusCode());
        assertNull(messages.message(1, ghost).orElseThrow().getReadAt(), "GET detail never marks read");
    }

    private void assertMessagingBlocked(String cookie, String csrf) throws Exception {
        for (String path : List.of("/messages", "/messages/new", "/messages/view?id=1")) {
            assertRedirect(request("GET", path, cookie, null), "/");
        }
        assertRedirect(request("POST", "/messages/send", cookie,
                "agentId=1&body=Must+not+be+saved&csrf=" + csrf), "/");
        assertRedirect(request("POST", "/messages/open", cookie, "id=1&csrf=" + csrf), "/");
        assertEquals(1, messages.inbox(ghost).size());
        assertNull(messages.message(1, ghost).orElseThrow().getReadAt());
    }

    private String login(String username) throws Exception {
        var response = request("POST", "/login", null, "username=" + username + "&password=Secret1");
        assertRedirect(response, "/menu");
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private static String csrf(String html) {
        var matcher = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Expected an authenticated message form");
        return matcher.group(1);
    }

    private static void assertRedirect(HttpResponse<String> response, String path) {
        assertEquals(303, response.statusCode());
        assertEquals(path, response.headers().firstValue("Location").orElseThrow());
    }

    private HttpResponse<String> request(String method, String path, String cookie, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.address().getPort() + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (body != null) builder.header("Content-Type", "application/x-www-form-urlencoded");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
