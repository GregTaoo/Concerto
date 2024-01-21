package top.gregtao.concerto.player.exp;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public class ConcertoDiscItem extends Item {

    public static final Item DISC = new ConcertoDiscItem();

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("concerto", "disc"), DISC);
    }

    public ConcertoDiscItem() {
        super(new FabricItemSettings());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        BlockPos blockPos;
        World world = context.getWorld();
        BlockState blockState = world.getBlockState(blockPos = context.getBlockPos());
        if (!blockState.isOf(ConcertoJukeboxBlock.CONCERTO_JUKEBOX_BLOCK) || blockState.get(JukeboxBlock.HAS_RECORD)) {
            return ActionResult.PASS;
        }
        ItemStack itemStack = context.getStack();
        if (!world.isClient) {
            PlayerEntity playerEntity = context.getPlayer();
            BlockEntity blockEntity = world.getBlockEntity(blockPos);
            if (blockEntity instanceof ConcertoJukeboxBlockEntity jukeboxBlockEntity) {
                NbtCompound nbt = itemStack.getOrCreateNbt();
                nbt.putString("music", "eyJuYW1lIjoicXFfbXVzaWMiLCJtaWQiOiIwMDNSejJIYjRGQXlUaCIsIm1ldGEiOnsibmFtZSI6ImJhc2ljIiwiZHIiOjEwNzAwMCwidGl0bGUiOiJCYXJkJ3MgQWR2ZW50dXJlIOivl+S6uueahOW3peS9nCIsInNyYyI6IlFR6Z+z5LmQIiwiYXV0aG9yIjoi6ZmI6Ie06YC4LCBIT1lPLU1pWCIsInBpYyI6Imh0dHBzOi8veS5xcS5jb20vbXVzaWMvcGhvdG9fbmV3L1QwMDJSNTAweDUwME0wMDAwMDRTak83QTJuV2RDaF8xLmpwZyJ9fQ==");
                itemStack.setNbt(nbt);
                jukeboxBlockEntity.setStack(itemStack.copy());
                world.updateListeners(blockPos, blockState, blockState, Block.NOTIFY_ALL);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(playerEntity, blockState));
            }
            itemStack.decrement(1);
            if (playerEntity != null) {
                playerEntity.incrementStat(Stats.PLAY_RECORD);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
