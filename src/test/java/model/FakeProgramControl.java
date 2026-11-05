package model;

import model.*;
import view.*;
import controller.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Stand-in for B's control layer so the UI can be tested on its own.
 * A test sets up what this should return, then checks afterwards what the UI
 * actually asked for.
 */
public class FakeProgramControl implements ProgramControl {

    private String errorMessage = null;

    private List<Mission> missions = new ArrayList<>();
    private List<Mission> searchResults = new ArrayList<>();
    private String lastSearchQuery = null;

    /* set up what it returns */

    public void setMissions(Mission... m) {
        this.missions = new ArrayList<>(Arrays.asList(m));
    }

    public void setSearchResults(Mission... m) {
        this.searchResults = new ArrayList<>(Arrays.asList(m));
    }

    /** Make the next call blow up, like the real thing would. */
    public void failWith(String message) {
        this.errorMessage = message;
    }

    /* check what the UI asked for */

    public String getLastSearchQuery() {
        return lastSearchQuery;
    }

    /* the actual interface */

    @Override
    public List<Mission> listMissions() throws ProgramControlException {
        if (errorMessage != null) {
            throw new ProgramControlException(errorMessage);
        }
        return missions;
    }

    @Override
    public List<Mission> searchMissions(String query) throws ProgramControlException {
        lastSearchQuery = query;
        if (errorMessage != null) {
            throw new ProgramControlException(errorMessage);
        }
        return searchResults;
    }
}
