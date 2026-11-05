package view;

import controller.ProgramControlException;
import model.Agent;
import model.Message;
import model.MessageService;
import model.Session;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

/** The two terminal screens for messaging: write to an agent, and read your own mail. */
public class MessageMenu {

    private final MessageService messages;

    public MessageMenu(MessageService messages) {
        this.messages = Objects.requireNonNull(messages, "messages cannot be null");
    }

    /** Pick an agent, write the message, send it. */
    public void send(Scanner in, PrintStream out, Session session) {
        try {
            Map<Integer, String> agents = messages.recipients(session);
            if (agents.isEmpty()) {
                out.println("No agents available.");
                return;
            }
            out.println("Agents:");
            agents.forEach((id, name) -> out.println(id + ". " + name));

            String idText = prompt(in, out, "Agent ID: ");
            if (idText == null) {
                out.println("Message cancelled.");
                return;
            }
            int agentId = Integer.parseInt(idText);
            String body = prompt(in, out, "Message: ");
            if (body == null) {
                out.println("Message cancelled.");
                return;
            }
            messages.send(agentId, body, session);
            out.println("Message sent to " + agents.get(agentId) + ".");
        } catch (NumberFormatException exception) {
            out.println("Enter a numeric agent ID.");
        } catch (ProgramControlException exception) {
            out.println("Error: " + exception.getMessage());
        }
    }

    /** Messages addressed to the agent whose name matches the logged-in username. */
    public void showInbox(PrintStream out, Session session) {
        try {
            var inbox = messages.inbox(session);
            if (inbox.isEmpty()) {
                out.println("No messages.");
                return;
            }
            for (Message message : inbox) {
                out.println(message.getCreatedAt().toString().replace('T', ' ')
                        + " | from " + message.getSender() + " | " + message.getBody());
            }
        } catch (ProgramControlException exception) {
            out.println("Error: " + exception.getMessage());
        }
    }

    private String prompt(Scanner in, PrintStream out, String message) {
        out.print(message);
        return in.hasNextLine() ? in.nextLine().trim() : null;
    }
}
