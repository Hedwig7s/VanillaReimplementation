package net.minestom.vanilla.server;

import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.extras.bungee.BungeeCordProxy;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import net.minestom.vanilla.VanillaReimplementation;
import net.minestom.vanilla.logging.Level;
import net.minestom.vanilla.logging.Loading;
import net.minestom.vanilla.logging.Logger;
import net.minestom.vanilla.system.RayFastManager;
import net.minestom.vanilla.system.ServerProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

class VanillaServer {


    /**
     * A standard vanilla server launch used for testing purposes
     *
     * @param args arguments passed from console
     */
    public static void main(String[] args) {

        // Use the static server process
        MinecraftServer server = MinecraftServer.init();

        // Use SETUP logging level since this is a standalone server.
        Loading.level(Level.SETUP);
        Logger.info("Setting up vri...");
        VanillaReimplementation vri = VanillaReimplementation.hook(MinecraftServer.process());

        VanillaServer vanillaServer = new VanillaServer(server, vri, args);
        Logger.info("Vanilla Reimplementation (%s) is setup.", MinecraftServer.VERSION_NAME);
        ServerProperties properties = ServerProperties.getOrGenerateServerProperties();
        int port;
        try {
            port = Integer.parseInt(properties.get("server-port"));
        } catch (NumberFormatException ignored) {
            properties.set("server-port", "25565");
            saveProperties(properties);
            port = 25565;
        }
        vanillaServer.start("0.0.0.0", port);
        String proxyMode = properties.get("proxy-mode");
        if (proxyMode != null) {
            String token = properties.get("proxy-token");
            switch (proxyMode.toUpperCase()) {
                case "NONE":
                    break;
                case "LEGACY":
                    BungeeCordProxy.enable();
                    break;
                case "MODERN":
                    if (token == null || token.isEmpty()) {
                        Logger.error("No proxy token defined!");
                        properties.set("proxy-token", null);
                        saveProperties(properties);
                        break;
                    }
                    VelocityProxy.enable(token);
                    break;
                case "BUNGEEGUARD":
                    if (token == null || token.isEmpty()) {
                        Logger.error("No proxy token defined!");
                        properties.set("proxy-token", null);
                        saveProperties(properties);
                        break;
                    }
                    BungeeCordProxy.setBungeeGuardTokens(Set.of(token));
                    BungeeCordProxy.enable();
                    break;
            }
        }
    }

    private final MinecraftServer minecraftServer;
    private final @NotNull ServerProperties serverProperties;

    private final @NotNull VanillaReimplementation vri;

    // Instances
    private final @NotNull Instance overworld;

    public VanillaServer(@NotNull MinecraftServer minecraftServer, @NotNull VanillaReimplementation vri,
                         @Nullable String... args) {
        this.minecraftServer = minecraftServer;
        this.serverProperties = ServerProperties.getOrGenerateServerProperties();
        this.vri = vri;


        // Register all dimension types before making the worlds:

        this.overworld = vri.createInstance(NamespaceID.from("world"), DimensionType.OVERWORLD);

        // Try to get server properties

        // Set up raycasting lib
        RayFastManager.init();

        EventNode<Event> eventHandler = MinecraftServer.getGlobalEventHandler();
        ConnectionManager connectionManager = MinecraftServer.getConnectionManager();

        vri.process().eventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(overworld);
                    event.getPlayer().setGameMode(GameMode.SPECTATOR);
                    overworld.loadChunk(0, 0).join();
                    // TODO: Find the first block that is not air
                    DimensionType dimensionType = vri.process().dimensionType().get(overworld.getDimensionType());
                    int y = dimensionType.maxY();
                    while (Block.AIR.compare(overworld.getBlock(0, y, 0))) {
                        y--;
                        if (y == dimensionType.minY()) {
                            break;
                        }
                    }
                    event.getPlayer().setRespawnPoint(new Pos(0, y, 0));
                });

        vri.process().scheduler().scheduleNextTick(OpenToLAN::open);

        // Register systems
        {
            // dimension types

            // Events
            VanillaEvents.register(this, serverProperties, eventHandler);
        }

        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            connectionManager.getOnlinePlayers().forEach(player -> {
                // TODO: Saving
                player.kick("Server is closing.");
                connectionManager.removePlayer(player.getPlayerConnection());
            });
            OpenToLAN.close();
        });

        // Preload chunks
        long start = System.nanoTime();
        int radius = ServerFlag.CHUNK_VIEW_DISTANCE;
        int total = radius * 2 * radius * 2;
        Loading.start("Preloading " + total + " chunks");
        CompletableFuture<?>[] chunkFutures = new CompletableFuture[total];
        AtomicInteger completed = new AtomicInteger(0);
        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                int index = (x + radius) + (z + radius) * radius;
                chunkFutures[index] = overworld.loadChunk(x, z)
                        .thenRun(() -> {
                            int completedCount = completed.incrementAndGet();
                            Loading.updater().progress((double) completedCount / (double) chunkFutures.length);
                        });
            }
        }
        for (CompletableFuture<?> future : chunkFutures) {
            if (future != null) future.join();
        }
        long end = System.nanoTime();
        Loading.finish();
        Logger.debug("Chunks per second: " + (total / ((end - start) / 1e9)));

        // Debug
        if (List.of(args).contains("-debug")) {
            Logger.info("Debug mode enabled.");
            Logger.info("To disable it, remove the -debug argument");
            VanillaDebug.hook(this);
        }
    }

    private static void saveProperties(ServerProperties serverProperties) {
        try{
            serverProperties.save();
        } catch (Exception e){
            Logger.error("Failed to save server properties.\n" + e.getStackTrace());
        }
    }
    public void start(String address, int port) {
        minecraftServer.start(address, port);
    }

    public VanillaReimplementation vri() {
        return vri;
    }

    public Instance overworld() {
        return overworld;
    }
}
