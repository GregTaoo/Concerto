package top.gregtao.concerto.experimental.player.mc;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.music.NeteaseCloudMusic;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoBlock extends Block implements BlockEntityProvider {

    public ConcertoBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand == Hand.MAIN_HAND && !world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ConcertoBlockEntity concertoBlockEntity) {
                if (!concertoBlockEntity.isPlaying) {
                    concertoBlockEntity.setPlaying(new NeteaseCloudMusic("114514", NeteaseCloudMusic.Level.STANDARD));
                    ServerMusicNetworkHandler.playerBlockStatusUpdate(concertoBlockEntity);
                } else {
                    concertoBlockEntity.setStopped();
                }
            }
        } else {
            return ActionResult.PASS;
        }
        return ActionResult.SUCCESS;
    }

    public static final Block EXAMPLE_BLOCK = new ConcertoBlock(Settings.create());

    public static void register() {
        Registry.register(Registries.BLOCK, new Identifier("concerto", "example_block"), EXAMPLE_BLOCK);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConcertoBlockEntity(pos, state);
    }
}
