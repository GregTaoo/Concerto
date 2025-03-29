package top.gregtao.concerto.screen.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.CacheManager;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.util.HashUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.function.Function;

public class URLImageWidget implements Drawable, AutoCloseable {

    protected int width;
    protected int height;
    private int x;
    private int y;
    private String url;
    private final NativeImageBackedTexture texture;
    private final Identifier textureId;
    private boolean loading = true;

    public URLImageWidget(int width, int height, int x, int y, String url) {
        this.height = height;
        this.width = width;
        this.x = x;
        this.y = y;
        this.url = url;
        this.textureId = new Identifier(ConcertoClient.MOD_ID, "image" + System.currentTimeMillis());
        this.texture = new NativeImageBackedTexture(width << 3, height << 3, false);
        MinecraftClient.getInstance().getTextureManager().registerTexture(this.textureId, this.texture);
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
            ConcertoClient.LOGGER.error("Error parsing BufferedImage to NativeImage", e);
            throw new RuntimeException(e);
        }
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileName() {
        return HashUtil.md5(this.url) + ".png";
    }

    public boolean cacheExists() {
        return CacheManager.IMAGE_CACHE_MANAGER.exists(this.getFileName());
    }

    public File getFromCache() {
        return CacheManager.IMAGE_CACHE_MANAGER.getChild(this.getFileName());
    }

    public void writeCacheFile(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        CacheManager.IMAGE_CACHE_MANAGER.addFile(this.getFileName(), new ByteArrayInputStream(outputStream.toByteArray()));
    }

    public void loadImage() {
        this.loadImage(true);
    }

    public void loadImage(boolean useCache) {
        if (this.url == null) return;
        try {
            this.loading = true;
            BufferedImage image;
            if (useCache && this.cacheExists()) {
                image = ImageIO.read(this.getFromCache());
            } else {
                image = resizeImage(ImageIO.read(URI.create(this.url).toURL()), this.width << 3, this.height << 3);
                if (useCache) this.writeCacheFile(image);
            }
            this.texture.setImage(toNativeImage(image));
            this.loading = false;
        } catch (MalformedURLException e) {
            ConcertoClient.LOGGER.error("Malformed URL: {}", this.url, e);
            throw new RuntimeException("Malformed URL: " + this.url, e);
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            throw new RuntimeException(e);
        }
    }

    public void loadImage(Function<String, byte[]> imageSupplier) {
        this.loadImage(imageSupplier, true);
    }

    public void loadImage(Function<String, byte[]> imageSupplier, boolean useCache) {
        try {
            this.loading = true;
            BufferedImage image;
            if (useCache && this.cacheExists()) {
                image = ImageIO.read(this.getFromCache());
            } else {
                image = resizeImage(ImageIO.read(new ByteArrayInputStream(imageSupplier.apply(this.url))), this.width << 3, this.height << 3);
                if (useCache) this.writeCacheFile(image);
            }
            this.texture.setImage(toNativeImage(image));
            this.loading = false;
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        this.loading = true;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
        this.texture.close();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        drawBorder(matrices, this.x, this.y, this.width, this.height, 0xffffffff);
        if (this.url == null) {
            ConcertoScreen.drawCenteredTextWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                    new TranslatableText("concerto.screen.url_image.empty").asOrderedText(), this.x + this.width / 2, this.y + this.height / 2, 0xffffffff);
        } else {
            NativeImage image = this.texture.getImage();
            if (image != null && !this.loading) {
                this.texture.upload();
                MatrixStack matrixStack = new MatrixStack();
                matrixStack.push();
                matrixStack.scale(0.125f, 0.125f, 1);
                matrixStack.translate(7 * this.x, 7 * this.y, 0);
                RenderSystem.bindTexture(this.texture.getGlId());
                DrawableHelper.drawTexture(matrixStack, this.x, this.y, 0, 0, this.width << 3, this.height << 3, image.getWidth(), image.getHeight());
                matrixStack.pop();
            } else {
                ConcertoScreen.drawCenteredTextWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                        new TranslatableText("concerto.screen.loading").asOrderedText(), this.x + this.width / 2, this.y + this.height / 2, 0xffffffff);
            }
        }
    }

    public static void drawBorder(MatrixStack matrices, int x, int y, int width, int height, int color) {
        DrawableHelper.fill(matrices, x, y, x + width, y + 1, color);
        DrawableHelper.fill(matrices, x, y + height - 1, x + width, y + height, color);
        DrawableHelper.fill(matrices, x, y + 1, x + 1, y + height - 1, color);
        DrawableHelper.fill(matrices, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

}
