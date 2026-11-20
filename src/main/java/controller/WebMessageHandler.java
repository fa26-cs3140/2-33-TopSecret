package controller;

import com.sun.net.httpserver.HttpExchange;
import model.MessageService;
import model.Session;
import view.MessageHtmlView;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Messaging-only HTTP flow; the server performs the shared login check first. */
public final class WebMessageHandler {
    private final MessageService messages;
    private final WebFormData forms = new WebFormData();
    private final byte[] csrfKey = new byte[32];

    public WebMessageHandler(MessageService messages) {
        this.messages = java.util.Objects.requireNonNull(messages);
        new SecureRandom().nextBytes(csrfKey);
    }

    public void handle(HttpExchange exchange, String path, Session session, String sessionToken,
                       MessageHtmlView view)
            throws IOException, ProgramControlException {
        String method = exchange.getRequestMethod();
        String required = switch (path) {
            case "/messages", "/messages/new", "/messages/view" -> "GET";
            case "/messages/send", "/messages/open" -> "POST";
            default -> null;
        };
        if (required == null) {
            send(exchange, 404, view.error("Unknown page."));
            return;
        }
        if (!required.equals(method)) {
            exchange.getResponseHeaders().set("Allow", required);
            send(exchange, 405, view.error("Use the displayed links and forms."));
            return;
        }
        switch (path) {
            case "/messages" -> send(exchange, 200, view.inbox(messages.inbox(session), csrf(sessionToken)));
            case "/messages/new" -> send(exchange, 200,
                    view.compose(messages.recipients(session), csrf(sessionToken), null));
            case "/messages/view" -> {
                int id = positiveId(WebFormData.parameters(exchange.getRequestURI().getRawQuery()).get("id"));
                var message = messages.message(id, session);
                send(exchange, message.isPresent() ? 200 : 404,
                        message.map(view::detail).orElseGet(() -> view.error("Message not found.")));
            }
            case "/messages/send" -> {
                Map<String, String> form = forms.read(exchange);
                if (form == null) {
                    send(exchange, 413, view.error("Form is too large."));
                    return;
                }
                if (!form.keySet().equals(Set.of("agentId", "body", "csrf")) || !validCsrf(form, sessionToken)) {
                    send(exchange, 400, view.error("Invalid message form."));
                    return;
                }
                int agentId = positiveId(form.get("agentId"));
                String body = form.get("body");
                if (body == null || body.isBlank() || body.length() > 4096
                        || !messages.recipients(session).containsKey(agentId)) {
                    send(exchange, 400, view.compose(messages.recipients(session), csrf(sessionToken),
                            "Select an agent and enter a message of up to 4096 characters."));
                    return;
                }
                messages.send(agentId, body, session);
                redirect(exchange, "/messages");
            }
            case "/messages/open" -> {
                Map<String, String> form = forms.read(exchange);
                if (form == null) {
                    send(exchange, 413, view.error("Form is too large."));
                    return;
                }
                if (!form.keySet().equals(Set.of("id", "csrf")) || !validCsrf(form, sessionToken)) {
                    send(exchange, 400, view.error("Invalid message form."));
                    return;
                }
                int id = positiveId(form.get("id"));
                if (!messages.markRead(id, session)) {
                    send(exchange, 404, view.error("Message not found."));
                    return;
                }
                redirect(exchange, "/messages/view?id=" + id);
            }
            default -> throw new IllegalStateException("Unreachable message route");
        }
    }

    private boolean validCsrf(Map<String, String> form, String sessionToken) {
        String supplied = form.get("csrf");
        return supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.US_ASCII), csrf(sessionToken).getBytes(StandardCharsets.US_ASCII));
    }

    private String csrf(String sessionToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(csrfKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(sessionToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("CSRF protection unavailable", exception);
        }
    }

    private static int positiveId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException exception) {
            // Fall through to the same response for all malformed IDs.
        }
        throw new IllegalArgumentException("Invalid ID");
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
