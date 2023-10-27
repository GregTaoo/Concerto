package top.gregtao.concerto.config;

import com.google.gson.GsonBuilder;
import org.joml.Vector2i;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.enums.TextAlignment;

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

        MusicCacheManager.INSTANCE = new MusicCacheManager(this.options.maxCacheSize);
        ConcertoClient.IMAGE_CACHE_MANAGER = new CacheManager("images", this.options.maxCacheSize);
    }

    public void writeOptions() {
        this.write(new GsonBuilder().setPrettyPrinting().create().toJson(this.options, ClientConfigOptions.class));
    }

    public static class ClientConfigOptions {
        public boolean confirmAfterReceived = true;
        public boolean hideWhenChat = true;
        public boolean printRequestResults = false;
        public int maxCacheSize = 1000 * 1000 * 100;
        public boolean displayLyrics = true;
        public String lyricsPosition = "0.5,1-70";
        public TextAlignment lyricsAlignment = TextAlignment.CENTER;
        public boolean displaySubLyrics = true;
        public String subLyricsPosition = "0.5,1-60";
        public TextAlignment subLyricsAlignment = TextAlignment.CENTER;
        public boolean displayMusicDetails = true;
        public String musicDetailsPosition = "1-5,0+5";
        public TextAlignment musicDetailsAlignment = TextAlignment.RIGHT;
        public boolean displayTimeProgress = true;
        public String timeProgressPosition = "1-5,0+15";
        public TextAlignment timeProgressAlignment = TextAlignment.RIGHT;
    }

    public static Vector2i parsePosition(String str, int width, int height) {
        String[] xy = str.split(",");
        if (xy.length != 2) return new Vector2i(0, 0);
        int x = parsePositionXY(xy[0], width), y = parsePositionXY(xy[1], height);
        return new Vector2i(x, y);
    }

    private static int parsePositionXY(String str, int widthOrHeight) {
        if (str.contains("+")) {
            String[] strings = str.split("\\+");
            try {
                return strings.length != 2 ? 0 : (int) (Float.parseFloat(strings[0]) * widthOrHeight + Integer.parseInt(strings[1]));
            } catch (NumberFormatException e) {
                return 0;
            }
        } else if (str.contains("-")) {
            String[] strings = str.split("-");
            try {
                return strings.length != 2 ? 0 : (int) (Float.parseFloat(strings[0]) * widthOrHeight - Integer.parseInt(strings[1]));
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return (int) (Float.parseFloat(str) * widthOrHeight);
        }
    }
}
