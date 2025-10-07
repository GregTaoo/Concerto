package top.gregtao.concerto.util;

import net.minecraft.client.option.CyclingOption;
import net.minecraft.client.option.DoubleOption;
import net.minecraft.client.option.Option;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.TextAlignment;

import java.util.ArrayList;
import java.util.List;

public class ConcertoOptions {
    public static ConcertoOptions INSTANCE = new ConcertoOptions(ClientConfig.INSTANCE);

    private final ClientConfig config;
    private final List<Option> updaters = new ArrayList<>();

    public ConcertoOptions(ClientConfig config) {
        this.config = config;

        this.updaters.add(CyclingOption.create(
                "concerto.options.confirmAfterReceived",
                o -> this.config.options.confirmAfterReceived,
                (o, option, value) -> this.config.options.confirmAfterReceived = value
        ));

        this.updaters.add(CyclingOption.create(
                "concerto.options.hideWhenChat",
                o -> this.config.options.hideWhenChat,
                (o, option, value) -> this.config.options.hideWhenChat = value
        ));

        this.updaters.add(CyclingOption.create(
                "concerto.options.printRequestResults",
                o -> this.config.options.printRequestResults,
                (o, option, value) -> this.config.options.printRequestResults = value
        ));

        this.updaters.add(CyclingOption.create(
                "concerto.options.joinAgentWhenInvited",
                o -> this.config.options.joinAgentWhenInvited,
                (o, option, value) -> this.config.options.joinAgentWhenInvited = value
        ));

        this.updaters.add(CyclingOption.create(
                "concerto.options.textShadow",
                o -> this.config.options.textShadow,
                (o, option, value) -> this.config.options.textShadow = value
        ));

        this.updaters.add(CyclingOption.create(
                "concerto.options.handshakeRequired",
                o -> this.config.options.handshakeRequired,
                (o, option, value) -> this.config.options.handshakeRequired = value
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.display.lyrics",
                o -> this.config.options.displayLyrics,
                (o, option, value) -> this.config.options.displayLyrics = value
        ));
        this.updaters.add(CyclingOption.create(
                "concerto.options.align.lyrics",
                TextAlignment.values(),
                align -> new TranslatableText("concerto.options.align." + align.name().toLowerCase()),
                o -> this.config.options.lyricsAlignment,
                (o, option, value) -> this.config.options.lyricsAlignment = value
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXPercent.lyrics", 0.0, 1.0, 0.0F,
                o -> this.config.lyricsPosSupplier.getX().getPercentage(),
                (o, value) -> {
                    this.config.lyricsPosSupplier.getX().setPercentage(value);
                    this.config.options.lyricsPosition = getPositionXYString(this.config.lyricsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posXPercent.lyrics", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXDelta.lyrics", -250, 250, 1.0F,
                o -> (double) this.config.lyricsPosSupplier.getX().getDelta(),
                (o, value) -> {
                    this.config.lyricsPosSupplier.getX().setDelta((int) value.doubleValue());
                    this.config.options.lyricsPosition = getPositionXYString(this.config.lyricsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posXDelta.lyrics", (int) option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYPercent.lyrics", 0.0, 1.0, 0.0F,
                o -> this.config.lyricsPosSupplier.getY().getPercentage(),
                (o, value) -> {
                    this.config.lyricsPosSupplier.getY().setPercentage(value);
                    this.config.options.lyricsPosition = getPositionXYString(this.config.lyricsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posYPercent.lyrics", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYDelta.lyrics", -250, 250, 1.0F,
                o -> (double) this.config.lyricsPosSupplier.getY().getDelta(),
                (o, value) -> {
                    this.config.lyricsPosSupplier.getY().setDelta((int) value.doubleValue());
                    this.config.options.lyricsPosition = getPositionXYString(this.config.lyricsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posYDelta.lyrics", (int) option.get(null))
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.display.subLyrics",
                o -> this.config.options.displaySubLyrics,
                (o, option, value) -> this.config.options.displaySubLyrics = value
        ));
        this.updaters.add(CyclingOption.create(
                "concerto.options.align.subLyrics",
                TextAlignment.values(),
                align -> new TranslatableText("concerto.options.align." + align.name().toLowerCase()),
                o -> this.config.options.subLyricsAlignment,
                (o, option, value) -> this.config.options.subLyricsAlignment = value
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXPercent.subLyrics", 0.0, 1.0, 0.0F,
                o -> this.config.subLyricsPosSupplier.getX().getPercentage(),
                (o, value) -> {
                    this.config.subLyricsPosSupplier.getX().setPercentage(value);
                    this.config.options.subLyricsPosition = getPositionXYString(this.config.subLyricsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posXPercent.subLyrics", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXDelta.subLyrics", -250, 250, 1.0F,
                o -> (double) this.config.subLyricsPosSupplier.getX().getDelta(),
                (o, value) -> {
                    this.config.subLyricsPosSupplier.getX().setDelta((int) value.doubleValue());
                    this.config.options.subLyricsPosition = getPositionXYString(this.config.subLyricsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posXDelta.subLyrics", (int) option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYPercent.subLyrics", 0.0, 1.0, 0.0F,
                o -> this.config.subLyricsPosSupplier.getY().getPercentage(),
                (o, value) -> {
                    this.config.subLyricsPosSupplier.getY().setPercentage(value);
                    this.config.options.subLyricsPosition = getPositionXYString(this.config.subLyricsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posYPercent.subLyrics", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYDelta.subLyrics", -250, 250, 1.0F,
                o -> (double) this.config.subLyricsPosSupplier.getY().getDelta(),
                (o, value) -> {
                    this.config.subLyricsPosSupplier.getY().setDelta((int) value.doubleValue());
                    this.config.options.subLyricsPosition = getPositionXYString(this.config.subLyricsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posYDelta.subLyrics", (int) option.get(null))
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.display.musicDetails",
                o -> this.config.options.displayMusicDetails,
                (o, option, value) -> this.config.options.displayMusicDetails = value
        ));
        this.updaters.add(CyclingOption.create(
                "concerto.options.align.musicDetails",
                TextAlignment.values(),
                align -> new TranslatableText("concerto.options.align." + align.name().toLowerCase()),
                o -> this.config.options.musicDetailsAlignment,
                (o, option, value) -> this.config.options.musicDetailsAlignment = value
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXPercent.musicDetails", 0.0, 1.0, 0.0F,
                o -> this.config.musicDetailsPosSupplier.getX().getPercentage(),
                (o, value) -> {
                    this.config.musicDetailsPosSupplier.getX().setPercentage(value);
                    this.config.options.musicDetailsPosition = getPositionXYString(this.config.musicDetailsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posXPercent.musicDetails", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXDelta.musicDetails", -250, 250, 1.0F,
                o -> (double) this.config.musicDetailsPosSupplier.getX().getDelta(),
                (o, value) -> {
                    this.config.musicDetailsPosSupplier.getX().setDelta((int) value.doubleValue());
                    this.config.options.musicDetailsPosition = getPositionXYString(this.config.musicDetailsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posXDelta.musicDetails", (int) option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYPercent.musicDetails", 0.0, 1.0, 0.0F,
                o -> this.config.musicDetailsPosSupplier.getY().getPercentage(),
                (o, value) -> {
                    this.config.musicDetailsPosSupplier.getY().setPercentage(value);
                    this.config.options.musicDetailsPosition = getPositionXYString(this.config.musicDetailsPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posYPercent.musicDetails", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYDelta.musicDetails", -250, 250, 1.0F,
                o -> (double) this.config.musicDetailsPosSupplier.getY().getDelta(),
                (o, value) -> {
                    this.config.musicDetailsPosSupplier.getY().setDelta((int) value.doubleValue());
                    this.config.options.musicDetailsPosition = getPositionXYString(this.config.musicDetailsPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posYDelta.musicDetails", (int) option.get(null))
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.display.timeProgress",
                o -> this.config.options.displayTimeProgress,
                (o, option, value) -> this.config.options.displayTimeProgress = value
        ));
        this.updaters.add(CyclingOption.create(
                "concerto.options.align.timeProgress",
                TextAlignment.values(),
                align -> new TranslatableText("concerto.options.align." + align.name().toLowerCase()),
                o -> this.config.options.timeProgressAlignment,
                (o, option, value) -> this.config.options.timeProgressAlignment = value
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXPercent.timeProgress", 0.0, 1.0, 0.0F,
                o -> this.config.timeProgressPosSupplier.getX().getPercentage(),
                (o, value) -> {
                    this.config.timeProgressPosSupplier.getX().setPercentage(value);
                    this.config.options.timeProgressPosition = getPositionXYString(this.config.timeProgressPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posXPercent.timeProgress", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXDelta.timeProgress", -250, 250, 1.0F,
                o -> (double) this.config.timeProgressPosSupplier.getX().getDelta(),
                (o, value) -> {
                    this.config.timeProgressPosSupplier.getX().setDelta((int) value.doubleValue());
                    this.config.options.timeProgressPosition = getPositionXYString(this.config.timeProgressPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posXDelta.timeProgress", (int) option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYPercent.timeProgress", 0.0, 1.0, 0.0F,
                o -> this.config.timeProgressPosSupplier.getY().getPercentage(),
                (o, value) -> {
                    this.config.timeProgressPosSupplier.getY().setPercentage(value);
                    this.config.options.timeProgressPosition = getPositionXYString(this.config.timeProgressPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posYPercent.timeProgress", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYDelta.timeProgress", -250, 250, 1.0F,
                o -> (double) this.config.timeProgressPosSupplier.getY().getDelta(),
                (o, value) -> {
                    this.config.timeProgressPosSupplier.getY().setDelta((int) value.doubleValue());
                    this.config.options.timeProgressPosition = getPositionXYString(this.config.timeProgressPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posYDelta.timeProgress", (int) option.get(null))
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.display.coverImg",
                o -> this.config.options.displayCoverImg,
                (o, option, value) -> this.config.options.displayCoverImg = value
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.size.coverImg", 0.0, 300.0, 1.0F,
                o -> (double) this.config.options.coverImgSize,
                (o, value) -> this.config.options.coverImgSize = value.intValue(),
                (o, option) -> getPercentValueText("concerto.options.size.coverImg", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXPercent.coverImg", 0.0, 1.0, 0.0F,
                o -> this.config.coverImgPosSupplier.getX().getPercentage(),
                (o, value) -> {
                    this.config.coverImgPosSupplier.getX().setPercentage(value);
                    this.config.options.coverImgPosition = getPositionXYString(this.config.coverImgPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posXPercent.coverImg", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posXDelta.coverImg", -250, 250, 1.0F,
                o -> (double) this.config.coverImgPosSupplier.getX().getDelta(),
                (o, value) -> {
                    this.config.coverImgPosSupplier.getX().setDelta((int) value.doubleValue());
                    this.config.options.coverImgPosition = getPositionXYString(this.config.coverImgPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posXDelta.coverImg", (int) option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYPercent.coverImg", 0.0, 1.0, 0.0F,
                o -> this.config.coverImgPosSupplier.getY().getPercentage(),
                (o, value) -> {
                    this.config.coverImgPosSupplier.getY().setPercentage(value);
                    this.config.options.coverImgPosition = getPositionXYString(this.config.coverImgPosSupplier);
                },
                (o, option) -> getPercentValueText("concerto.options.posYPercent.coverImg", option.get(null))
        ));
        this.updaters.add(new DoubleOption(
                "concerto.options.posYDelta.coverImg", -250, 250, 1.0F,
                o -> (double) this.config.coverImgPosSupplier.getY().getDelta(),
                (o, value) -> {
                    this.config.coverImgPosSupplier.getY().setDelta((int) value.doubleValue());
                    this.config.options.coverImgPosition = getPositionXYString(this.config.coverImgPosSupplier);
                },
                (o, option) -> getPixelValueText("concerto.options.posYDelta.coverImg", (int) option.get(null))
        ));

        // ====================================

        this.updaters.add(CyclingOption.create(
                "concerto.options.coverImgInCircle",
                o -> this.config.options.coverImgInCircle,
                (o, option, value) -> this.config.options.coverImgInCircle = value
        ));
        this.updaters.add(CyclingOption.create(
                "concerto.options.coverImgRotate",
                o -> this.config.options.coverImgRotate,
                (o, option, value) -> this.config.options.coverImgRotate = value
        ));

        this.updaters.add(new SingleBooleanOption(
                "kugouIsLite",
                value -> this.config.options.kuGouMusicLite = value,
                () -> this.config.options.kuGouMusicLite
        ));

        this.updaters.add(new SingleBooleanOption(
                "autoGetKuGouDailyVIP",
                value -> this.config.options.autoGetKuGouDailyVIP = value,
                () -> this.config.options.autoGetKuGouDailyVIP
        ));
    }

    public Option[] getOptions() {
        return this.updaters.toArray(Option[]::new);
    }

    public void saveOptions() {
        this.config.writeOptions();
    }

    public void resetOptions() {
        this.config.resetOptions();
    }

    private static Text getPixelValueText(String prefix, int value) {
        return new TranslatableText("options.pixel_value", new TranslatableText(prefix), value);
    }

    private static Text getPercentValueText(String prefix, double value) {
        return new TranslatableText("options.percent_value", new TranslatableText(prefix), (int)(value * 100.0));
    }

    private static String getPositionString(double percent, int delta) {
        return String.format("%.2f%+d", MathHelper.clamp(percent, 0, 1), delta);
    }

    private static String getPositionXYString(double xPercent, int xDelta, double yPercent, int yDelta) {
        return getPositionString(xPercent, xDelta) + "," + getPositionString(yPercent, yDelta);
    }

    private static String getPositionXYString(ClientConfig.PositionXYSupplier supplier) {
        return getPositionXYString(
                supplier.getX().getPercentage(), supplier.getX().getDelta(),
                supplier.getY().getPercentage(), supplier.getY().getDelta()
        );
    }
}
