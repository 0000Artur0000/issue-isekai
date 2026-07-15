package ru.arzer0.issueisekai.plugin.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import ru.arzer0.issueisekai.plugin.PluginConfig;

public final class BugReportDialog {
    private static final String CATEGORY_KEY = "category";
    private static final String DESCRIPTION_KEY = "description";
    private final List<PluginConfig.Category> categories;

    public BugReportDialog(List<PluginConfig.Category> categories) {
        this.categories = List.copyOf(categories);
    }

    public void open(Player player, BiConsumer<Player, Response> onSubmit) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(onSubmit);
        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Report a bug"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Describe what you expected, what happened, and how to reproduce it."),
                                400)))
                        .inputs(List.of(categoryInput(), descriptionInput()))
                        .build())
                .type(DialogType.confirmation(submitButton(onSubmit), cancelButton()))));
    }

    private DialogInput categoryInput() {
        var entries = new ArrayList<SingleOptionDialogInput.OptionEntry>(categories.size());
        for (int index = 0; index < categories.size(); index++) {
            PluginConfig.Category category = categories.get(index);
            entries.add(SingleOptionDialogInput.OptionEntry.create(
                    category.id(), Component.text(category.title()), index == 0));
        }
        return DialogInput.singleOption(CATEGORY_KEY, Component.text("Category"), entries)
                .width(400)
                .build();
    }

    private DialogInput descriptionInput() {
        return DialogInput.text(DESCRIPTION_KEY, Component.text("Description"))
                .width(400)
                .maxLength(1000)
                .multiline(TextDialogInput.MultilineOptions.create(10, 150))
                .build();
    }

    private ActionButton submitButton(BiConsumer<Player, Response> onSubmit) {
        var options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(5))
                .build();
        var action = DialogAction.customClick((response, audience) -> {
            Player player = onlinePlayer(audience);
            if (player != null) {
                onSubmit.accept(
                        player,
                        new Response(
                                response.getText(CATEGORY_KEY),
                                response.getText(DESCRIPTION_KEY)));
            }
        }, options);
        return ActionButton.builder(Component.text("Submit", NamedTextColor.GREEN))
                .tooltip(Component.text("Queue this bug report"))
                .width(150)
                .action(action)
                .build();
    }

    private static ActionButton cancelButton() {
        return ActionButton.builder(Component.text("Cancel", NamedTextColor.GRAY))
                .tooltip(Component.text("Close without sending"))
                .width(150)
                .build();
    }

    static Player onlinePlayer(Audience audience) {
        return audience instanceof Player player && player.isOnline() ? player : null;
    }

    public record Response(String category, String description) {}
}
