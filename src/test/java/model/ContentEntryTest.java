package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One row handed back to the menu: an id and its column values. */
class ContentEntryTest {

    @Test
    void theAccessorsReturnWhatWasGiven() {
        ContentEntry entry = new ContentEntry(7, List.of("Alpha", "1970-01-01"));

        assertEquals(7, entry.id());
        assertEquals(List.of("Alpha", "1970-01-01"), entry.values());
    }

    @Test
    void theValuesAreCopiedSoLaterChangesDoNotLeakIn() {
        List<String> original = new ArrayList<>(List.of("Alpha"));

        ContentEntry entry = new ContentEntry(1, original);
        original.add("Bravo");

        assertEquals(List.of("Alpha"), entry.values());
    }

    @Test
    void theStoredValuesCannotBeChanged() {
        ContentEntry entry = new ContentEntry(1, new ArrayList<>(List.of("Alpha")));

        assertThrows(UnsupportedOperationException.class, () -> entry.values().add("Bravo"));
    }

    @Test
    void nullValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> new ContentEntry(1, null));
    }

    @Test
    void entriesWithTheSameContentAreEqualAndShareAHashCode() {
        ContentEntry first = new ContentEntry(3, List.of("Alpha"));
        ContentEntry second = new ContentEntry(3, List.of("Alpha"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void aDifferentIdOrValueMakesThemUnequal() {
        ContentEntry entry = new ContentEntry(3, List.of("Alpha"));

        assertNotEquals(entry, new ContentEntry(4, List.of("Alpha")));
        assertNotEquals(entry, new ContentEntry(3, List.of("Bravo")));
    }

    @Test
    void toStringShowsTheIdAndValues() {
        String text = new ContentEntry(3, List.of("Alpha")).toString();

        assertTrue(text.contains("3"));
        assertTrue(text.contains("Alpha"));
    }
}
