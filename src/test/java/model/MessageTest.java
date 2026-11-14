package model;

import model.*;
import view.*;
import controller.*;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private Message sample() {
        return new Message(1, 2, "Ghost", "admin", "Meet at dawn", NOW);
    }

    @Test
    void readTimeIsPartOfMessageState() {
        Message unread = sample();
        Message read = new Message(1, 2, "Ghost", "admin", "Meet at dawn", NOW, NOW.plusMinutes(1));
        assertEquals(null, unread.getReadAt());
        assertEquals(NOW.plusMinutes(1), read.getReadAt());
        assertNotEquals(unread, read);
    }

    @Test
    void exposesEveryField() {
        Message m = sample();
        assertEquals(1, m.getId());
        assertEquals(2, m.getAgentId());
        assertEquals("Ghost", m.getAgentName());
        assertEquals("admin", m.getSender());
        assertEquals("Meet at dawn", m.getBody());
        assertEquals(NOW, m.getCreatedAt());
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThrows(IllegalArgumentException.class, () -> new Message(0, 2, "Ghost", "a", "b", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Message(1, 0, "Ghost", "a", "b", NOW));
    }

    @Test
    void rejectsBlankOrNullText() {
        assertThrows(IllegalArgumentException.class, () -> new Message(1, 2, " ", "a", "b", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Message(1, 2, "Ghost", "", "b", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Message(1, 2, "Ghost", "a", "  ", NOW));
        assertThrows(NullPointerException.class, () -> new Message(1, 2, null, "a", "b", NOW));
        assertThrows(NullPointerException.class, () -> new Message(1, 2, "Ghost", "a", "b", null));
    }

    @Test
    void equalMessagesMatchAndHashAlike() {
        assertEquals(sample(), sample());
        assertEquals(sample().hashCode(), sample().hashCode());
        assertNotEquals(sample(), new Message(9, 2, "Ghost", "admin", "Meet at dawn", NOW));
        assertNotEquals(sample(), "not a message");
        Message m = sample();
        assertEquals(m, m);
    }

    @Test
    void toStringNamesTheMessageButNotItsBody() {
        String text = sample().toString();
        assertTrue(text.contains("Ghost"));
        assertFalse(text.contains("Meet at dawn"));
    }
}
