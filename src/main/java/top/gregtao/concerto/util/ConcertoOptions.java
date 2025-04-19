package top.gregtao.concerto.util;

import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import org.apache.logging.log4j.util.TriConsumer;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ConcertoOptions {
    public static ConcertoOptions INSTANCE = new ConcertoOptions(ClientConfig.INSTANCE);

    private ClientConfig config;
    private final List<OptionsUpdater> updaters = new ArrayList<>();

    private final SingleBooleanOption confirmAfterReceivedOption = new SingleBooleanOption(
            "confirmAfterReceived",
            value -> this.config.options.confirmAfterReceived = value,
            () -> this.config.options.confirmAfterReceived
    );

    private final SingleBooleanOption hideWhenChatOption = new SingleBooleanOption(
            "hideWhenChat",
            value -> this.config.options.hideWhenChat = value,
            () -> this.config.options.hideWhenChat
    );

    private final SingleBooleanOption printRequestResultsOption = new SingleBooleanOption(
            "printRequestResults",
            value -> this.config.options.printRequestResults = value,
            () -> this.config.options.printRequestResults
    );

    private final SingleBooleanOption joinAgentWhenInvitedOption = new SingleBooleanOption(
            "joinAgentWhenInvited",
            value -> this.config.options.joinAgentWhenInvited = value,
            () -> this.config.options.joinAgentWhenInvited
    );

    private final TextOptions lyricsOption = new TextOptions("lyrics", (display, align, pos) -> {
        this.config.options.displayLyrics = display;
        this.config.options.lyricsAlignment = align;
        this.config.options.lyricsPosition = pos;
        this.config.parseOptions();
    }, options -> {
        options.display.setValue(this.config.options.displayLyrics);
        options.align.setValue(this.config.options.lyricsAlignment.ordinal());
        TextOptions.setPosition(options, this.config.lyricsPosSupplier);
    });

    private final TextOptions subLyricsOption = new TextOptions("subLyrics", (display, align, pos) -> {
        this.config.options.displaySubLyrics = display;
        this.config.options.subLyricsAlignment = align;
        this.config.options.subLyricsPosition = pos;
        this.config.parseOptions();
    }, options -> {
        options.display.setValue(this.config.options.displaySubLyrics);
        options.align.setValue(this.config.options.subLyricsAlignment.ordinal());
        TextOptions.setPosition(options, this.config.subLyricsPosSupplier);
    });

    private final TextOptions musicDetailsOption = new TextOptions("musicDetails", (display, align, pos) -> {
        this.config.options.displayMusicDetails = display;
        this.config.options.musicDetailsAlignment = align;
        this.config.options.musicDetailsPosition = pos;
        this.config.parseOptions();
    }, options -> {
        options.display.setValue(this.config.options.displayMusicDetails);
        options.align.setValue(this.config.options.musicDetailsAlignment.ordinal());
        TextOptions.setPosition(options, this.config.musicDetailsPosSupplier);
    });

    private final TextOptions timeProgressOption = new TextOptions("timeProgress", (display, align, pos) -> {
        this.config.options.displayTimeProgress = display;
        this.config.options.timeProgressAlignment = align;
        this.config.options.timeProgressPosition = pos;
        this.config.parseOptions();
    },
    options -> {
        options.display.setValue(this.config.options.displayTimeProgress);
        options.align.setValue(this.config.options.timeProgressAlignment.ordinal());
        TextOptions.setPosition(options, this.config.timeProgressPosSupplier);
    });


    public ConcertoOptions(ClientConfig config) {
        this.config = config;
        this.updaters.add(this.confirmAfterReceivedOption);
        this.updaters.add(this.hideWhenChatOption);
        this.updaters.add(this.printRequestResultsOption);
        this.updaters.add(this.joinAgentWhenInvitedOption);
        this.updaters.add(this.lyricsOption);
        this.updaters.add(this.subLyricsOption);
        this.updaters.add(this.musicDetailsOption);
        this.updaters.add(this.timeProgressOption);
    }

    public SimpleOption<?>[] getOptions() {
        return this.updaters.stream().flatMap(OptionsUpdater::streamOptions).toArray(SimpleOption[]::new);
    }

    public void readOptions() {
        this.updaters.forEach(OptionsUpdater::readOptions);
    }

    public void writeOptions() {
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

    private static Text getPixelValueText(Text prefix, int value) {
        return Text.translatable("options.pixel_value", prefix, value);
    }

    private static Text getPercentValueText(Text prefix, double value) {
        return Text.translatable("options.percent_value", prefix, (int)(value * 100.0));
    }

    private static Text getAlignValueText(Text prefix, int value) {
        return Text.translatable("concerto.options.align", prefix,
                Text.translatable("concerto.options.align." + TextAlignment.values()[value].name().toLowerCase()));
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
        Stream<SimpleOption<?>> streamOptions();
    }

    private static class SingleBooleanOption implements OptionsUpdater {
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
            this.writer.accept(this.option.getValue());
        }

        @Override
        public Stream<SimpleOption<?>> streamOptions() {
            return Stream.of(this.option);
        }
    }

    private static class TextOptions implements OptionsUpdater {
        public final SimpleOption<Boolean> display;
        public final SimpleOption<Integer> align;
        public final SimpleOption<Double> posXPercent;
        public final SimpleOption<Integer> posXDelta;
        public final SimpleOption<Double> posYPercent;
        public final SimpleOption<Integer> posYDelta;

        private final TriConsumer<Boolean, TextAlignment, String> writer;
        private final Consumer<TextOptions> reader;

        public TextOptions(String name, TriConsumer<Boolean, TextAlignment, String> writer,
                           Consumer<TextOptions> reader) {
            this.writer = writer;
            this.reader = reader;
            this.display = SimpleOption.ofBoolean(
                    "concerto.options.display." + name, true,
                    value -> this.writeOptions()
            );
            this.align = new SimpleOption<>(
                    "concerto.options.align." + name,
                    SimpleOption.emptyTooltip(),
                    ConcertoOptions::getAlignValueText,
                    new SimpleOption.ValidatingIntSliderCallbacks(0, 2),
                    0,
                    value -> this.writeOptions()
            );
            this.posXPercent = new SimpleOption<>(
                    "concerto.options.posXPercent." + name,
                    SimpleOption.emptyTooltip(),
                    (optionText, value) -> getPercentValueText(optionText, value * 0.9 + 0.1),
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
                    (optionText, value) -> getPercentValueText(optionText, value * 0.9 + 0.1),
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

        private static void setPosition(TextOptions options, ClientConfig.PositionXYSupplier pos) {
            options.posXPercent.setValue(pos.getX().getPercentage());
            options.posXDelta.setValue(pos.getX().getDelta());
            options.posYPercent.setValue(pos.getY().getPercentage());
            options.posYDelta.setValue(pos.getY().getDelta());
        }

        public void readOptions() {
            this.reader.accept(this);
        }

        public void writeOptions() {
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
}
