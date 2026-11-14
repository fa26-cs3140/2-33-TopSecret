package controller;

import model.Message;
import model.MessageService;
import model.Role;
import model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebMessageHandlerTest {
    private final WebAuthentication authentication = mock(WebAuthentication.class);
    private final ContentController content = mock(ContentController.class);
    private final MessageService messages = mock(MessageService.class);
    private final Session ghost = new Session("Ghost", Role.STANDARD);
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpWebServer server;

    @BeforeEach
    void start() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        when(authentication.authenticate("Ghost", "Secret1")).thenReturn(Optional.of("Ghost"));
        when(content.sessionFor("Ghost")).thenReturn(ghost);
        server = new HttpWebServer(authentication, new InMemoryWebSessionStore(), content,
                new InetSocketAddress("localhost", 0), messages);
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void menuInboxAndComposeWorkForStandardUserAndEscapeNames() throws Exception {
        String cookie = login();
        Message mail = mail(null);
        when(messages.inbox(ghost)).thenReturn(List.of(mail));
        when(messages.recipients(ghost)).thenReturn(Map.of(2, "<Ghost>"));
        assertTrue(request("GET", "/menu", cookie, null).body().contains("href=\"/messages\""));
        HttpResponse<String> inbox = request("GET", "/messages", cookie, null);
        assertEquals(200, inbox.statusCode());
        assertTrue(inbox.body().contains("Unread"));
        assertTrue(inbox.body().contains("&lt;admin&gt;"));
        assertFalse(inbox.body().contains("<admin>"));
        HttpResponse<String> compose = request("GET", "/messages/new", cookie, null);
        assertTrue(compose.body().contains("&lt;Ghost&gt;"));
        assertTrue(compose.body().contains("<textarea"));
        assertTrue(compose.body().contains("name=\"csrf\""));
    }

    @Test
    void sendRequiresValidFormAndCsrfThenRedirects() throws Exception {
        String cookie = login();
        when(messages.recipients(ghost)).thenReturn(Map.of(2, "Ghost"));
        String csrf = csrf(request("GET", "/messages/new", cookie, null).body());
        assertEquals(400, request("POST", "/messages/send", cookie,
                "agentId=2&body=hello&csrf=wrong").statusCode());
        assertEquals(400, request("POST", "/messages/send", cookie,
                "agentId=9&body=hello&csrf=" + csrf).statusCode());
        assertEquals(400, request("POST", "/messages/send", cookie,
                "agentId=2&body=++&csrf=" + csrf).statusCode());
        assertEquals(400, request("POST", "/messages/send", cookie,
                "agentId=2&body=x&body=y&csrf=" + csrf).statusCode());
        HttpResponse<String> sent = request("POST", "/messages/send", cookie,
                "agentId=2&body=Meet+at+dawn&csrf=" + csrf);
        assertEquals(303, sent.statusCode());
        assertEquals("/messages", sent.headers().firstValue("Location").orElseThrow());
        verify(messages).send(2, "Meet at dawn", ghost);
    }

    @Test
    void openingChecksOwnershipAndDetailDoesNotMarkReadAgain() throws Exception {
        String cookie = login();
        when(messages.inbox(ghost)).thenReturn(List.of(mail(null)));
        String csrf = csrf(request("GET", "/messages", cookie, null).body());
        when(messages.markRead(7, ghost)).thenReturn(true);
        when(messages.message(7, ghost)).thenReturn(Optional.of(mail(LocalDateTime.now())));
        HttpResponse<String> opened = request("POST", "/messages/open", cookie, "id=7&csrf=" + csrf);
        assertEquals(303, opened.statusCode());
        assertEquals("/messages/view?id=7", opened.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> detail = request("GET", "/messages/view?id=7", cookie, null);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("&lt;secret&gt;"));
        assertFalse(detail.body().contains("<secret>"));
        verify(messages, times(1)).markRead(7, ghost);
        assertEquals(404, request("POST", "/messages/open", cookie, "id=8&csrf=" + csrf).statusCode());
        assertEquals(404, request("GET", "/messages/view?id=8", cookie, null).statusCode());
    }

    @Test
    void missingSessionsWrongMethodsAndBadIdsAreRejected() throws Exception {
        assertEquals(303, request("GET", "/messages", null, null).statusCode());
        assertEquals(303, request("POST", "/messages/open", null, "id=7").statusCode());
        String cookie = login();
        assertEquals(405, request("POST", "/messages", cookie, "").statusCode());
        assertEquals(405, request("GET", "/messages/open", cookie, null).statusCode());
        assertEquals(404, request("GET", "/messages/unknown", cookie, null).statusCode());
        assertEquals(400, request("GET", "/messages/view?id=x", cookie, null).statusCode());
        assertEquals(400, request("GET", "/messages/view", cookie, null).statusCode());
        assertEquals(400, request("POST", "/messages/open", cookie, "id=7&csrf=bad").statusCode());
        verifyNoInteractions(messages);
    }

    @Test
    void emptyInboxAndRecipientsHaveReadablePages() throws Exception {
        String cookie = login();
        assertTrue(request("GET", "/messages", cookie, null).body().contains("No messages available"));
        assertTrue(request("GET", "/messages/new", cookie, null).body().contains("No agents available"));
    }

    private Message mail(LocalDateTime readAt) {
        return new Message(7, 2, "Ghost", "<admin>", "<secret>",
                LocalDateTime.of(2026, 11, 14, 12, 0), readAt);
    }

    private String csrf(String html) {
        var matcher = Pattern.compile("name=\\\"csrf\\\" value=\\\"([^\\\"]+)\\\"").matcher(html);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private String login() throws Exception {
        return request("POST", "/login", null, "username=Ghost&password=Secret1")
                .headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> request(String method, String path, String cookie, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.address().getPort() + path));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (body != null) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
