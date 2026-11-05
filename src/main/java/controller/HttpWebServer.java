package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.*;
import view.HtmlView;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpWebServer implements WebServerController {
    private final WebAuthentication authentication;
    private final WebSessionStore sessions;
    private final ContentController content;
    private final InetSocketAddress bindAddress;
    private HttpServer server;
    private ExecutorService executor;

    public HttpWebServer(WebAuthentication authentication, WebSessionStore sessions,
                         ContentController content, InetSocketAddress bindAddress) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.content = content;
        this.bindAddress = bindAddress;
    }

    @Override
    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IOException("Web server is already running.");
        }
        if (!authentication.hasAccounts()) {
            throw new IOException("No accounts found. Run the terminal application to create an account first.");
        }
        HttpServer candidate = HttpServer.create(bindAddress, 0);
        executor = Executors.newFixedThreadPool(4);
        candidate.setExecutor(executor);
        candidate.createContext("/", this::handle);
        candidate.start();
        server = candidate;
    }

    @Override
    public synchronized InetSocketAddress address() {
        return server == null ? bindAddress : server.getAddress();
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sessions.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'");
            route(exchange);
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, HtmlView.page("Invalid request", "<p>Check the form or record parameters.</p>"));
        } catch (ContentNotFoundException exception) {
            send(exchange, 404, HtmlView.page("Not found", "<p>That record does not exist.</p>"));
        } catch (IOException | CredentialException | ProgramControlException | RuntimeException exception) {
            send(exchange, 500, HtmlView.page("Service unavailable", "<p>The request could not be completed.</p>"));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException, CredentialException, ProgramControlException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String requiredMethod = switch (path) {
            case "/login", "/logout" -> "POST";
            case "/", "/menu", "/content", "/record", "/audit" -> "GET";
            default -> null;
        };
        String token = sessionToken(exchange);
        Optional<String> username = sessions.usernameFor(token);
        if (!path.equals("/") && !path.equals("/login") && username.isEmpty()) {
            redirect(exchange, "/");
            return;
        }
        if (requiredMethod == null) {
            send(exchange, 404, HtmlView.page("Not found", "<p>Unknown page.</p>"));
            return;
        }
        if (!requiredMethod.equals(method)) {
            exchange.getResponseHeaders().set("Allow", requiredMethod);
            send(exchange, 405, HtmlView.page("Method not allowed", "<p>Use the displayed links and forms.</p>"));
            return;
        }
        if (path.equals("/")) {
            if (username.isPresent()) {
                redirect(exchange, "/menu");
            } else {
                send(exchange, 200, HtmlView.login("Enter your credentials."));
            }
            return;
        }
        if (path.equals("/login")) {
            login(exchange, token);
            return;
        }
        if (path.equals("/logout")) {
            sessions.invalidate(token);
            exchange.getResponseHeaders().set("Set-Cookie", SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
            redirect(exchange, "/");
            return;
        }
        Session session = content.sessionFor(username.orElseThrow());
        switch (path) {
            case "/menu" -> send(exchange, 200, HtmlView.menu(content.canViewAudit(session)));
            case "/content" -> {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                ContentType type = contentType(query.get("type"));
                send(exchange, 200, HtmlView.list(type, content.list(type, session)));
            }
            case "/record" -> {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                ContentType type = contentType(query.get("type"));
                int id = Integer.parseInt(query.getOrDefault("id", ""));
                if (id < 1) {
                    throw new IllegalArgumentException("Invalid ID");
                }
                send(exchange, 200, HtmlView.record(type, content.read(type, id, session)));
            }
            case "/audit" -> {
                if (!content.canViewAudit(session)) {
                    send(exchange, 403, HtmlView.page("Access denied", "<p>Administrator access required.</p>"));
                } else {
                    send(exchange, 200, HtmlView.audit(content.audit(session)));
                }
            }
            default -> throw new IllegalArgumentException("Unknown route");
        }
    }

    private void login(HttpExchange exchange, String previousToken) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Expected a form");
        }
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192) {
            send(exchange, 413, HtmlView.login("Form is too large."));
            return;
        }
        Map<String, String> form = parameters(new String(body, StandardCharsets.UTF_8));
        if (!form.keySet().equals(java.util.Set.of("username", "password"))) {
            throw new IllegalArgumentException("Credentials required");
        }
        Optional<String> username = authentication.authenticate(form.get("username"), form.get("password"));
        if (username.isEmpty()) {
            send(exchange, 401, HtmlView.login("Invalid username or password"));
            return;
        }
        sessions.invalidate(previousToken);
        String token = sessions.create(username.get());
        exchange.getResponseHeaders().set("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax");
        redirect(exchange, "/menu");
    }

    private static ContentType contentType(String value) {
        for (ContentType type : ContentType.values()) {
            if (type.table.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown content type");
    }

    private static Map<String, String> parameters(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid parameter");
            }
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate parameter");
            }
        }
        return values;
    }

    private static String sessionToken(HttpExchange exchange) {
        String token = null;
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", java.util.List.of())) {
            for (String cookie : header.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals(SESSION_COOKIE)) {
                    if (token != null) {
                        return null;
                    }
                    token = parts[1];
                }
            }
        }
        return token;
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
