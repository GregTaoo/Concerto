package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ConcertoScreen extends Screen {

    protected final int width;
    protected final int height;
    private final Screen parent;

    public ConcertoScreen(Text title, Screen parent) {
        super(title.getWithStyle(Style.EMPTY.withColor(Formatting.DARK_AQUA)).get(0));
        this.parent = parent;
        this.width = MinecraftClient.getInstance().getWindow().getScaledWidth();
        this.height = MinecraftClient.getInstance().getWindow().getScaledHeight();
    }

    @Override
    public void close() {
        super.close();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.title, this.width / 2, 5, 0xffffffff);
    }
}
