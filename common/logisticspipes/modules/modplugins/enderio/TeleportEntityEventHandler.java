package logisticspipes.modules.modplugins.enderio;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import crazypants.enderio.api.teleport.TeleportEntityEvent;

import logisticspipes.LPConstants;

@Mod.EventBusSubscriber(modid = LPConstants.LP_MOD_ID)
public class TeleportEntityEventHandler {

	static boolean doneReg = false;

	public static void reg() {
		if (doneReg) return;
		MinecraftForge.EVENT_BUS.register(new TeleportEntityEventHandler());
		doneReg = true;
	}

	@SubscribeEvent
	public void onTeleportEntityEvent(TeleportEntityEvent evt) {
		if (evt.getEntity().world.isRemote) return;
		evt.setCanceled(true);

		Entity toTp = evt.getEntity();
		EntityPlayer player = toTp instanceof EntityPlayer ? (EntityPlayer) toTp : null;

		BlockPos pos = evt.getTarget();
		World world = evt.getEntity().world;
		for (int n = 0; n < 16; n++) {
			IBlockState state = world.getBlockState(pos);
			BlockPos pos1 = pos.up();
			IBlockState state1 = world.getBlockState(pos1);
			if ((!state.getMaterial().blocksMovement() || state.getBlock().isLeaves(state, world, pos) || state.getBlock().isFoliage(world, pos)) &&
					(!state1.getMaterial().blocksMovement() || state1.getBlock().isLeaves(state1, world, pos1) || state1.getBlock().isFoliage(world, pos1))) {
				if (player != null) {
					player.setPositionAndUpdate(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				} else {
					toTp.setPosition(pos.getX(), pos.getY(), pos.getZ());
				}
				toTp.fallDistance = 0;
				break;
			}
			pos = pos.up();
		}
	}
}
