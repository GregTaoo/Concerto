package top.gregtao.concerto.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(at = @At("TAIL"), method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V")
    public void onPlayerConnectInject(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        Executor delayedExecutor = CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS);
        CompletableFuture.runAsync(() -> ServerMusicNetworkHandler.playerJoinHandshake(player), delayedExecutor);
    }

    @Inject(at = @At("HEAD"), method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V")
    public void removeInject(ServerPlayer player, CallbackInfo ci) {
        List<UUID> removeList = new ArrayList<>();
        for (Map.Entry<UUID, MusicRoom> entry : MusicRoom.ROOMS.entrySet()) {
            if (entry.getValue().serverGetOwner().equals(player.getName().getString())) {
                removeList.add(entry.getKey());
                break;
            }
            if (entry.getValue().serverGetMembers().containsKey(player.getName().getString())) {
                entry.getValue().serverOnQuit(player.getName().getString());
                break;
            }
        }
        removeList.forEach(MusicRoom.ROOMS::remove);
    }
}
