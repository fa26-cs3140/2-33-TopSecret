package controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaunchModeTest {
    @Test
    void terminalIsTheDefault() {
        assertEquals(LaunchMode.TERMINAL, LaunchMode.fromArguments(null));
        assertEquals(LaunchMode.TERMINAL, LaunchMode.fromArguments(new String[0]));
    }

    @Test
    void webRequiresTheAssignmentSwitch() {
        assertEquals(LaunchMode.WEB, LaunchMode.fromArguments(new String[]{"-web"}));
    }

    @Test
    void passwordChangeRemainsSupported() {
        assertEquals(LaunchMode.CHANGE_PASSWORD,
                LaunchMode.fromArguments(new String[]{"--change-password"}));
    }

    @Test
    void invalidOrCombinedArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LaunchMode.fromArguments(new String[]{"--web"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchMode.fromArguments(new String[]{"-web", "--change-password"}));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchMode.fromArguments(new String[]{null}));
    }
}
