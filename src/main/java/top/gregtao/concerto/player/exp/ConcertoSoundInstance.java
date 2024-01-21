package top.gregtao.concerto.player.exp;

import net.minecraft.client.sound.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.music.Music;

public class ConcertoSoundInstance extends MovingSoundInstance {

    private final PlayerEntity player;
    private final ConcertoJukeboxBlockEntity block;
    private final Music music;
    private final WeightedSoundSet weightedSoundSet;

    public ConcertoSoundInstance(PlayerEntity player, ConcertoJukeboxBlockEntity block, Music music) {
        super(ConcertoServer.MY_SOUND_EVENT, SoundCategory.NEUTRAL, SoundInstance.createRandom());
        this.player = player;
        this.block = block;
        this.repeat = false;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
        this.relative = false;
        this.weightedSoundSet = new WeightedSoundSet(ConcertoServer.MY_SOUND_EVENT.getId(), "");
        this.sound = new Sound("concerto:empty", ConstantFloatProvider.create(1.0f),
                ConstantFloatProvider.create(1.0f), 1, Sound.RegistrationType.SOUND_EVENT, true, false, 16);
        this.weightedSoundSet.add(this.sound);
        this.x = (float) block.getPos().getX();
        this.y = (float) block.getPos().getY();
        this.z = (float) block.getPos().getZ();
        this.music = music;
        System.out.println(this.music.getMeta().asString());
    }

    public Music getMusic() {
        return this.music;
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }

    @Override
    public WeightedSoundSet getSoundSet(SoundManager soundManager) {
        return this.weightedSoundSet;
    }

    @Override
    public void tick() {
        if (!this.block.isPlayingRecord()) {
            this.setDone();
        }
        double x = this.player.getX(), y = this.player.getY(), z = this.player.getZ();
        float distance = (float) Math.sqrt(Math.abs(x * x + y * y + z * z - this.x * this.x - this.y * this.y - this.z * this.z));
        this.volume = 1 - distance / 100;
        System.out.println(this.volume);
        this.pitch = 1.0f;
    }
}
