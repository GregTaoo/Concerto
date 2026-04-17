package top.gregtao.concerto.bridge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.Consumer;

public interface MinecraftClientBridge {

    interface ClientCommandRegister {
        <S extends SharedSuggestionProvider> void register(CommandDispatcher<S> dispatcher, CommandBuildContext access);
    }

    void registerClientCommand(ClientCommandRegister register);

    void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener);

    KeyMapping registerKeyMapping(KeyMapping keyMapping);

    void registerEndOfTickListener(Consumer<Minecraft> listener);

    <T extends CustomPacketPayload> void registerClientPayloadReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler);

    void sendPayload(CustomPacketPayload payload);
}
