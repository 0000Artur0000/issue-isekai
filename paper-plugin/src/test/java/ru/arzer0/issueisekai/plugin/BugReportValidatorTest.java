package ru.arzer0.issueisekai.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BugReportValidatorTest {
    @Test
    void bypassesCooldownWithoutChangingIt() {
        var validator = new BugReportValidator(
                List.of(new PluginConfig.Category("gameplay", "Gameplay")),
                Duration.ofMinutes(1));
        UUID playerId = UUID.randomUUID();

        assertTrue(validator.validate(playerId, "gameplay", "valid report").accepted());
        assertTrue(validator
                .validate(playerId, "gameplay", "valid report", true)
                .accepted());
        assertFalse(validator.validate(playerId, "gameplay", "valid report").accepted());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void validatesBoundariesAndCooldown(
            String category, String description, boolean repeat, boolean accepted, String normalizedDescription) {
        var validator = new BugReportValidator(
                List.of(new PluginConfig.Category("gameplay", "Gameplay")), Duration.ofMinutes(1));
        UUID playerId = UUID.randomUUID();

        BugReportValidator.Result result = validator.validate(playerId, category, description);
        if (repeat) {
            result = validator.validate(playerId, category, description);
        }

        assertEquals(accepted, result.accepted());
        assertEquals(normalizedDescription, result.description());
        if (accepted) {
            assertNull(result.error());
        }
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(" gameplay ", "  1234567890  ", false, true, "1234567890"),
                Arguments.of("gameplay", "x".repeat(1000), false, true, "x".repeat(1000)),
                Arguments.of("gameplay", "x".repeat(9), false, false, "x".repeat(9)),
                Arguments.of("gameplay", "x".repeat(1001), false, false, "x".repeat(1001)),
                Arguments.of("missing", "valid report", false, false, "valid report"),
                Arguments.of("gameplay", "valid report", true, false, "valid report"));
    }
}
