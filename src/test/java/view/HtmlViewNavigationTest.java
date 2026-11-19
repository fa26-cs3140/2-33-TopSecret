package view;

import controller.WebEditForm;
import model.ContentType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The navigation block that every signed-in page carries.
 *
 * The view is shared by every thread the HTTP server serves, so the navigation
 * cannot be a field a request sets. withNav answers a separate view carrying
 * it, and the original keeps rendering the unauthenticated pages.
 */
class HtmlViewNavigationTest {

    private static final List<HtmlView.Link> LINKS = List.of(
            new HtmlView.Link("briefs", "/content?type=briefs"),
            new HtmlView.Link("countries", "/content?type=countries"),
            new HtmlView.Link("Messaging", "/messages"));

    private final HtmlView plain = new HtmlView();
    private final HtmlView signedIn = new HtmlView().withNav("hendricks", LINKS);

    /** Every page a signed-in user can reach. */
    private List<String> authenticatedPages() {
        return List.of(
                signedIn.menu("hendricks", LINKS),
                signedIn.list("countries", LINKS),
                signedIn.record("facilities #1", List.of(new HtmlView.Field("name", "Blackwood"))),
                signedIn.audit(List.of()),
                signedIn.editForm(WebEditForm.newRecord(ContentType.COUNTRY), "token",
                        Map.of(), Map.of(), null));
    }

    @Test
    void withNavAnswersASeparateViewAndLeavesTheOriginalAlone() {
        HtmlView derived = plain.withNav("hendricks", LINKS);

        assertNotSame(plain, derived);
        assertFalse(plain.list("countries", List.of()).contains("Log out"));
        assertTrue(derived.list("countries", List.of()).contains("Log out"));
    }

    @Test
    void everyAuthenticatedPageCarriesTheNavigationBlock() {
        for (String page : authenticatedPages()) {
            assertTrue(page.contains("<nav"), page);
            assertTrue(page.contains("/content?type=countries"), page);
            assertTrue(page.contains("/messages"), page);
        }
    }

    @Test
    void everyAuthenticatedPageOffersLogOut() {
        for (String page : authenticatedPages()) {
            assertTrue(page.contains("action=\"/logout\""), page);
            assertTrue(page.contains("Log out"), page);
        }
    }

    @Test
    void logOutIsASubmittedFormRatherThanALink() {
        String page = signedIn.menu("hendricks", LINKS);

        assertTrue(page.contains("<form action=\"/logout\" method=\"post\">"));
        assertFalse(page.contains("<a href=\"/logout\""));
    }

    @Test
    void theSignedInUserIsNamedOnEveryAuthenticatedPage() {
        for (String page : authenticatedPages()) {
            assertTrue(page.contains("hendricks"), page);
        }
    }

    @Test
    void anAwkwardUsernameIsEscapedInTheNavigation() {
        String page = new HtmlView().withNav("<script>x</script>", LINKS).audit(List.of());

        assertTrue(page.contains("&lt;script&gt;"));
        assertFalse(page.toLowerCase(Locale.ROOT).contains("<script>x"));
    }

    @Test
    void navigationLabelsAndAddressesAreEscaped() {
        String page = new HtmlView()
                .withNav("reader", List.of(new HtmlView.Link("a & b", "/x?y=1&z=\"2\"")))
                .audit(List.of());

        assertTrue(page.contains("a &amp; b"));
        assertTrue(page.contains("&amp;z=&quot;2&quot;"));
    }

    @Test
    void theLogInAndErrorPagesCarryNoNavigation() {
        for (String page : List.of(plain.login(null), plain.error("Nope"),
                signedIn.login(null), signedIn.error("Nope"))) {
            assertFalse(page.contains("action=\"/logout\""), page);
            assertFalse(page.contains("/content?type=countries"), page);
        }
    }

    @Test
    void thePerPageBackToMenuAnchorIsRetired() {
        for (String page : authenticatedPages()) {
            assertFalse(page.contains("Back to menu"), page);
        }
    }

    @Test
    void anEmptyLinkListStillRendersLogOut() {
        String page = new HtmlView().withNav("reader", List.of()).audit(List.of());

        assertTrue(page.contains("action=\"/logout\""));
    }

    @Test
    void theNavigationIsTheSameOnEveryPage() {
        List<String> blocks = authenticatedPages().stream()
                .map(page -> page.substring(page.indexOf("<nav"), page.indexOf("</nav>")))
                .distinct().toList();

        assertEquals(1, blocks.size(), "the navigation block should not vary between pages");
    }
}
