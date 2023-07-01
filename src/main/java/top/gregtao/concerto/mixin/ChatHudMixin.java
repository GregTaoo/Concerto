package top.gregtao.concerto.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.JsonUtil;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    private static final Pattern PATTERN = Pattern.compile("Concerto:Share:([a-zA-Z0-9+/=]+)");

    private static boolean handleMessage(Text text) {
        if (ConcertoClient.isServerAvailable()) return false;
        Matcher matcher = PATTERN.matcher(text.getString());
        if (!matcher.find()) return false;
        String code = new String(Base64.getDecoder().decode(matcher.group(1)));
        Music music = MusicJsonParsers.from(JsonUtil.from(code), false);
        if (music == null) return false;
        music.getMeta();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        String[] authors = music.getMeta().getSource().split(",\\s");
        String sender = authors[authors.length - 1];
        try {
            ClientMusicNetworkHandler.addToWaitList(client, new MusicDataPacket(music, sender, true), client.player);
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Received an unsafe music data packet");
            return false;
        }
        return true;
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    public void addMessageInject1(Text message, CallbackInfo ci){
        MusicPlayer.run(() -> handleMessage(message));
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V ", at = @At("HEAD"))
    public void addMessageInject2(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci){
        MusicPlayer.run(() -> handleMessage(message));
    }
}
