/**
 * Copyright (c) Krapht, 2011
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.gui;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

import logisticspipes.blocks.LogisticsSecurityTileEntity;
import logisticspipes.gui.popup.GuiSelectSatellitePopup;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.pipe.ChassiGuiProvider;
import logisticspipes.network.packets.chassis.ChassisGUI;
import logisticspipes.network.packets.gui.OpenUpgradePacket;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.string.StringUtils;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

public class GuiChassiPipe extends LogisticsBaseGuiScreen {

	private final PipeLogisticsChassi _chassiPipe;
	private final IInventory _moduleInventory;
	//private final GuiScreen _previousGui;
	private final List<SmallGuiButton> moduleConfigButtons = new LinkedList<>();

	private final Slot[] upgradeslot = new Slot[16];
	private GuiButton[] upgradeConfig = new GuiButton[16];

	private boolean hasUpgradeModuleUpgarde;
	int normalSlotsY;
	private SmallGuiButton satOverrideItems;
	private SmallGuiButton satOverrideFluids;

	public GuiChassiPipe(EntityPlayer player, PipeLogisticsChassi chassi, boolean hasUpgradeModuleUpgarde) { //, GuiScreen previousGui) {
		super(null);
		_chassiPipe = chassi;
		_moduleInventory = chassi.getModuleInventory();
		//_previousGui = previousGui;
		this.hasUpgradeModuleUpgarde = hasUpgradeModuleUpgarde;

		DummyContainer dummy = new DummyContainer(player.inventory, _moduleInventory);
		normalSlotsY = Math.max(97, 9 + _chassiPipe.getChassiSize() * 20 + 12 * 2);
		dummy.addNormalSlotsForPlayerInventory(18, normalSlotsY);
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			dummy.addModuleSlot(i, _moduleInventory, 19, 9 + i * 20, _chassiPipe);
		}

		if (hasUpgradeModuleUpgarde) {
			for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
				final int fI = i;
				ModuleUpgradeManager upgradeManager = _chassiPipe.getModuleUpgradeManager(i);
				upgradeslot[i * 2] = dummy.addUpgradeSlot(0, upgradeManager, 0, 145, 9 + i * 20, itemStack -> ChassiGuiProvider.checkStack(itemStack, _chassiPipe, fI));
				upgradeslot[i * 2 + 1] = dummy.addUpgradeSlot(1, upgradeManager, 1, 165, 9 + i * 20, itemStack -> ChassiGuiProvider.checkStack(itemStack, _chassiPipe, fI));
			}
		}

		inventorySlots = dummy;

		xSize = 194;
		ySize = 9 + normalSlotsY + 76 + 9;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initGui() {
		super.initGui();

		int left = width / 2 - xSize / 2;
		int top = height / 2 - ySize / 2;

		buttonList.clear();
		moduleConfigButtons.clear();
		upgradeConfig = new GuiButton[_chassiPipe.getChassiSize() * 2];
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			moduleConfigButtons.add(addButton(new SmallGuiButton(i, left + 5, top + 12 + 20 * i, 10, 10, "!")));
			if (_moduleInventory == null) {
				continue;
			}
			ItemStack module = _moduleInventory.getStackInSlot(i);
			if (module.isEmpty() || _chassiPipe.getLogisticsModule().getSubModule(i) == null) {
				moduleConfigButtons.get(i).visible = false;
			} else {
				moduleConfigButtons.get(i).visible = _chassiPipe.getLogisticsModule().getSubModule(i).hasGui();
			}
			buttonList.add(satOverrideItems = new SmallGuiButton(141, guiLeft + 14 + 28, guiTop + normalSlotsY - 14, 55, 10, _chassiPipe.satelliteItemOverride == null ? "Off" : _chassiPipe.satelliteItemOverride.toString()));
			buttonList.add(satOverrideFluids = new SmallGuiButton(142, guiLeft + 14 + 80 + 28, guiTop + normalSlotsY - 14, 55, 10, _chassiPipe.satelliteFluidOverride == null ? "Off" : _chassiPipe.satelliteFluidOverride.toString()));

			if (hasUpgradeModuleUpgarde) {
				upgradeConfig[i * 2] = addButton(new SmallGuiButton(100 + i, guiLeft + 134, guiTop + 12 + i * 20, 10, 10, "!"));
				upgradeConfig[i * 2].visible = _chassiPipe.getModuleUpgradeManager(i).hasGuiUpgrade(0);
				upgradeConfig[i * 2 + 1] = addButton(new SmallGuiButton(120 + i, guiLeft + 182, guiTop + 12 + i * 20, 10, 10, "!"));
				upgradeConfig[i * 2 + 1].visible = _chassiPipe.getModuleUpgradeManager(i).hasGuiUpgrade(1);
			}
		}
	}

	@Override
	protected void actionPerformed(GuiButton guibutton) {
		if (guibutton.id >= 0 && guibutton.id < _chassiPipe.getChassiSize()) {
			LogisticsModule module = _chassiPipe.getLogisticsModule().getSubModule(guibutton.id);
			if (module != null) {
				final ModernPacket packet = PacketHandler.getPacket(ChassisGUI.class).setButtonID(guibutton.id).setPosX(_chassiPipe.getX()).setPosY(_chassiPipe.getY()).setPosZ(_chassiPipe.getZ());
				MainProxy.sendPacketToServer(packet);
			}
		}
		for (int i = 0; i < upgradeConfig.length; i++) {
			if (upgradeConfig[i] == guibutton) {
				MainProxy.sendPacketToServer(PacketHandler.getPacket(OpenUpgradePacket.class).setSlot(upgradeslot[i]));
			}
		}
		if (guibutton.id == 141 || guibutton.id == 142) openSubGuiForSatelliteSelection(guibutton.id == 142);
	}

	private void openSubGuiForSatelliteSelection(boolean fluidSatellite) {
		this.setSubGui(new GuiSelectSatellitePopup(new BlockPos(_chassiPipe.getX(), _chassiPipe.getY(), _chassiPipe.getZ()), fluidSatellite, uuid -> {
					if (fluidSatellite) _chassiPipe.satelliteFluidOverride = uuid;
					else _chassiPipe.satelliteItemOverride = uuid;
					MainProxy.sendPacketToServer(
							PacketHandler.getPacket(SetSatelliteUUID.class).setUuid(uuid).setFluidSatellite(fluidSatellite).setBlockPos(_chassiPipe.getPos()));
				})
		);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int par1, int par2) {
		super.drawGuiContainerForegroundLayer(par1, par2);
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			ItemStack module = _moduleInventory.getStackInSlot(i);
			if (module.isEmpty() || _chassiPipe.getLogisticsModule().getSubModule(i) == null) {
				moduleConfigButtons.get(i).visible = false;
			} else {
				moduleConfigButtons.get(i).visible = _chassiPipe.getLogisticsModule().getSubModule(i).hasGui();
			}
		}
		if (hasUpgradeModuleUpgarde) {
			for (int i = 0; i < upgradeConfig.length; i++) {
				upgradeConfig[i].visible = _chassiPipe.getModuleUpgradeManager(i / 2).hasGuiUpgrade(i % 2);
			}
		}
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			mc.fontRenderer.drawString(getModuleName(i), 40, 14 + i * 20, 0x404040);
		}
		mc.fontRenderer.drawString("Satellite Override", 45, normalSlotsY - 18 - 4, 0x404040);
		mc.fontRenderer.drawString("Item", 18, normalSlotsY - 9 - 4, 0x404040);
		mc.fontRenderer.drawString("Fluid", 18 + 80, normalSlotsY - 9 - 4, 0x404040);
	}

	private String getModuleName(int slot) {
		if (_moduleInventory == null) {
			return "";
		}
		if (_moduleInventory.getStackInSlot(slot).isEmpty()) {
			return "";
		}
		if (!(_moduleInventory.getStackInSlot(slot).getItem() instanceof ItemModule)) {
			return "";
		}
		String name = ((ItemModule) _moduleInventory.getStackInSlot(slot).getItem()).getItemStackDisplayName(_moduleInventory.getStackInSlot(slot));
		if (!hasUpgradeModuleUpgarde) {
			return name;
		}
		return StringUtils.getWithMaxWidth(name, 100, fontRenderer);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
		GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
		GuiGraphics.drawPlayerInventoryBackground(mc, guiLeft + 18, guiTop + normalSlotsY);
		for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
			GuiGraphics.drawSlotBackground(mc, guiLeft + 18, guiTop + 8 + i * 20);
		}
		if (hasUpgradeModuleUpgarde) {
			for (int i = 0; i < _chassiPipe.getChassiSize(); i++) {
				GuiGraphics.drawSlotBackground(mc, guiLeft + 144, guiTop + 8 + i * 20);
				GuiGraphics.drawSlotBackground(mc, guiLeft + 164, guiTop + 8 + i * 20);
			}
		}
		satOverrideItems.drawButton(mc, x, y, f);
		satOverrideFluids.drawButton(mc, x, y, f);
	}

	@StaticResolve
	public static class SetSatelliteUUID extends CoordinatesPacket {

		@Getter
		@Setter
		private UUID uuid;
		@Getter
		@Setter
		private Boolean fluidSatellite;

		public SetSatelliteUUID(int id) {
			super(id);
		}

		@Override
		public ModernPacket template() {
			return new SetSatelliteUUID(getId());
		}

		@Override
		public void processPacket(EntityPlayer player) {
			LogisticsTileGenericPipe tile = this.getTile(player.world, LogisticsTileGenericPipe.class);
			if (tile == null) return;
			((PipeLogisticsChassi) tile.pipe).setSatelliteUUID(getUuid(), getFluidSatellite());
		}

		@Override
		public void writeData(LPDataOutput output) {
			super.writeData(output);
			output.writeUUID(uuid);
			output.writeBoolean(fluidSatellite);
		}

		@Override
		public void readData(LPDataInput input) {
			super.readData(input);
			uuid = input.readUUID();
			fluidSatellite = input.readBoolean();
		}
	}
}
