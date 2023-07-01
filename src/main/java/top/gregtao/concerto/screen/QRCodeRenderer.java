package top.gregtao.concerto.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class QRCodeRenderer {
    private static NativeImageBackedTexture TEXTURE;

    public static Identifier IDENTIFIER;

    public static final int SIZE = 128;

    public static final int BLACK = 0xff000000;

    public static final int WHITE = 0xffffffff;

    public static void load(String content) throws WriterException {
        clear();
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE);
        for (int i = 0; i < SIZE; ++i) {
            for (int j = 0; j < SIZE; ++j) {
                image.setColor(i, j, matrix.get(i, j) ? BLACK : WHITE);
            }
        }
        TEXTURE = new NativeImageBackedTexture(image);
        IDENTIFIER = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("qrcode", TEXTURE);
    }

    public static void clear() {
        if (TEXTURE == null) return;
        TEXTURE.close();
    }

    public static void drawQRCode(DrawContext matrices, int x, int y) {
        if (TEXTURE == null || TEXTURE.getImage() == null) return;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        matrices.drawTexture(IDENTIFIER, x, y, 8, 8, SIZE - 16, SIZE - 16, SIZE, SIZE);
    }
}
