package dev.che1s.phantomcreeper.stats;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class PhantomCreeperExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final StatsService stats;

    public PhantomCreeperExpansion(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "phantomcreeper";
    }

    @Override
    public @NotNull String getAuthor() {
        return "che1s";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "0";
        }
        StatsService.PlayerStats value = stats.get(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "kills" -> Long.toString(value.kills());
            case "deaths" -> Long.toString(value.deaths());
            default -> null;
        };
    }
}
