package top.gregtao.concerto.experimental.player;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import top.gregtao.concerto.experimental.player.mc.ConcertoBlockEntity;
import top.gregtao.concerto.music.Music;

public class ConcertoMusicSound extends MovingSoundInstance {
    private final Music music;
    private final BlockPos blockPos;

    public ConcertoMusicSound(Music music, BlockPos blockPos) {
        super(ConcertoSounds.CONCERTO_SOUND_EVENT, SoundCategory.RECORDS, SoundInstance.createRandom());
        this.music = music;
        this.blockPos = blockPos;
        this.volume = 4;
        this.relative = true;
        this.x = blockPos.getX();
        this.y = blockPos.getY();
        this.z = blockPos.getZ();
    }

    @Override
    public void tick() {
        World world = MinecraftClient.getInstance().world;
        if (world != null) {
            BlockEntity blockEntity = world.getBlockEntity(this.blockPos);
            if (blockEntity instanceof ConcertoBlockEntity concertoBlockEntity) {
                if (!concertoBlockEntity.isPlaying) this.setDone();
            } else {
                this.setDone();
            }
        }
    }

    public Music getMusic() {
        return this.music;
    }
}
