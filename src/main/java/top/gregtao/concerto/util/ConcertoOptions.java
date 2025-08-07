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

        this.updaters.add(new TextOptions("timeProgress", (display, align, pos) -> {
            this.config.options.displayTimeProgress = display;
            this.config.options.timeProgressAlignment = align;
            this.config.options.timeProgressPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.setValue(this.config.options.displayTimeProgress);
            options.align.setValue(this.config.options.timeProgressAlignment.ordinal());
            TextOptions.setPosition(options, this.config.timeProgressPosSupplier);
        }));

        this.updaters.add(new ImageOptions("coverImg", (display, size, pos) -> {
            this.config.options.displayCoverImg = display;
            this.config.options.coverImgSize = size;
            this.config.options.coverImgPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.setValue(this.config.options.displayCoverImg);
            options.size.setValue(this.config.options.coverImgSize);
            PosOptions.setPosition(options, this.config.coverImgPosSupplier);
        }));

        this.updaters.add(new SingleBooleanOption(
                "coverImgInCircle",
                value -> this.config.options.coverImgInCircle = value,
                () -> this.config.options.coverImgInCircle
        ));

        this.updaters.add(new SingleBooleanOption(
                "coverImgRotate",
                value -> this.config.options.coverImgRotate = value,
                () -> this.config.options.coverImgRotate
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

    private interface OptionsUpdater {
        void readOptions();
        void writeOptions();
        Stream<SimpleOption<?>> streamOptions();
    }

    private class SingleBooleanOption implements OptionsUpdater {
        public final SimpleOption<Boolean> option;

        private final Consumer<Boolean> writer;
        private final Supplier<Boolean> reader;

        public SingleBooleanOption(String name, Consumer<Boolean> writer, Supplier<Boolean> reader) {
            this.writer = writer;
            this.reader = reader;
            this.option = SimpleOption.ofBoolean(
                    "concerto.options." + name, true,
                    value -> this.writeOptions()
            );
        }

        @Override
        public void readOptions() {
            this.option.setValue(this.reader.get());
        }

        @Override
        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(this.option.getValue());
        }

        @Override
        public Stream<SimpleOption<?>> streamOptions() {
            return Stream.of(this.option);
        }
    }

    private class PosOptions implements OptionsUpdater {
        public final SimpleOption<Boolean> display;
        public final SimpleOption<Double> posXPercent;
        public final SimpleOption<Integer> posXDelta;
        public final SimpleOption<Double> posYPercent;
        public final SimpleOption<Integer> posYDelta;

        private final BiConsumer<Boolean, String> writer;
        private final Consumer<PosOptions> reader;

        public PosOptions(String name, BiConsumer<Boolean, String> writer, Consumer<PosOptions> reader) {
            this.writer = writer;
            this.reader = reader;
            this.display = SimpleOption.ofBoolean(
                "concerto.options.display." + name, true,
                value -> this.writeOptions()
            );
            this.posXPercent = new SimpleOption<>(
                "concerto.options.posXPercent." + name,
                SimpleOption.emptyTooltip(),
                ConcertoOptions::getPercentValueText,
                SimpleOption.DoubleSliderCallbacks.INSTANCE,
                1.0,
                value -> this.writeOptions()
            );
            this.posXDelta = new SimpleOption<>(
                "concerto.options.posXDelta." + name,
                SimpleOption.emptyTooltip(),
                ConcertoOptions::getPixelValueText,
                new SimpleOption.ValidatingIntSliderCallbacks(-250, 250),
                0,
                value -> this.writeOptions()
            );
            this.posYPercent = new SimpleOption<>(
                "concerto.options.posYPercent." + name,
                SimpleOption.emptyTooltip(),
                ConcertoOptions::getPercentValueText,
                SimpleOption.DoubleSliderCallbacks.INSTANCE,
                1.0,
                value -> this.writeOptions()
            );
            this.posYDelta = new SimpleOption<>(
                "concerto.options.posYDelta." + name,
                SimpleOption.emptyTooltip(),
                ConcertoOptions::getPixelValueText,
                new SimpleOption.ValidatingIntSliderCallbacks(-250, 250),
                0,
                value -> this.writeOptions()
            );
        }

        protected static void setPosition(PosOptions options, ClientConfig.PositionXYSupplier pos) {
            options.posXPercent.setValue(pos.getX().getPercentage());
            options.posXDelta.setValue(pos.getX().getDelta());
            options.posYPercent.setValue(pos.getY().getPercentage());
            options.posYDelta.setValue(pos.getY().getDelta());
        }

        public void readOptions() {
            this.reader.accept(this);
        }

        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(
                this.display.getValue(),
                getPositionXYString(
                    this.posXPercent.getValue(), this.posXDelta.getValue(),
                    this.posYPercent.getValue(), this.posYDelta.getValue()
                )
            );
        }

        public Stream<SimpleOption<?>> streamOptions() {
            return Stream.of(this.display, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }

    private class TextOptions extends PosOptions {
        public final SimpleOption<Integer> align;

        private final TriConsumer<Boolean, TextAlignment, String> writer;
        private final Consumer<TextOptions> reader;

        public TextOptions(String name, TriConsumer<Boolean, TextAlignment, String> writer,
                           Consumer<TextOptions> reader) {
            super(name, null, null);
            this.writer = writer;
            this.reader = reader;
            this.align = new SimpleOption<>(
                    "concerto.options.align." + name,
                    SimpleOption.emptyTooltip(),
                    ConcertoOptions::getAlignValueText,
                    new SimpleOption.ValidatingIntSliderCallbacks(0, 2),
                    0,
                    value -> this.writeOptions()
            );
        }

        public void readOptions() {
            this.reader.accept(this);
        }

        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(
                    this.display.getValue(), TextAlignment.values()[this.align.getValue()],
                    getPositionXYString(
                            this.posXPercent.getValue(), this.posXDelta.getValue(),
                            this.posYPercent.getValue(), this.posYDelta.getValue()
                    )
            );
        }

        public Stream<SimpleOption<?>> streamOptions() {
            return Stream.of(this.display, this.align, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }

    private class ImageOptions extends PosOptions {
        public final SimpleOption<Integer> size;

        private final TriConsumer<Boolean, Integer, String> writer;
        private final Consumer<ImageOptions> reader;

        public ImageOptions(String name, TriConsumer<Boolean, Integer, String> writer,
                           Consumer<ImageOptions> reader) {
            super(name, null, null);
            this.writer = writer;
            this.reader = reader;
            this.size = new SimpleOption<>(
                "concerto.options.size." + name,
                SimpleOption.emptyTooltip(),
                ConcertoOptions::getPixelValueText,
                new SimpleOption.ValidatingIntSliderCallbacks(0, 300),
                0,
                value -> this.writeOptions()
            );
        }

        public void readOptions() {
            this.reader.accept(this);
        }

        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(
                this.display.getValue(), this.size.getValue(),
                getPositionXYString(
                    this.posXPercent.getValue(), this.posXDelta.getValue(),
                    this.posYPercent.getValue(), this.posYDelta.getValue()
                )
            );
        }

        public Stream<SimpleOption<?>> streamOptions() {
            return Stream.of(this.display, this.size, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }
}
