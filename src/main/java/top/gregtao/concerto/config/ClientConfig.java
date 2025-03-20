package top.gregtao.concerto.config;

import com.google.gson.GsonBuilder;
import net.minecraft.client.util.math.Vector2f;
import top.gregtao.concerto.enums.TextAlignment;

public class ClientConfig extends ConfigFile {
    public static ClientConfig INSTANCE = new ClientConfig();

    public ClientConfigOptions options = new ClientConfigOptions();

    public PositionXYSupplier lyricsPosSupplier, subLyricsPosSupplier, musicDetailsPosSupplier, timeProgressPosSupplier;

    public HexSupplier timeProgressColor, timeProgressBgColor;

    public ClientConfig() {
        super("Concerto/client_config.json");
    }

    public void readOptions() {
        String raw = this.read();
        this.options = new GsonBuilder().serializeNulls().create().fromJson(raw, ClientConfigOptions.class);
        this.options = this.options != null ? this.options : new ClientConfigOptions();
        this.writeOptions();

        MusicCacheManager.INSTANCE = new MusicCacheManager(this.options.maxCacheSize);
        CacheManager.IMAGE_CACHE_MANAGER = new CacheManager("images", this.options.maxCacheSize);

        this.lyricsPosSupplier = new PositionXYSupplier(this.options.lyricsPosition);
        this.subLyricsPosSupplier = new PositionXYSupplier(this.options.subLyricsPosition);
        this.musicDetailsPosSupplier = new PositionXYSupplier(this.options.musicDetailsPosition);
        this.timeProgressPosSupplier = new PositionXYSupplier(this.options.timeProgressPosition);

        this.timeProgressColor = new HexSupplier(this.options.timeProgressColor);
        this.timeProgressBgColor = new HexSupplier(this.options.timeProgressBgColor);
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
        public boolean joinAgentWhenInvited = false;
        public boolean registerMusicCommand = true;
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
        public String timeProgressColor = "#ff0155bc";
        public String timeProgressBgColor = "#ffa1c7f6";
    }

    public static class PositionXYSupplier {
        private final PositionSupplier x, y;

        public PositionXYSupplier(String str) {
            String[] strings = str.split(",");
            this.x = new PositionSupplier(strings[0]);
            this.y = new PositionSupplier(strings[1]);
        }

        public int getX(int width) {
            return this.x.getPosition(width);
        }

        public int getY(int height) {
            return this.y.getPosition(height);
        }

        public Vector2f getPos(int width, int height) {
            return new Vector2f(this.getX(width), this.getY(height));
        }
    }

    public static class PositionSupplier {
        private float percentage;
        private int delta = 0;

        public PositionSupplier(String str) {
            try {
                if (str.contains("+")) {
                    String[] strings = str.split("\\+");
                    this.percentage = Float.parseFloat(strings[0]);
                    this.delta = Integer.parseInt(strings[1]);
                } else if (str.contains("-")) {
                    String[] strings = str.split("-");
                    this.percentage = Float.parseFloat(strings[0]);
                    this.delta = -Integer.parseInt(strings[1]);
                } else {
                    this.percentage = Float.parseFloat(str);
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                this.percentage = this.delta = 0;
            }
        }

        public int getPosition(int total) {
            return (int) (total * this.percentage) + this.delta;
        }
    }

    public static class HexSupplier {
        public long number;

        public HexSupplier(String str) {
            try {
                str = str.toLowerCase();
                str = str.startsWith("0x") ? str.substring(2) : str;
                str = str.startsWith("#") ? str.substring(1) : str;
                this.number = Long.parseLong(str, 16);
            } catch (NumberFormatException e) {
                this.number = 0;
            }
        }

        public long getNumber() {
            return this.number;
        }
    }
}
