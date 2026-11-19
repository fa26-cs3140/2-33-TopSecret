package view;

import model.Message;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messaging pages. These render through {@link HtmlView}'s page shell, so
 * they are styled and carry the same navigation as every other page rather
 * than a second shell of their own. That delegation is what these pin: before
 * it, the messaging pages were the one part of the web interface that no
 * styling or menu reached.
 */
class MessageHtmlViewTest {

    private static final LocalDateTime SENT = LocalDateTime.of(2026, 11, 19, 9, 30);

    private static final List<HtmlView.Link> LINKS =
            List.of(new HtmlView.Link("countries", "/content?type=countries"));

    private final MessageHtmlView plain = new MessageHtmlView();
    private final MessageHtmlView signedIn = new MessageHtmlView().withNav("hendricks", LINKS);

    private static Message unread(String sender, String body) {
        return new Message(1, 2, "Ghost", sender, body, SENT);
    }

    private static Message read(String sender, String body) {
        return new Message(2, 2, "Ghost", sender, body, SENT, SENT.plusHours(1));
    }

    private List<String> everyPage(MessageHtmlView view) {
        return List.of(
                view.inbox(List.of(unread("admin", "Body")), "token"),
                view.inbox(List.of(), "token"),
                view.detail(unread("admin", "Body")),
                view.compose(Map.of(2, "Ghost"), "token", null),
                view.compose(Map.of(), "token", "Nope"),
                view.error("Nope"));
    }

    @Test
    void everyMessagingPageIsStyledByTheSharedShell() {
        for (String page : everyPage(plain)) {
            assertTrue(page.contains("<style"), page);
            assertTrue(page.toLowerCase(Locale.ROOT).startsWith("<!doctype html>"), page);
        }
    }

    @Test
    void noMessagingPageCarriesScriptsOrImages() {
        for (String page : everyPage(signedIn)) {
            String lower = page.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("<script"), page);
            assertFalse(lower.contains("<img"), page);
            assertFalse(lower.contains("javascript:"), page);
            assertFalse(lower.contains("onclick"), page);
        }
    }

    @Test
    void everyMessagingPageCarriesTheNavigationAndLogOut() {
        for (String page : everyPage(signedIn)) {
            assertTrue(page.contains("action=\"/logout\""), page);
            assertTrue(page.contains("/content?type=countries"), page);
            assertTrue(page.contains("hendricks"), page);
        }
    }

    @Test
    void withNavAnswersASeparateViewAndLeavesTheOriginalAlone() {
        MessageHtmlView derived = plain.withNav("hendricks", LINKS);

        assertNotSame(plain, derived);
        assertFalse(plain.error("Nope").contains("action=\"/logout\""));
        assertTrue(derived.error("Nope").contains("action=\"/logout\""));
    }

    @Test
    void theInboxMarksWhatHasBeenReadAndWhatHasNot() {
        String page = plain.inbox(List.of(unread("admin", "First"), read("admin", "Second")), "token");

        assertTrue(page.contains("Unread"), page);
        assertTrue(page.contains(">Read<"), page);
        assertTrue(page.contains("name=\"csrf\" value=\"token\""), page);
        assertTrue(page.contains("action=\"/messages/open\""), page);
    }

    @Test
    void anEmptyInboxSaysSoRatherThanRenderingATable() {
        String page = plain.inbox(List.of(), "token");

        assertTrue(page.contains("No messages available."), page);
        assertFalse(page.contains("<table"), page);
    }

    @Test
    void aMessageDetailShowsWhoSentItAndItsBody() {
        String page = plain.detail(unread("admin", "The pouch is in the locker."));

        assertTrue(page.contains("admin"), page);
        assertTrue(page.contains("Ghost"), page);
        assertTrue(page.contains("The pouch is in the locker."), page);
        assertTrue(page.contains(SENT.toString()), page);
    }

    @Test
    void theComposeFormListsRecipientsAndCarriesTheToken() {
        String page = plain.compose(Map.of(2, "Ghost"), "token", null);

        assertTrue(page.contains("<option value=\"2\">Ghost</option>"), page);
        assertTrue(page.contains("name=\"csrf\" value=\"token\""), page);
        assertTrue(page.contains("<textarea"), page);
        assertTrue(page.contains("action=\"/messages/send\""), page);
    }

    @Test
    void composeWithNoAgentsSaysSoAndOffersNoForm() {
        String page = plain.compose(Map.of(), "token", null);

        assertTrue(page.contains("No agents available."), page);
        assertFalse(page.contains("<textarea"), page);
    }

    @Test
    void aComposeErrorIsShownAsAnAlert() {
        String page = plain.compose(Map.of(2, "Ghost"), "token", "Choose an agent");

        assertTrue(page.contains("role=\"alert\""), page);
        assertTrue(page.contains("Choose an agent"), page);
    }

    @Test
    void everyPieceOfBorrowedTextIsEscaped() {
        String page = plain.inbox(List.of(unread("<admin>", "a & b")), "token");

        assertTrue(page.contains("&lt;admin&gt;"), page);
        assertFalse(page.contains("<admin>"), page);

        String detail = plain.detail(unread("admin", "<b>bold</b> & more"));
        assertTrue(detail.contains("&lt;b&gt;bold&lt;/b&gt; &amp; more"), detail);

        String compose = plain.compose(Map.of(2, "<Ghost>"), "to\"ken", null);
        assertTrue(compose.contains("&lt;Ghost&gt;"), compose);
        assertTrue(compose.contains("&quot;"), compose);

        String error = plain.error("<nope>");
        assertTrue(error.contains("&lt;nope&gt;"), error);
        assertFalse(error.contains("<nope>"), error);
    }
}
