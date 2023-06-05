package top.gregtao.concerto.screen.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;

public class URLImageWidget extends DrawableHelper implements Drawable, Widget {

    protected int width;
    protected int height;
    private int x;
    private int y;
    private URL url;
    private final NativeImageBackedTexture texture;
    private final Identifier textureId;
    private boolean loading = true;

    public URLImageWidget(int width, int height, int x, int y, URL url) {
        this.height = height;
        this.width = width;
        this.x = x;
        this.y = y;
        this.url = url;
        this.texture = new NativeImageBackedTexture(width, height, false);
        this.textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("urlimg", this.texture);
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_AREA_AVERAGING);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    public static NativeImage toNativeImage(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
            return NativeImage.read(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public void loadImage() {
        try {
            this.loading = true;
            this.texture.setImage(toNativeImage(resizeImage(ImageIO.read(this.url), this.width, this.height)));
            this.texture.upload();
            this.loading = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadImage(Function<URL, byte[]> imageSupplier) {
        try {
            this.loading = true;
            this.texture.setImage(toNativeImage(resizeImage(ImageIO.read(
                    new ByteArrayInputStream(imageSupplier.apply(this.url))), this.width, this.height)));
            this.texture.upload();
            this.loading = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        this.loading = true;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawableHelper.drawBorder(matrices, this.x, this.y, this.width, this.height, 0xffffffff);
        if (this.url == null) {
            DrawableHelper.drawCenteredTextWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                    Text.translatable("concerto.screen.url_image.empty"), this.x + this.width / 2, this.y + this.height / 2, 0xffffffff);
        } else {
            NativeImage image = this.texture.getImage();
            if (image != null && !this.loading) {
                RenderSystem.setShaderTexture(0, this.textureId);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                DrawableHelper.drawTexture(matrices, this.x, this.y, 0, 0, this.width, this.height, image.getWidth(), image.getHeight());
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                DrawableHelper.drawCenteredTextWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.translatable("concerto.screen.loading"), this.x + this.width / 2, this.y + this.height / 2, 0xffffffff);
            }
        }
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {}
}
