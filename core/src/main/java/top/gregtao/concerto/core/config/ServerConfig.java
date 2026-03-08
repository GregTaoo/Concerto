package top.gregtao.concerto.core.config;

import com.google.gson.GsonBuilder;

public class ServerConfig extends ConfigFile {
    public static ServerConfig INSTANCE = new ServerConfig();

    public ServerConfigOptions options = new ServerConfigOptions();

    public ServerConfig() {
        super("Concerto/server_config.json");
    }

    public void readOptions() {
        String raw = this.read();
        this.options = new GsonBuilder().serializeNulls().create().fromJson(raw, ServerConfigOptions.class);
        this.options = this.options != null ? this.options : new ServerConfigOptions();
        this.writeOptions();
    }

    public void writeOptions() {
        this.write(new GsonBuilder().setPrettyPrinting().create().toJson(this.options, ServerConfigOptions.class));
    }

    public static class ServerConfigOptions {
        public boolean auditionRequired = true;
        public boolean serverMusicAgent = true;
        public boolean agentInviteWhenJoin = true;
        public int musicRoomCommandPermission = 2;
        public int musicAgentAddTimeLimit = 60;
        public boolean musicAgentUseShared = true;
        public boolean kuGouMusicLite = false;
    }
}