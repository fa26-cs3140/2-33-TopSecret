package view;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import controller.WebEditForm;

/** Renders controller-provided display data as HTML. */
public final class HtmlView {
    public record Link(String label, String href) {}

    private final Nav nav;

    public HtmlView() {
        this(null);
    }

    private HtmlView(Nav nav) {
        this.nav = nav;
    }

    /** The navigation a signed-in page carries: who is signed in, and where they may go. */
    public record Nav(String username, List<Link> links) {}

    /**
     * A view that renders the navigation block on every page it produces. The
     * original is unchanged, so one shared view can serve concurrent requests
     * while each request renders its own user's menu.
     */
    public HtmlView withNav(String username, List<Link> links) {
        Objects.requireNonNull(links, "links cannot be null");
        return new HtmlView(new Nav(username, List.copyOf(links)));
    }

    /**
     * One row of a record page. A field that names a related record carries
     * that record's address, so the reader can follow it to the detail; a
     * field with no destination renders as plain text.
     */
    public record Field(String label, String value, String href) {
        public Field(String label, String value) {
            this(label, value, null);
        }
    }

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
        return unauthenticated("Log in", body.toString());
    }

    public String menu(String username, List<Link> links) {
        Objects.requireNonNull(links, "links cannot be null");
        StringBuilder body = new StringBuilder("<h1>Menu</h1>\n<p>Signed in as ")
                .append(escape(username)).append("</p>\n<nav aria-label=\"Main menu\">\n<ul>\n");
        appendLinks(body, links);
        body.append("</ul>\n</nav>");
        return document("Menu", body.toString());
    }

    public String list(String title, List<Link> records) {
        return list(title, records, List.of());
    }

    public String list(String title, List<Link> records, List<Link> actions) {
        Objects.requireNonNull(records, "records cannot be null");
        StringBuilder body = new StringBuilder("<h1>").append(escape(title)).append("</h1>\n");
        body.append("<nav aria-label=\"Record actions\"><ul>");
        appendLinks(body, actions);
        body.append("</ul></nav>\n");
        if (records.isEmpty()) {
            body.append("<p>No entries available.</p>\n");
        } else {
            body.append("<ul>\n");
            appendLinks(body, records);
            body.append("</ul>\n");
        }
        return document(title, body.toString());
    }

    public String record(String title, List<Field> fields) {
        return record(title, fields, List.of());
    }

    public String record(String title, List<Field> fields, List<Link> actions) {
        Objects.requireNonNull(fields, "fields cannot be null");
        StringBuilder body = new StringBuilder("<h1>").append(escape(title))
                .append("</h1>\n<table>\n<tbody>\n");
        for (Field field : fields) {
            body.append("<tr><th scope=\"row\">").append(escape(field.label()))
                    .append("</th><td>");
            if (field.href() == null || field.href().isBlank()) {
                body.append(escape(field.value()));
            } else {
                body.append("<a href=\"").append(escape(field.href()))
                        .append("\">").append(escape(field.value())).append("</a>");
            }
            body.append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        body.append("<nav aria-label=\"Record actions\"><ul>");
        appendLinks(body, actions);
        body.append("</ul></nav>");
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
        return document("Audit log", body.toString());
    }

    /**
     * An error page. A reader who is signed in keeps the menu, so a refused
     * request does not strand them; a reader who is not gets the way back to
     * the log in page instead.
     */
    public String error(String message) {
        String body = "<h1>Error</h1>\n<p role=\"alert\">" + escape(message) + "</p>\n";
        if (nav == null) {
            return unauthenticated("Error", body + "<p><a href=\"/\">Back to login</a></p>");
        }
        return document("Error", body);
    }

    public String editForm(WebEditForm form, String csrf, Map<String, List<Link>> choices,
                           Map<String, String> selected, String error) {
        boolean creating = form.recordId() == null;
        String title = (creating ? "New " : "Edit ") + form.type().table;
        StringBuilder body = new StringBuilder("<h1>").append(escape(title)).append("</h1>\n");
        if (error != null) {
            body.append("<p role=\"alert\">").append(escape(error)).append("</p>\n");
        }
        body.append("<form method=\"post\" action=\"").append(creating ? "/create" : "/update").append("\">\n");
        hidden(body, "type", form.type().table);
        hidden(body, "csrf", csrf);
        if (!creating) {
            hidden(body, "id", form.recordId().toString());
        }
        for (int index = 0; index < form.type().fields.size(); index++) {
            String field = form.type().fields.get(index);
            String value = form.values().get(index);
            if (choices.containsKey(field)) {
                select(body, field, value, choices.get(field));
            } else {
                body.append("<label for=\"").append(escape(field)).append("\">").append(escape(field))
                        .append("</label>\n");
                if (field.equals("text") || field.equals("brief") || field.equals("notes")) {
                    body.append("<textarea rows=\"8\" id=\"").append(escape(field)).append("\" name=\"")
                            .append(escape(field)).append("\">\n").append(escape(value)).append("</textarea>");
                } else {
                    body.append("<input type=\"").append(field.contains("date") ? "date" : "text")
                            .append("\" id=\"").append(escape(field)).append("\" name=\"").append(escape(field))
                            .append("\" value=\"").append(escape(value)).append("\">");
                }
            }
        }
        for (Map.Entry<String, List<Link>> choice : choices.entrySet()) {
            if (!form.type().fields.contains(choice.getKey())) {
                select(body, choice.getKey(), selected.getOrDefault(choice.getKey(), ""), choice.getValue());
            }
        }
        String cancel = creating ? "/content?type=" + form.type().table
                : "/record?type=" + form.type().table + "&id=" + form.recordId();
        body.append("<button type=\"submit\">Save</button></form>\n<a href=\"")
                .append(escape(cancel)).append("\">Cancel</a>");
        return document(title, body.toString());
    }

    private static void hidden(StringBuilder body, String name, String value) {
        body.append("<input type=\"hidden\" name=\"").append(escape(name))
                .append("\" value=\"").append(escape(value)).append("\">\n");
    }

    private static void select(StringBuilder body, String field, String value, List<Link> choices) {
        body.append("<label for=\"").append(escape(field)).append("\">").append(escape(field))
                .append("</label><select required id=\"").append(escape(field)).append("\" name=\"")
                .append(escape(field)).append("\"><option value=\"\">Choose a record</option>");
        for (Link choice : choices) {
            body.append("<option value=\"").append(escape(choice.href())).append("\"")
                    .append(choice.href().equals(value) ? " selected" : "").append(">")
                    .append(escape(choice.label())).append("</option>");
        }
        body.append("</select>\n");
    }

    private static void appendLinks(StringBuilder body, List<Link> links) {
        for (Link link : links) {
            body.append("<li><a href=\"").append(escape(link.href()))
                    .append("\">").append(escape(link.label())).append("</a></li>\n");
        }
    }

    /**
     * Wrap already-escaped markup in the shared page shell, so another view in
     * this package renders with the same styling and the same menu rather than
     * building a second shell of its own. The caller escapes its own content;
     * {@link #escape} is available for that.
     */
    public String shell(String title, String body) {
        return document(title, body);
    }

    /** A page for a signed-in reader: styled, and carrying the session menu. */
    private String document(String title, String body) {
        return page(title, chrome(), body);
    }

    /**
     * A page for a reader with no session. It is styled but never carries the
     * menu, whichever view renders it, so an unauthenticated page cannot show
     * session controls by accident.
     */
    private static String unauthenticated(String title, String body) {
        return page(title, "", body);
    }

    private static String page(String title, String chrome, String body) {
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + escape(title) + "</title>\n<style>" + STYLE + "</style>\n"
                + "</head>\n<body>\n" + chrome + "<main>\n" + body
                + "\n</main>\n</body>\n</html>\n";
    }

    /**
     * The navigation every signed-in page carries. Log out is a submitted form
     * rather than a link so that no prefetch or crafted request can end a
     * session. A view with no navigation renders nothing here, which is what
     * the log in and error pages want.
     */
    private String chrome() {
        if (nav == null) {
            return "";
        }
        StringBuilder block = new StringBuilder(
                "<nav class=\"chrome\" aria-label=\"Site menu\">\n<ul>\n");
        block.append("<li class=\"brand\">Top Secret</li>\n");
        for (Link link : nav.links()) {
            block.append("<li><a href=\"").append(escape(link.href())).append("\">")
                    .append(escape(link.label())).append("</a></li>\n");
        }
        block.append("<li class=\"who\">Signed in as ").append(escape(nav.username()))
                .append("</li>\n<li><form action=\"/logout\" method=\"post\">")
                .append("<button type=\"submit\">Log out</button></form></li>\n");
        block.append("</ul>\n</nav>\n");
        return block.toString();
    }

    private static final String STYLE = """
            *{box-sizing:border-box}
            body{margin:0;background:#f4f7fa;color:#1b2733;line-height:1.5;font-family:\
            system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif}
            nav.chrome{background:#1b2733;padding:.5rem .75rem}
            nav.chrome ul{list-style:none;margin:0;padding:0;display:flex;flex-wrap:wrap;\
            align-items:center;gap:.25rem .5rem}
            nav.chrome li{margin:0}
            nav.chrome .brand{color:#fff;font-weight:700;letter-spacing:.04em;\
            text-transform:uppercase;font-size:.85rem;margin-right:.5rem}
            nav.chrome .who{color:#9fb3c8;font-size:.85rem;margin-left:auto}
            nav.chrome a{color:#dce6f0;text-decoration:none;display:block;\
            padding:.3rem .55rem;border-radius:4px}
            nav.chrome a:hover,nav.chrome a:focus{background:#2c3e50;text-decoration:underline}
            nav.chrome form{display:inline;margin:0}
            nav.chrome button{margin:0;padding:.3rem .7rem;font-size:.85rem;\
            background:#5a6b7c;border-color:#5a6b7c}
            main{max-width:58rem;margin:1.5rem auto;padding:0 1rem}
            h1{font-size:1.55rem;margin:0 0 1rem;border-bottom:2px solid #c8d4e0;\
            padding-bottom:.4rem}
            a{color:#1d5c8f}
            table{border-collapse:collapse;width:100%;background:#fff;\
            border:1px solid #c8d4e0;margin:0 0 1rem}
            th,td{border:1px solid #c8d4e0;padding:.45rem .6rem;text-align:left;\
            vertical-align:top}
            thead th{background:#e7eef5}
            tbody tr:nth-child(even){background:#f7fafc}
            main ul{background:#fff;border:1px solid #c8d4e0;border-radius:4px;\
            padding:.5rem .5rem .5rem 1.6rem;margin:0 0 1rem}
            main li{margin:.15rem 0}
            main nav ul{background:none;border:0;padding:0;list-style:none;display:flex;\
            flex-wrap:wrap;gap:.5rem}
            label{display:block;margin:.9rem 0 .2rem;font-weight:600;font-size:.95rem}
            input,select,textarea{width:100%;max-width:32rem;padding:.45rem;font:inherit;\
            background:#fff;border:1px solid #c8d4e0;border-radius:4px;display:block}
            textarea{max-width:40rem}
            button{margin-top:1rem;padding:.5rem 1.1rem;font:inherit;cursor:pointer;\
            color:#fff;background:#1d5c8f;border:1px solid #1d5c8f;border-radius:4px}
            button:hover{background:#174a73}
            :focus-visible{outline:3px solid #c77700;outline-offset:2px}
            p[role=alert]{background:#fdecea;border:1px solid #e0b4ae;color:#7d2a20;\
            padding:.55rem .75rem;border-radius:4px}
            """;


    static String escape(String text) {
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
