import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The audit log and the mission table living in one real SQLite file. */
class AuditLogIntegrationTest {

    private static final String TSV = "Title\tDate\tText\n"
            + "Operation Sandtrap\t1970-11-03\tBug the diplomatic pouch in Rome.\n"
            + "The Warsaw Whisper\t1975-08-12\tIntercept the underground press.\n";

    @Test
    void auditLogAndMissionsShareOneDatabaseFile(@TempDir Path folder) throws Exception {
        Path database = folder.resolve("missions.db");
        SqliteMissionRepository missions = new SqliteMissionRepository(new TsvFileHandler(), database, "m.tsv");
        SqliteAuditLogRepository auditLog = new SqliteAuditLogRepository(database);

        missions.initialize();
        auditLog.initialize();
        auditLog.record(AuditEventType.READ, AuditContentType.BRIEF, 1, "agent");

        assertEquals(2, missions.getAllMissions().size());
        assertEquals(1, auditLog.getAllEntries().size());
    }

    @Test
    void entriesSurviveARestart(@TempDir Path folder) throws Exception {
        Path database = folder.resolve("audit.db");
        SqliteAuditLogRepository first = new SqliteAuditLogRepository(database);
        first.initialize();
        first.record(new AuditLogEntry(AuditEventType.CREATE, AuditContentType.AGENT, 4, "bob",
                LocalDateTime.of(2026, 10, 20, 9, 30, 0)));

        SqliteAuditLogRepository second = new SqliteAuditLogRepository(database);
        second.initialize();

        assertEquals(1, second.getAllEntries().size());
        assertEquals("bob", second.getAllEntries().get(0).getUsername());
    }

    private static class TsvFileHandler implements FileHandler {
        @Override
        public String getFileList() {
            return "";
        }

        @Override
        public String readFile(int fileNumber) {
            return null;
        }

        @Override
        public String readKey(String keyFileName) {
            return null;
        }

        @Override
        public String readText(String relativePath) {
            return TSV;
        }
    }
}