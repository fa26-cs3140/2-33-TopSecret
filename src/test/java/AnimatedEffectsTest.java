import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedEffectsTest {

    @Test
    void bannerAndInterruptedLoadingWriteToTheProvidedStream() {
        AnimatedEffects effects = new AnimatedEffects();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer);

        effects.banner(out);
        Thread.currentThread().interrupt();
        effects.loading(out, "Loading");

        assertTrue(buffer.toString().contains("TOP SECRET: MISSION ARCHIVE"));
        assertTrue(buffer.toString().contains("Loading"));
        assertTrue(Thread.currentThread().isInterrupted());
        assertTrue(Thread.interrupted());
    }
}
