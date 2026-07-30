package top.gregtao.concerto.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.network.room.MusicRoomManager;

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
        MinecraftServer server = ((PlayerList) (Object) this).getServer();
        if (server == null) return;
        MusicRoom.serverOnPlayerDisconnect(player.getName().getString(),
                MusicRoomManager.createServerBridge(server));
    }
}
