package top.gregtao.concerto.config;

import com.google.gson.GsonBuilder;
import top.gregtao.concerto.enums.TextAlignment;
import top.gregtao.concerto.music.NeteaseCloudMusic;
import top.gregtao.concerto.util.Vector2i;

public class ClientConfig extends ConfigFile {
    public static ClientConfig INSTANCE = new ClientConfig();

    public ClientConfigOptions options = new ClientConfigOptions();

    public PositionXYSupplier
        lyricsPosSupplier, subLyricsPosSupplier, musicDetailsPosSupplier, timeProgressPosSupplier, coverImgPosSupplier;

    public HexSupplier
        lyricsColor, subLyricsColor, musicDetailsColor, timeProgressTextColor, timeProgressColor, timeProgressBgColor;

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
        this.parseOptions();
    }

    public void parseOptions() {
        this.lyricsPosSupplier = new PositionXYSupplier(this.options.lyricsPosition);
        this.subLyricsPosSupplier = new PositionXYSupplier(this.options.subLyricsPosition);
        this.musicDetailsPosSupplier = new PositionXYSupplier(this.options.musicDetailsPosition);
        this.timeProgressPosSupplier = new PositionXYSupplier(this.options.timeProgressPosition);
        this.coverImgPosSupplier = new PositionXYSupplier(this.options.coverImgPosition);

        this.lyricsColor = new HexSupplier(this.options.lyricsColor);
        this.subLyricsColor = new HexSupplier(this.options.subLyricsColor);
        this.musicDetailsColor = new HexSupplier(this.options.musicDetailsColor);
        this.timeProgressTextColor = new HexSupplier(this.options.timeProgressTextColor);
        this.timeProgressColor = new HexSupplier(this.options.timeProgressColor);
        this.timeProgressBgColor = new HexSupplier(this.options.timeProgressBgColor);
    }

    public void writeOptions() {
        this.write(new GsonBuilder().setPrettyPrinting().create().toJson(this.options, ClientConfigOptions.class));
    }

    public void resetOptions() {
        this.options = new ClientConfigOptions();
        this.parseOptions();
        this.writeOptions();
    }

    public static class ClientConfigOptions {
        public boolean confirmAfterReceived = true;
        public boolean hideWhenChat = true;
        public boolean printRequestResults = false;
        public boolean joinAgentWhenInvited = false;

        public int maxCacheSize = 1000 * 1000 * 100;
        public boolean registerMusicCommand = true;
        public float scrollingTextSpeed = 1.0f;
        public NeteaseCloudMusic.Level neteaseMusicQuality = NeteaseCloudMusic.Level.HIRES;

        public boolean displayLyrics = true;
        public String lyricsPosition = "0.5,1-70";
        public TextAlignment lyricsAlignment = TextAlignment.CENTER;
        public String lyricsColor = "#ff00aaaa";

        public boolean displaySubLyrics = true;
        public String subLyricsPosition = "0.5,1-60";
        public TextAlignment subLyricsAlignment = TextAlignment.CENTER;
        public String subLyricsColor = "#ffffaa00";

        public boolean displayMusicDetails = true;
        public String musicDetailsPosition = "1-30,0+5";
        public TextAlignment musicDetailsAlignment = TextAlignment.RIGHT;
        public String musicDetailsColor = "#ffffffff";

        public boolean displayTimeProgress = true;
        public String timeProgressPosition = "1-30,0+15";
        public TextAlignment timeProgressAlignment = TextAlignment.RIGHT;
        public String timeProgressTextColor = "#ffffffff";
        public String timeProgressColor = "#ff0155bc";
        public String timeProgressBgColor = "#ffa1c7f6";

        public boolean displayCoverImg = true;
        public int coverImgSize = 25;
        public String coverImgPosition = "1-25,0";
        public boolean coverImgInCircle = true;
        public boolean coverImgRotate = true;

        public boolean textShadow = true;
        public boolean handshakeRequired = true;

        // 是否为概念版
        public boolean kuGouMusicLite = false;

        // 是否自动领取酷狗每日VIP
        public boolean autoGetKuGouDailyVIP = false;
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

        public Vector2i getPos(int width, int height) {
            return new Vector2i(this.getX(width), this.getY(height));
        }

        public PositionSupplier getX() {
            return this.x;
        }

        public PositionSupplier getY() {
            return this.y;
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

        public double getPercentage() {
            return this.percentage;
        }

        public int getDelta() {
            return this.delta;
        }

        public void setPercentage(double percentage) {
            this.percentage = (float) percentage;
        }

        public void setDelta(int delta) {
            this.delta = delta;
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
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                this.number = 0xffffffffL;
            }
        }

        public long getNumber() {
            return this.number;
        }
    }
}
