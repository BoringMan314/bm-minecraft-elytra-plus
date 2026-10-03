package bm.minecraft.elytra.plus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Loads player-facing text for the language selected by this build. */
public final class BmMinecraftElytraPlusLanguageManager {
    private static final String FALLBACK_LANGUAGE = "zh_TW";

    private final BmMinecraftElytraPlusPlugin plugin;
    private YamlConfiguration languageConfig = new YamlConfiguration();

    public BmMinecraftElytraPlusLanguageManager(BmMinecraftElytraPlusPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveResource("active-language.yml", true);
        File activeLanguageFile = new File(plugin.getDataFolder(), "active-language.yml");
        String language = YamlConfiguration.loadConfiguration(activeLanguageFile)
                .getString("language", FALLBACK_LANGUAGE);
        String resourcePath = "lang/" + language + ".yml";
        if (plugin.getResource(resourcePath) == null) {
            YamlConfiguration fallback = loadBundledConfiguration("lang/" + FALLBACK_LANGUAGE + ".yml");
            plugin.getLogger().warning(console(fallback, "unknown-language", Map.of(
                    "language", language,
                    "fallback", FALLBACK_LANGUAGE)));
            resourcePath = "lang/" + FALLBACK_LANGUAGE + ".yml";
        }
        YamlConfiguration bundled = loadBundledConfiguration(resourcePath);
        File languageFile = new File(plugin.getDataFolder(), resourcePath);
        if (!languageFile.isFile()) {
            plugin.saveResource(resourcePath, false);
        } else if (!upgradeOutdatedLanguageFile(languageFile, resourcePath, bundled)) {
            languageConfig = bundled;
            return;
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        languageConfig.setDefaults(bundled);
        saveMissingDefaults(languageFile);
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(colour(replace(raw(key), replacements)));
    }

    public void sendItem(CommandSender sender, String key, String placeholder, ItemStack item) {
        Map<String, ItemStack> items = new HashMap<>();
        items.put(placeholder, item);
        sendItems(sender, key, items);
    }

    public void sendItems(CommandSender sender, String key, Map<String, ItemStack> items) {
        String text = colour(raw(key));
        Component message = Component.empty();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf('{', index);
            int end = start < 0 ? -1 : text.indexOf('}', start + 1);
            if (start < 0 || end < 0) {
                message = message.append(legacy(text.substring(index)));
                break;
            }
            if (start > index) {
                message = message.append(legacy(text.substring(index, start)));
            }
            String name = text.substring(start + 1, end);
            ItemStack item = items.get(name);
            message = message.append(item == null && !items.containsKey(name)
                    ? legacy(text.substring(start, end + 1))
                    : itemName(item));
            index = end + 1;
        }
        sender.sendMessage(message);
    }

    public String get(String key) {
        return colour(raw(key));
    }

    public String getConsole(String key) {
        return languageConfig.getString("console." + key, key);
    }

    private YamlConfiguration loadBundledConfiguration(String resourcePath) {
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning(getConsole("bundled-defaults-load-failed")
                    .replace("{resource}", resourcePath));
        }
        return new YamlConfiguration();
    }

    private boolean upgradeOutdatedLanguageFile(
            File languageFile, String resourcePath, YamlConfiguration bundled) {
        int localVersion = YamlConfiguration.loadConfiguration(languageFile)
                .getInt("language-format-version", 0);
        int bundledVersion = bundled.getInt("language-format-version", 0);
        if (localVersion >= bundledVersion) {
            return true;
        }

        File backupFile = new File(
                languageFile.getParentFile(),
                languageFile.getName() + ".pre-v" + bundledVersion + ".bak");
        File temporaryFile = new File(languageFile.getParentFile(), languageFile.getName() + ".upgrade.tmp");
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                throw new IOException("Bundled resource is unavailable");
            }
            Files.copy(input, temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(languageFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporaryFile.toPath(), languageFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile.toPath(), languageFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(temporaryFile.toPath());
            } catch (IOException ignored) {
                // The original language file is still intact.
            }
            plugin.getLogger().warning(console(bundled, "language-upgrade-failed", Map.of(
                    "file", languageFile.getName(),
                    "error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())));
            return false;
        }
    }

    private void saveMissingDefaults(File languageFile) {
        languageConfig.options().copyDefaults(true);
        try {
            languageConfig.save(languageFile);
        } catch (Exception exception) {
            plugin.getLogger().warning(getConsole("language-update-failed")
                    .replace("{file}", languageFile.getName()));
        }
    }

    private String console(YamlConfiguration configuration, String key, Map<String, String> replacements) {
        return replace(configuration.getString("console." + key, key), replacements);
    }

    private String raw(String key) {
        return languageConfig.getString("messages." + key, key);
    }

    private String replace(String message, Map<String, String> replacements) {
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return message;
    }

    private String colour(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private Component legacy(String message) {
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    private Component itemName(ItemStack item) {
        if (item == null) {
            return Component.text("-", NamedTextColor.YELLOW);
        }
        return item.effectiveName().colorIfAbsent(NamedTextColor.YELLOW);
    }
}
