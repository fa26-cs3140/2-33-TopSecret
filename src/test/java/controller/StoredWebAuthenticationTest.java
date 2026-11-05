package controller;

import model.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoredWebAuthenticationTest {
    @Test
    void delegatesAccountValidationAndAuthenticationWithoutCreatingUsers() throws Exception {
        AuthenticationService auth = mock(AuthenticationService.class);
        StoredWebAuthentication web = new StoredWebAuthentication(auth);
        assertFalse(web.hasAccounts());
        verify(auth, never()).verifyStoredCredentialsAreValid();
        when(auth.hasAccounts()).thenReturn(true);
        assertTrue(web.hasAccounts());
        verify(auth).verifyStoredCredentialsAreValid();
        assertTrue(web.authenticate("agent", "wrong").isEmpty());
        when(auth.authenticate("agent", "Secret1")).thenReturn(true);
        assertEquals(Optional.of("agent"), web.authenticate("agent", "Secret1"));
        verify(auth, never()).createCredentials(any(), any());
        doThrow(new CredentialException("corrupt")).when(auth).verifyStoredCredentialsAreValid();
        assertThrows(IOException.class, web::hasAccounts);
        when(auth.authenticate(any(), any())).thenThrow(new CredentialException("storage error"));
        assertThrows(IOException.class, () -> web.authenticate("agent", "Secret1"));
    }
}
