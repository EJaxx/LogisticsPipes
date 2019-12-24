package logisticspipes.pipes;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidTank;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.ITankUtil;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.network.GuiIDs;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;
import logisticspipes.utils.tuples.Triplet;

public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink {

	public ItemIdentifierInventory filterInv = new ItemIdentifierInventory(1, "Dummy", 1, true);

	public ItemIdentifierInventory getMyInventory() {
		return filterInv;
	}

	private PlayerCollectionList guiOpenedBy = new PlayerCollectionList();

	public PipeFluidBasic(Item item) {
		super(item);
	}

	@Override
	public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
		super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
		for (Pair<ItemIdentifierStack, Integer> slot : getMyInventory()) {
			if (slot.getValue1() != null)
				probeInfo.horizontal()
						.text("Target for: ")
						.text(slot.getValue1().getItem().getFriendlyName());
		}
	}

	@Override
	public TextureType getCenterTexture() {
		return Textures.LOGISTICSPIPE_LIQUID_BASIC;
	}

	@Override
	public boolean canInsertFromSideToTanks() {
		return true;
	}

	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_Fluid_Basic_ID, getWorld(), getX(), getY(), getZ());
	}

	@Override
	public int sinkAmount(FluidIdentifierStack stack) {
		if (!guiOpenedBy.isEmpty()) {
			return 0; //Don't sink when the gui is open
		}
		FluidIdentifier ident = stack.getFluid();
		if (getMyInventory().getIDStackInSlot(0) == null) {
			return 0;
		}
		if (!ident.equals(FluidIdentifier.get(getMyInventory().getIDStackInSlot(0).getItem()))) {
			return 0;
		}
		int onTheWay = this.countOnRoute(ident);
		long freeSpace = -onTheWay;
		for (Triplet<ITankUtil, TileEntity, EnumFacing> pair : getAdjacentTanksAdvanced(true)) {
			FluidTank tank = ((PipeFluidTransportLogistics) transport).sideTanks[pair.getValue3().ordinal()];
			freeSpace += pair.getValue1().getFreeSpaceInsideTank(ident);
			freeSpace += ident.getFreeSpaceInsideTank(tank);
			if (freeSpace >= stack.getAmount()) {
				return stack.getAmount();
			}
		}
		return freeSpace > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) freeSpace;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		getMyInventory().writeToNBT(nbttagcompound);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		getMyInventory().readFromNBT(nbttagcompound);
	}

	@Override
	public boolean canInsertToTanks() {
		return true;
	}

	public void guiOpenedByPlayer(EntityPlayer player) {
		guiOpenedBy.add(player);
	}

	public void guiClosedByPlayer(EntityPlayer player) {
		guiOpenedBy.remove(player);
	}

	@Override
	public boolean canReceiveFluid() {
		return false;
	}
}
