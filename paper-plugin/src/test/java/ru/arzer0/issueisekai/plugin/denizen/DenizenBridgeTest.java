package ru.arzer0.issueisekai.plugin.denizen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DenizenBridgeTest {
    @Test
    void mainPluginLoadsWithoutDenizen() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.denizenscript.denizen.Denizen"));
        assertDoesNotThrow(() -> Class.forName("ru.arzer0.issueisekai.plugin.IssueIsekaiPlugin"));
    }
}
