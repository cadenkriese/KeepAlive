package codes.caden.keepalive;

import codes.caden.keepalive.config.Config;
import codes.caden.keepalive.config.ServerAddress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.network.packet.s2c.common.StoreCookieS2CPacket;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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


        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerAddress limboServerAddress = config.limboServer;
            ServerAddress destinationServerAddress = config.destinationServer;

            LOGGER.info("Transferring players to {}:{}", limboServerAddress.hostname, limboServerAddress.port);

            NbtCompound destinationServerCompound = new NbtCompound();
            destinationServerCompound.putString("host", destinationServerAddress.hostname);
            destinationServerCompound.putInt("port", destinationServerAddress.port);

            LOGGER.debug("Destination cookie payload: {}", NbtHelper.toFormattedString(destinationServerCompound));

            StoreCookieS2CPacket cookiePayload = new StoreCookieS2CPacket(
                    INTENT,
                    destinationServerCompound.asByteArray().orElse(ByteArrays.EMPTY_ARRAY)
            );
            ServerTransferS2CPacket transferPayload = new ServerTransferS2CPacket(
                    limboServerAddress.hostname, limboServerAddress.port
            );

            server.getPlayerManager().getPlayerList().forEach(player -> {
                player.networkHandler.sendPacket(cookiePayload);
                player.networkHandler.sendPacket(transferPayload);
            });
        });
    }

    // TODO add config commands

    public void writeConfig() {
        try {
            Files.writeString(configFile.toPath(), gson.toJson(config));
        } catch (IOException ex) {
            LOGGER.error("Failed to write config file", ex);
        }
    }
}
