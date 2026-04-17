package top.gregtao.concerto.bridge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MinecraftServerBridge {

    interface CommandRegister {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access,
                      Commands.CommandSelection environment);
    }

    void registerCommand(CommandRegister register);

    void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener);

    <T extends CustomPacketPayload> void registerS2CPayload(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    <T extends CustomPacketPayload> void registerC2SPayload(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    record NetworkingContext(ServerPlayer player, MinecraftServer server) {}

    <T extends CustomPacketPayload> void registerServerPayloadReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, NetworkingContext> handler);

    void sendPayload(ServerPlayer player, CustomPacketPayload payload);

    boolean isDedicatedServer();
}
