package ru.cherepokivan.donationalerts.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.cherepokivan.donationalerts.DonationAlertsPlugin;

import java.util.List;

public final class DonationAlertsCommand implements CommandExecutor, TabCompleter {
    private final DonationAlertsPlugin plugin;
    public DonationAlertsCommand(DonationAlertsPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { plugin.reloadPlugin(); sender.sendMessage("§a[DonationAlerts] Конфигурация перезагружена."); return true; }
        sender.sendMessage("§eИспользование: /donationalerts reload"); return true;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) { return args.length == 1 ? List.of("reload") : List.of(); }
}
