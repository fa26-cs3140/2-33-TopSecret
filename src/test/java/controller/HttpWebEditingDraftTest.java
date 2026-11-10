package controller;

import model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Disabled("Homework 7 Week 2: wire editing routes, HTML forms and CSRF before enabling")
class HttpWebEditingDraftTest {
    private final ContentService service = mock(ContentService.class);
    private final AuthenticationService accounts = mock(AuthenticationService.class);
    private final Session admin = new Session("admin", Role.ADMIN);
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
    private HttpWebServer server;
    private String cookie;

    @BeforeEach
    void startServer() throws Exception {
        WebAuthentication login = mock(WebAuthentication.class);
        when(login.hasAccounts()).thenReturn(true);
        when(accounts.sessionFor("admin")).thenReturn(admin);
        InMemoryWebSessionStore sessions = new InMemoryWebSessionStore();
        cookie = WebServerController.SESSION_COOKIE + "=" + sessions.create("admin");
        server = new HttpWebServer(login, sessions, new ContentController(service, accounts),
                new InetSocketAddress("localhost", 0));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void administratorSeesAnEmptyNewFormForEveryCurrentType() throws Exception {
        for (ContentType type : ContentType.values()) {
            HttpResponse<String> response = request("GET", "/new?type=" + type.table, null, cookie);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("action=\"/create\""));
            assertTrue(response.body().contains("method=\"post\""));
            for (String field : type.fields) {
                assertTrue(response.body().contains("name=\"" + field + "\""));
            }
            assertFalse(csrf(response.body()).isBlank());
        }
        verify(service, never()).read(any(), anyInt(), any());
        assertNoWrites();
    }

    @Test
    void editFormShowsCurrentEscapedValuesAndAStableRecordId() throws Exception {
        when(service.read(ContentType.FACILITY, 7, admin))
                .thenReturn(new ContentEntry(7, List.of("<script>alert('x')</script>", "NO")));
        HttpResponse<String> response = request("GET", "/edit?type=facilities&id=7", null, cookie);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("action=\"/update\""));
        assertTrue(response.body().contains("&lt;script&gt;"));
        assertFalse(response.body().contains("<script>"));
        assertTrue(response.body().contains("value=\"NO\""));
        assertTrue(response.body().contains("name=\"id\" value=\"7\""));
        assertNoWrites();
    }

    @Test
    void allEditingRoutesRequireLoginAndTheCurrentAdminRole() throws Exception {
        for (String path : List.of("/new?type=facilities", "/edit?type=facilities&id=7", "/create", "/update")) {
            String method = path.equals("/create") || path.equals("/update") ? "POST" : "GET";
            HttpResponse<String> anonymous = request(method, path, "type=facilities", null);
            assertEquals(303, anonymous.statusCode());
            assertEquals("/", anonymous.headers().firstValue("Location").orElseThrow());
            when(accounts.sessionFor("admin")).thenReturn(new Session("admin", Role.STANDARD));
            assertEquals(403, request(method, path, "type=facilities", cookie).statusCode());
        }
        verifyNoInteractions(service);
    }

    @Test
    void createRedirectsAfterExactlyOneModelWrite() throws Exception {
        String token = csrf(request("GET", "/new?type=facilities", null, cookie).body());
        HttpResponse<String> response = request("POST", "/create",
                "type=facilities&name=North&abbreviation=NO&csrf=" + encode(token), cookie);
        assertEquals(303, response.statusCode());
        assertEquals("/content?type=facilities", response.headers().firstValue("Location").orElseThrow());
        verify(service).add(ContentType.FACILITY, List.of("North", "NO"), admin);
        verify(service, never()).update(any(), anyInt(), anyList(), any());
    }

    @Test
    void updateRedirectsToTheSameRecordAfterSaving() throws Exception {
        when(service.read(ContentType.FACILITY, 7, admin))
                .thenReturn(new ContentEntry(7, List.of("North", "NO")));
        String token = csrf(request("GET", "/edit?type=facilities&id=7", null, cookie).body());
        HttpResponse<String> response = request("POST", "/update",
                "type=facilities&id=7&name=South&abbreviation=SO&csrf=" + encode(token), cookie);
        assertEquals(303, response.statusCode());
        assertEquals("/record?type=facilities&id=7", response.headers().firstValue("Location").orElseThrow());
        verify(service).update(ContentType.FACILITY, 7, List.of("South", "SO"), admin);
        verify(service, never()).add(any(), anyList(), any());
    }

    @Test
    void invalidCsrfAndMalformedFieldsCannotMutateRecords() throws Exception {
        String token = csrf(request("GET", "/new?type=facilities", null, cookie).body());
        for (String body : List.of(
                "type=facilities&name=North&abbreviation=NO",
                "type=facilities&name=North&abbreviation=NO&csrf=forged",
                "type=facilities&name=North&name=South&abbreviation=NO&csrf=" + encode(token),
                "type=facilities&name=North&abbreviation=NO&unexpected=yes&csrf=" + encode(token))) {
            assertEquals(400, request("POST", "/create", body, cookie).statusCode());
        }
        assertNoWrites();
    }

    @Test
    void accountTypesAreNeverEditableAndGetCannotSave() throws Exception {
        assertEquals(400, request("GET", "/new?type=users", null, cookie).statusCode());
        HttpResponse<String> response = request("GET", "/create?type=facilities", null, cookie);
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
        assertNoWrites();
    }

    private void assertNoWrites() throws Exception {
        verify(service, never()).add(any(), anyList(), any());
        verify(service, never()).update(any(), anyInt(), anyList(), any());
        verify(service, never()).addMission(anyList(), anyInt(), anyInt(), any());
        verify(service, never()).delete(any(), anyInt(), any());
    }

    private static String csrf(String html) {
        Matcher input = Pattern.compile("<input\\b[^>]*name=\"csrf\"[^>]*>").matcher(html);
        assertTrue(input.find(), "Editing form must carry a CSRF token");
        Matcher value = Pattern.compile("value=\"([^\"]+)\"").matcher(input.group());
        assertTrue(value.find(), "CSRF token must have a value");
        return value.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> request(String method, String path, String body, String sessionCookie)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.address().getPort() + path))
                .timeout(Duration.ofSeconds(3));
        if (sessionCookie != null) {
            request.header("Cookie", sessionCookie);
        }
        if (method.equals("POST")) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
