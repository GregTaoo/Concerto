package top.gregtao.concerto.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(at = @At("HEAD"), method = "runServer()V")
    public void runServerInject(CallbackInfo ci) {
        ServerMusicAgentManager.init((MinecraftServer) (Object) this);
        ConcertoClient.LOGGER.info("Server launching.");
    }

    @Inject(at = @At("HEAD"), method = "stopServer()V")
    public void shutdownInject(CallbackInfo ci) {
        MusicRoom.ROOMS.clear();
        if (ServerMusicAgent.INSTANCE != null) {
            // dispose(), not reset(): the scheduler must die with the server,
            // a new agent instance is created on the next server start
            ServerMusicAgent.INSTANCE.dispose();
            ServerMusicAgent.INSTANCE = null;
        }
        ConcertoClient.LOGGER.info("Server shutting down.");
    }
}
