import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionAssignmentTest {

    @Test
    void constructorAndGettersExposeRelationships() {
        MissionAssignment assignment = new MissionAssignment(10, 2, List.of(3, 7));

        assertEquals(10, assignment.getMissionId());
        assertEquals(2, assignment.getFacilityId());
        assertEquals(List.of(3, 7), assignment.getAgentIds());
    }

    @Test
    void agentIdsAreDefensivelyCopiedAndCannotBeChanged() {
        ArrayList<Integer> ids = new ArrayList<>(List.of(3, 7));
        MissionAssignment assignment = new MissionAssignment(10, 2, ids);

        ids.clear();

        assertEquals(List.of(3, 7), assignment.getAgentIds());
        assertThrows(UnsupportedOperationException.class, () -> assignment.getAgentIds().add(9));
    }

    @Test
    void valueMethodsUseAllRelationships() {
        MissionAssignment first = new MissionAssignment(10, 2, List.of(3, 7));
        MissionAssignment same = new MissionAssignment(10, 2, List.of(3, 7));
        MissionAssignment different = new MissionAssignment(10, 2, List.of(3));

        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "not an assignment");
        assertTrue(first.toString().contains("missionId=10"));
    }

    @Test
    void constructorRequiresOneFacilityAndAtLeastOneValidAgent() {
        assertThrows(IllegalArgumentException.class,
                () -> new MissionAssignment(0, 1, List.of(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new MissionAssignment(1, 0, List.of(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new MissionAssignment(1, 1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MissionAssignment(1, 1, List.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new MissionAssignment(1, 1, List.of(2, 2)));
        assertThrows(NullPointerException.class,
                () -> new MissionAssignment(1, 1, null));
    }
}
