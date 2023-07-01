package top.gregtao.concerto.experimental.player;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;

public class ConcertoSounds {

    public static final Identifier CONCERTO_SOUND_ID = new Identifier(ConcertoClient.MOD_ID, "concerto_sound");
    public static final SoundEvent CONCERTO_SOUND_EVENT = SoundEvent.of(CONCERTO_SOUND_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, CONCERTO_SOUND_ID, CONCERTO_SOUND_EVENT);
    }
}
