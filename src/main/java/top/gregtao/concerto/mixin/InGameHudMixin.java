package top.gregtao.concerto.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.screen.InGameHudRenderer;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
    public void renderInject(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        InGameHudRenderer.render(matrices, tickDelta);
    }
}
