package controller;

import model.ContentEntry;
import model.ContentType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record WebEditForm(ContentType type, Integer recordId, List<String> values) {
    public WebEditForm {
        Objects.requireNonNull(type, "type cannot be null");
        values = List.copyOf(values);
        if (recordId != null && recordId < 1) {
            throw new IllegalArgumentException("Record ID must be positive.");
        }
        if (values.size() != type.fields.size()) {
            throw new IllegalArgumentException("Incorrect number of form fields.");
        }
    }

    public static WebEditForm newRecord(ContentType type) {
        return new WebEditForm(type, null, Collections.nCopies(type.fields.size(), ""));
    }

    public static WebEditForm editRecord(ContentType type, ContentEntry entry) {
        return new WebEditForm(type, entry.id(), entry.values());
    }
}
