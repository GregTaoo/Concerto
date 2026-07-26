package top.gregtao.concerto.bridge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.function.Consumer;

public interface MinecraftClientBridge {

    interface ClientCommandRegister {
        <S extends SharedSuggestionProvider> void register(CommandDispatcher<S> dispatcher, CommandBuildContext access);
    }

    void registerClientCommand(ClientCommandRegister register);

    void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener);

    KeyMapping registerKeyMapping(KeyMapping keyMapping);

    void registerEndOfTickListener(Consumer<Minecraft> listener);

    void registerClientPayloadReceiver(ResourceLocation id, Consumer<ConcertoPayload> handler);

    void sendPayload(ConcertoPayload payload);
}
