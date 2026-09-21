import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvalidCipherExceptionTest {
    @Test void carriesMessage() {
        assertEquals("bad key", new InvalidCipherException("bad key").getMessage());
    }

    @Test void isUnchecked() {
        assertTrue(new InvalidCipherException("x") instanceof RuntimeException);
    }
}