package controller;

import model.ContentType;
import model.Session;

import java.util.List;

public interface WebEditingWorkflow {
    WebEditForm newForm(ContentType type, Session session) throws ProgramControlException;

    WebEditForm editForm(ContentType type, int id, Session session) throws ProgramControlException;

    void create(ContentType type, List<String> values, Integer facilityId, Integer agentId,
                Session session) throws ProgramControlException;

    void update(ContentType type, int id, List<String> values, Session session)
            throws ProgramControlException;
}
