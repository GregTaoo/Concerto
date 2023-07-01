package top.gregtao.concerto.screen.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.util.HashUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;

public class URLImageWidget implements Drawable, Widget, Closeable {

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
        this.texture = new NativeImageBackedTexture(width << 3, height << 3, false);
        this.textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("urlimg", this.texture);
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        if (originalImage.getWidth() == targetWidth && originalImage.getHeight() == targetHeight) return originalImage;
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    public static NativeImage toNativeImage(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
            return NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getFileName() {
        return HashUtil.md5(this.url.toString()) + ".png";
    }

    public boolean cacheExists() {
        return ConcertoClient.IMAGE_CACHE_MANAGER.exists(this.getFileName());
    }

    public File getFromCache() {
        return ConcertoClient.IMAGE_CACHE_MANAGER.getChild(this.getFileName());
    }

    public void writeCacheFile(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        ConcertoClient.IMAGE_CACHE_MANAGER.addFile(this.getFileName(), new ByteArrayInputStream(outputStream.toByteArray()));
    }

    public void loadImage() {
        if (this.url == null) return;
        try {
            this.loading = true;
            BufferedImage image;
            if (this.cacheExists()) {
                image = ImageIO.read(this.getFromCache());
            } else {
                image = resizeImage(ImageIO.read(this.url), this.width << 3, this.height << 3);
                this.writeCacheFile(image);
            }
            this.texture.setImage(toNativeImage(image));
            this.texture.upload();
            this.loading = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadImage(Function<URL, byte[]> imageSupplier) {
        try {
            this.loading = true;
            BufferedImage image;
            if (this.cacheExists()) {
                image = ImageIO.read(this.getFromCache());
            } else {
                image = resizeImage(ImageIO.read(new ByteArrayInputStream(imageSupplier.apply(this.url))), this.width << 3, this.height << 3);
                this.writeCacheFile(image);
            }
            this.texture.setImage(toNativeImage(image));
            this.texture.upload();
            this.loading = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        this.loading = true;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        matrices.drawBorder(this.x, this.y, this.width, this.height, 0xffffffff);
        if (this.url == null) {
            matrices.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.translatable("concerto.screen.url_image.empty"), this.x + this.width / 2, this.y + this.height / 2, 0xffffffff);
        } else {
            NativeImage image = this.texture.getImage();
            if (image != null && !this.loading) {
                DrawContext drawContext = new DrawContext(MinecraftClient.getInstance(), matrices.getVertexConsumers());
                drawContext.getMatrices().scale(0.125f, 0.125f, 1);
                drawContext.getMatrices().translate(7 * this.x, 7 * this.y, 0);
                drawContext.drawTexture(this.textureId, this.x, this.y, 0, 0, this.width << 3, this.height << 3, image.getWidth(), image.getHeight());
            } else {
                matrices.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
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
