package top.gregtao.concerto.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ShareMusicTargetArgumentType implements ArgumentType<String> {
    public static List<String> EXAMPLES = List.of("@a", "GregTao", "who_am_i");

    public static ShareMusicTargetArgumentType create() {
        return new ShareMusicTargetArgumentType();
    }

    public static List<String> getPlayerNameList(CommandSource source) {
        List<String> players = new ArrayList<>(source.getPlayerNames());
        players.add("@a");
        return players;
    }

    public static String get(CommandContext<FabricClientCommandSource> context, String key) throws CommandSyntaxException {
        String str = context.getArgument(key, String.class);
        if (getPlayerNameList(context.getSource()).contains(str)) return str;
        else throw EntityArgumentType.PLAYER_NOT_FOUND_EXCEPTION.create();
    }

    @Override
    public String parse(StringReader reader) {
        boolean isAt = reader.peek() == '@';
        if (isAt) reader.read();
        return (isAt ? "@" : "") + reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        S contextSource = context.getSource();
        if (contextSource instanceof CommandSource source) {
            return CommandSource.suggestMatching(getPlayerNameList(source), builder);
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
