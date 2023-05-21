package top.gregtao.concerto.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.command.argument.NeteaseLevelArgumentType;
import top.gregtao.concerto.command.builder.MusicAdderBuilder;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.http.qrcode.QRCode;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.NeteaseCloudMusic;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.TextUtil;

public class NeteaseCloudMusicCommand {


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        dispatcher.register(ClientCommandManager.literal("neteasecloud").then(
                ClientCommandManager.literal("login").then(
                        ClientCommandManager.literal("phone").then(
                                ClientCommandManager.argument("phone", StringArgumentType.string()).then(
                                        ClientCommandManager.argument("password", StringArgumentType.string()).executes(
                                                context -> cellphoneLoginCommand(context, false)
                                        )
                                )
                        )
                ).then(
                        ClientCommandManager.literal("captcha").then(
                                ClientCommandManager.argument("phone", StringArgumentType.string()).then(
                                        ClientCommandManager.argument("code", StringArgumentType.string()).executes(
                                                context -> cellphoneLoginCommand(context, true)
                                        )
                                ).executes(NeteaseCloudMusicCommand::sendPhoneCaptchaCommand)
                        )
                ).then(
                        ClientCommandManager.literal("email").then(
                                ClientCommandManager.argument("email", StringArgumentType.string()).then(
                                        ClientCommandManager.argument("password", StringArgumentType.string()).executes(
                                                NeteaseCloudMusicCommand::emailLoginCommand
                                        )
                                )
                        )
                ).then(
                        ClientCommandManager.literal("qrcode").executes(context -> {
                            PlayerEntity player = context.getSource().getPlayer();
                            try {
                                String key = NeteaseCloudApiClient.INSTANCE.generateQRCodeKey();
                                QRCode.load(NeteaseCloudApiClient.INSTANCE.getQRCodeLoginLink(key));
                                NeteaseCloudApiClient.checkQRCodeStatusProgress(player, key);
                            } catch (Exception e) {
                                player.sendMessage(Text.translatable("concerto.login.163.qrcode.error"));
                                throw new RuntimeException(e);
                            }
                            return 0;
                        })
                )
        ));
    }

    public static int sendPhoneCaptchaCommand(CommandContext<FabricClientCommandSource> context) {
        MusicPlayer.executeThread(() -> {
            String[] phoneNumber = StringArgumentType.getString(context, "phone").split("\\+");
            try {
                Pair<Integer, String> message;
                if (phoneNumber.length == 1) {
                    message = NeteaseCloudApiClient.INSTANCE.sendPhoneCaptcha(phoneNumber[0]);
                } else {
                    message = NeteaseCloudApiClient.INSTANCE.sendPhoneCaptcha(phoneNumber[0], phoneNumber[1]);
                }
                if (message.getFirst() == 200) {
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.captcha.163.success"));
                } else {
                    TextUtil.commandMessageClient(context, Text.translatable(
                            "concerto.captcha.163.failed", message.getSecond()));
                }
            } catch (Exception e) {
                TextUtil.commandMessageClient(context, Text.translatable("concerto.captcha.163.error"));
            }
        });
        return 0;
    }

    public static int cellphoneLoginCommand(CommandContext<FabricClientCommandSource> context, boolean captcha) {
        MusicPlayer.executeThread(() -> {
            String[] phoneNumber = StringArgumentType.getString(context, "phone").split("\\+");
            String password = StringArgumentType.getString(context, captcha ? "code" : "password");
            try {
                Pair<Integer, String> message;
                if (phoneNumber.length == 1) {
                    message = NeteaseCloudApiClient.INSTANCE.cellphoneLogin(phoneNumber[0], captcha, password);
                } else {
                    message = NeteaseCloudApiClient.INSTANCE.cellphoneLogin(phoneNumber[0], phoneNumber[1], captcha, password);
                }
                if (message.getFirst() == 200) {
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.login.163.success"));
                } else {
                    TextUtil.commandMessageClient(context, Text.translatable(
                            "concerto.login.163.failed." + message.getFirst()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                TextUtil.commandMessageClient(context, Text.translatable("concerto.login.163.error"));
            }
        });
        return 0;
    }

    public static int emailLoginCommand(CommandContext<FabricClientCommandSource> context) {
        MusicPlayer.executeThread(() -> {
            String phoneNumber = StringArgumentType.getString(context, "email");
            String password = StringArgumentType.getString(context, "password");
            try {
                Pair<Integer, String> message = NeteaseCloudApiClient.INSTANCE.emailPasswordLogin(phoneNumber, password);
                if (message.getFirst() == 200) {
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.login.163.success"));
                } else {
                    TextUtil.commandMessageClient(context, Text.translatable(
                            "concerto.login.163.failed", message.getSecond()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                TextUtil.commandMessageClient(context, Text.translatable("concerto.login.163.error"));
            }
        });
        return 0;
    }

    public static Pair<Music, Text> musicGetter(CommandContext<FabricClientCommandSource> context) {
        NeteaseCloudMusic music = new NeteaseCloudMusic(StringArgumentType.getString(context, "id"),
                NeteaseLevelArgumentType.getOrderType(context, "level"));
        return Pair.of(music, Text.translatable(Sources.NETEASE_CLOUD.getKey("add"), music.getId()));
    }

    public static ArgumentBuilder<FabricClientCommandSource, ?> builderWithIdAndLevel(Command<FabricClientCommandSource> command) {
        return ClientCommandManager.argument("id", StringArgumentType.string()).executes(command).then(
                ClientCommandManager.argument("level", NeteaseLevelArgumentType.level()).executes(command));
    }

    public static int addPlaylistExecutor(CommandContext<FabricClientCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        return MusicAdderBuilder.executePlayList(context, Pair.of(
                () -> NeteaseCloudApiClient.INSTANCE.getPlayList(id,
                        NeteaseLevelArgumentType.getOrderType(context, "level")).getFirst(),
                Text.translatable("concerto.playlist.netease_cloud.add", id)
        ));
    }

    public static int addAlbumExecutor(CommandContext<FabricClientCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        return MusicAdderBuilder.executePlayList(context, Pair.of(
                () -> NeteaseCloudApiClient.INSTANCE.getAlbum(id,
                        NeteaseLevelArgumentType.getOrderType(context, "level")).getFirst(),
                Text.translatable("concerto.playlist.netease_cloud.add", id)
        ));
    }
}
