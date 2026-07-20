package ru.arzer0.issueisekai.panel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class PanelLocaleTest {
    @Test
    void loadsBothBundlesAndRejectsUnknownLocale() {
        assertEquals("Требуется вход", new PanelLocale("ru").error("UNAUTHORIZED").message());
        assertEquals("Authentication required", new PanelLocale("en").error("UNAUTHORIZED").message());
        assertEquals(
                ResourceBundle.getBundle("messages", Locale.forLanguageTag("ru")).keySet(),
                ResourceBundle.getBundle("messages", Locale.forLanguageTag("en")).keySet());
        assertThrows(IllegalArgumentException.class, () -> new PanelLocale("de"));
    }
}
