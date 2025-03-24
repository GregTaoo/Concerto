package top.gregtao.concerto.screen.widget;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.*;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public class CyclingButtonWidget<T> extends PressableWidget {
    static final BooleanSupplier HAS_ALT_DOWN = Screen::hasAltDown;
    private final Text optionText;
    private int index;
    private T value;
    private final Values<T> values;
    private final Function<T, Text> valueToText;
    private final UpdateCallback<T> callback;
    private final boolean optionTextOmitted;

    CyclingButtonWidget(int x, int y, int width, int height, Text message, Text optionText, int index, T value, Values<T> values, Function<T, Text> valueToText, UpdateCallback<T> callback, boolean optionTextOmitted) {
        super(x, y, width, height, message);
        this.optionText = optionText;
        this.index = index;
        this.value = value;
        this.values = values;
        this.valueToText = valueToText;
        this.callback = callback;
        this.optionTextOmitted = optionTextOmitted;
    }

    public void onPress() {
        if (Screen.hasShiftDown()) {
            this.cycle(-1);
        } else {
            this.cycle(1);
        }

    }

    private void cycle(int amount) {
        List<T> list = this.values.getCurrent();
        this.index = MathHelper.floorMod(this.index + amount, list.size());
        T object = list.get(this.index);
        this.internalSetValue(object);
        this.callback.onValueChange(this, object);
    }

    private T getValue(int offset) {
        List<T> list = this.values.getCurrent();
        return list.get(MathHelper.floorMod(this.index + offset, list.size()));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 0.0) {
            this.cycle(-1);
        } else if (amount < 0.0) {
            this.cycle(1);
        }

        return true;
    }

    public void setValue(T value) {
        List<T> list = this.values.getCurrent();
        int i = list.indexOf(value);
        if (i != -1) {
            this.index = i;
        }

        this.internalSetValue(value);
    }

    private void internalSetValue(T value) {
        Text text = this.composeText(value);
        this.setMessage(text);
        this.value = value;
    }

    private Text composeText(T value) {
        return this.optionTextOmitted ? this.valueToText.apply(value) : this.composeGenericOptionText(value);
    }

    private MutableText composeGenericOptionText(T value) {
        return new LiteralText(this.optionText.getString() + ": " + this.valueToText.apply(value).getString());
    }

    public T getValue() {
        return this.value;
    }

    public static <T> Builder<T> builder(Function<T, Text> valueToText) {
        return new Builder<>(valueToText);
    }

    @Environment(EnvType.CLIENT)
    interface Values<T> {
        List<T> getCurrent();

        List<T> getDefaults();

        static <T> Values<T> of(List<T> values) {
            final List<T> list = ImmutableList.copyOf(values);
            return new Values<>() {
                public List<T> getCurrent() {
                    return list;
                }

                public List<T> getDefaults() {
                    return list;
                }
            };
        }

        static <T> Values<T> of(final BooleanSupplier alternativeToggle, List<T> defaults, List<T> alternatives) {
            final List<T> list = ImmutableList.copyOf(defaults);
            final List<T> list2 = ImmutableList.copyOf(alternatives);
            return new Values<>() {
                public List<T> getCurrent() {
                    return alternativeToggle.getAsBoolean() ? list2 : list;
                }

                public List<T> getDefaults() {
                    return list;
                }
            };
        }
    }

    @Environment(EnvType.CLIENT)
    public interface UpdateCallback<T> {
        void onValueChange(CyclingButtonWidget<T> button, T value);
    }

    @Environment(EnvType.CLIENT)
    public static class Builder<T> {
        private int initialIndex;
        @Nullable
        private T value;
        private final Function<T, Text> valueToText;
        private Values<T> values = CyclingButtonWidget.Values.of(ImmutableList.of());
        private boolean optionTextOmitted;

        public Builder(Function<T, Text> valueToText) {
            this.valueToText = valueToText;
        }

        public Builder<T> values(List<T> values) {
            this.values = CyclingButtonWidget.Values.of(values);
            return this;
        }

        @SafeVarargs
        public final Builder<T> values(T... values) {
            return this.values(ImmutableList.copyOf(values));
        }

        public Builder<T> values(List<T> defaults, List<T> alternatives) {
            this.values = CyclingButtonWidget.Values.of(CyclingButtonWidget.HAS_ALT_DOWN, defaults, alternatives);
            return this;
        }

        public Builder<T> values(BooleanSupplier alternativeToggle, List<T> defaults, List<T> alternatives) {
            this.values = CyclingButtonWidget.Values.of(alternativeToggle, defaults, alternatives);
            return this;
        }

        public Builder<T> initially(T value) {
            this.value = value;
            int i = this.values.getDefaults().indexOf(value);
            if (i != -1) {
                this.initialIndex = i;
            }

            return this;
        }

        public Builder<T> omitKeyText() {
            this.optionTextOmitted = true;
            return this;
        }

        public CyclingButtonWidget<T> build(int x, int y, int width, int height, Text optionText) {
            return this.build(x, y, width, height, optionText, (button, value) -> {});
        }

        public CyclingButtonWidget<T> build(int x, int y, int width, int height, Text optionText, UpdateCallback<T> callback) {
            List<T> list = this.values.getDefaults();
            if (list.isEmpty()) {
                throw new IllegalStateException("No values for cycle button");
            } else {
                T object = this.value != null ? this.value : list.get(this.initialIndex);
                Text text = this.valueToText.apply(object);
                Text text2 = this.optionTextOmitted ? text : new LiteralText(optionText.getString() + ": " + text.getString());
                return new CyclingButtonWidget<>(x, y, width, height, text2, optionText, this.initialIndex, object, this.values, this.valueToText, callback, this.optionTextOmitted);
            }
        }
    }
}
