package top.gregtao.concerto.core.bridge;

public interface Minecraft {

    String getTranslatableText(String key, Object... args);
    void sendMessageToClientPlayer(String message, boolean overlay);
}
