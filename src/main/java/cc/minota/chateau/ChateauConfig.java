package cc.minota.chateau;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Plain {@code key=value} config, kept in the same format and location the Fabric version used
 * ({@code <game dir>/config/chateau.properties}) so an existing config file carries over as-is.
 */
public final class ChateauConfig {
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("chateau.properties");

    // Default values
    public static String server = "http://aprils-macbook-pro:3000";
    public static String id = "default";
    public static boolean enabled = true;
    public static boolean dontSend = false;

    private ChateauConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(CONFIG_FILE, StandardCharsets.UTF_8);

            for (String line : lines) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                String value = parts[1].trim();
                switch (parts[0].trim()) {
                    case "server" -> server = value;
                    case "id" -> id = value;
                    case "enabled" -> enabled = Boolean.parseBoolean(value);
                    case "dontSend" -> dontSend = Boolean.parseBoolean(value);
                    default -> {
                    }
                }
            }
        } catch (IOException e) {
            Chateau.LOGGER.error("Failed to read config: {}", e.getMessage());
        }
    }

    public static void save() {
        String contents = "server=" + server
                + "\nid=" + id
                + "\nenabled=" + enabled
                + "\ndontSend=" + dontSend;

        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(CONFIG_FILE, contents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chateau.LOGGER.error("Failed to write config: {}", e.getMessage());
        }
    }
}
