package top.gregtao.concerto.screen.widget;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.util.HashUtil;
import top.gregtao.concerto.core.util.ConcertoRunner;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class URLImageWidget implements Renderable, LayoutElement, AutoCloseable {

    protected int width;
    protected int height;
    private int x;
    private int y;
    private String url;
    private DynamicTexture texture;
    private final Identifier textureId;
    private volatile State state = State.LOADING;
    private boolean border = true;

    public URLImageWidget(int width, int height, int x, int y, String url) {
        this.height = height;
        this.width = width;
        this.x = x;
        this.y = y;
        this.url = url;
        this.textureId = Identifier.fromNamespaceAndPath(Concerto.MOD_ID, "image" + System.currentTimeMillis());
    }

    public URLImageWidget(int width, int height, int x, int y, String url, boolean border) {
        this(width, height, x, y, url);
        this.border = border;
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        int sourceWidth = originalImage.getWidth();
        int sourceHeight = originalImage.getHeight();
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) return originalImage;

        int sourceSize = Math.min(sourceWidth, sourceHeight);
        int sourceX = (sourceWidth - sourceSize) / 2;
        int sourceY = (sourceHeight - sourceSize) / 2;
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = outputImage.createGraphics();
        graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight,
                sourceX, sourceY, sourceX + sourceSize, sourceY + sourceSize, null);
        graphics.dispose();
        return outputImage;
    }

    public static BufferedImage cropCircleImage(BufferedImage inputImage) {
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        int diameter = Math.min(width, height);

        // 创建一个透明背景的 ARGB 图像
        BufferedImage output = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();

        // 开启抗锯齿
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 创建圆形剪裁区域
        g2.setClip(new Ellipse2D.Float(0, 0, diameter, diameter));

        // 计算居中位置（如果原图不是正方形）
        int x = (width - diameter) / 2;
        int y = (height - diameter) / 2;

        // 裁剪绘制
        g2.drawImage(inputImage, -x, -y, null);

        g2.dispose();
        return output;
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

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileName() {
        String url = this.url;
        return url == null ? null : HashUtil.md5(url) + ".png";
    }

    public boolean cacheExists() {
        String fileName = this.getFileName();
        return fileName != null && CacheManager.IMAGE_CACHE_MANAGER.exists(fileName);
    }

    public File getFromCache() {
        String fileName = this.getFileName();
        return fileName == null ? null : CacheManager.IMAGE_CACHE_MANAGER.getChild(fileName);
    }

    public void writeCacheFile(BufferedImage image) throws IOException {
        // JPEG 不支持透明通道，先转为 RGB，白色填充背景
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        g.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPEG writers available");
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.8f); // 0.8 ~ 1.0 推荐

        ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(rgbImage, null, null), param);
        ios.close();
        writer.dispose();

        String fileName = this.getFileName();
        if (fileName != null) {
            CacheManager.IMAGE_CACHE_MANAGER.addFile(fileName, new ByteArrayInputStream(outputStream.toByteArray()));
        }
    }

    public static BufferedImage readImageFromUrl(String url) throws IOException {
        for (int i = 0; i < 5; ++i) {
            try {
                return ImageIO.read(URI.create(url).toURL());
            } catch (Exception e) {
                ConcertoClient.LOGGER.warn("Error reading image from URL: {}", url);
            }
        }
        throw new IOException("Error reading image from URL: " + url);
    }

    private void uploadImage(BufferedImage image, Runnable callback) {
        // ImageIO.write() 是耗时操作, 会卡住渲染线程
        NativeImage nativeImage = toNativeImage(image);
        Minecraft.getInstance().submit(() -> {
            if (this.texture != null) {
                Minecraft.getInstance().getTextureManager().release(this.textureId);
            }
            this.texture = new DynamicTexture(this.textureId::toString, nativeImage);
            Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        }).thenRun(callback);
    }

    public void loadImage() {
        this.loadImage(true, false);
    }

    public void loadImage(boolean useCache, boolean cropCircle) {
        if (this.url == null) return;
        try {
            this.state = State.LOADING;
            BufferedImage image;
            if (useCache && this.cacheExists()) {
                image = resizeImage(ImageIO.read(this.getFromCache()), this.getImageWidth(), this.getImageHeight());
            } else {
                image = resizeImage(readImageFromUrl(this.url), this.getImageWidth(), this.getImageHeight());
                if (useCache) this.writeCacheFile(image);
            }
            if (cropCircle) image = cropCircleImage(image);
            this.uploadImage(image, () -> this.state = State.READY);
        } catch (MalformedURLException e) {
            ConcertoClient.LOGGER.error("Malformed URL: {}", this.url, e);
            this.state = State.FAILED;
        } catch (IOException | NullPointerException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            this.state = State.FAILED;
        }
    }
    /**
     * Downloads, decodes, resizes and caches the image away from the render thread.
     * Texture upload remains scheduled by {@link #uploadImage(BufferedImage, Runnable)}.
     */
    public void loadImageAsync(boolean useCache, boolean cropCircle) {
        ConcertoRunner.run(() -> this.loadImage(useCache, cropCircle));
    }

    public void loadImage(Function<String, byte[]> imageSupplier) {
        this.loadImage(imageSupplier, true);
    }

    public void loadImage(Function<String, byte[]> imageSupplier, boolean useCache) {
        try {
            this.state = State.LOADING;
            BufferedImage image;
            if (useCache && this.cacheExists()) {
                image = resizeImage(ImageIO.read(this.getFromCache()), this.getImageWidth(), this.getImageHeight());
            } else {
                image = resizeImage(ImageIO.read(new ByteArrayInputStream(imageSupplier.apply(this.url))), this.getImageWidth(), this.getImageHeight());
                if (useCache) this.writeCacheFile(image);
            }
            this.uploadImage(image, () -> this.state = State.READY);
        } catch (IOException | NullPointerException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            this.state = State.FAILED;
        }
    }

    @Override
    public void close() {
        this.state = State.FAILED;
        Minecraft.getInstance().getTextureManager().release(this.textureId);
        if (this.texture != null) this.texture.close();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (this.border) context.renderOutline(this.x, this.y, this.width, this.height, 0xffffffff);
        Font textRenderer = Minecraft.getInstance().font;
        if (this.url == null || this.texture == null) {
            context.drawCenteredString(
                    textRenderer, Component.translatable("concerto.screen.url_image.empty"),
                    this.x + this.width / 2, this.y + (this.height - textRenderer.lineHeight) / 2, 0xffffffff
            );
        } else {
            NativeImage image = this.texture.getPixels();
            if (image != null && this.state == State.READY) {
                Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.scale(0.0625f, 0.0625f, matrices);
                matrices.translate(15 * this.x, 15 * this.y, matrices);
                context.blit(RenderPipelines.GUI_TEXTURED, this.textureId, this.x, this.y, 0, 0,
                        this.getImageWidth(), this.getImageHeight(), this.getImageWidth(), this.getImageHeight());
                matrices.popMatrix();
            } else if (this.state == State.LOADING) {
                context.drawCenteredString(
                        textRenderer, Component.translatable("concerto.screen.loading"),
                        this.x + this.width / 2, this.y + (this.height - textRenderer.lineHeight) / 2, 0xffffffff
                );
            } else {
                context.drawCenteredString(
                        textRenderer, Component.translatable("concerto.fail"),
                        this.x + this.width / 2, this.y + (this.height - textRenderer.lineHeight) / 2, 0xffffffff
                );
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

    public int getImageWidth() {
        return this.getWidth() << 4;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    public int getImageHeight() {
        return this.getHeight() << 4;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {
    }

    enum State {
        LOADING,
        FAILED,
        READY,
    }
}
