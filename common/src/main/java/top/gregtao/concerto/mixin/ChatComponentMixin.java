package top.gregtao.concerto.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.JsonUtil;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.screen.InGameHudRenderer;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Unique
    private static final Pattern concerto$PATTERN = Pattern.compile("Concerto:Share:([a-zA-Z0-9+/=]+)");

    @Unique
    private static void concerto$handleMessage(Component text) {
        if (ConcertoClient.isServerAvailable()) return;
        Matcher matcher = concerto$PATTERN.matcher(text.getString());
        if (!matcher.find()) return;
        String code = new String(Base64.getDecoder().decode(matcher.group(1)));
        Music music = MusicJsonParsers.from(JsonUtil.from(code), false);
        if (music == null) return;
        music.getMeta();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        String[] authors = music.getMeta().getSource().split(",\\s");
        String sender = authors[authors.length - 1];
        if (client.player.getDisplayName().getString().equalsIgnoreCase(sender)) {
            try {
                ClientMusicNetworkHandler.addToWaitList(client, new MusicDataPacket(music, sender, true), client.player);
            } catch (Exception e) {
                ConcertoClient.LOGGER.warn("Received an unsafe music data packet");
            }
        }
    }

    @Inject(method = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
    public void addMessageInject2(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ConcertoRunner.run(() -> concerto$handleMessage(contents));
    }

    @Inject(method = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    public void renderInject(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        InGameHudRenderer.render(graphics, mouseX, mouseY, ticks);
    }
}
