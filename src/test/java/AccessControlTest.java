import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessControlTest {

    private AccessControl access;
    private Session admin;
    private Session standard;

    @BeforeEach
    void setUp() {
        access = new AccessControl();
        admin = new Session("boss", Role.ADMIN);
        standard = new Session("agent", Role.STANDARD);
    }

    /* reading is open to anyone signed in */

    @Test
    void adminsCanRead() {
        assertTrue(access.canReadContent(admin));
    }

    @Test
    void standardUsersCanRead() {
        assertTrue(access.canReadContent(standard));
    }

    @Test
    void nobodySignedInCannotRead() {
        assertFalse(access.canReadContent(null));
    }

    /* creating content */

    @Test
    void adminsCanCreateContent() {
        assertTrue(access.canCreateContent(admin));
    }

    @Test
    void standardUsersCannotCreateContent() {
        assertFalse(access.canCreateContent(standard));
    }

    @Test
    void nobodySignedInCannotCreateContent() {
        assertFalse(access.canCreateContent(null));
    }

    /* creating users */

    @Test
    void adminsCanCreateUsers() {
        assertTrue(access.canCreateUsers(admin));
    }

    @Test
    void standardUsersCannotCreateUsers() {
        assertFalse(access.canCreateUsers(standard));
    }

    @Test
    void nobodySignedInCannotCreateUsers() {
        assertFalse(access.canCreateUsers(null));
    }

    /* the audit log */

    @Test
    void adminsCanViewTheAuditLog() {
        assertTrue(access.canViewAuditLog(admin));
    }

    @Test
    void standardUsersCannotViewTheAuditLog() {
        assertFalse(access.canViewAuditLog(standard));
    }

    @Test
    void nobodySignedInCannotViewTheAuditLog() {
        assertFalse(access.canViewAuditLog(null));
    }

    /* changing roles */

    @Test
    void adminsCanManageRoles() {
        assertTrue(access.canManageRoles(admin));
    }

    @Test
    void standardUsersCannotManageRoles() {
        assertFalse(access.canManageRoles(standard));
    }

    @Test
    void nobodySignedInCannotManageRoles() {
        assertFalse(access.canManageRoles(null));
    }
}
