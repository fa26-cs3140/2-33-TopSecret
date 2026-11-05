package view;

import java.util.List;
import java.util.Objects;

/** Renders controller-provided display data as plain, read-only HTML. */
public final class HtmlView {
    public record Link(String label, String href) {}

    public record Field(String label, String value) {}

    public record AuditRow(String timestamp, String username, String event,
                           String contentType, String contentId) {}

    public String login(String errorMessage) {
        StringBuilder body = new StringBuilder("<h1>Top Secret</h1>\n");
        if (errorMessage != null && !errorMessage.isBlank()) {
            body.append("<p role=\"alert\">").append(escape(errorMessage)).append("</p>\n");
        }
        body.append("<form action=\"/login\" method=\"post\">\n")
                .append("<label for=\"username\">Username</label>\n")
                .append("<input id=\"username\" name=\"username\" type=\"text\" required>\n")
                .append("<label for=\"password\">Password</label>\n")
                .append("<input id=\"password\" name=\"password\" type=\"password\" required>\n")
                .append("<button type=\"submit\">Log in</button>\n")
                .append("</form>");
        return document("Log in", body.toString());
    }

    public String menu(String username, List<Link> links) {
        Objects.requireNonNull(links, "links cannot be null");
        StringBuilder body = new StringBuilder("<h1>Menu</h1>\n<p>Signed in as ")
                .append(escape(username)).append("</p>\n<nav aria-label=\"Main menu\">\n<ul>\n");
        appendLinks(body, links);
        body.append("</ul>\n</nav>\n<form action=\"/logout\" method=\"post\">"
                + "<button type=\"submit\">Log out</button></form>");
        return document("Menu", body.toString());
    }

    public String list(String title, List<Link> records) {
        Objects.requireNonNull(records, "records cannot be null");
        StringBuilder body = new StringBuilder("<h1>").append(escape(title)).append("</h1>\n");
        if (records.isEmpty()) {
            body.append("<p>No entries available.</p>\n");
        } else {
            body.append("<ul>\n");
            appendLinks(body, records);
            body.append("</ul>\n");
        }
        body.append("<p><a href=\"/menu\">Back to menu</a></p>");
        return document(title, body.toString());
    }

    public String record(String title, List<Field> fields) {
        Objects.requireNonNull(fields, "fields cannot be null");
        StringBuilder body = new StringBuilder("<h1>").append(escape(title))
                .append("</h1>\n<table>\n<tbody>\n");
        for (Field field : fields) {
            body.append("<tr><th scope=\"row\">").append(escape(field.label()))
                    .append("</th><td>").append(escape(field.value()))
                    .append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n<p><a href=\"/menu\">Back to menu</a></p>");
        return document(title, body.toString());
    }

    public String audit(List<AuditRow> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");
        StringBuilder body = new StringBuilder("<h1>Audit log</h1>\n");
        if (entries.isEmpty()) {
            body.append("<p>No audit entries available.</p>\n");
        } else {
            body.append("<table>\n<thead><tr><th>Time</th><th>User</th><th>Event</th>")
                    .append("<th>Content type</th><th>Content ID</th></tr></thead>\n<tbody>\n");
            for (AuditRow entry : entries) {
                body.append("<tr><td>").append(escape(entry.timestamp()))
                        .append("</td><td>").append(escape(entry.username()))
                        .append("</td><td>").append(escape(entry.event()))
                        .append("</td><td>").append(escape(entry.contentType()))
                        .append("</td><td>").append(escape(entry.contentId()))
                        .append("</td></tr>\n");
            }
            body.append("</tbody>\n</table>\n");
        }
        body.append("<p><a href=\"/menu\">Back to menu</a></p>");
        return document("Audit log", body.toString());
    }

    public String error(String message) {
        return document("Error", "<h1>Error</h1>\n<p>" + escape(message)
                + "</p>\n<p><a href=\"/\">Back to login</a></p>");
    }

    private static void appendLinks(StringBuilder body, List<Link> links) {
        for (Link link : links) {
            body.append("<li><a href=\"").append(escape(link.href()))
                    .append("\">").append(escape(link.label())).append("</a></li>\n");
        }
    }

    private static String document(String title, String body) {
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<title>" + escape(title) + "</title>\n</head>\n<body>\n"
                + body + "\n</body>\n</html>\n";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
