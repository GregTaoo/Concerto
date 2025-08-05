package top.gregtao.concerto.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.CacheManager;
import top.gregtao.concerto.util.HashUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class URLImageWidget implements Drawable, Widget, AutoCloseable {

    protected int width;
    protected int height;
    private int x;
    private int y;
    private String url;
    private NativeImageBackedTexture texture;
    private final Identifier textureId;
    private State state = State.LOADING;
    private boolean border = true;

    public URLImageWidget(int width, int height, int x, int y, String url) {
        this.height = height;
        this.width = width;
        this.x = x;
        this.y = y;
        this.url = url;
        this.textureId = Identifier.of(ConcertoClient.MOD_ID, "image" + System.currentTimeMillis());
    }

    public URLImageWidget(int width, int height, int x, int y, String url, boolean border) {
        this(width, height, x, y, url);
        this.border = border;
    }

    public static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        if (originalImage.getWidth() == targetWidth && originalImage.getHeight() == targetHeight) return originalImage;
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
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
        return HashUtil.md5(this.url) + ".png";
    }

    public boolean cacheExists() {
        return CacheManager.IMAGE_CACHE_MANAGER.exists(this.getFileName());
    }

    public File getFromCache() {
        return CacheManager.IMAGE_CACHE_MANAGER.getChild(this.getFileName());
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

        CacheManager.IMAGE_CACHE_MANAGER.addFile(this.getFileName(), new ByteArrayInputStream(outputStream.toByteArray()));
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
        MinecraftClient.getInstance().submit(() -> {
            if (this.texture != null)
              MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
            this.texture = new NativeImageBackedTexture(toNativeImage(image));
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.textureId, this.texture);
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
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            this.state = State.FAILED;
        }
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
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Error while loading image: {}", this.url, e);
            this.state = State.FAILED;
        }
    }

    @Override
    public void close() {
        this.state = State.FAILED;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
        if (this.texture != null) this.texture.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.border) context.drawBorder(this.x, this.y, this.width, this.height, 0xffffffff);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (this.url == null || this.texture == null) {
            context.drawCenteredTextWithShadow(
                textRenderer, Text.translatable("concerto.screen.url_image.empty"),
                this.x + this.width / 2, this.y + (this.height - textRenderer.fontHeight) / 2, 0xffffffff
            );
        } else {
            NativeImage image = this.texture.getImage();
            if (image != null && this.state == State.READY) {
                context.getMatrices().push();
                context.getMatrices().scale(0.0625f, 0.0625f, 1);
                context.getMatrices().translate(15 * this.x, 15 * this.y, 0);
                context.drawTexture(RenderLayer::getGuiTextured, this.textureId, this.x, this.y, 0, 0,
                        this.getImageWidth(), this.getImageHeight(), this.getImageWidth(), this.getImageHeight());
                context.getMatrices().pop();
            } else if (this.state == State.LOADING) {
                context.drawCenteredTextWithShadow(
                        textRenderer, Text.translatable("concerto.screen.loading"),
                        this.x + this.width / 2, this.y + (this.height - textRenderer.fontHeight) / 2, 0xffffffff
                );
            } else {
                context.drawCenteredTextWithShadow(
                        textRenderer, Text.translatable("concerto.fail"),
                        this.x + this.width / 2, this.y + (this.height - textRenderer.fontHeight) / 2, 0xffffffff
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
    public void forEachChild(Consumer<ClickableWidget> consumer) {}

    enum State {
        LOADING,
        FAILED,
        READY,
    }
}
