package top.gregtao.concerto.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.paper.ConcertoPaperPlugin;
import top.gregtao.concerto.paper.network.MusicDataPacket;
import top.gregtao.concerto.paper.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.paper.util.ComponentUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// Paper's brigadier command API only exists from 1.20.6 on, so this version
// registers the same command tree through the classic Bukkit executor instead
public class ConcertoServerCommand implements CommandExecutor, TabCompleter {

    // Mirrors the brigadier requires(): the executor had to be an op entity,
    // which the console never was
    private static boolean isOpPlayer(CommandSender sender) {
        return sender instanceof Player player && player.isOp();
    }

    private static boolean noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "audit" -> {
                if (!isOpPlayer(sender)) return noPermission(sender);
                return this.audit(sender, args);
            }
            case "reload" -> {
                if (!isOpPlayer(sender)) return noPermission(sender);
                ConcertoPaperPlugin.reload();
                return true;
            }
            case "reload-cookie" -> {
                if (!isOpPlayer(sender)) return noPermission(sender);
                NeteaseCloudApiClient.INSTANCE.readCookie();
                QQMusicApiClient.INSTANCE.readCookie();
                KuGouMusicApiClient.INSTANCE.readCookie();
                return true;
            }
            case "clean-cache" -> {
                if (!isOpPlayer(sender)) return noPermission(sender);
                CacheManager.cleanAllCache();
                return true;
            }
            case "fetch-radios" -> {
                Player player = Bukkit.getPlayer(sender.getName());
                ServerMusicNetworkHandler.sendS2CPresetRadiosPacket(player);
                return true;
            }
            case "agent" -> {
                if (!isOpPlayer(sender)) return noPermission(sender);
                if (args.length < 2) return false;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "reset" -> ServerMusicAgent.INSTANCE.reset();
                    case "cut" -> ServerMusicAgent.INSTANCE.schedulePlayNext(0, false);
                    case "stop" -> ServerMusicAgent.INSTANCE.stop();
                    case "start" -> ServerMusicAgent.INSTANCE.start();
                    default -> {
                        return false;
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean audit(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reject" -> {
                if (args.length < 3) return false;
                if (args[2].equalsIgnoreCase("all")) {
                    ServerMusicNetworkHandler.rejectAll(sender);
                    return true;
                }
                UUID uuid = parseUuid(sender, args[2]);
                if (uuid == null) return true;
                ServerMusicNetworkHandler.rejectAudition(sender, uuid);
                return true;
            }
            case "list" -> {
                if (args.length < 3) return false;
                int parsedPage;
                try {
                    parsedPage = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    return false;
                }
                int requestedPage = parsedPage;
                ConcertoRunner.run(() -> {
                    Map<UUID, MusicDataPacket> map = ServerMusicNetworkHandler.WAIT_AUDITION;
                    Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = map.entrySet().iterator();
                    int page = Math.min(requestedPage, (int) Math.ceil(map.size() / 10f));
                    sender.sendMessage(ComponentUtil.PAGE_SPLIT);
                    for (int i = 1; i < 10 * (page - 1); ++i) {
                        if (iterator.hasNext()) iterator.next();
                    }
                    for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                        Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                        MusicDataPacket packet = entry.getValue();
                        sender.sendMessage(Component.text((i + 1) + ". ").append(chatMessageBuilder(
                                entry.getKey(), packet.from, packet.music.getMeta().title()
                        )));
                    }
                    sender.sendMessage(ComponentUtil.PAGE_SPLIT);
                });
                return true;
            }
            default -> {
                UUID uuid = parseUuid(sender, args[1]);
                if (uuid == null) return true;
                ServerMusicNetworkHandler.passAudition(sender, uuid);
                return true;
            }
        }
    }

    private static UUID parseUuid(CommandSender sender, String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid UUID: " + raw, NamedTextColor.RED));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        boolean op = isOpPlayer(sender);
        if (args.length == 1) {
            List<String> roots = new ArrayList<>();
            roots.add("fetch-radios");
            if (op) roots.addAll(List.of("audit", "reload", "reload-cookie", "clean-cache", "agent"));
            return filterPrefix(roots, args[0]);
        }
        if (!op) return List.of();
        if (args[0].equalsIgnoreCase("agent") && args.length == 2) {
            return filterPrefix(List.of("reset", "cut", "stop", "start"), args[1]);
        }
        if (args[0].equalsIgnoreCase("audit")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>(List.of("reject", "list"));
                options.addAll(pendingUuids());
                return filterPrefix(options, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("reject")) {
                List<String> options = new ArrayList<>(List.of("all"));
                options.addAll(pendingUuids());
                return filterPrefix(options, args[2]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
                return filterPrefix(List.of("1"), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> pendingUuids() {
        return ServerMusicNetworkHandler.WAIT_AUDITION.keySet().stream().map(UUID::toString).toList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    public static Component chatMessageBuilder(UUID uuid, String name, String title) {
        return Component.translatable("concerto.audit.message", Component.text(name), Component.text(title))
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.accept")
                        .clickEvent(ClickEvent.runCommand("/concerto-server audit " + uuid))
                        .color(TextColor.color(5635925)))
                .append(Component.text("]"))
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.reject")
                        .clickEvent(ClickEvent.runCommand("/concerto-server audit reject " + uuid))
                        .color(TextColor.color(16733525)))
                .append(Component.text("]"));
    }
}
