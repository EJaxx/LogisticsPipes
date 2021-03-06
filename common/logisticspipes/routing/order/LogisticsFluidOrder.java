package logisticspipes.routing.order;

import lombok.Getter;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.modules.CrafterBarrier;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class LogisticsFluidOrder extends LogisticsOrder {

	public LogisticsFluidOrder(FluidIdentifier fuild, Integer amount, IRequestFluid destination, ResourceType type, IAdditionalTargetInformation info) {
		super(type, info);
		if (destination == null) {
			throw new NullPointerException();
		}
		fluid = fuild;
		this.amount = amount;
		this.destination = destination;

		if (info instanceof ModuleCrafter.CraftingChassieInformation) {
			CrafterBarrier.DeliveryLine line = ((ModuleCrafter.CraftingChassieInformation) info).deliveryLine;
			if (line != null) line.dst(this, true);
		}
	}

	@Getter
	private final FluidIdentifier fluid;
	@Getter
	private int amount;
	private final IRequestFluid destination;

	@Override
	public ItemIdentifierStack getAsDisplayItem() {
		return fluid.getItemIdentifier().makeStack(amount);
	}

	@Override
	public IRouter getRouter() {
		return destination.getRouter();
	}

	@Override
	public void sendFailed() {
		destination.sendFailed(fluid, amount);
	}

	@Override
	public void reduceAmountBy(int reduce) {
		amount -= reduce;
		if (amount <= 0 && deliveryLine != null) {
			deliveryLine.dst(this, false);
		}
	}
}
