package top.gregtao.concerto.core.util;

import java.util.Optional;
import java.util.function.Function;

public class Optionals {

    @SafeVarargs
    public static <T, R> Optional<R> firstOf(Optional<T> opt, Function<? super T, ? extends R>... mapper) {
        if (opt.isEmpty()) return Optional.empty();
        for (Function<? super T, ? extends R> func : mapper) {
            R res = func.apply(opt.get());
            if (res != null) return Optional.of(res);
        }
        return Optional.empty();
    }

    @SafeVarargs
    public static <T, R> Optional<R> flatFirstOf(Optional<T> opt, Function<? super T, ? extends Optional<R>>... mapper) {
        if (opt.isEmpty()) return Optional.empty();
        for (Function<? super T, ? extends Optional<R>> func : mapper) {
            Optional<R> res = func.apply(opt.get());
            if (res.isPresent()) return res;
        }
        return Optional.empty();
    }

}
