import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class AnimatedEffectsTest {
    private String capture(Consumer<PrintStream> action) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        action.accept(new PrintStream(buffer));
        return buffer.toString();
    }

    @Test
    void bannerPrintsTitle() {
        assertTrue(capture(o -> new AnimatedEffects().banner(o)).contains("TOP SECRET"));
    }

    @Test
    void loadingPrintsMessageThenDone() {
        assertTrue(capture(o -> new AnimatedEffects().loading(o, "Working")).contains("Working done"));
    }

    @Test
    void loadingRestoresInterruptFlagWhenInterrupted() {
        Thread.currentThread().interrupt();
        capture(o -> new AnimatedEffects().loading(o, "Working"));
        assertTrue(Thread.interrupted());   // also clears the flag for later tests
    }

    @Test
    void noneEffectsPrintNothing() {
        assertEquals("", capture(o -> { Effects.NONE.banner(o); Effects.NONE.loading(o, "x"); }));
    }
}