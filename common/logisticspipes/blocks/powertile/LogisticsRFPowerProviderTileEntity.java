package logisticspipes.blocks.powertile;

import logisticspipes.asm.ModDependentField;
import logisticspipes.asm.ModDependentInterface;
import logisticspipes.asm.ModDependentMethod;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;

@ModDependentInterface(modId={"CoFHAPI|energy"}, interfacePath={"cofh.api.energy.IEnergyHandler"})
public class LogisticsRFPowerProviderTileEntity extends LogisticsPowerProviderTileEntity implements IEnergyHandler {
	
	public static final int MAX_STORAGE = 10000000;
	public static final int MAX_MAXMODE = 8;
	public static final int MAX_PROVIDE_PER_TICK = 10000; //TODO
	
	@ModDependentField(modId="CoFHAPI|energy")
	private EnergyStorage	storage;
	
	public LogisticsRFPowerProviderTileEntity() {
		if(SimpleServiceLocator.cofhPowerProxy.isAvailable()) {
			storage	= new EnergyStorage(10000);
		}
	}

	public void addEnergy(float amount) {
		if(MainProxy.isClient(getWorldObj())) return;
		internalStorage += amount;
		if(internalStorage > MAX_STORAGE) {
			internalStorage = MAX_STORAGE;
		}
		if(internalStorage >= getMaxStorage())
			needMorePowerTriggerCheck=false;
	}
	
	private void addStoredRF() {
		if(SimpleServiceLocator.cofhPowerProxy.isAvailable()) {
			int space = freeSpace();
			int available = (int)(storage.extractEnergy(space, true));
			if(available > 0) {
				if(storage.extractEnergy(available, false) == available) {
					addEnergy(available);
				}
			}
		}
	}

	public int freeSpace() {
		return (int) (getMaxStorage() - internalStorage);
	}
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		if(MainProxy.isServer(this.worldObj)) {
			if(freeSpace() > 0) {
				addStoredRF();
			}
		}
	}
	
	@Override
	@ModDependentMethod(modId="CoFHAPI|energy")
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
		return this.storage.receiveEnergy(maxReceive, simulate);
	}
	
	@Override
	@ModDependentMethod(modId="CoFHAPI|energy")
	public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
		return this.storage.extractEnergy(maxExtract, simulate);
	}
	
	@Override
	@ModDependentMethod(modId="CoFHAPI|energy")
	public boolean canConnectEnergy(ForgeDirection from) {
		return true;
	}
	
	@Override
	@ModDependentMethod(modId="CoFHAPI|energy")
	public int getEnergyStored(ForgeDirection from) {
		return this.storage.getEnergyStored();
	}
	
	@Override
	@ModDependentMethod(modId="CoFHAPI|energy")
	public int getMaxEnergyStored(ForgeDirection from) {
		return this.storage.getMaxEnergyStored();
	}
	
	public int getMaxStorage() {
		maxMode = Math.min(MAX_MAXMODE, Math.max(1, maxMode));
		return (MAX_STORAGE / maxMode);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if(SimpleServiceLocator.cofhPowerProxy.isAvailable()) {
			storage.readFromNBT(nbt);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		if(SimpleServiceLocator.cofhPowerProxy.isAvailable()) {
			storage.writeToNBT(nbt);
		}
	}

	@Override
	public String getBrand() {
		return "RF";
	}

	@Override
	protected float getMaxProvidePerTick() {
		return MAX_PROVIDE_PER_TICK;
	}

	@Override
	protected void handlePower(CoreRoutedPipe pipe, float toSend) {
		pipe.handleRFPowerArival(toSend);
	}

	@Override
	protected int getLaserColor() {
		return RF_COLOR;
	}
}
