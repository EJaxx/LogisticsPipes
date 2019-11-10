package logisticspipes.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.apiimpl.styles.ItemStyle;

import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.InventoryUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import network.rs485.logisticspipes.world.CoordinateUtils;
import network.rs485.logisticspipes.world.IntegerCoordinates;
import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

public class CrafterBarrier {

	public Recipe current = null;
	private static Map<WorldCoordinatesWrapper, CrafterBarrier> globalBarrierRegistry;

	public static CrafterBarrier GetOrCreateBarrier(WorldCoordinatesWrapper wc, EnumFacing direction) {
		IntegerCoordinates newCoords = CoordinateUtils.add(new IntegerCoordinates(wc.getCoords()), direction);
		return GetOrCreateBarrier(new WorldCoordinatesWrapper(wc.getWorld(), newCoords));
	}

	public static CrafterBarrier GetOrCreateBarrier(WorldCoordinatesWrapper wc) {
		CrafterBarrier value = globalBarrierRegistry.get(wc);
		if (value == null) {
			value = new CrafterBarrier();
			globalBarrierRegistry.put(wc, value);
		}
		return value;
	}

	public static class LogisticsModuleValue {
		public ModuleCrafter value = null;
	}

	public static int maxSend(LogisticsItemOrder order, int maxToSend, LogisticsModuleValue setModule, boolean nio) {
		ModuleCrafter destModule = null;
		if (order.getInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
			ModuleCrafter.CraftingChassieInformation info = (ModuleCrafter.CraftingChassieInformation) order.getInformation();
			try {
				LogisticsModule module = order.getDestination().getRouter().getPipe().getLogisticsModule();
				destModule = (ModuleCrafter) (module instanceof ModuleCrafter ? module : module.getSubModule(info.getModuleSlot()));
				int canSend = destModule.myBarrierRecipe.canEnter(order.getResource().stack, info.getCraftingSlot(), null, nio);
				if (canSend <= 0)
					return 0;
				if (setModule != null)
					setModule.value = destModule;
				return Math.min(canSend, maxToSend);
			} catch (NullPointerException e) {
				// System.err.println("ModuleProvider NullPointerException !!!!!");
				return maxToSend;
			}
		}
		return maxToSend;
	}

	public static class Recipe implements ISimpleInventoryEventHandler {

		public CrafterBarrier parent = null;
		public boolean shapeless = false;
		public ItemIdentifierInventory inventory;
		public List<Element> elements = new ArrayList<>(9);
		public InventoryUtil connectedInventory;
		public TileEntity connectedTileEntity;

		public Recipe(ItemIdentifierInventory inventory) {
			this.inventory = inventory;
			for (int i = 0; i < 9; i++) {
				elements.add(new Element(null));
			}
		}

		public void tryUnlock() {
			if (parent.current == this) {
				int steps = elements.stream()
						.filter(o -> o.stack != null && o.stack.getStackSize() > 0)
						.map(o -> o.arrived / o.stack.getStackSize())
						.min(Integer::compare).orElse(0);
				if (steps > 0) {
					elements.stream()
							.filter(o -> o.stack != null && o.stack.getStackSize() > 0)
							.forEach(o -> o.arrived -= steps * o.stack.getStackSize());
				}
				if (elements.stream().allMatch(o -> o.arrived == 0 && o.tickets.isEmpty())) {
					if (!shapeless && connectedTileEntity instanceof LogisticsCraftingTableTileEntity) {
						if (connectedInventory.getItemsAndCount().keySet().stream().anyMatch(o -> !ModuleCrafter.isSharedTool(o)))
							return;
					}
					parent.current = null;
					elements.forEach(o -> o.stack = null);
					System.err.println("Unlocked (" + inventory.getIDStackInSlot(9) + ")");
				}
			}
		}

