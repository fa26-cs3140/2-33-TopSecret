package view;

import model.Message;

import java.util.List;
import java.util.Map;

/**
 * Escaped HTML for the web messaging center. The page shell comes from
 * {@link HtmlView}, so these pages carry the same styling and the same
 * navigation as every other page rather than a second shell of their own.
 */
public final class MessageHtmlView {
    private final HtmlView chrome;

    public MessageHtmlView() {
        this(new HtmlView());
    }

    private MessageHtmlView(HtmlView chrome) {
        this.chrome = chrome;
    }

    /** A messaging view whose pages carry this reader's navigation. */
    public MessageHtmlView withNav(String username, List<HtmlView.Link> links) {
        return new MessageHtmlView(chrome.withNav(username, links));
    }
    public String inbox(List<Message> messages, String csrf) {
        StringBuilder body = new StringBuilder("<h1>My messages</h1><p><a href=\"/messages/new\">New message</a></p>");
        if (messages.isEmpty()) {
            body.append("<p>No messages available.</p>");
        } else {
            body.append("<table><thead><tr><th>Status</th><th>From</th><th>Received</th><th>Action</th></tr></thead><tbody>");
            for (Message message : messages) {
                body.append("<tr><td>").append(message.getReadAt() == null ? "Unread" : "Read")
                        .append("</td><td>").append(escape(message.getSender()))
                        .append("</td><td>").append(escape(message.getCreatedAt().toString()))
                        .append("</td><td><form action=\"/messages/open\" method=\"post\">")
                        .append("<input type=\"hidden\" name=\"id\" value=\"").append(message.getId()).append("\">")
                        .append("<input type=\"hidden\" name=\"csrf\" value=\"").append(escape(csrf)).append("\">")
                        .append("<button type=\"submit\">Open</button></form></td></tr>");
            }
            body.append("</tbody></table>");
        }
        return document("My messages", body.toString());
    }

    public String detail(Message message) {
        String body = "<h1>Message</h1><dl><dt>From</dt><dd>" + escape(message.getSender())
                + "</dd><dt>To</dt><dd>" + escape(message.getAgentName())
                + "</dd><dt>Received</dt><dd>" + escape(message.getCreatedAt().toString())
                + "</dd></dl><p style=\"white-space:pre-wrap\">" + escape(message.getBody()) + "</p>"
                + "<p><a href=\"/messages\">Back to my messages</a></p>";
        return document("Message", body);
    }

    public String compose(Map<Integer, String> recipients, String csrf, String error) {
        StringBuilder body = new StringBuilder("<h1>New message</h1>");
        if (error != null) {
            body.append("<p role=\"alert\">").append(escape(error)).append("</p>");
        }
        if (recipients.isEmpty()) {
            body.append("<p>No agents available.</p>");
        } else {
            body.append("<form action=\"/messages/send\" method=\"post\">")
                    .append("<input type=\"hidden\" name=\"csrf\" value=\"").append(escape(csrf)).append("\">")
                    .append("<label for=\"agentId\">To</label><select id=\"agentId\" name=\"agentId\" required>")
                    .append("<option value=\"\">Select an agent</option>");
            recipients.forEach((id, name) -> body.append("<option value=\"").append(id)
                    .append("\">").append(escape(name)).append("</option>"));
            body.append("</select><label for=\"body\">Message</label>")
                    .append("<textarea id=\"body\" name=\"body\" maxlength=\"4096\" required></textarea>")
                    .append("<button type=\"submit\">Send message</button></form>");
        }
        body.append("<p><a href=\"/messages\">Cancel</a></p>");
        return document("New message", body.toString());
    }

    public String error(String message) {
        return document("Message error", "<h1>Message error</h1><p>" + escape(message)
                + "</p><p><a href=\"/messages\">Back to my messages</a></p>");
    }

    private String document(String title, String body) {
        return chrome.shell(title, body);
    }

    private static String escape(String value) {
        return HtmlView.escape(value);
    }
}
