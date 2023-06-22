package top.gregtao.concerto.music;

public class MusicSourceNotFoundException extends UnsupportedOperationException {

    public MusicSourceNotFoundException(String message) {
        super(message);
    }

    public MusicSourceNotFoundException(Exception e) {
        super(e.getMessage());
    }
}
