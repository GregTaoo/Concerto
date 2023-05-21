package top.gregtao.concerto.config;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class CookieFile extends ConfigFile {
    public CookieFile(String name) {
        super("Concerto/cache/" + name + ".cookie");
    }

    public void write(URI uri, CookieManager manager) {
        try {
            this.write(Base64.getEncoder().encodeToString(
                    String.join("\n", manager.get(uri, Map.of()).get("Cookie")).getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void read(URI uri, CookieManager manager) {
        try {
            String baseRaw = this.read();
            if (baseRaw.isEmpty()) return;
            String raw = new String(Base64.getDecoder().decode(baseRaw));
            List<String> cookies = List.of(raw.split("\n"));
            manager.put(uri, Map.of("Set-Cookie", cookies));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
