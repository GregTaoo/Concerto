package top.gregtao.concerto.core.music;

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

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof PathFileMusic music) && music.rawPath.equals(this.rawPath);
    }
}
