import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** The facility and agents assigned to one legacy mission. */
public final class MissionAssignment {
    private final int missionId;
    private final int facilityId;
    private final List<Integer> agentIds;

    public MissionAssignment(int missionId, int facilityId, List<Integer> agentIds) {
        if (missionId < 1) {
            throw new IllegalArgumentException("missionId must be positive");
        }
        if (facilityId < 1) {
            throw new IllegalArgumentException("facilityId must be positive");
        }
        Objects.requireNonNull(agentIds, "agentIds cannot be null");
        if (agentIds.isEmpty() || agentIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(agentIds).size() != agentIds.size()) {
            throw new IllegalArgumentException("agentIds must contain unique positive ids");
        }
        this.missionId = missionId;
        this.facilityId = facilityId;
        this.agentIds = List.copyOf(agentIds);
    }

    public int getMissionId() {
        return missionId;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public List<Integer> getAgentIds() {
        return agentIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MissionAssignment)) {
            return false;
        }
        MissionAssignment assignment = (MissionAssignment) other;
        return missionId == assignment.missionId && facilityId == assignment.facilityId
                && agentIds.equals(assignment.agentIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(missionId, facilityId, agentIds);
    }

    @Override
    public String toString() {
        return "MissionAssignment{" + "missionId=" + missionId
                + ", facilityId=" + facilityId + ", agentIds=" + agentIds + '}';
    }
}
