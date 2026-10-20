import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationServiceMockitoTest {
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

    @Test
    void createUserSavesNewAccountWithoutPassword() throws CredentialException {
        when(store.exists()).thenReturn(true);
        when(store.readAll()).thenReturn(List.of(new Credential("agent", "Secret1")));

        auth.createUser("bob");

        List<Credential> saved = savedList();
        assertEquals(2, saved.size());
        assertEquals("bob", saved.get(1).getUsername());
        assertFalse(saved.get(1).hasPassword());
    }

    @Test
    void duplicateUserIsNeverSaved() throws CredentialException {
        when(store.exists()).thenReturn(true);
        when(store.readAll()).thenReturn(List.of(new Credential("agent", "Secret1")));

        assertThrows(CredentialException.class, () -> auth.createUser("agent"));

        verify(store, never()).writeAll(anyList());
    }

    @Test
    void invalidUsernameNeverTouchesTheStore() {
        assertThrows(CredentialException.class, () -> auth.createUser("x"));

        verifyNoInteractions(store);
    }

    @Test
    void storeFailureDuringLoginIsPassedOn() throws CredentialException {
        when(store.readAll()).thenThrow(new CredentialException("boom"));

        assertThrows(CredentialException.class, () -> auth.authenticate("agent", "Secret1"));
    }

    @Test
    void changePasswordSavesOnlyTheChangedAccount() throws CredentialException {
        when(store.readAll()).thenReturn(List.of(
                new Credential("agent", "Secret1"), new Credential("bob", "Bobpass1")));

        auth.changePassword("agent", "Secret1", "Newpass1", "Newpass1");

        List<Credential> saved = savedList();
        assertEquals("Newpass1", saved.get(0).getPassword());
        assertEquals("Bobpass1", saved.get(1).getPassword());
    }

    @Test
    void wrongCurrentPasswordIsNeverSaved() throws CredentialException {
        when(store.readAll()).thenReturn(List.of(new Credential("agent", "Secret1")));

        assertThrows(CredentialException.class,
                () -> auth.changePassword("agent", "wrong", "Newpass1", "Newpass1"));

        verify(store, never()).writeAll(anyList());
    }

    @Test
    void existingFileWithAccountsCountsAsHavingAccounts() throws CredentialException {
        when(store.exists()).thenReturn(true);
        when(store.readAll()).thenReturn(List.of(new Credential("agent", "Secret1")));

        assertTrue(auth.hasAccounts());
    }
}