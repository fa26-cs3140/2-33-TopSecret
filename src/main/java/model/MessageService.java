package model;

import controller.ProgramControlException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leaving messages for agents and reading your own.
 *
 * A message is addressed to an agent. A user can read it when their username
 * equals that agent's name, ignoring case.
 */
public interface MessageService {

    /** Agent ID to agent name, in ID order, for choosing who to write to. */
    Map<Integer, String> recipients(Session session) throws ProgramControlException;

    /** Leaves a message for an agent. The sender is the session's username. */
    void send(int agentId, String body, Session session) throws ProgramControlException;

    /** Messages addressed to the agent named like the session's username, newest first. */
    List<Message> inbox(Session session) throws ProgramControlException;

    /** One message owned by the signed-in user; empty for unknown or foreign IDs. */
    Optional<Message> message(int id, Session session) throws ProgramControlException;

    /** Marks an owned message read once; false for unknown or foreign IDs. */
    boolean markRead(int id, Session session) throws ProgramControlException;
}
