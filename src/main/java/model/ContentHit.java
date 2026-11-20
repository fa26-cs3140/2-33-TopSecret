package model;

import java.util.Objects;

/** One search result: which kind of record matched, and the record itself. */
public record ContentHit(ContentType type, ContentEntry entry) {
    public ContentHit {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");
    }
}