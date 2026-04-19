package top.gregtao.concerto.util;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.util.TriConsumer;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ConcertoOptions {
    public static ConcertoOptions INSTANCE = new ConcertoOptions(ClientConfig.INSTANCE);

    public boolean canUpdate = false;

    private final ClientConfig config;
    private final List<OptionsUpdater> updaters = new ArrayList<>();

    public ConcertoOptions(ClientConfig config) {
        this.config = config;

        this.updaters.add(new SingleBooleanOption(
                "confirmAfterReceived",
                value -> this.config.options.confirmAfterReceived = value,
                () -> this.config.options.confirmAfterReceived
        ));

        this.updaters.add(new SingleBooleanOption(
                "hideWhenChat",
                value -> this.config.options.hideWhenChat = value,
                () -> this.config.options.hideWhenChat
        ));

        this.updaters.add(new SingleBooleanOption(
                "printRequestResults",
                value -> this.config.options.printRequestResults = value,
                () -> this.config.options.printRequestResults
        ));

        this.updaters.add(new SingleBooleanOption(
                "joinAgentWhenInvited",
                value -> this.config.options.joinAgentWhenInvited = value,
                () -> this.config.options.joinAgentWhenInvited
        ));

        this.updaters.add(new SingleBooleanOption(
                "textShadow",
                value -> this.config.options.textShadow = value,
                () -> this.config.options.textShadow
        ));

        this.updaters.add(new SingleBooleanOption(
                "handshakeRequired",
                value -> this.config.options.handshakeRequired = value,
                () -> this.config.options.handshakeRequired
        ));

        this.updaters.add(new TextOptions("lyrics", (display, align, pos) -> {
            this.config.options.displayLyrics = display;
            this.config.options.lyricsAlignment = align;
            this.config.options.lyricsPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.set(this.config.options.displayLyrics);
            options.align.set(this.config.options.lyricsAlignment.ordinal());
            TextOptions.setPosition(options, this.config.lyricsPosSupplier);
        }));

        this.updaters.add(new TextOptions("subLyrics", (display, align, pos) -> {
            this.config.options.displaySubLyrics = display;
            this.config.options.subLyricsAlignment = align;
            this.config.options.subLyricsPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.set(this.config.options.displaySubLyrics);
            options.align.set(this.config.options.subLyricsAlignment.ordinal());
            TextOptions.setPosition(options, this.config.subLyricsPosSupplier);
        }));

        this.updaters.add(new TextOptions("musicDetails", (display, align, pos) -> {
            this.config.options.displayMusicDetails = display;
            this.config.options.musicDetailsAlignment = align;
            this.config.options.musicDetailsPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.set(this.config.options.displayMusicDetails);
            options.align.set(this.config.options.musicDetailsAlignment.ordinal());
            TextOptions.setPosition(options, this.config.musicDetailsPosSupplier);
        }));

        this.updaters.add(new TextOptions("timeProgress", (display, align, pos) -> {
            this.config.options.displayTimeProgress = display;
            this.config.options.timeProgressAlignment = align;
            this.config.options.timeProgressPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.set(this.config.options.displayTimeProgress);
            options.align.set(this.config.options.timeProgressAlignment.ordinal());
            TextOptions.setPosition(options, this.config.timeProgressPosSupplier);
        }));

        this.updaters.add(new ImageOptions("coverImg", (display, size, pos) -> {
            this.config.options.displayCoverImg = display;
            this.config.options.coverImgSize = size;
            this.config.options.coverImgPosition = pos;
            this.config.parseOptions();
        }, options -> {
            options.display.set(this.config.options.displayCoverImg);
            options.size.set(this.config.options.coverImgSize);
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

    public OptionInstance<?>[] getOptions() {
        return this.updaters.stream().flatMap(OptionsUpdater::streamOptions).toArray(OptionInstance[]::new);
    }

    public void readOptions() {
        // 避免重新设置
        this.canUpdate = false;
        this.updaters.forEach(OptionsUpdater::readOptions);
        this.canUpdate = true;
    }

    public void writeOptions() {
        if (!this.canUpdate) return;
        this.updaters.forEach(OptionsUpdater::writeOptions);
        this.config.parseOptions();
    }

    public void saveOptions() {
        this.writeOptions();
        this.config.writeOptions();
    }

    public void resetOptions() {
        this.config.resetOptions();
        this.readOptions();
    }

    private static Component getPixelValueText(Component prefix, int value) {
        return Component.translatable("options.pixel_value", prefix, value);
    }

    private static Component getPercentValueText(Component prefix, double value) {
        return Component.translatable("options.percent_value", prefix, (int)(value * 100.0));
    }

    private static Component getAlignValueText(Component prefix, int value) {
        return Component.translatable("concerto.options.align", prefix,
                Component.translatable("concerto.options.align." + TextAlignment.values()[value].name().toLowerCase()));
    }

    private static String getPositionString(double percent, int delta) {
        return String.format("%.2f%+d", Math.clamp(percent, 0, 1), delta);
    }

    private static String getPositionXYString(double xPercent, int xDelta, double yPercent, int yDelta) {
        return getPositionString(xPercent, xDelta) + "," + getPositionString(yPercent, yDelta);
    }

    private interface OptionsUpdater {
        void readOptions();
        void writeOptions();
        Stream<OptionInstance<?>> streamOptions();
    }

    private class SingleBooleanOption implements OptionsUpdater {
        public final OptionInstance<Boolean> option;

        private final Consumer<Boolean> writer;
        private final Supplier<Boolean> reader;

        public SingleBooleanOption(String name, Consumer<Boolean> writer, Supplier<Boolean> reader) {
            this.writer = writer;
            this.reader = reader;
            this.option = OptionInstance.createBoolean(
                    "concerto.options." + name, true,
                    value -> this.writeOptions()
            );
        }

        @Override
        public void readOptions() {
            this.option.set(this.reader.get());
        }

        @Override
        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(this.option.get());
        }

        @Override
        public Stream<OptionInstance<?>> streamOptions() {
            return Stream.of(this.option);
        }
    }

    private class PosOptions implements OptionsUpdater {
        public final OptionInstance<Boolean> display;
        public final OptionInstance<Double> posXPercent;
        public final OptionInstance<Integer> posXDelta;
        public final OptionInstance<Double> posYPercent;
        public final OptionInstance<Integer> posYDelta;

        private final BiConsumer<Boolean, String> writer;
        private final Consumer<PosOptions> reader;

        public PosOptions(String name, BiConsumer<Boolean, String> writer, Consumer<PosOptions> reader) {
            this.writer = writer;
            this.reader = reader;
            this.display = OptionInstance.createBoolean(
                "concerto.options.display." + name, true,
                value -> this.writeOptions()
            );
            this.posXPercent = new OptionInstance<>(
                "concerto.options.posXPercent." + name,
                OptionInstance.noTooltip(),
                ConcertoOptions::getPercentValueText,
                OptionInstance.UnitDouble.INSTANCE,
                1.0,
                value -> this.writeOptions()
            );
            this.posXDelta = new OptionInstance<>(
                "concerto.options.posXDelta." + name,
                OptionInstance.noTooltip(),
                ConcertoOptions::getPixelValueText,
                new OptionInstance.IntRange(-250, 250),
                0,
                value -> this.writeOptions()
            );
            this.posYPercent = new OptionInstance<>(
                "concerto.options.posYPercent." + name,
                OptionInstance.noTooltip(),
                ConcertoOptions::getPercentValueText,
                OptionInstance.UnitDouble.INSTANCE,
                1.0,
                value -> this.writeOptions()
            );
            this.posYDelta = new OptionInstance<>(
                "concerto.options.posYDelta." + name,
                OptionInstance.noTooltip(),
                ConcertoOptions::getPixelValueText,
                new OptionInstance.IntRange(-250, 250),
                0,
                value -> this.writeOptions()
            );
        }

        protected static void setPosition(PosOptions options, ClientConfig.PositionXYSupplier pos) {
            options.posXPercent.set(pos.getX().getPercentage());
            options.posXDelta.set(pos.getX().getDelta());
            options.posYPercent.set(pos.getY().getPercentage());
            options.posYDelta.set(pos.getY().getDelta());
        }

        public void readOptions() {
            this.reader.accept(this);
        }

        public void writeOptions() {
            if (!ConcertoOptions.this.canUpdate) return;
            this.writer.accept(
                this.display.get(),
                getPositionXYString(
                    this.posXPercent.get(), this.posXDelta.get(),
                    this.posYPercent.get(), this.posYDelta.get()
                )
            );
        }

        public Stream<OptionInstance<?>> streamOptions() {
            return Stream.of(this.display, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }

    private class TextOptions extends PosOptions {
        public final OptionInstance<Integer> align;

        private final TriConsumer<Boolean, TextAlignment, String> writer;
        private final Consumer<TextOptions> reader;

        public TextOptions(String name, TriConsumer<Boolean, TextAlignment, String> writer,
                           Consumer<TextOptions> reader) {
            super(name, null, null);
            this.writer = writer;
            this.reader = reader;
            this.align = new OptionInstance<>(
                    "concerto.options.align." + name,
                    OptionInstance.noTooltip(),
                    ConcertoOptions::getAlignValueText,
                    new OptionInstance.IntRange(0, 2),
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
                    this.display.get(), TextAlignment.values()[this.align.get()],
                    getPositionXYString(
                            this.posXPercent.get(), this.posXDelta.get(),
                            this.posYPercent.get(), this.posYDelta.get()
                    )
            );
        }

        public Stream<OptionInstance<?>> streamOptions() {
            return Stream.of(this.display, this.align, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }

    private class ImageOptions extends PosOptions {
        public final OptionInstance<Integer> size;

        private final TriConsumer<Boolean, Integer, String> writer;
        private final Consumer<ImageOptions> reader;

        public ImageOptions(String name, TriConsumer<Boolean, Integer, String> writer,
                           Consumer<ImageOptions> reader) {
            super(name, null, null);
            this.writer = writer;
            this.reader = reader;
            this.size = new OptionInstance<>(
                "concerto.options.size." + name,
                OptionInstance.noTooltip(),
                ConcertoOptions::getPixelValueText,
                new OptionInstance.IntRange(0, 300),
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
                this.display.get(), this.size.get(),
                getPositionXYString(
                    this.posXPercent.get(), this.posXDelta.get(),
                    this.posYPercent.get(), this.posYDelta.get()
                )
            );
        }

        public Stream<OptionInstance<?>> streamOptions() {
            return Stream.of(this.display, this.size, this.posXPercent, this.posXDelta, this.posYPercent, this.posYDelta);
        }
    }
}
