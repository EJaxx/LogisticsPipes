package logisticspipes.network.guis.pipe;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.abstractguis.BooleanModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.DummyContainer;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

@StaticResolve
public class ChassiGuiProvider extends BooleanModuleCoordinatesGuiProvider {

	private UUID satelliteItemOverride;
	private UUID satelliteFluidOverride;

	public ChassiGuiProvider(int id) {
		super(id);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		LogisticsTileGenericPipe pipe = getPipe(player.getEntityWorld());
		if (pipe == null || !(pipe.pipe instanceof PipeLogisticsChassi)) {
			return null;
		}
		((PipeLogisticsChassi) pipe.pipe).satelliteItemOverride = satelliteItemOverride;
		((PipeLogisticsChassi) pipe.pipe).satelliteFluidOverride = satelliteFluidOverride;
		return new GuiChassiPipe(player, (PipeLogisticsChassi) pipe.pipe, isFlag());
	}

	@Override
	public DummyContainer getContainer(EntityPlayer player) {
		LogisticsTileGenericPipe pipe = getPipe(player.getEntityWorld());
		if (pipe == null || !(pipe.pipe instanceof PipeLogisticsChassi)) {
			return null;
		}
		final PipeLogisticsChassi _chassiPipe = (PipeLogisticsChassi) pipe.pipe;
		IInventory _moduleInventory = _chassiPipe.getModuleInventory();
		DummyContainer dummy = new DummyContainer(player.inventory, _moduleInventory);
		int normalSlotsY = Math.max(97, 9 + _chassiPipe.getChassiSize() * 20 + 12 * 2);
		dummy.addNormalSlotsForPlayerInventory(18, normalSlotsY);
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			dummy.addModuleSlot(i, _moduleInventory, 19, 9 + i * 20, _chassiPipe);
		}

		satelliteItemOverride = _chassiPipe.satelliteItemOverride;
		satelliteFluidOverride = _chassiPipe.satelliteFluidOverride;
		if (_chassiPipe.getUpgradeManager().hasUpgradeModuleUpgrade()) {
			for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
				final int fI = i;
				ModuleUpgradeManager upgradeManager = _chassiPipe.getModuleUpgradeManager(i);
				dummy.addUpgradeSlot(0, upgradeManager, 0, 145, 9 + i * 20, itemStack -> ChassiGuiProvider.checkStack(itemStack, _chassiPipe, fI));
				dummy.addUpgradeSlot(1, upgradeManager, 1, 165, 9 + i * 20, itemStack -> ChassiGuiProvider.checkStack(itemStack, _chassiPipe, fI));
			}
		}
		return dummy;
	}

	public static boolean checkStack(ItemStack stack, PipeLogisticsChassi chassiPipe, int moduleSlot) {
		if (stack == null) {
			return false;
		}
		if (!(stack.getItem() instanceof ItemUpgrade)) {
			return false;
		}
		LogisticsModule module = chassiPipe.getModules().getModule(moduleSlot);
		if (module == null) {
			return false;
		}
		return ((ItemUpgrade) stack.getItem()).getUpgradeForItem(stack, null).isAllowedForModule(module);
	}

	@Override
	public GuiProvider template() {
		return new ChassiGuiProvider(getId());
	}

	@Override
	public void writeData(LPDataOutput output) {
		super.writeData(output);
		output.writeUUID(satelliteItemOverride);
		output.writeUUID(satelliteFluidOverride);
	}

	@Override
	public void readData(LPDataInput input) {
		super.readData(input);
		satelliteItemOverride = input.readUUID();
		satelliteFluidOverride = input.readUUID();
	}
}
