package net.minestom.vanilla.system;

import net.minestom.vanilla.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

/**
 * Helper class to load and save the contents of the server.properties file
 */
public class ServerProperties {

    private final File source;
    private final Properties properties;

    /**
     * Creates a new property list from a given file. Will attempt to create the file and fill with defaults if it does not exist
     */
    public ServerProperties(File source) throws IOException {
        properties = new Properties();
        //loadDefault();
        this.source = source;
        if (source.exists()) {
            load();
        } else {
            save(); // write defaults to file
        }
    }

    public ServerProperties(String source) throws IOException {
        properties = new Properties();
        properties.load(new StringReader(source));
        this.source = null;

    }

    private void loadDefault() throws IOException {
        try (var defaultInput = new InputStreamReader(Objects.requireNonNull(ServerProperties.class.getResourceAsStream("server.properties.default")))) {
            properties.load(defaultInput);
        }
    }

    public void load() throws IOException {
        try (var reader = new FileReader(source)) {
            properties.load(reader);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public void set(String key, String value) {
        properties.put(key, value);
    }

    public void save() throws IOException {
        try (var writer = new FileWriter(source)) {
            properties.store(writer, "Minestom server properties");
        }
    }
    public static ServerProperties getOrGenerateServerProperties() {
        try {
            File propertyFile = new File("./server.properties");
            String properties = """
                    #Minecraft server properties from a fresh 1.16.1 server
                    #Generated on Mon Jul 13 17:23:48 CEST 2020
                    spawn-protection=16
                    max-tick-time=60000
                    query.port=25565
                    generator-settings=
                    sync-chunk-writes=true
                    force-gamemode=false
                    allow-nether=true
                    enforce-whitelist=false
                    gamemode=survival
                    broadcast-console-to-ops=true
                    enable-query=false
                    player-idle-timeout=0
                    difficulty=easy
                    broadcast-rcon-to-ops=true
                    spawn-monsters=true
                    op-permission-level=4
                    pvp=true
                    entity-broadcast-range-percentage=100
                    snooper-enabled=true
                    level-type=default
                    enable-status=true
                    hardcore=false
                    enable-command-block=false
                    max-players=20
                    network-compression-threshold=256
                    max-world-size=29999984
                    resource-pack-sha1=
                    function-permission-level=2
                    rcon.port=25575
                    server-port=25565
                    server-ip=
                    spawn-npcs=true
                    allow-flight=false
                    level-name=world
                    view-distance=10
                    resource-pack=
                    spawn-animals=true
                    white-list=false
                    rcon.password=
                    generate-structures=true
                    online-mode=false
                    max-build-height=256
                    level-seed=125
                    prevent-proxy-connections=false
                    use-native-transport=true
                    enable-jmx-monitoring=false
                    motd=A Minecraft Server
                    enable-rcon=false
                    proxy-mode=NONE
                    proxy-token=
                    """;
            if (!propertyFile.exists()) {
                if (propertyFile.createNewFile()) {
                    try {
                        FileOutputStream outputStream = new FileOutputStream(propertyFile);
                        outputStream.write(properties.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        Logger.error("Failed to create server properties\n" + e.getStackTrace());
                    }
                }
            }
            return new ServerProperties(propertyFile);
        } catch (Throwable e) {
            Logger.error("Failed to load server properties\n" + e.getStackTrace());
            System.exit(1);
            return null;
        }
    }
}
