import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogRepositoryTest {

    @Test
    void shortRecordMethodBuildsEntryStampedWithCurrentTime() throws AuditLogException {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        doCallRealMethod().when(repository).record(any(AuditEventType.class), any(AuditContentType.class),
                anyInt(), any(String.class));
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        repository.record(AuditEventType.CREATE, AuditContentType.FACILITY, 8, "agent");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).record(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertEquals(AuditEventType.CREATE, entry.getEventType());
        assertEquals(AuditContentType.FACILITY, entry.getContentType());
        assertEquals(8, entry.getContentId());
        assertEquals("agent", entry.getUsername());
        assertNotNull(entry.getTimestamp());
        assertEquals(false, entry.getTimestamp().isBefore(before));
    }
}