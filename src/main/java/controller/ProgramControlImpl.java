package controller;

import model.Mission;
import model.MissionRepository;
import model.MissionRepositoryException;
import model.MissionSearch;
import model.MissionSearchException;

import java.util.List;
import java.util.Objects;

public class ProgramControlImpl implements ProgramControl {

    private final MissionSearch missionSearch;
    private final MissionRepository missionRepository;

    public ProgramControlImpl() {
        this(query -> {
            throw new MissionSearchException("Mission database is not connected.");
        });
    }

    public ProgramControlImpl(MissionSearch missionSearch) {
        this(missionSearch, () -> {
            throw new MissionRepositoryException("Mission database is not connected.");
        });
    }

    public ProgramControlImpl(MissionSearch missionSearch, MissionRepository missionRepository) {
        this.missionSearch = Objects.requireNonNull(missionSearch, "missionSearch cannot be null");
        this.missionRepository = Objects.requireNonNull(missionRepository, "missionRepository cannot be null");
    }

    @Override
    public List<Mission> searchMissions(String query) throws ProgramControlException {
        try {
            return missionSearch.search(query);
        } catch (MissionSearchException exception) {
            throw new ProgramControlException(exception.getMessage(), exception);
        }
    }

    @Override
    public List<Mission> listMissions() throws ProgramControlException {
        try {
            List<Mission> missions = missionRepository.getAllMissions();
            return missions == null ? List.of() : missions;
        } catch (MissionRepositoryException e) {
            throw new ProgramControlException(e.getMessage(), e);
        }
    }
}
