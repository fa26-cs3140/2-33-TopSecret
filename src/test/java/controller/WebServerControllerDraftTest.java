package controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("HW6 week 1 draft: connect createServer to the week 2 HTTP implementation")
class WebServerControllerDraftTest {
    private final WebAuthentication authentication = mock(WebAuthentication.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void noAccountsPreventsServerStartup() throws Exception {
        when(authentication.hasAccounts()).thenReturn(false);
        try (WebServerController server = createServer()) {
            assertThrows(IOException.class, server::start);
        }
    }

    @Test
    void rootShowsTheLoginForm() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        try (WebServerController server = createServer()) {
            server.start();
            HttpResponse<String> response = get(server, "/", null);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
            assertTrue(response.body().contains("name=\"username\""));
            assertTrue(response.body().contains("name=\"password\""));
            assertTrue(response.body().contains("method=\"post\""));
        }
    }

    @Test
    void invalidCredentialsReturnAnErrorWithoutACookie() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        when(authentication.authenticate("agent", "wrong")).thenReturn(Optional.empty());
        try (WebServerController server = createServer()) {
            server.start();
            HttpResponse<String> response = login(server, "username=agent&password=wrong");
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Invalid username or password"));
            assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        }
    }

    @Test
    void validLoginCreatesABrowserSessionAndAllowsNavigation() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        when(authentication.authenticate("agent", "Secret1")).thenReturn(Optional.of("agent"));
        try (WebServerController server = createServer()) {
            server.start();
            HttpResponse<String> response = login(server, "username=agent&password=Secret1");
            assertEquals(303, response.statusCode());
            assertEquals("/menu", response.headers().firstValue("Location").orElseThrow());
            List<String> cookies = response.headers().allValues("Set-Cookie");
            assertEquals(1, cookies.size());
            String cookie = cookies.get(0);
            String attributes = cookie.toLowerCase(Locale.ROOT);
            assertTrue(cookie.startsWith(WebServerController.SESSION_COOKIE + "="));
            assertTrue(attributes.contains("httponly"));
            assertTrue(attributes.contains("path=/"));
            assertFalse(attributes.contains("max-age"));
            assertFalse(attributes.contains("expires="));
            assertEquals(200, get(server, "/menu", cookie.split(";", 2)[0]).statusCode());
        }
    }

    @Test
    void missingAndForgedCookiesCannotAccessTheMenu() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        try (WebServerController server = createServer()) {
            server.start();
            for (String cookie : new String[]{null, WebServerController.SESSION_COOKIE + "=forged"}) {
                HttpResponse<String> response = get(server, "/menu", cookie);
                assertEquals(303, response.statusCode());
                assertEquals("/", response.headers().firstValue("Location").orElseThrow());
            }
        }
    }

    @Test
    void webNavigationRejectsWrites() throws Exception {
        when(authentication.hasAccounts()).thenReturn(true);
        when(authentication.authenticate("agent", "Secret1")).thenReturn(Optional.of("agent"));
        try (WebServerController server = createServer()) {
            server.start();
            String cookie = login(server, "username=agent&password=Secret1")
                    .headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/menu"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Cookie", cookie)
                    .POST(HttpRequest.BodyPublishers.ofString("action=add"))
                    .build();
            assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    private WebServerController createServer() {
        throw new UnsupportedOperationException("Week 2: construct the HTTP controller with authentication and port 0");
    }

    private URI uri(WebServerController server, String path) {
        InetSocketAddress address = server.address();
        return URI.create("http://localhost:" + address.getPort() + path);
    }

    private HttpResponse<String> get(WebServerController server, String path, String cookie)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(server, path))
                .timeout(Duration.ofSeconds(3));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(WebServerController server, String form)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(server, "/login"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
