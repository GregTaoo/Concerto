package top.gregtao.concerto.core.api;

public class MusicSourceNotFoundException extends UnsupportedOperationException {

    public MusicSourceNotFoundException(String message) {
        super(message);
    }

    public MusicSourceNotFoundException(Exception e) {
        super(e.getMessage());
    }
}
