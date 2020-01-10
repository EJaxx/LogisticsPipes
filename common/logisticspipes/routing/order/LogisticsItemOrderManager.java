package logisticspipes.routing.order;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.ILPPositionProvider;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.modules.CrafterBarrier;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.DictResource;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class LogisticsItemOrderManager extends LogisticsOrderManager<LogisticsItemOrder, DictResource.Identifier> {

	private static class IC implements LogisticsOrderLinkedList.IIdentityProvider<LogisticsItemOrder, DictResource.Identifier> {

		@Override
		public DictResource.Identifier getIdentity(LogisticsItemOrder o) {
			if (o == null || o.getResource() == null) {
				return null;
			}
			return o.getResource().getIdentifier();
		}

		@Override
		public boolean isExtra(LogisticsItemOrder o) {
			return o instanceof LogisticsItemOrderExtra;
		}
	}

	private static class LogisticsItemOrderExtra extends LogisticsItemOrder {

		public LogisticsItemOrderExtra(DictResource item, IRequestItems destination, ResourceType type, IAdditionalTargetInformation info) {
			super(item, destination, type, info);
		}
	}

	public LogisticsItemOrderManager(ILPPositionProvider pos) {
		super(new LogisticsOrderLinkedList<LogisticsItemOrder, DictResource.Identifier>(new IC()), pos);
	}

	public LogisticsItemOrderManager(IChangeListener listener, ILPPositionProvider pos) {
		super(listener, pos, new LogisticsOrderLinkedList<LogisticsItemOrder, DictResource.Identifier>(new IC()));
	}

	@Override
	public void sendFailed() {
		_orders.getFirst().sendFailed();
		super.sendFailed();
	}

	public void addOrJoinOrder(ItemIdentifierStack stack, IRequestItems requester, ResourceType type, IAdditionalTargetInformation info) {
		if (info instanceof ModuleCrafter.CraftingChassieInformation) {
			CrafterBarrier.DeliveryLine line = ((ModuleCrafter.CraftingChassieInformation) info).deliveryLine;
			if (line != null) {
				for (LogisticsOrder ord : line.orders.keySet())
					if (ord.getType() == ResourceType.PROVIDER && type == ResourceType.PROVIDER && !ord.isFinished() && ord.deliveryLine.equals(line)) {
						if (ord instanceof LogisticsItemOrder) {
							LogisticsItemOrder order = (LogisticsItemOrder) ord;
							if (order.getResource().getItem().equals(stack.getItem()) && order.getDestination().equals(requester)) {
								order.reduceAmountBy(-stack.getStackSize());
								return;
							} else {
								System.err.print("AssertionError");
							}
						} else if (ord instanceof LogisticsFluidOrder) {
							LogisticsFluidOrder order = (LogisticsFluidOrder) ord;
							FluidIdentifierStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
							fluid.setAmount(fluid.getAmount() * stack.getStackSize());
							if (order.getFluid().equals(fluid.getFluid()) && order.getRouter().equals(requester)) {
								order.reduceAmountBy(-fluid.getAmount());
								return;
							} else {
								System.err.print("AssertionError");
							}
						}
					}
				System.err.println("recipe: " + line.group.owner.getCraftedItem());
			}
		}
		addOrder(stack, requester, type, info);
	}

	public LogisticsItemOrder addOrder(ItemIdentifierStack stack, IRequestItems requester, ResourceType type, IAdditionalTargetInformation info) {
		System.err.println("addOrder: " + stack + ", " + requester + ", " + type + ", " + info);
		LogisticsItemOrder order = new LogisticsItemOrder(new DictResource(stack, null), requester, type, info);
		_orders.addLast(order);
		listen();
		return order;
	}

	public LogisticsItemOrder addOrder(DictResource stack, IRequestItems requester, ResourceType type, IAdditionalTargetInformation info) {
		LogisticsItemOrder order = new LogisticsItemOrder(stack, requester, type, info);
		_orders.addLast(order);
		listen();
		return order;
	}

	public LogisticsItemOrderExtra addExtra(DictResource stack) {
		LogisticsItemOrderExtra order = new LogisticsItemOrderExtra(stack, null, ResourceType.EXTRA, null);
		_orders.addLast(order);
		listen();
		return order;
	}

	public void removeExtras(DictResource resource) {
		int itemsToRemove = resource.getRequestedAmount();
		DictResource.Identifier ident = resource.getIdentifier();
		Iterator<LogisticsItemOrder> iter = _orders.iterator();
		List<LogisticsItemOrder> toRemove = new LinkedList<LogisticsItemOrder>();
		while (iter.hasNext()) {
			LogisticsItemOrder order = iter.next();
			if (order.getType() != ResourceType.EXTRA) continue;
			if (order.getResource().getIdentifier().equals(ident)) {
				if (itemsToRemove >= order.getAmount()) {
					itemsToRemove -= order.getAmount();
					toRemove.add(order);
					if (itemsToRemove == 0) {
						_orders.removeAll(toRemove);
						return;
					}
				} else {
					order.getResource().getItemStack().setStackSize(order.getAmount() - itemsToRemove);
					break;
				}
			}
		}
		_orders.removeAll(toRemove);
	}

	public int totalItemsCountInOrders(ItemIdentifier item) {
		int itemCount = 0;
		for (LogisticsItemOrder request : _orders) {
			if (!request.getResource().getItem().equals(item)) {
				continue;
			}
			itemCount += request.getResource().stack.getStackSize();
		}
		return itemCount;
	}
}
