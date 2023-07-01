package top.gregtao.concerto.experimental.player.mc;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.util.JsonUtil;

import java.util.Objects;

public class ConcertoBlockEntity extends BlockEntity {

    public Music currentMusic;

    public boolean isPlaying = false;

    public ConcertoBlockEntity(BlockPos pos, BlockState state) {
        super(CONCERTO_BLOCK_ENTITY, pos, state);
    }

    public static BlockEntityType<ConcertoBlockEntity> CONCERTO_BLOCK_ENTITY;

    public static void register() {
        CONCERTO_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ConcertoClient.MOD_ID, "concerto_block_entity"),
                FabricBlockEntityTypeBuilder.create(ConcertoBlockEntity::new, ConcertoBlock.EXAMPLE_BLOCK).build()
        );
    }

    public void updatePlayer() {
        this.markDirty();
        if (this.world != null) {
            BlockState state = this.world.getBlockState(this.pos);
            this.world.updateListeners(this.pos, state, state, Block.NOTIFY_ALL);
        }
    }

    public void setPlaying(Music music) {
        this.isPlaying = true;
        this.currentMusic = music;
        this.updatePlayer();
    }

    public void setStopped() {
        this.isPlaying = false;
        this.currentMusic = null;
        this.updatePlayer();
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = super.toInitialChunkDataNbt();
        this.writeNbt(nbt);
        return nbt;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        String raw = nbt.getString("music");
        if (!raw.equals("null")) this.currentMusic = MusicJsonParsers.from(JsonUtil.from(raw));
        this.isPlaying = nbt.getBoolean("play");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        try {
            nbt.putString("music", Objects.requireNonNull(MusicJsonParsers.to(this.currentMusic, false)).toString());
        } catch (NullPointerException e) {
            nbt.putString("music", "null");
        }
        nbt.putBoolean("play", this.isPlaying);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

}
