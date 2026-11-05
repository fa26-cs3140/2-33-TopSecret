package controller;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryWebSessionStoreTest {
    @Test
    void tokensAreOpaqueUniqueAndInvalidatedIndependently() {
        InMemoryWebSessionStore store = new InMemoryWebSessionStore();
        String first = store.create("agent");
        String second = store.create("agent");
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(first, second);
        assertEquals(Optional.of("agent"), store.usernameFor(first));
        assertTrue(store.usernameFor(null).isEmpty());
        assertTrue(store.usernameFor("forged").isEmpty());
        store.invalidate(null);
        store.invalidate("unknown");
        store.invalidate(first);
        assertTrue(store.usernameFor(first).isEmpty());
        assertTrue(store.usernameFor(second).isPresent());
        store.clear();
        assertTrue(store.usernameFor(second).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.create(null));
        assertThrows(IllegalArgumentException.class, () -> store.create(" "));
    }

    @Test
    void concurrentSessionsRemainUniqueAndResolvable() throws Exception {
        InMemoryWebSessionStore store = new InMemoryWebSessionStore();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                tasks.add(() -> store.create("agent"));
            }
            Set<String> tokens = new HashSet<>();
            for (Future<String> result : executor.invokeAll(tasks)) {
                String token = result.get();
                assertEquals(Optional.of("agent"), store.usernameFor(token));
                tokens.add(token);
            }
            assertEquals(100, tokens.size());
        }
    }
}
