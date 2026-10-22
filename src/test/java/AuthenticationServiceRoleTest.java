import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Roles, promotion and demotion, with the credential store mocked out. */
class AuthenticationServiceRoleTest {

    private CredentialStore store;
    private AuthenticationService auth;

    @BeforeEach
    void setUp() {
        store = mock(CredentialStore.class);
        auth = new AuthenticationService(store);
    }

    @SuppressWarnings("unchecked")
    private List<Credential> savedList() throws CredentialException {
        ArgumentCaptor<List<Credential>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).writeAll(captor.capture());
        return captor.getValue();
    }

    private void accountsAre(Credential... accounts) throws CredentialException {
        when(store.exists()).thenReturn(true);
        when(store.readAll()).thenReturn(List.of(accounts));
    }

    /* who becomes what */

    @Test
    void theFirstAccountIsAnAdmin() throws CredentialException {
        when(store.exists()).thenReturn(false);

        auth.createCredentials("boss", "Secret1");

        assertEquals(Role.ADMIN, savedList().get(0).getRole());
    }

    @Test
    void lateUsersAreStandard() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        auth.createUser("agent");

        assertEquals(Role.STANDARD, savedList().get(1).getRole());
    }

    /* reading roles back */

    @Test
    void roleOfReturnsTheStoredRole() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("agent", "Secret1", Role.STANDARD));

        assertEquals(Role.ADMIN, auth.roleOf("boss"));
        assertEquals(Role.STANDARD, auth.roleOf("agent"));
    }

    @Test
    void roleOfRejectsAnUnknownUser() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.roleOf("nobody"));
    }

    @Test
    void sessionForCarriesTheUsernameAndRole() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        Session session = auth.sessionFor("boss");

        assertEquals("boss", session.getUsername());
        assertEquals(Role.ADMIN, session.getRole());
        assertTrue(session.isAdmin());
    }

    @Test
    void sessionForRejectsAnUnknownUser() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.sessionFor("nobody"));
    }

    @Test
    void listUsernamesReturnsEveryAccountInOrder() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("agent", "", Role.STANDARD));

        assertEquals(List.of("boss", "agent"), auth.listUsernames());
    }

    /* promotion */

    @Test
    void anAdminCanPromoteAStandardUser() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("agent", "Secret1", Role.STANDARD));

        auth.promote("boss", "agent");

        assertEquals(Role.ADMIN, savedList().get(1).getRole());
    }

    @Test
    void promotingKeepsThePassword() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("agent", "Agentpw1", Role.STANDARD));

        auth.promote("boss", "agent");

        assertEquals("Agentpw1", savedList().get(1).getPassword());
    }

    @Test
    void aStandardUserCannotPromoteAnyone() throws CredentialException {
        accountsAre(new Credential("agent", "Secret1", Role.STANDARD),
                new Credential("other", "Secret1", Role.STANDARD));

        assertThrows(CredentialException.class, () -> auth.promote("agent", "other"));
        verify(store, never()).writeAll(anyList());
    }

    @Test
    void anUnknownActorCannotPromoteAnyone() throws CredentialException {
        accountsAre(new Credential("agent", "Secret1", Role.STANDARD));

        assertThrows(CredentialException.class, () -> auth.promote("ghost", "agent"));
        verify(store, never()).writeAll(anyList());
    }

    @Test
    void promotingAnUnknownUserFails() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.promote("boss", "nobody"));
        verify(store, never()).writeAll(anyList());
    }

    @Test
    void promotingSomeoneWhoIsAlreadyAnAdminFails() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("deputy", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.promote("boss", "deputy"));
        verify(store, never()).writeAll(anyList());
    }

    /* demotion */

    @Test
    void anAdminCanDemoteAnotherAdmin() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("deputy", "Secret1", Role.ADMIN));

        auth.demote("boss", "deputy");

        assertEquals(Role.STANDARD, savedList().get(1).getRole());
    }

    @Test
    void demotingSomeoneWhoIsAlreadyStandardFails() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN),
                new Credential("agent", "Secret1", Role.STANDARD));

        assertThrows(CredentialException.class, () -> auth.demote("boss", "agent"));
        verify(store, never()).writeAll(anyList());
    }

    @Test
    void demotingAnUnknownUserFails() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.demote("boss", "nobody"));
        verify(store, never()).writeAll(anyList());
    }

    /* the rule that keeps one admin standing */

    @Test
    void anAdminCannotDemoteThemselves() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        CredentialException thrown =
                assertThrows(CredentialException.class, () -> auth.demote("boss", "boss"));

        assertTrue(thrown.getMessage().contains("your own role"));
        verify(store, never()).writeAll(anyList());
    }

    @Test
    void anAdminCannotPromoteThemselvesEither() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        assertThrows(CredentialException.class, () -> auth.promote("boss", "boss"));
        verify(store, never()).writeAll(anyList());
    }

    /* a password change must not quietly change a role */

    @Test
    void settingTheFirstPasswordKeepsTheRole() throws CredentialException {
        accountsAre(new Credential("deputy", "", Role.ADMIN));

        auth.setInitialPassword("deputy", "Secret1", "Secret1");

        assertEquals(Role.ADMIN, savedList().get(0).getRole());
    }

    @Test
    void changingAPasswordKeepsTheRole() throws CredentialException {
        accountsAre(new Credential("boss", "Secret1", Role.ADMIN));

        auth.changePassword("boss", "Secret1", "Newpass1", "Newpass1");

        assertEquals(Role.ADMIN, savedList().get(0).getRole());
    }
}
