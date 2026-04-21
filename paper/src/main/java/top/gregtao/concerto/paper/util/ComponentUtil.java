package top.gregtao.concerto.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextColor;

public class ComponentUtil {

    public static Component PAGE_SPLIT = Component.text("==============================================").color(TextColor.color(43690));

    public static Component translatable(String key, Object... args) {
        ComponentLike[] processed = new ComponentLike[args.length];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof String s) {
                processed[i] = Component.text(s);
            } else if (arg instanceof ComponentLike componentLike) {
                processed[i] = componentLike;
            } else {
                processed[i] = Component.text(String.valueOf(arg));
            }
        }

        return Component.translatable(key, processed);
    }
}
