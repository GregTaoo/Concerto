package top.gregtao.concerto.player.exp;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.TextUtil;

import java.util.Base64;
import java.util.Objects;

public class ConcertoJukeboxBlockEntity extends BlockEntity implements Clearable, SingleStackInventory {

    public static BlockEntityType<ConcertoJukeboxBlockEntity> CONCERTO_JUKEBOX_BLOCK_ENTITY;

    public static void register() {
        CONCERTO_JUKEBOX_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier("concerto", "jukebox_block_entity"),
                FabricBlockEntityTypeBuilder.create(ConcertoJukeboxBlockEntity::new, ConcertoJukeboxBlock.CONCERTO_JUKEBOX_BLOCK).build()
        );
    }

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(this.size(), ItemStack.EMPTY);
    private int ticksThisSecond;
    private long tickCount;
    private long recordStartTick;
    private boolean isPlaying;

    public ConcertoJukeboxBlockEntity(BlockPos pos, BlockState state) {
        super(CONCERTO_JUKEBOX_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("RecordItem", NbtElement.COMPOUND_TYPE)) {
            this.inventory.set(0, ItemStack.fromNbt(nbt.getCompound("RecordItem")));
        }
        this.isPlaying = nbt.getBoolean("IsPlaying");
        this.recordStartTick = nbt.getLong("RecordStartTick");
        this.tickCount = nbt.getLong("TickCount");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (!this.getStack().isEmpty()) {
            nbt.put("RecordItem", this.getStack().writeNbt(new NbtCompound()));
        }
        nbt.putBoolean("IsPlaying", this.isPlaying);
        nbt.putLong("RecordStartTick", this.recordStartTick);
        nbt.putLong("TickCount", this.tickCount);
    }

    public boolean isPlayingRecord() {
        return !this.getStack().isEmpty() && this.isPlaying;
    }

    private void updateState(@Nullable Entity entity, boolean hasRecord) {
        if (this.world == null) return;
        if (this.world.getBlockState(this.getPos()) == this.getCachedState()) {
            this.world.setBlockState(this.getPos(), this.getCachedState().with(ConcertoJukeboxBlock.HAS_RECORD, hasRecord), Block.NOTIFY_LISTENERS);
            this.world.emitGameEvent(GameEvent.BLOCK_CHANGE, this.getPos(), GameEvent.Emitter.of(entity, this.getCachedState()));
        }
    }

    public void startPlaying() {
        if (this.world == null) return;
        this.recordStartTick = this.tickCount;
        this.isPlaying = true;
        this.world.updateNeighborsAlways(this.getPos(), this.getCachedState().getBlock());
        this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_LISTENERS);
        if (!this.world.isClient) {
            ServerMusicNetworkHandler.broadcastJukeboxEvent((ServerWorld) this.world, this, true);
        }
        this.markDirty();
    }

    public void stopPlaying() {
        if (this.world == null) return;
        this.isPlaying = false;
        this.world.updateNeighborsAlways(this.getPos(), this.getCachedState().getBlock());
        this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_LISTENERS);
        if (!this.world.isClient) {
            ServerMusicNetworkHandler.broadcastJukeboxEvent((ServerWorld) this.world, this, false);
        }
        this.markDirty();
    }

    private void tick(World world, BlockPos pos, BlockState state) {
        ++this.ticksThisSecond;
        if (this.isPlayingRecord() && this.getStack().getItem() instanceof ConcertoDiscItem item) {
            if (this.isSongFinished(this.getStack())) {
                this.stopPlaying();
            } else if (this.hasSecondPassed()) {
                this.ticksThisSecond = 0;
                this.spawnNoteParticle(world, pos);
            }
        }
        ++this.tickCount;
    }

    private boolean isSongFinished(ItemStack itemStack) {
        NbtCompound nbt = itemStack.getNbt();
        if (nbt != null) {
            Music music = MusicJsonParsers.from(JsonUtil.from(new String(Base64.getDecoder().decode(nbt.getString("music")))));
            return this.tickCount >= this.recordStartTick + music.getMeta().getDuration().asMilliseconds() / 50 + 20;
        }
        return true;
    }

    private boolean hasSecondPassed() {
        return this.ticksThisSecond >= 20;
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack itemStack = Objects.requireNonNullElse(this.inventory.get(slot), ItemStack.EMPTY);
        this.inventory.set(slot, ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            this.updateState(null, false);
            this.stopPlaying();
        }
        return itemStack;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (this.world != null) {
            this.inventory.set(slot, stack);
            this.updateState(null, true);
            this.startPlaying();
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public boolean canTransferTo(Inventory hopperInventory, int slot, ItemStack stack) {
        return hopperInventory.containsAny(ItemStack::isEmpty);
    }

    private void spawnNoteParticle(World world, BlockPos pos) {
        if (world instanceof ServerWorld serverWorld) {
            Vec3d vec3d = Vec3d.ofBottomCenter(pos).add(0.0, 1.2f, 0.0);
            float f = (float) world.getRandom().nextInt(4) / 24.0f;
            serverWorld.spawnParticles(ParticleTypes.NOTE, vec3d.getX(), vec3d.getY(), vec3d.getZ(), 0, f, 0.0, 0.0, 1.0);
        }
    }

    public void dropRecord() {
        if (this.world == null || this.world.isClient) {
            return;
        }
        BlockPos blockPos = this.getPos();
        ItemStack itemStack = this.getStack();
        if (itemStack.isEmpty()) {
            return;
        }
        this.removeStack();
        Vec3d vec3d = Vec3d.add(blockPos, 0.5, 1.01, 0.5).addRandom(this.world.random, 0.7f);
        ItemStack itemStack2 = itemStack.copy();
        ItemEntity itemEntity = new ItemEntity(this.world, vec3d.getX(), vec3d.getY(), vec3d.getZ(), itemStack2);
        itemEntity.setToDefaultPickupDelay();
        this.world.spawnEntity(itemEntity);
    }

    public Music getMusic() {
        ItemStack itemStack = this.getStack();
        NbtCompound nbt = itemStack.getNbt();
        if (nbt != null) {
            String str = nbt.getString("music");
            return MusicJsonParsers.from(TextUtil.fromBase64(str));
        }
        return null;
    }

    public static void tick(World world, BlockPos pos, BlockState state, ConcertoJukeboxBlockEntity blockEntity) {
        blockEntity.tick(world, pos, state);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }
}
