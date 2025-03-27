package top.gregtao.concerto.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class QRCodeRenderer {
    private static NativeImageBackedTexture TEXTURE;

    public static Identifier IDENTIFIER;

    public static final int SIZE = 128;

    public static final int BLACK = 0xff000000;

    public static final int WHITE = 0xffffffff;

    public static byte[] generateQRCode(String text) {
        return generateQRCode(text, SIZE, SIZE);
    }

    public static byte[] generateQRCode(String text, int width, int height) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
                }
            }
            ImageIO.write(image, "png", stream);
            return stream.toByteArray();
        } catch (IOException | WriterException e) {
            ConcertoClient.LOGGER.error("Error while generating QR Code", e);
            return new byte[]{};
        }
    }

    public static void load(String content) throws WriterException {
        clear();
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE);
        for (int i = 0; i < SIZE; ++i) {
            for (int j = 0; j < SIZE; ++j) {
                image.setColorArgb(i, j, matrix.get(i, j) ? BLACK : WHITE);
            }
        }
        IDENTIFIER = Identifier.of(ConcertoClient.MOD_ID, "qrcode");
        TEXTURE = new NativeImageBackedTexture(IDENTIFIER.toString(), SIZE, SIZE, false);
        TEXTURE.setImage(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(IDENTIFIER, TEXTURE);
    }

    public static void clear() {
        if (TEXTURE == null) return;
        TEXTURE.close();
    }

    public static void drawQRCode(DrawContext matrices, int x, int y) {
        if (TEXTURE == null) return;
        TEXTURE.upload();
        if (TEXTURE.getImage() == null) return;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        matrices.drawTexture(RenderLayer::getGuiTextured, IDENTIFIER, x, y, 8, 8, SIZE - 16, SIZE - 16, SIZE, SIZE);
    }
}
