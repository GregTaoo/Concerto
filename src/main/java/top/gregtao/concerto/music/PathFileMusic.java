package top.gregtao.concerto.music;

public abstract class PathFileMusic extends Music {
    private String rawPath;

    public PathFileMusic(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawPath() {
        return this.rawPath;
    }

    public void setRawPath(String s) {
        this.rawPath = s;
    }

    @Override
    public String getLink() {
        return this.getRawPath();
    }
}
