package top.gregtao.concerto.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.network.room.ServerMusicAgent;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(at = @At("TAIL"), method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;)V")
    public void onPlayerConnectInject(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        ServerMusicNetworkHandler.playerJoinHandshake(player);
    }

    @Inject(at = @At("HEAD"), method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V")
    public void removeInject(ServerPlayerEntity player, CallbackInfo ci) {
        List<UUID> removeList = new ArrayList<>();
        for (Map.Entry<UUID, MusicRoom> entry : MusicRoom.ROOMS.entrySet()) {
            if (entry.getValue().owner.equals(player.getName().getString())) {
                removeList.add(entry.getKey());
                try {
                    entry.getValue().serverOnRemove(player.getName().getString(), player.server);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            if (entry.getValue().members.containsKey(player.getName().getString())) {
                entry.getValue().serverOnQuit(player.getName().getString(), player.server);
                break;
            }
        }
        removeList.forEach(MusicRoom.ROOMS::remove);
        if (ServerMusicAgent.INSTANCE.isMember(player)) ServerMusicAgent.INSTANCE.playerQuit(player);
    }
}
