package top.gregtao.concerto.config;

import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.util.TextUtil;

import java.net.CookieManager;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class CookieFile extends ConfigFile {
    public CookieFile(String name) {
        super("Concerto/cache/" + name + ".cookie");
    }

    public void write(CookieManager manager) {
        try {
            StringBuilder builder = new StringBuilder();
            for (URI uri : manager.getCookieStore().getURIs()) {
                builder.append(TextUtil.toBase64(uri.toString())).append(":")
                        .append(TextUtil.toBase64(String.join("\n", manager.get(uri, Map.of()).get("Cookie"))))
                        .append('\n');
            }
            this.write(builder.toString());
        } catch (Exception e) {
            ConcertoServer.LOGGER.error("Error writing cookie", e);
        }
    }

    public void read(CookieManager manager) {
        try {
            String baseRaw = this.read();
            if (baseRaw.isEmpty()) return;
            String[] lines = baseRaw.split("\n");
            for (String line : lines) {
                String[] args = line.split(":");
                if (args.length != 2) continue;
                URI uri = new URI(TextUtil.fromBase64(args[0]));
                String raw = TextUtil.fromBase64(args[1]);
                List<String> cookies = List.of(raw.split("\n"));
                manager.put(uri, Map.of("Set-Cookie", cookies));
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.error("Error reading cookie", e);
        }
    }

    public String readAsHeader() {
        try {
            String baseRaw = this.read();
            if (baseRaw.isEmpty()) return "";
            String[] lines = baseRaw.split("\n");
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                String[] args = line.split(":");
                if (args.length != 2) continue;
                String raw = TextUtil.fromBase64(args[1]).replace("\n", "; ");
                result.append(raw);
            }
            return result.toString();
        } catch (Exception e) {
            ConcertoServer.LOGGER.error("Error reading cookie as header", e);
            return "";
        }
    }
}
