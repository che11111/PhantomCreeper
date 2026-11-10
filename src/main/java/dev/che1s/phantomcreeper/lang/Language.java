package dev.che1s.phantomcreeper.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class Language {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private volatile String prefix = "";
    private volatile YamlConfiguration strings = new YamlConfiguration();

    public Language(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(String languageCode) {
        String normalized = languageCode == null || languageCode.isBlank()
                ? "zh_cn"
                : languageCode.toLowerCase(Locale.ROOT);
        File languageFolder = new File(plugin.getDataFolder(), "lang");
        if (!languageFolder.isDirectory() && !languageFolder.mkdirs()) {
            throw new IllegalStateException();
        }
        copyDefault("lang/zh_cn.yml");
        copyDefault("lang/en_us.yml");

        File selected = new File(languageFolder, normalized + ".yml");
        if (!selected.isFile()) {
            selected = new File(languageFolder, "zh_cn.yml");
        }
        strings = YamlConfiguration.loadConfiguration(selected);
        String bundledName = selected.getName();
        try (InputStream bundled = plugin.getResource("lang/" + bundledName)) {
            if (bundled != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8));
                boolean migrated = migrateLegacyValues(strings, defaults, bundledName);
                strings.setDefaults(defaults);
                if (migrated) {
                    saveMigratedLanguage(strings, selected);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        prefix = strings.getString("prefix", "");
    }

    public Component component(String key, Map<String, String> placeholders) {
        String raw = resolve(strings, key);
        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((name, value) -> resolver.resolver(Placeholder.unparsed(name, value)));
        return MINI_MESSAGE.deserialize(raw, resolver.build());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((name, value) -> resolver.resolver(Placeholder.unparsed(name, value)));
        String raw = prefix + resolve(strings, key);
        sender.sendMessage(MINI_MESSAGE.deserialize(raw, resolver.build()));
    }

    public String plain(String key, Map<String, String> placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(component(key, placeholders));
    }

    private void copyDefault(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.isFile()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    static String resolve(YamlConfiguration strings, String key) {
        String value = strings.getString(key);
        return value == null ? key : value;
    }

    private static boolean migrateLegacyValues(
            YamlConfiguration current,
            YamlConfiguration defaults,
            String fileName) {
        boolean migrated = false;
        if (fileName.equals("zh_cn.yml")) {
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "command.usage",
                    "<yellow>用法：/phantomcreeper <spawn [玩家]|removeall|reload|info></yellow>",
                    "<yellow>用法：/phantomcreeper <stats|spawn [玩家]|removeall|reload|info></yellow>"
            );
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "command.info",
                    "<gray>当前存活：<white><count></white>；自然生成：<white><natural>/<natural_limit></white>；生命：<white><health></white>；命中箱：<white><hitboxes></white>；BetterModel：<white><model></white>；位置：<white><position></white>；速度：<white><velocity></white></gray>",
                    "<gray>当前存活：<white><count></white>；BetterModel：<white><model></white></gray>"
            );
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "model.loaded",
                    "模型 phantom_creeper 已加载，资源包将由 BetterModel 生成。",
                    "模型 phantom_creeper 已加载，资源包将由 BetterModel 生成并可由 CraftEngine 合并。"
            );
            migrated |= replaceIfEquals(
                    current,
                    defaults,
                    "model.missing",
                    "尚未找到 BetterModel 模型 phantom_creeper；实体暂时使用原版潜影蝠外观。"
            );
            migrated |= replaceIfEquals(
                    current,
                    defaults,
                    "model.failed",
                    "无法附加 BetterModel 模型，请检查 BetterModel 启动日志。"
            );
        } else if (fileName.equals("en_us.yml")) {
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "command.usage",
                    "<yellow>Usage: /phantomcreeper <spawn [player]|removeall|reload|info></yellow>",
                    "<yellow>Usage: /phantomcreeper <stats|spawn [player]|removeall|reload|info></yellow>"
            );
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "command.info",
                    "<gray>Alive: <white><count></white>; Natural: <white><natural>/<natural_limit></white>; Health: <white><health></white>; Hitboxes: <white><hitboxes></white>; BetterModel: <white><model></white>; Position: <white><position></white>; Velocity: <white><velocity></white></gray>",
                    "<gray>Alive: <white><count></white>; BetterModel: <white><model></white></gray>"
            );
            migrated |= replaceIfEqualsAny(
                    current,
                    defaults,
                    "model.loaded",
                    "Model phantom_creeper is loaded. BetterModel generates its resource pack.",
                    "Model phantom_creeper is loaded. BetterModel generates its pack and CraftEngine can merge it."
            );
            migrated |= replaceIfEquals(
                    current,
                    defaults,
                    "model.missing",
                    "BetterModel model phantom_creeper is not available yet; using the vanilla Phantom temporarily."
            );
            migrated |= replaceIfEquals(
                    current,
                    defaults,
                    "model.failed",
                    "Could not attach the BetterModel model. Check BetterModel startup logs."
            );
        }
        return migrated;
    }

    private static boolean replaceIfEquals(
            YamlConfiguration current,
            YamlConfiguration defaults,
            String key,
            String legacyValue) {
        if (!legacyValue.equals(current.getString(key))) {
            return false;
        }
        String replacement = defaults.getString(key);
        if (replacement == null) {
            return false;
        }
        current.set(key, replacement);
        return true;
    }

    private static boolean replaceIfEqualsAny(
            YamlConfiguration current,
            YamlConfiguration defaults,
            String key,
            String... legacyValues) {
        for (String legacyValue : legacyValues) {
            if (replaceIfEquals(current, defaults, key, legacyValue)) {
                return true;
            }
        }
        return false;
    }

    private static void saveMigratedLanguage(YamlConfiguration strings, File file) {
        try {
            strings.save(file);
        } catch (IOException ignored) {
            // 保存失败时仍使用内存中已经迁移的语言内容。
        }
    }
}
