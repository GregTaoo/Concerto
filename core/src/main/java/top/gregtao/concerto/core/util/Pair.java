package top.gregtao.concerto.core.util;


import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Pair<F, S> {
    private final F first;
    private final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <F, S> Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    public static <F, S> Pair<F, S> ofNull() {
        return new Pair<>(null, null);
    }

    public F getFirst() {
        return this.first;
    }

    public S getSecond() {
        return this.second;
    }

    public <F2> Pair<F2, S> mapFirst(Function<? super F, ? extends F2> function) {
        return of(function.apply(this.first), this.second);
    }

    public <S2> Pair<F, S2> mapSecond(Function<? super S, ? extends S2> function) {
        return of(this.first, function.apply(this.second));
    }

    public Pair<S, F> swap() {
        return of(this.second, this.first);
    }

    public String toString() {
        return "(" + this.first + ", " + this.second + ")";
    }

    public static <F, S> Collector<Pair<F, S>, ?, Map<F, S>> toMap() {
        return Collectors.toMap(Pair::getFirst, Pair::getSecond);
    }

}
