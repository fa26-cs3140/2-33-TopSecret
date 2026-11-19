package controller;

import com.sun.net.httpserver.HttpExchange;
import model.ContentEntry;
import model.ContentNotFoundException;
import model.ContentType;
import model.Session;
import view.ContentText;
import view.HtmlView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static controller.HttpWebServer.*;

final class WebEditingHandler {
    private final ContentController content;
    private final WebFormData forms = new WebFormData();
    private final String secret = UUID.randomUUID().toString();

    WebEditingHandler(ContentController content) {
        this.content = content;
    }

    void handle(HttpExchange exchange, String path, Session session, String sessionToken,
                HtmlView view) throws IOException, ProgramControlException {
        if (!session.isAdmin()) {
            send(exchange, 403, view.error("Administrator access required."));
            return;
        }
        String csrf = csrf(sessionToken);
        boolean submitting = path.equals("/create") || path.equals("/update");
        Map<String, String> input = submitting ? forms.read(exchange)
                : WebFormData.parameters(exchange.getRequestURI().getRawQuery());
        if (input == null) {
            send(exchange, 413, view.error("Form is too large."));
            return;
        }
        if (submitting && !MessageDigest.isEqual(csrf.getBytes(StandardCharsets.UTF_8),
                input.getOrDefault("csrf", "").getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid CSRF token");
        }
        ContentType type = contentType(input.get("type"));
        boolean creating = path.equals("/new") || path.equals("/create");
        Set<String> expected = new HashSet<>(Set.of("type"));
        if (!creating) {
            expected.add("id");
        }
        if (submitting) {
            expected.add("csrf");
            expected.addAll(type.fields);
            if (creating && type == ContentType.MISSION) {
                expected.addAll(Set.of("facility_id", "agent_id"));
            }
        }
        // A facility's country is optional in the request although the form
        // always offers it, because the column is nullable: a facility may
        // exist before its country is known. Anything outside these two sets
        // is still refused.
        Set<String> optional = submitting && type == ContentType.FACILITY
                ? Set.of("country_id") : Set.of();
        Set<String> allowed = new HashSet<>(expected);
        allowed.addAll(optional);
        if (!input.keySet().containsAll(expected) || !allowed.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unexpected or missing fields");
        }
        Integer id = creating ? null : positiveId(input.get("id"));
        if (!submitting) {
            WebEditForm form = creating ? content.newForm(type, session) : content.editForm(type, id, session);
            send(exchange, 200, view.editForm(form, csrf, choices(type, creating, session),
                    selected(type, creating, id, session), null));
            return;
        }
        List<String> values = type.fields.stream().map(input::get).toList();
        WebEditForm submitted = new WebEditForm(type, id, values);
        try {
            Integer facilityId = null;
            Integer agentId = null;
            Integer countryId = null;
            if (type == ContentType.AGENT || (creating && type == ContentType.MISSION)) {
                facilityId = relationship(ContentType.FACILITY, input.get("facility_id"), session);
            }
            if (creating && type == ContentType.MISSION) {
                agentId = relationship(ContentType.AGENT, input.get("agent_id"), session);
            }
            String chosenCountry = input.get("country_id");
            if (type == ContentType.FACILITY && chosenCountry != null && !chosenCountry.isBlank()) {
                countryId = relationship(ContentType.COUNTRY, chosenCountry, session);
            }
            if (creating) {
                content.create(type, values, type == ContentType.MISSION ? facilityId : null,
                        agentId, countryId, session);
            } else {
                content.editForm(type, id, session);
                content.update(type, id, values, countryId, session);
            }
        } catch (WebValidationException exception) {
            send(exchange, 400, view.editForm(submitted, csrf, choices(type, creating, session), input,
                    exception.getMessage()));
            return;
        }
        redirect(exchange, creating ? "/content?type=" + type.table
                : "/record?type=" + type.table + "&id=" + id);
    }

    private Map<String, List<HtmlView.Link>> choices(ContentType type, boolean creating, Session session)
            throws ProgramControlException {
        Map<String, List<HtmlView.Link>> choices = new LinkedHashMap<>();
        if (type == ContentType.AGENT || (creating && type == ContentType.MISSION)) {
            choices.put("facility_id", options(ContentType.FACILITY, session));
        }
        if (creating && type == ContentType.MISSION) {
            choices.put("agent_id", options(ContentType.AGENT, session));
        }
        if (type == ContentType.FACILITY) {
            choices.put("country_id", options(ContentType.COUNTRY, session));
        }
        return choices;
    }

    /** The relationship values a form opens with, so an edit shows the current choice. */
    private Map<String, String> selected(ContentType type, boolean creating, Integer id,
                                         Session session) throws ProgramControlException {
        if (creating || type != ContentType.FACILITY) {
            return Map.of();
        }
        return content.countryFor(id, session)
                .map(country -> Map.of("country_id", Integer.toString(country.id())))
                .orElseGet(Map::of);
    }

    private List<HtmlView.Link> options(ContentType type, Session session) throws ProgramControlException {
        List<HtmlView.Link> links = new ArrayList<>();
        for (ContentEntry entry : content.list(type, session)) {
            links.add(new HtmlView.Link(ContentText.title(type, entry), Integer.toString(entry.id())));
        }
        return links;
    }

    private int relationship(ContentType type, String value, Session session) throws ProgramControlException {
        try {
            int id = positiveId(value);
            content.read(type, id, session);
            return id;
        } catch (IllegalArgumentException | ContentNotFoundException exception) {
            throw new WebValidationException("Choose an existing " + type.table + " record.");
        }
    }

    private static int positiveId(String value) {
        int id = Integer.parseInt(value);
        if (id < 1) {
            throw new IllegalArgumentException("Invalid ID");
        }
        return id;
    }

    private String csrf(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((secret + ":" + token).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
