package top.gregtao.concerto.config;

import com.google.gson.GsonBuilder;

public class ClientConfig extends ConfigFile {
    public static ClientConfig INSTANCE = new ClientConfig();

    public ClientConfigOptions options = new ClientConfigOptions();

    public ClientConfig() {
        super("Concerto/client_config.json");
    }

    public void readOptions() {
        String raw = this.read();
        this.options = new GsonBuilder().serializeNulls().create().fromJson(raw, ClientConfigOptions.class);
        this.options = this.options != null ? this.options : new ClientConfigOptions();
        this.writeOptions();
    }

    public void writeOptions() {
        this.write(new GsonBuilder().setPrettyPrinting().create().toJson(this.options, ClientConfigOptions.class));
    }

    public static class ClientConfigOptions {
        public boolean confirmAfterReceived = true;
        public boolean hideWhenChat = true;
        public String displayMusicInfo = "111";
        public int musicInfoPosition = 0;
    }
}
