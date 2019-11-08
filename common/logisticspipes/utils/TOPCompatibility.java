package logisticspipes.utils;

import java.util.function.Function;
import java.util.logging.Level;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class TOPCompatibility {

	private static boolean registered;

	public static void register() {
		if (Loader.isModLoaded("theoneprobe")) {
			if (registered)
				return;
			registered = true;
			FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe", "logisticspipes.utils.TOPCompatibility$GetTheOneProbe");
		}
	}

	public static class GetTheOneProbe implements Function<ITheOneProbe, Void> {

		public static ITheOneProbe probe;

		@Nullable
		@Override
		public Void apply(ITheOneProbe theOneProbe) {
			probe = theOneProbe;
			probe.registerProvider(new IProbeInfoProvider() {

				@Override
				public String getID() {
					return "logisticspipes:default";
				}

				@Override
				public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
					TileEntity te = world.getTileEntity(data.getPos());
					if (mode == ProbeMode.EXTENDED && te instanceof LogisticsTileGenericPipe && ((LogisticsTileGenericPipe) te).pipe instanceof TOPInfoProvider) {
						TOPInfoProvider provider = (TOPInfoProvider) ((LogisticsTileGenericPipe) te).pipe;
						provider.addProbeInfo(mode, probeInfo, player, world, blockState, data);
					}
				}
			});
			return null;
		}
	}

	public interface TOPInfoProvider {

		void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data);
	}
}
