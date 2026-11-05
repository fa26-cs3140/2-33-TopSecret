package controller;

import model.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpWebServerTest {
    private final WebAuthentication authentication = mock(WebAuthentication.class);
    private final ContentController content = mock(ContentController.class);
    private final InMemoryWebSessionStore sessions = new InMemoryWebSessionStore();
    private final Session admin = new Session("agent", Role.ADMIN);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpWebServer server;

    @BeforeEach
    void start() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        when(authentication.authenticate("agent", "Secret1")).thenReturn(Optional.of("agent"));
        when(content.sessionFor("agent")).thenReturn(admin);
        when(content.canViewAudit(admin)).thenReturn(true);
        server = new HttpWebServer(authentication, sessions, content, new InetSocketAddress("localhost", 0));
        assertEquals(0, server.address().getPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void everyProtectedRouteGuardsAccessBeforeReadingTheModel() throws Exception {
        for (String path : List.of("/menu", "/content?type=briefs", "/record?type=briefs&id=1", "/audit", "/logout", "/unknown")) {
            assertEquals(303, request("GET", path, null, null, null).statusCode());
            assertEquals(303, request("GET", path, "TopSecretSession=forged", null, null).statusCode());
        }
        verifyNoInteractions(content);
    }

    @Test
    void listsAndDetailsUseTheSharedControllerAndEscapeContent() throws Exception {
        String cookie = login();
        ContentEntry entry = new ContentEntry(1, List.of("<script>&\"'", "2026-11-05", "Secret\ntext"));
        when(content.list(ContentType.BRIEF, admin)).thenReturn(List.of(entry));
        when(content.read(ContentType.BRIEF, 1, admin)).thenReturn(entry);
        HttpResponse<String> list = request("GET", "/content?type=briefs", cookie, null, null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("/record?type=briefs&amp;id=1"));
        assertTrue(list.body().contains("&lt;script&gt;&amp;&quot;&#39;"));
        assertFalse(list.body().contains("<script>"));
        HttpResponse<String> record = request("GET", "/record?type=briefs&id=1", cookie, null, null);
        assertEquals(200, record.statusCode());
        assertTrue(record.body().contains("<th scope=\"row\">text</th><td>Secret\ntext</td>"));
        verify(content).list(ContentType.BRIEF, admin);
        verify(content).read(ContentType.BRIEF, 1, admin);
        assertEquals("no-store", record.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(200, request("GET", "/content?type=agents", cookie, null, null).statusCode());
        assertEquals(200, request("GET", "/content?type=facilities", cookie, null, null).statusCode());
        assertEquals(200, request("GET", "/content?type=missions", cookie, null, null).statusCode());
    }

    @Test
    void missionDetailShowsItsRelationshipsWithoutEditingControls() throws Exception {
        String cookie = login();
        when(content.read(ContentType.MISSION, 7, admin)).thenReturn(
                new ContentEntry(7, List.of("Nightfall", "Mission text", "2026-11-05")));
        when(content.facilityFor(7, admin)).thenReturn(Optional.of(
                new ContentEntry(2, List.of("North Office", "NO"))));
        when(content.agentsFor(7, admin)).thenReturn(List.of(
                new ContentEntry(4, List.of("Ghost", "1992-11-22", "", "", "2"))));
        when(content.briefsFor(7, admin)).thenReturn(List.of(
                new ContentEntry(9, List.of("<Signal>", "2026-11-04", "Details"))));

        HttpResponse<String> response = request("GET", "/record?type=missions&id=7", cookie, null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<th scope=\"row\">facility</th>"));
        assertTrue(response.body().contains("<th scope=\"row\">agents</th>"));
        assertTrue(response.body().contains("<th scope=\"row\">briefs</th>"));
        assertTrue(response.body().contains("2. North Office"));
        assertTrue(response.body().contains("4. Ghost"));
        assertTrue(response.body().contains("9. &lt;Signal&gt;"));
        assertFalse(response.body().contains("<input"));
        assertFalse(response.body().contains("contenteditable"));
        verify(content).facilityFor(7, admin);
        verify(content).agentsFor(7, admin);
        verify(content).briefsFor(7, admin);
    }

    @Test
    void auditUsesCurrentRoleForEveryRequest() throws Exception {
        String cookie = login();
        assertEquals(200, request("GET", "/audit", cookie, null, null).statusCode());
        when(content.canViewAudit(admin)).thenReturn(false);
        assertEquals(403, request("GET", "/audit", cookie, null, null).statusCode());
        String menu = request("GET", "/menu", cookie, null, null).body();
        assertFalse(menu.contains("href=\"/audit\""));
        verify(content, times(3)).sessionFor("agent");
        verify(content, times(1)).audit(admin);
    }

    @Test
    void successfulLoginAndLogoutRotateAndInvalidateTokens() throws Exception {
        String first = login();
        String second = request("POST", "/login", first, "username=agent&password=Secret1", "application/x-www-form-urlencoded")
                .headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        assertNotEquals(first, second);
        assertEquals(303, request("GET", "/menu", first, null, null).statusCode());
        assertEquals("/menu", request("GET", "/", second, null, null).headers().firstValue("Location").orElseThrow());
        HttpResponse<String> logout = request("POST", "/logout", second, "", null);
        assertEquals(303, logout.statusCode());
        assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertEquals(303, request("GET", "/menu", second, null, null).statusCode());
    }

    @Test
    void closingAndRestartingInvalidatesSessionsAndReleasesPort() throws Exception {
        String cookie = login();
        assertThrows(IOException.class, server::start);
        server.close();
        server.close();
        server.start();
        assertEquals(303, request("GET", "/menu", cookie, null, null).statusCode());
    }

    @Test
    void malformedFormsAndQueriesAreRejectedWithoutModelAccess() throws Exception {
        String cookie = login();
        for (String form : List.of("", "username=agent", "username=agent&password=x&password=y", "username=%ZZ&password=x", "username&password=x")) {
            assertEquals(400, request("POST", "/login", null, form, "application/x-www-form-urlencoded").statusCode(), form);
        }
        assertEquals(400, request("POST", "/login", null, "{}", "application/json").statusCode());
        assertEquals(400, request("POST", "/login", null, "", null).statusCode());
        assertEquals(413, request("POST", "/login", null, "x".repeat(8193), "application/x-www-form-urlencoded").statusCode());
        for (String path : List.of("/content", "/content?type=invalid", "/record?type=briefs", "/record?type=briefs&id=0",
                "/record?type=briefs&id=abc", "/content?type=briefs&type=agents", "/content?type")) {
            assertEquals(400, request("GET", path, cookie, null, null).statusCode(), path);
        }
        verify(content, never()).list(any(), any());
        verify(content, never()).read(any(), anyInt(), any());
    }

    @Test
    void methodsAndUnknownPagesReturnExplicitErrors() throws Exception {
        String cookie = login();
        for (String path : List.of("/", "/menu", "/content", "/record", "/audit")) {
            HttpResponse<String> response = request("POST", path, cookie, "action=add", null);
            assertEquals(405, response.statusCode());
            assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        }
        for (String path : List.of("/login", "/logout")) {
            assertEquals(405, request("GET", path, cookie, null, null).statusCode());
        }
        assertEquals(404, request("GET", "/menu/extra", cookie, null, null).statusCode());
        assertEquals(404, request("GET", "/unknown", cookie, null, null).statusCode());
    }

    @Test
    void backendFailuresDoNotLeakDetailsOrStopTheServer() throws Exception {
        String cookie = login();
        when(content.list(ContentType.BRIEF, admin)).thenThrow(new ProgramControlException("private database path"));
        HttpResponse<String> failure = request("GET", "/content?type=briefs", cookie, null, null);
        assertEquals(500, failure.statusCode());
        assertFalse(failure.body().contains("private database path"));
        when(authentication.authenticate("agent", "Secret1")).thenThrow(new IOException("password storage"));
        assertEquals(500, request("POST", "/login", null, "username=agent&password=Secret1", "application/x-www-form-urlencoded").statusCode());
        when(content.sessionFor("agent")).thenThrow(new CredentialException("account removed"));
        assertEquals(500, request("GET", "/menu", cookie, null, null).statusCode());
        assertEquals(200, request("GET", "/", null, null, null).statusCode());
    }

    @Test
    void encodedCredentialsAndIndependentClientsAreSupported() throws Exception {
        when(authentication.authenticate("agent", "Secret+&é")).thenReturn(Optional.of("agent"));
        HttpResponse<String> response = request("POST", "/login", null, "username=agent&password=Secret%2B%26%C3%A9", "application/x-www-form-urlencoded; charset=UTF-8");
        assertEquals(303, response.statusCode());
        String first = login();
        String second = login();
        assertNotEquals(first, second);
        assertEquals(200, request("GET", "/menu", "unrelated=x; " + first, null, null).statusCode());
        assertEquals(303, request("GET", "/menu", first + "; " + second, null, null).statusCode());
        request("POST", "/logout", first, "", null);
        assertEquals(200, request("GET", "/menu", second, null, null).statusCode());
    }

    @Test
    void storageFailureAndOccupiedPortPreventStartup() throws Exception {
        try (HttpWebServer other = new HttpWebServer(authentication, sessions, content, server.address())) {
            assertThrows(IOException.class, other::start);
        }
        when(authentication.hasAccounts()).thenThrow(new IOException("corrupt credentials"));
        try (HttpWebServer other = new HttpWebServer(authentication, sessions, content, new InetSocketAddress("localhost", 0))) {
            assertThrows(IOException.class, other::start);
            assertEquals(0, other.address().getPort());
        }
    }

    private String login() throws Exception {
        HttpResponse<String> response = request("POST", "/login", null, "username=agent&password=Secret1", "application/x-www-form-urlencoded");
        assertEquals(303, response.statusCode());
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> request(String method, String path, String cookie, String body, String type) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.address().getPort() + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (type != null) {
            request.header("Content-Type", type);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
