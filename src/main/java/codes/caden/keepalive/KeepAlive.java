package codes.caden.keepalive;

import codes.caden.keepalive.config.Config;
import codes.caden.keepalive.config.ServerAddress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import static net.minecraft.server.command.CommandManager.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KeepAlive implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("KeepAlive");
    protected final File configFile = new File("config", "keepalive.json");
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public Config config;

    final Identifier INTENT = Identifier.of("pico_limbo", "destination");

    @Override
    public void onInitialize() {
        if (configFile.exists()) {
            try {
                config = gson.fromJson(Files.readString(configFile.toPath()), Config.class);
            } catch (JsonSyntaxException ex) {
                LOGGER.error("Configuration syntax invalid", ex);
                return;
            } catch (IOException ignored) {}
        }

        if (config == null) {
            LOGGER.info("Creating new config file");
            config = Config.DEFAULT;
            writeConfig();
        }

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)
                    -> dispatcher.register(literal("test_transfer").executes(context -> {
                if (context.getSource().isExecutedByPlayer()) {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        transferToDestination(Collections.singleton(player));
                    }
                }

                return 1;
            })));
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            sendDestinationCookie(players);
            transferToDestination(players);
        });
    }

    private void sendDestinationCookie(Collection<ServerPlayerEntity> players) {
        ServerAddress destinationServerAddress = config.destinationServer;
        NbtCompound destinationServerCompound = new NbtCompound();
        destinationServerCompound.putString("host", destinationServerAddress.hostname);
        destinationServerCompound.putInt("port", destinationServerAddress.port);
        PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeNbt(destinationServerCompound);

        LOGGER.debug("Destination cookie payload: {}", destinationServerCompound.toString());

        StoreCookieS2CPacket cookiePayload = new StoreCookieS2CPacket(
                INTENT,
                packetByteBuf.array()
        );

        players.forEach(player -> player.networkHandler.sendPacket(cookiePayload));
    }

    private void transferToDestination(Collection<ServerPlayerEntity> players) {
        ServerAddress limboServerAddress = config.limboServer;

        LOGGER.info("Transferring players to {}:{}", limboServerAddress.hostname, limboServerAddress.port);

        ServerTransferS2CPacket transferPayload = new ServerTransferS2CPacket(
                limboServerAddress.hostname, limboServerAddress.port
        );

        players.forEach(player -> player.networkHandler.sendPacket(transferPayload));
    }

    public void writeConfig() {
        try {
            Files.writeString(configFile.toPath(), gson.toJson(config));
        } catch (IOException ex) {
            LOGGER.error("Failed to write config file", ex);
        }
    }
}