		// return the amount that can be sent, -1 = never
		public int canEnter(ItemIdentifierStack stack, int targetSlot, IRoutedItem traveler, boolean nio) {
			try {
				if (parent == null || parent.current != this && parent.current != null || connectedInventory == null)
					return 0;

				if (traveler != null) {
					stack = traveler.getItemIdentifierStack();
					ModuleCrafter.CraftingChassieInformation info = (ModuleCrafter.CraftingChassieInformation) traveler.getInfo().targetInfo;
					targetSlot = info.getCraftingSlot();
				}
				Element el = elements.get(targetSlot);
				ItemIdentifierStack inventoryID = inventory.getIDStackInSlot(targetSlot);
				if (inventoryID == null)// || !inventoryID.getItem().equalsForCrafting(stack.getItem()))
					return -1;
				if (el.arrived == 0 && el.stack == null)// || inventoryID.getItem().equalsForCrafting(stack.getItem())))
					el.stack = inventoryID;
//				if (!el.stack.getItem().equals(stack.getItem()))
//					return 0;

				int maxSteps = -1, ticketSum = 0;
				for (int i = 0; i < 9; i++) {
					if (inventory.getIDStackInSlot(i) == null) continue;
					ItemIdentifier invItem = inventory.getIDStackInSlot(i).getItem();
					if (ModuleCrafter.isTool(invItem)) continue;
					int inOrders = elements.get(i).orders.stream()
							.filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER).mapToInt(LogisticsItemOrder::getAmount).sum();
					int inTickets = elements.get(i).tickets.stream()
							.mapToInt(o -> o == traveler ? 0 : o.getItemIdentifierStack().getStackSize()).sum();
					if (i == targetSlot) {
						if (nio)  // not in PROVIDER orders
							inOrders += stack.getStackSize();
						if (traveler != null)  // stowaway item? not in orders, maybe in tickets
							inTickets += stack.getStackSize();
						ticketSum = inTickets;
					}
					int room = (shapeless || connectedTileEntity instanceof LogisticsCraftingTableTileEntity) ?
							connectedInventory.roomForItem(invItem) : connectedInventory.roomForItemToSlot(invItem, i);
					if (!shapeless && connectedTileEntity instanceof LogisticsCraftingTableTileEntity)
						room = invItem.getMaxStackSize(); // matrix will be changed upon receipt of the first item
					int arrived = elements.get(i).arrived;
					int res = Math.min(arrived + inOrders + inTickets, room + arrived) / inventory.getIDStackInSlot(i).getStackSize();
					if (maxSteps == -1 || maxSteps > res && res >= 0) maxSteps = res;
				}
				if (maxSteps == -1)
					maxSteps = 0;
				int res = Math.max(0, maxSteps * inventoryID.getStackSize() - el.arrived - ticketSum);

				if (maxSteps <= 0)
					return 0;
				return res;
			} catch (NullPointerException e) {
				e.printStackTrace();
				elements.get(targetSlot).arrived = 0;
				return 0;
			}
		}

		public void enterBarrier(IRoutedItem traveler) {
			ModuleCrafter.CraftingChassieInformation info = (ModuleCrafter.CraftingChassieInformation) traveler.getInfo().targetInfo;
			Element el = elements.get(info.getCraftingSlot());
			ItemIdentifierStack travelID = traveler.getItemIdentifierStack();

			if (parent.current != this) {
				if (parent.current != null)
					System.err.println("enter to locked Barrier by (" + inventory.getIDStackInSlot(9) + "), current=" + parent.current.inventory.getIDStackInSlot(9) + " !!!!!");
				parent.current = this;
				if (!shapeless && connectedTileEntity instanceof LogisticsCraftingTableTileEntity) {
					LogisticsCraftingTableTileEntity table = ((LogisticsCraftingTableTileEntity) connectedTileEntity);
					ItemStack[] arrItems = new ItemStack[9];
					for (int i = 0; i<9; i++) {
						ItemIdentifierStack v = inventory.getIDStackInSlot(i);
						arrItems[i] = v == null ? ItemStack.EMPTY : v.makeNormalStack();
					}
					table.handleNEIRecipePacket(arrItems);
				}
			}
			el.tickets.add(traveler);
		}

