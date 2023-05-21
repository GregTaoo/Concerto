package top.gregtao.concerto.command.builder;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;

public class MusicAdderBuilder {

    public static int execute(CommandContext<FabricClientCommandSource> context,
                              Pair<Music, Text> pair, boolean insert) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        Runnable callback = () -> player.sendMessage(pair.getSecond());
        if (insert) {
            MusicPlayer.INSTANCE.addMusicHere(pair.getFirst(), true, callback);
        } else {
            MusicPlayer.INSTANCE.addMusic(pair.getFirst(), callback);
        }
        return 0;
    }

    public static int executePlayList(CommandContext<FabricClientCommandSource> context,
                                      Pair<MusicPlayer.MusicListAdder, Text> pair) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        MusicPlayer.INSTANCE.addMusic(pair.getFirst(), () -> player.sendMessage(pair.getSecond()));
        return 0;
    }

    public interface MusicGetter<T> {

        Pair<T, Text> get(CommandContext<FabricClientCommandSource> context);
    }
}
