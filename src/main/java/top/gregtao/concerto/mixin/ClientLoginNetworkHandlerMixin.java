package top.gregtao.concerto.mixin;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;

@Mixin(ClientLoginNetworkHandler.class)
public class ClientLoginNetworkHandlerMixin {

    @Inject(at = @At("TAIL"), method = "onDisconnected(Lnet/minecraft/text/Text;)V")
    public void onDisconnectedInject(Text reason, CallbackInfo ci) {
        ConcertoClient.serverAvailable = false;
        ConcertoClient.LOGGER.info("Quit server. Server side functions are unavailable now");
    }
}
