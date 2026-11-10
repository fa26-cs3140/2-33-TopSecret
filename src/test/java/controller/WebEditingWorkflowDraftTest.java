package controller;

import model.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Disabled("Homework 7 Week 2: implement WebEditingWorkflow and connect createWorkflow before enabling")
class WebEditingWorkflowDraftTest {
    private final ContentService service = mock(ContentService.class);
    private final Session admin = new Session("admin", Role.ADMIN);

    @Test
    void newFormsDoNotReadOrWriteModelRecords() throws Exception {
        WebEditingWorkflow workflow = createWorkflow();
        for (ContentType type : ContentType.values()) {
            assertEquals(WebEditForm.newRecord(type), workflow.newForm(type, admin));
        }
        verifyNoInteractions(service);
    }

    @Test
    void editFormsReadTheCurrentRecord() throws Exception {
        ContentEntry entry = new ContentEntry(7, List.of("North", "NO"));
        when(service.read(ContentType.FACILITY, 7, admin)).thenReturn(entry);
        assertEquals(WebEditForm.editRecord(ContentType.FACILITY, entry),
                createWorkflow().editForm(ContentType.FACILITY, 7, admin));
        verify(service).read(ContentType.FACILITY, 7, admin);
        verifyNoMoreInteractions(service);
    }

    @Test
    void deniedSessionsCannotPrepareOrSubmitForms() throws Exception {
        WebEditingWorkflow workflow = createWorkflow();
        for (Session session : new Session[]{null, new Session("reader", Role.STANDARD)}) {
            assertThrows(ProgramControlException.class, () -> workflow.newForm(ContentType.FACILITY, session));
            assertThrows(ProgramControlException.class, () -> workflow.editForm(ContentType.FACILITY, 7, session));
            assertThrows(ProgramControlException.class,
                    () -> workflow.create(ContentType.FACILITY, List.of("North", "NO"), null, null, session));
            assertThrows(ProgramControlException.class,
                    () -> workflow.update(ContentType.FACILITY, 7, List.of("North", "NO"), session));
        }
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryWritesDelegateToTheAuditedModelOperations() throws Exception {
        WebEditingWorkflow workflow = createWorkflow();
        List<String> values = List.of("North", "NO");
        workflow.create(ContentType.FACILITY, values, null, null, admin);
        workflow.update(ContentType.FACILITY, 7, values, admin);
        verify(service).add(ContentType.FACILITY, values, admin);
        verify(service).update(ContentType.FACILITY, 7, values, admin);
        verifyNoMoreInteractions(service);
    }

    @Test
    void missionCreationUsesOneAtomicModelCall() throws Exception {
        List<String> values = List.of("Operation", "Details", "2026-11-10");
        createWorkflow().create(ContentType.MISSION, values, 2, 4, admin);
        verify(service).addMission(values, 2, 4, admin);
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingMissionChoicesAndInvalidIdsCannotWrite() throws Exception {
        WebEditingWorkflow workflow = createWorkflow();
        List<String> values = List.of("Operation", "Details", "2026-11-10");
        assertThrows(ProgramControlException.class,
                () -> workflow.create(ContentType.MISSION, values, null, 4, admin));
        assertThrows(ProgramControlException.class,
                () -> workflow.create(ContentType.MISSION, values, 2, null, admin));
        assertThrows(ProgramControlException.class,
                () -> workflow.create(ContentType.MISSION, values, 0, 4, admin));
        assertThrows(ProgramControlException.class,
                () -> workflow.update(ContentType.MISSION, 0, values, admin));
        verifyNoInteractions(service);
    }

    @Test
    void missingRecordIsNotReplacedWithAnEmptyEditForm() throws Exception {
        when(service.read(ContentType.FACILITY, 7, admin))
                .thenThrow(new ContentNotFoundException(7));
        WebEditingWorkflow workflow = createWorkflow();
        assertThrows(ContentNotFoundException.class,
                () -> workflow.editForm(ContentType.FACILITY, 7, admin));
        verify(service).read(ContentType.FACILITY, 7, admin);
        verifyNoMoreInteractions(service);
    }

    private WebEditingWorkflow createWorkflow() {
        throw new UnsupportedOperationException("Week 2: construct the implementation with service here.");
    }
}