		// return true, if the traveler can enter into
		// todo: split stack
		public boolean itemArrived(IRoutedItem traveler) {
			if (parent == null)
				return false;
			ModuleCrafter.CraftingChassieInformation info = (ModuleCrafter.CraftingChassieInformation) traveler.getInfo().targetInfo;
			Element el = elements.get(info.getCraftingSlot());
			ItemIdentifierStack travelID = traveler.getItemIdentifierStack();

			System.err.println("itemArrived " + traveler.getItemIdentifierStack()+" to (" + inventory.getIDStackInSlot(9) + ")");
			if (!el.tickets.remove(traveler)) {
				System.err.println("pass to (" + inventory.getIDStackInSlot(9) + "), " + traveler.getItemIdentifierStack() + ", stowaway item !!!!!");
				el.stack = travelID.getItem().makeStack(1);
			}
			el.arrived += travelID.getStackSize();
			if (parent.current == this
					&& !(connectedTileEntity instanceof LogisticsCraftingTableTileEntity)) // LogisticsCraftingTable must not be unlocked before extracting
				tryUnlock();
			return true;
		}

		public void itemWasLost(IRoutedItem traveler) {
			if (traveler.getInfo() == null || traveler.getInfo().targetInfo == null) return;
			ModuleCrafter.CraftingChassieInformation info = (ModuleCrafter.CraftingChassieInformation) traveler.getInfo().targetInfo;
			Element el = elements.get(info.getCraftingSlot());
			el.tickets.remove(traveler);
		}

		@Override
		public void InventoryChanged(IInventory inventory) {
			if (!(inventory instanceof ItemIdentifierInventory))
				return;
			for (int i = 0; i < 9 && i < inventory.getSizeInventory(); i++) {
				ItemIdentifierStack nv = ((ItemIdentifierInventory) inventory).getIDStackInSlot(i);
				Element el = elements.get(i);
				if (el.stack == null || el.arrived == 0) {
					el.stack = nv;
				}
			}
		}

		public void addOrder(LogisticsItemOrder logisticsItemOrder) {
			if (logisticsItemOrder.getInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
				int craftingSlot = ((ModuleCrafter.CraftingChassieInformation) logisticsItemOrder.getInformation()).getCraftingSlot();
				elements.get(craftingSlot).orders.add(logisticsItemOrder);
			}
		}

		public void removeOrder(LogisticsItemOrder logisticsItemOrder) {
			if (logisticsItemOrder.getInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
				int craftingSlot = ((ModuleCrafter.CraftingChassieInformation) logisticsItemOrder.getInformation()).getCraftingSlot();
				elements.get(craftingSlot).orders.remove(logisticsItemOrder);
			}
		}

		public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
			ItemIdentifierStack res = inventory.getIDStackInSlot(9);
			if (res != null)
				probeInfo.text("Locked to: " + res.getItem().getFriendlyName());

			Element it;
			for (int i = 0; i < 9 && i < inventory.getSizeInventory(); i++) {
				res = inventory.getIDStackInSlot(i);
				it = elements.get(i);
				if (it != null && res != null)
					probeInfo
							.horizontal().item(res.getItem().makeNormalStack(1), new ItemStyle().width(16).height(8)).text(res.getItem().getFriendlyName())
							.text(": a" + it.arrived
									+ "/t" + it.tickets.stream().mapToInt(o -> o.getItemIdentifierStack().getStackSize()).sum()
									+ "/p" + it.orders.stream().filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER).mapToInt(LogisticsItemOrder::getAmount).sum()
									+ "/c" + it.orders.stream().filter(o -> o.getType() != IOrderInfoProvider.ResourceType.PROVIDER).mapToInt(LogisticsItemOrder::getAmount).sum()
							);
			}
		}
	}

	public static class Element {

		Element(ItemIdentifierStack fromStack) { stack = fromStack; }

		ItemIdentifierStack stack;
		int arrived;
		public HashSet<IRoutedItem> tickets = new HashSet<>();
		public HashSet<LogisticsItemOrder> orders = new HashSet<>();
	}
}
