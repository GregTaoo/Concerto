package top.gregtao.concerto.util;

public class Vector2i extends Pair<Integer, Integer> {

    public Vector2i(int first, int second) {
        super(first, second);
    }

    public int getX() {
        return this.getFirst();
    }

    public int getY() {
        return this.getSecond();
    }
}
