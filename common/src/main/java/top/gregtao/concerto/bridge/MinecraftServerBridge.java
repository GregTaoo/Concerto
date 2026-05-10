package top.gregtao.concerto.bridge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MinecraftServerBridge {

    interface CommandRegister {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access,
                      Commands.CommandSelection environment);
    }

    void registerCommand(CommandRegister register);

    void registerResourceReloadListener(Identifier id, Consumer<ResourceManager> listener);

    record NetworkingContext(ServerPlayer player, MinecraftServer server) {
    }

    void registerServerPayloadReceiver(CustomPacketPayload.Type<@NotNull ConcertoPayload> type, StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull ConcertoPayload> codec, BiConsumer<ConcertoPayload, NetworkingContext> handler);

    void sendPayload(ServerPlayer player, ConcertoPayload payload);

    boolean isDedicatedServer();
}
