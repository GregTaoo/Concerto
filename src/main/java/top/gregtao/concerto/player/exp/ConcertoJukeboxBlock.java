package top.gregtao.concerto.player.exp;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class ConcertoJukeboxBlock extends BlockWithEntity {

    public static final BooleanProperty HAS_RECORD = BooleanProperty.of("has_record");

    public static final Block CONCERTO_JUKEBOX_BLOCK = new ConcertoJukeboxBlock(FabricBlockSettings.create().strength(4.0f));

    public static void register() {
        Registry.register(Registries.BLOCK, new Identifier("concerto", "jukebox_block"), CONCERTO_JUKEBOX_BLOCK);
        Registry.register(Registries.ITEM, new Identifier("concerto", "jukebox_block"), new BlockItem(CONCERTO_JUKEBOX_BLOCK, new FabricItemSettings()));
        ConcertoJukeboxBlockEntity.register();
        ConcertoDiscItem.register();
    }

    public ConcertoJukeboxBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(HAS_RECORD, false));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        NbtCompound nbtCompound = BlockItem.getBlockEntityNbt(itemStack);
        if (nbtCompound != null && nbtCompound.contains("RecordItem")) {
            world.setBlockState(pos, state.with(HAS_RECORD, true), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof ConcertoJukeboxBlockEntity entity) {
            entity.stopPlaying();
        }
        super.onBroken(world, pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.get(HAS_RECORD) && world.getBlockEntity(pos) instanceof ConcertoJukeboxBlockEntity blockEntity) {
            blockEntity.dropRecord();
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            return;
        }
        if (world.getBlockEntity(pos) instanceof ConcertoJukeboxBlockEntity blockEntity) {
            blockEntity.dropRecord();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ConcertoJukeboxBlockEntity && ((ConcertoJukeboxBlockEntity) blockEntity).isPlayingRecord()) {
            return 15;
        }
        return 0;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return state.get(HAS_RECORD) == Boolean.TRUE ? 1 : 0;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HAS_RECORD);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(HAS_RECORD)) {
            return ConcertoJukeboxBlock.checkType(type, ConcertoJukeboxBlockEntity.CONCERTO_JUKEBOX_BLOCK_ENTITY, ConcertoJukeboxBlockEntity::tick);
        }
        return null;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConcertoJukeboxBlockEntity(pos, state);
    }

}
