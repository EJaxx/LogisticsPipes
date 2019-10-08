package logisticspipes.interfaces;

import net.minecraft.util.EnumFacing;

public interface IPipeUpgradeManager {

	boolean hasPowerPassUpgrade();

	boolean hasRFPowerSupplierUpgrade();

	boolean hasBCPowerSupplierUpgrade();

	int getIC2PowerLevel();

	int getSpeedUpgradeCount();

	boolean isSideDisconnected(EnumFacing side);

	boolean hasCCRemoteControlUpgrade();

	boolean hasCraftingMonitoringUpgrade();

	boolean isOpaque();

	boolean hasUpgradeModuleUpgrade();

	boolean hasPatternUpgrade();

	boolean hasCombinedSneakyUpgrade();

	EnumFacing[] getCombinedSneakyOrientation();

}
