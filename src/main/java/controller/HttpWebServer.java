package controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.*;
import view.ContentText;
import view.HtmlView;
import view.MessageHtmlView;
import view.MissionBriefJson;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static controller.WebFormData.parameters;

public final class HttpWebServer implements WebServerController {
    private final WebAuthentication authentication;
    private final WebSessionStore sessions;
    private final ContentController content;
    private final HtmlView view = new HtmlView();
    private final MessageHtmlView messageView = new MessageHtmlView();
    private final WebFormData forms = new WebFormData();
    private final WebMessageHandler messageHandler;
    private final WebEditingHandler editingHandler;
    private final InetSocketAddress bindAddress;
    private HttpServer server;
    private ExecutorService executor;

    public HttpWebServer(WebAuthentication authentication, WebSessionStore sessions,
                         ContentController content, InetSocketAddress bindAddress) {
        this(authentication, sessions, content, bindAddress, null);
    }

    public HttpWebServer(WebAuthentication authentication, WebSessionStore sessions,
                         ContentController content, InetSocketAddress bindAddress,
                         MessageService messages) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.content = content;
        this.editingHandler = new WebEditingHandler(content);
        this.bindAddress = bindAddress;
        this.messageHandler = messages == null ? null : new WebMessageHandler(messages);
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
        // Explicit close and the JVM shutdown hook may both run. Only the
        // running server owns cleanup; a later call must not touch shared storage.
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
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
            // style-src is stated explicitly because it otherwise falls back to
            // default-src, and 'none' would make the browser refuse the
            // stylesheet the page shell carries. Scripts and images stay
            // refused by default-src, which is what the assignment wants.
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; "
                            + "frame-ancestors 'none'");
            route(exchange);
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, errorView(exchange).error("Check the form or record parameters."));
        } catch (ContentNotFoundException exception) {
            send(exchange, 404, errorView(exchange).error("That record does not exist."));
        } catch (IOException | CredentialException | ProgramControlException | RuntimeException exception) {
            send(exchange, 500, errorView(exchange).error("The request could not be completed."));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException, CredentialException, ProgramControlException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String requiredMethod = switch (path) {
            case "/login", "/logout", "/create", "/update" -> "POST";
            case "/", "/menu", "/content", "/record", "/audit", "/new", "/edit", "/search", "/mission-briefs" -> "GET";
            default -> null;
        };
        String token = sessionToken(exchange);
        Optional<String> username = sessions.usernameFor(token);
        if (!path.equals("/") && !path.equals("/login") && username.isEmpty()) {
            redirect(exchange, "/");
            return;
        }
        // One navigation per request. The view is immutable, so each thread
        // renders its own user's menu from the same shared instance.
        Session session = username.isPresent() ? content.sessionFor(username.get()) : null;
        HtmlView chrome = session == null ? view
                : view.withNav(session.getUsername(), menuLinks(session));
        if (messageHandler != null && path.startsWith("/messages")) {
            messageHandler.handle(exchange, path, session, token,
                    messageView.withNav(session.getUsername(), menuLinks(session)));
            return;
        }
        if (requiredMethod == null) {
            send(exchange, 404, chrome.error("Unknown page."));
            return;
        }
        if (!requiredMethod.equals(method)) {
            exchange.getResponseHeaders().set("Allow", requiredMethod);
            send(exchange, 405, chrome.error("Use the displayed links and forms."));
            return;
        }
        if (path.equals("/")) {
            if (username.isPresent()) {
                redirect(exchange, "/menu");
            } else {
                send(exchange, 200, view.login("Enter your credentials."));
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
        if (path.equals("/new") || path.equals("/edit") || path.equals("/create") || path.equals("/update")) {
            editingHandler.handle(exchange, path, session, token, chrome);
            return;
        }
        switch (path) {
            case "/menu" -> send(exchange, 200, chrome.menu(session.getUsername(), menuLinks(session)));
            case "/content" -> {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                ContentType type = contentType(query.get("type"));
                List<HtmlView.Link> actions = session.isAdmin()
                        ? List.of(new HtmlView.Link("New record", "/new?type=" + type.table)) : List.of();
                send(exchange, 200, chrome.list(type.table, recordLinks(type, content.list(type, session)), actions));
            }
            case "/record" -> {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                ContentType type = contentType(query.get("type"));
                int id = Integer.parseInt(query.getOrDefault("id", ""));
                if (id < 1) {
                    throw new IllegalArgumentException("Invalid ID");
                }
                send(exchange, 200, recordPage(type, content.read(type, id, session), session, chrome));
            }
            case "/mission-briefs" -> {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                if (!query.keySet().equals(java.util.Set.of("mission_id"))) {
                    throw new IllegalArgumentException("Mission ID required");
                }
                int missionId = Integer.parseInt(query.get("mission_id"));
                if (missionId < 1) {
                    throw new IllegalArgumentException("Invalid ID");
                }
                content.read(ContentType.MISSION, missionId, session);
                byte[] body = new MissionBriefJson().encode(missionId, content.briefsFor(missionId, session))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"mission-" + missionId + "-briefs.json\"");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            case "/audit" -> {
                if (!content.canViewAudit(session)) {
                    send(exchange, 403, chrome.error("Administrator access required."));
                } else {
                    send(exchange, 200, chrome.audit(auditRows(content.audit(session))));
                }
            }
            case "/search" -> {
                String raw = exchange.getRequestURI().getRawQuery();
                String phrase = parameters(raw == null ? "" : raw).getOrDefault("q", "");
                List<HtmlView.Link> results = new ArrayList<>();
                if (!phrase.isBlank()) {
                    for (ContentHit hit : content.search(phrase, session)) {
                        results.add(new HtmlView.Link(
                                hit.type().table + ": " + ContentText.title(hit.type(), hit.entry()),
                                "/record?type=" + hit.type().table + "&id=" + hit.entry().id()));
                    }
                }
                send(exchange, 200, chrome.search(phrase, results));
            }
            default -> throw new IllegalArgumentException("Unknown route");
        }
    }

    private void login(HttpExchange exchange, String previousToken) throws IOException {
        Map<String, String> form = forms.read(exchange);
        if (form == null) {
            send(exchange, 413, view.login("Form is too large."));
            return;
        }
        if (!form.keySet().equals(java.util.Set.of("username", "password"))) {
            throw new IllegalArgumentException("Credentials required");
        }
        Optional<String> username = authentication.authenticate(form.get("username"), form.get("password"));
        if (username.isEmpty()) {
            send(exchange, 401, view.login("Invalid username or password"));
            return;
        }
        sessions.invalidate(previousToken);
        String token = sessions.create(username.get());
        exchange.getResponseHeaders().set("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax");
        redirect(exchange, "/menu");
    }

    /**
     * The view an error page is rendered with. A signed-in reader keeps the
     * menu so a refused request does not strand them, and a reader with no
     * session gets the plain page with its way back to log in.
     *
     * Every failure here is swallowed deliberately: this runs while another
     * failure is already being reported, and an error page that throws would
     * replace a useful message with an empty response.
     */
    private HtmlView errorView(HttpExchange exchange) {
        try {
            Optional<String> username = sessions.usernameFor(sessionToken(exchange));
            if (username.isPresent()) {
                Session session = content.sessionFor(username.get());
                return view.withNav(session.getUsername(), menuLinks(session));
            }
        } catch (RuntimeException | CredentialException ignored) {
            return view;
        }
        return view;
    }

    private List<HtmlView.Link> menuLinks(Session session) {
        List<HtmlView.Link> links = new ArrayList<>();
        for (ContentType type : ContentType.values()) {
            links.add(new HtmlView.Link(type.table, "/content?type=" + type.table));
        }
        links.add(new HtmlView.Link("Search", "/search"));
        if (messageHandler != null) {
            links.add(new HtmlView.Link("Messaging", "/messages"));
        }
        if (content.canViewAudit(session)) {
            links.add(new HtmlView.Link("Review audit log", "/audit"));
        }
        return links;
    }

    private static List<HtmlView.Link> recordLinks(ContentType type, List<ContentEntry> entries) {
        List<HtmlView.Link> links = new ArrayList<>();
        for (ContentEntry entry : entries) {
            links.add(new HtmlView.Link(ContentText.title(type, entry),
                    "/record?type=" + type.table + "&id=" + entry.id()));
        }
        return links;
    }

    private String recordPage(ContentType type, ContentEntry entry, Session session, HtmlView view)
            throws ProgramControlException {
        List<HtmlView.Field> fields = new ArrayList<>();
        for (int index = 0; index < type.fields.size(); index++) {
            fields.add(new HtmlView.Field(type.fields.get(index), entry.values().get(index)));
        }
        int id = entry.id();
        switch (type) {
            case FACILITY -> {
                // Shown by name through the key, so a renamed country reads
                // correctly here without any value being copied onto the facility.
                Optional<ContentEntry> country = content.countryFor(id, session);
                fields.add(new HtmlView.Field("country",
                        country.map(value -> ContentText.title(ContentType.COUNTRY, value))
                                .orElse("Unassigned"),
                        country.map(value -> "/record?type=" + ContentType.COUNTRY.table
                                + "&id=" + value.id()).orElse(null)));
                fields.add(linked("agents", ContentType.AGENT, content.agentsAt(id, session)));
                fields.add(linked("missions", ContentType.MISSION, content.missionsAt(id, session)));
            }
            case AGENT -> {
                fields.add(linkedOne("facility", ContentType.FACILITY, content.facilityOf(id, session)));
                fields.add(linked("missions", ContentType.MISSION, content.missionsFor(id, session)));
            }
            case COUNTRY -> fields.add(linked("facilities", ContentType.FACILITY,
                    content.facilitiesIn(id, session)));
            case BRIEF -> fields.add(linkedOne("mission", ContentType.MISSION,
                    content.missionOf(id, session)));
            case MISSION -> {
                fields.add(linkedOne("facility", ContentType.FACILITY, content.facilityFor(id, session)));
                fields.add(linked("agents", ContentType.AGENT, content.agentsFor(id, session)));
                fields.add(linked("briefs", ContentType.BRIEF, content.briefsFor(id, session)));
            }
        }
        List<HtmlView.Link> actions = new ArrayList<>();
        if (type == ContentType.MISSION) {
            actions.add(new HtmlView.Link("Download briefs (JSON)", "/mission-briefs?mission_id=" + id));
        }
        if (session.isAdmin()) {
            actions.add(new HtmlView.Link("Edit record", "/edit?type=" + type.table + "&id=" + id));
            if (type == ContentType.MISSION) {
                actions.add(new HtmlView.Link("Add brief", "/new?type=briefs&mission_id=" + id));
            }
        }
        return view.record(type.table + " #" + id, fields, actions);
    }

    private static HtmlView.Field linked(String label, ContentType type, List<ContentEntry> entries) {
        return new HtmlView.Field(label, recordLinks(type, entries));
    }

    private static HtmlView.Field linkedOne(String label, ContentType type, Optional<ContentEntry> entry) {
        return linked(label, type, entry.map(List::of).orElse(List.of()));
    }

    private static List<HtmlView.AuditRow> auditRows(List<AuditLogEntry> entries) {
        List<HtmlView.AuditRow> rows = new ArrayList<>();
        for (AuditLogEntry entry : entries) {
            rows.add(new HtmlView.AuditRow(entry.getTimestamp().toString(), entry.getUsername(),
                    entry.getEventType().toString(), entry.getContentType().toString(),
                    Integer.toString(entry.getContentId())));
        }
        return rows;
    }

    static ContentType contentType(String value) {
        for (ContentType type : ContentType.values()) {
            if (type.table.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown content type");
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

    static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
