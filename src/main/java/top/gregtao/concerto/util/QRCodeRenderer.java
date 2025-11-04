package top.gregtao.concerto.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import top.gregtao.concerto.ConcertoClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class QRCodeRenderer {

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

    public static byte[] generateQRCode(String text, int margin) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    0,
                    0
                    ,Map.of(
                            EncodeHintType.MARGIN, 0
                    )
            );
            int qrWidth = matrix.getWidth();
            int qrHeight = matrix.getHeight();
            int width = qrWidth + margin * 2;
            int height = qrHeight + margin * 2;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int xi = x - margin;
                    int yi = y - margin;
                    if (xi < 0 || xi >= qrWidth || yi < 0 || yi >= qrHeight) {
                        image.setRGB(x, y, WHITE);
                        continue;
                    }
                    image.setRGB(x, y, matrix.get(xi, yi) ? BLACK : WHITE);
                }
            }
            ImageIO.write(image, "png", stream);
            return stream.toByteArray();
        } catch (IOException | WriterException e) {
            ConcertoClient.LOGGER.error("Error while generating QR Code", e);
            return new byte[]{};
        }
    }
}
