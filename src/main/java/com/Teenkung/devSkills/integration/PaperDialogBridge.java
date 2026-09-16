package com.Teenkung.devSkills.integration;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("UnstableApiUsage")
public final class PaperDialogBridge {

    private final JavaPlugin plugin;

    public PaperDialogBridge(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean open(Player player, DialogView view) {
        try {
            List<ActionButton> actions = new ArrayList<>();
            for (DialogButton button : view.buttons()) {
                actions.add(ActionButton.builder(button.label())
                        .tooltip(button.tooltip())
                        .width(button.width())
                        .action(DialogAction.customClick((response, audience) -> button.action().run(), ClickCallback.Options.builder().uses(1).build()))
                        .build());
            }
            ActionButton exit = ActionButton.builder(view.exitLabel()).width(100).build();
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(view.title())
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(DialogBody.plainMessage(view.content(), 400)))
                            .build())
                    .type(DialogType.multiAction(actions).columns(Math.max(1, Math.min(view.columns(), 4))).exitAction(exit).build()));
            player.showDialog(dialog);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().warning("Paper Dialog rendering failed for " + player.getName() + ": " + exception.getMessage());
            }
            return false;
        }
    }

    public record DialogView(Component title, Component content, List<DialogButton> buttons, Component exitLabel, int columns) {
        public DialogView {
            title = Objects.requireNonNull(title, "title");
            content = Objects.requireNonNull(content, "content");
            buttons = List.copyOf(buttons);
            exitLabel = Objects.requireNonNull(exitLabel, "exitLabel");
            columns = Math.max(1, columns);
        }
    }

    public record DialogButton(Component label, Component tooltip, int width, Runnable action) {
        public DialogButton {
            label = Objects.requireNonNull(label, "label");
            tooltip = tooltip == null ? Component.empty() : tooltip;
            width = Math.max(1, Math.min(width, 1024));
            action = Objects.requireNonNull(action, "action");
        }
    }
}
