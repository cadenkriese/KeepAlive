package codes.caden.keepalive;

import codes.caden.keepalive.config.Config;
import codes.caden.keepalive.config.ServerAddress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import static net.minecraft.commands.Commands.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
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

    final Identifier INTENT = Identifier.fromNamespaceAndPath("pico_limbo", "destination");

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
                if (context.getSource().isPlayer()) {
                    ServerPlayer player = context.getSource().getPlayer();

                    if (player == null) {
                        LOGGER.warn("Only players can execute /test_transfer");
                        return 0;
                    }

                    sendDestinationCookie(Collections.singleton(player));
                    transferToDestination(Collections.singleton(player));
                }

                    return 1;
            })));
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            sendDestinationCookie(players);
            transferToDestination(players);
        });
    }

    private void sendDestinationCookie(Collection<ServerPlayer> players) {
        ServerAddress destinationServerAddress = config.destinationServer;

        CompoundTag destinationServerCompound = new CompoundTag();
        destinationServerCompound.putString("host", destinationServerAddress.hostname);
        destinationServerCompound.putInt("port", destinationServerAddress.port);

        FriendlyByteBuf packetByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        packetByteBuf.writeNbt(destinationServerCompound);
        byte[] payloadBytes = new byte[packetByteBuf.readableBytes()];
        packetByteBuf.readBytes(payloadBytes);

        LOGGER.debug("Destination cookie payload: {}", destinationServerCompound.toString());

        ClientboundStoreCookiePacket cookiePayload = new ClientboundStoreCookiePacket(
                INTENT,
                payloadBytes
        );

        players.forEach(player -> player.connection.send(cookiePayload));
    }

    private void transferToDestination(Collection<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }

        ServerAddress limboServerAddress = config.limboServer;

        LOGGER.info("Transferring players to {}:{}", limboServerAddress.hostname, limboServerAddress.port);

        ClientboundTransferPacket transferPayload = new ClientboundTransferPacket(
                limboServerAddress.hostname, limboServerAddress.port
        );

        players.forEach(player -> player.connection.send(transferPayload));
    }

    public void writeConfig() {
        try {
            Files.writeString(configFile.toPath(), gson.toJson(config));
        } catch (IOException ex) {
            LOGGER.error("Failed to write config file", ex);
        }
    }
}
