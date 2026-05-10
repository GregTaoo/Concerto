package top.gregtao.concerto.util;

import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.util.qrcode.QrCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class QRCodeRenderer {

    public static final int SIZE = 128;

    public static final int BLACK = 0xff000000;
    public static final int WHITE = 0xffffffff;

    public static byte[] generateQRCode(String text) {
        return generateQRCode(text, SIZE, SIZE);
    }

    public static byte[] generateQRCode(String text, int width, int height) {
        return generateQRCode(text, width, height, 3);
    }

    public static byte[] generateQRCode(String text, int width, int height, int margin) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
            int qrSize = qr.size;
            int fullSize = qrSize + margin * 2;

            // 缩放
            int scale = Math.min(width / fullSize, height / fullSize);
            if (scale <= 0) scale = 1;

            int imgWidth = fullSize * scale;
            int imgHeight = fullSize * scale;

            // 居中
            int offsetX = (width - imgWidth) / 2;
            int offsetY = (height - imgHeight) / 2;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // 白底
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, WHITE);
                }
            }

            for (int y = 0; y < qrSize; y++) {
                for (int x = 0; x < qrSize; x++) {
                    if (qr.getModule(x, y)) {
                        for (int dy = 0; dy < scale; dy++) {
                            for (int dx = 0; dx < scale; dx++) {
                                int px = offsetX + (x + margin) * scale + dx;
                                int py = offsetY + (y + margin) * scale + dy;
                                image.setRGB(px, py, BLACK);
                            }
                        }
                    }
                }
            }

            ImageIO.write(image, "png", stream);
            return stream.toByteArray();
        } catch (IOException e) {
            ConcertoClient.LOGGER.error("Error while generating QR Code", e);
            return new byte[]{};
        }
    }
}
