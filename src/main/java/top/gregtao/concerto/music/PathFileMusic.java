package top.gregtao.concerto.music;

public abstract class PathFileMusic extends Music {
    private final String rawPath;

    public PathFileMusic(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawPath() {
        return this.rawPath;
    }
}
