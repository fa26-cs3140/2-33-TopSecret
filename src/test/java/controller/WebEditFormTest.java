package controller;

import model.ContentEntry;
import model.ContentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebEditFormTest {
    @Test
    void newFormsHaveEmptyValuesForEveryCurrentType() {
        for (ContentType type : ContentType.values()) {
            WebEditForm form = WebEditForm.newRecord(type);
            assertEquals(type, form.type());
            assertNull(form.recordId());
            assertEquals(Collections.nCopies(type.fields.size(), ""), form.values());
        }
    }

    @Test
    void editFormsKeepTheRecordIdFieldOrderAndOptionalBlanks() {
        for (ContentType type : ContentType.values()) {
            List<String> values = type.fields.stream()
                    .map(field -> field.equals("notes") ? "" : field + " <&\"'>")
                    .toList();
            WebEditForm form = WebEditForm.editRecord(type, new ContentEntry(7, values));
            assertEquals(type, form.type());
            assertEquals(7, form.recordId());
            assertEquals(values, form.values());
        }
    }

    @Test
    void submittedValuesCannotBeChangedThroughEitherListReference() {
        List<String> values = new ArrayList<>(List.of("North", "NO"));
        WebEditForm form = new WebEditForm(ContentType.FACILITY, 3, values);
        values.set(0, "Changed");
        assertEquals(List.of("North", "NO"), form.values());
        assertThrows(UnsupportedOperationException.class, () -> form.values().set(0, "Changed"));
    }

    @Test
    void malformedDisplayDataIsRejectedBeforeRendering() {
        assertThrows(NullPointerException.class, () -> new WebEditForm(null, null, List.of()));
        assertThrows(NullPointerException.class, () -> new WebEditForm(ContentType.BRIEF, null, null));
        assertThrows(NullPointerException.class, () -> WebEditForm.newRecord(null));
        assertThrows(NullPointerException.class, () -> WebEditForm.editRecord(ContentType.BRIEF, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WebEditForm(ContentType.FACILITY, null, List.of("Missing abbreviation")));
        for (int id : new int[]{0, -1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new WebEditForm(ContentType.FACILITY, id, List.of("North", "NO")));
        }
        List<String> values = new ArrayList<>(List.of("North", "NO"));
        values.set(1, null);
        assertThrows(NullPointerException.class, () -> new WebEditForm(ContentType.FACILITY, null, values));
    }
}
