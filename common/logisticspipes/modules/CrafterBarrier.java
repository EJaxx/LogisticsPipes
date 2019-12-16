package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.IItemHandler;

import lombok.AllArgsConstructor;
import lombok.Data;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.apiimpl.styles.ItemStyle;
// import xt9.deepmoblearning.common.tiles.TileEntityExtractionChamber;

import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.modplugins.enderio.TeleportEntityEventHandler;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.IntegerModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.RequestPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.ServerRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.transport.LPTravelingItem;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.DummyModuleContainer;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;
import logisticspipes.utils.tuples.Triplet;

import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

public class CrafterBarrier implements Runnable {

	private Thread thread;
	World posW;
	BlockPos posB;
	private boolean endOfTick;
	final ConcurrentHashMap<CraftingTask, Boolean> registry = new ConcurrentHashMap<>();

	CrafterBarrier(World posW, BlockPos posB) {
		this.posW = posW;
		this.posB = posB;
		thread = new Thread(this, "CrafterBarrier@{" + posB.getX() + ":" + posB.getY() + ":" + posB.getZ() + "}");
		thread.start();
		System.out.println("Created " + posW.getWorldInfo().getWorldName() + ":" + posB);
	}

	@Override
	public String toString() {
		return "CrafterBarrier{" +
				", posB=" + posB +
				'}';
	}

	private static class RecipeLines extends ConcurrentHashMap<Pair<Boolean, Integer>, DeliveryLine> {}

	private static class CrafterKey extends Pair<Integer, Integer> {

		public CrafterKey(Integer value1, Integer value2) {
			super(value1, value2);
		}
	}

	private static final ConcurrentHashMap<CrafterKey, RecipeLines> lines = new ConcurrentHashMap<>();

	@Data
	@AllArgsConstructor
	static
	class CrafterInfo {

		HashMap<Integer, ItemIdentifierStack> inputItems;
		HashMap<Integer, FluidIdentifierStack> inputFluids;
		HashMap<Integer, ItemIdentifierStack> outputItems;
		HashMap<Integer, FluidIdentifierStack> outputFluids;
	}

	@Override
	public void run() {
		while (true) {
			try { Thread.sleep(200);} catch (InterruptedException ignored) {}
			try {
				// CraftingTask.registry.forEach(CraftingTask::run);
				registry.keySet().forEach(o -> {
					o.run();
					if (o.alive) return;
					registry.remove(o);
				});
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
	}

	public Recipe current = null;
	private static Map<Pair<World, BlockPos>, CrafterBarrier> globalBarrierRegistry = new HashMap<>();
	final public static Set<ItemIdentifier> availableTools = Collections.synchronizedSet(new HashSet<>());

	public static CrafterBarrier GetOrCreateBarrier(World posW, BlockPos posB, EnumFacing direction) {
		return GetOrCreateBarrier(posW, posB.offset(direction));
	}

	public static CrafterBarrier GetOrCreateBarrier(World posW, BlockPos posB) {
		CrafterBarrier value = globalBarrierRegistry.get(new Pair<>(posW, posB));
		if (value == null) {
			value = new CrafterBarrier(posW, posB);
			globalBarrierRegistry.put(new Pair<>(posW, posB), value);
		}
		return value;
	}

	public static void serverTickAll(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		globalBarrierRegistry.values().forEach(o -> o.serverTickEnd(event));
		// System.out.println("End of tick");
	}

	public void serverTickEnd(TickEvent.ServerTickEvent event) {
		TileEntity te = posW.getTileEntity(posB);
		if (te == null) return;
		if (te instanceof LogisticsTileGenericPipe) return;

		// inventories collector
		InventoryLink.registry2.values().forEach(InventoryLink::update);

		endOfTick = true;
	}

	public static class LogisticsModuleValue {

		public ModuleCrafter value = null;
	}

	public static class Recipe implements ISimpleInventoryEventHandler {

		public CrafterBarrier parent = null;
		public ItemIdentifierInventory inventory;
		public List<Element> elements = new ArrayList<>(9);

		public Recipe(ItemIdentifierInventory inventory) {
			this.inventory = inventory;
			for (int i = 0; i < 9; i++) {
				elements.add(new Element(null));
			}
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

		public void removeOrder(LogisticsItemOrder logisticsItemOrder) {
			//			if (logisticsItemOrder.getInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
			//				int craftingSlot = ((ModuleCrafter.CraftingChassieInformation) logisticsItemOrder.getInformation()).getCraftingSlot();
			//				elements.get(craftingSlot).orders.remove(logisticsItemOrder);
			//			}
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

	public static class DeliveryLine {

		final public CraftingTask group;

		public ConcurrentHashMap<LogisticsOrder, Boolean> orders = new ConcurrentHashMap<>();

		public DeliveryLine(CraftingTask group, ModuleCrafter owner, Fluid fluid, int cmSlot, int quantumSize, InventoryLink destination) {
			this.group = group;
			this.owner = owner;
			this.fluidStack = fluid == null ? null : new FluidStack(fluid, Integer.MAX_VALUE);
			this.cmSlot = cmSlot;
			this.quantumSize = quantumSize;
			this.destination = destination;
		}

		@Override
		public String toString() {
			return "DeliveryLine{" +
					"group=" + group +
					", orders=" + orderSum() +
					", orderBarrier=" + orderBarrier +
					", travelers=" + travelSum() +
					", owner=" + owner +
					", itemStack=" + (itemStack == null ? null : itemStack.getUnlocalizedName()) +
					", fluidStack=" + (fluidStack == null ? null : fluidStack.getUnlocalizedName()) +
					", cmSlot=" + cmSlot +
					", quantumSize=" + quantumSize +
					", destination=" + destination +
					", insertedCount=" + insertedCount +
					", assignedSlot=" + assignedSlot +
					'}';
		}

		int orderSum() {
			return orders.keySet().stream()
					.filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER)
					.mapToInt(LogisticsOrder::getAmount).sum();
		}

		public AtomicInteger orderBarrier = new AtomicInteger(0);
		public ConcurrentHashMap<LPTravelingItem.LPTravelingItemServer, Boolean> travelers = new ConcurrentHashMap<>();

		int travelSum() {
			if (fluidStack == null)
				return travelers.keySet().stream().mapToInt(o -> o.getItemIdentifierStack().getStackSize()).sum();
			else
				return travelers.keySet().stream().filter(o -> o.getItemIdentifierStack().getStackSize() > 0).mapToInt(o -> {
					FluidIdentifierStack fl = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(o.getItemIdentifierStack());
					return fl.getAmount();
				}).sum();
		}

		final ModuleCrafter owner;
		public ItemStack itemStack = null;
		public FluidStack fluidStack = null;
		final int cmSlot;
		final int quantumSize;
		// public InventoryLink source;
		final public InventoryLink destination;
		public AtomicInteger insertedCount = new AtomicInteger(0);
		public Integer assignedSlot;

		public void dst(LogisticsOrder order, boolean doAdd) {
			if (doAdd) orders.put(order, true);
			else orders.remove(order);
		}

		public void dst(LPTravelingItem.LPTravelingItemServer traveler, boolean doAdd) {
			if (doAdd) travelers.put(traveler, true);
			else travelers.remove(traveler);
		}

		public int availableProvided() {
			return (orderSum() + insertedCount.get()) / quantumSize;
		}

		public Integer availableSpace() {
			if (fluidStack == null) {
				if (assignedSlot == null) return null;
				return (destination.incomingItems.get(this) + insertedCount.get()) / quantumSize;
			} else
				return (destination.incomingFluids.get(this) + insertedCount.get()) / quantumSize;
		}

		public void extend(int cycles) {
			orderBarrier.addAndGet(quantumSize * cycles);
			insertedCount.addAndGet(-quantumSize * cycles);
		}

		public boolean delivered() {
			if (orderBarrier.get() > 0) return false; // when all orders are completed
			if (travelSum() > 0 || (insertedCount.get() < 0)) return false; // and all the travelers arrived
			if (fluidStack == null && !destination.assignMany) {
				ItemStack c = destination.cachedItems.getOrDefault(assignedSlot, ItemStack.EMPTY);
				return !c.equals(itemStack); // and all delivered materials are consumed
			}
			return true;
		}

	}

	public static class CraftingTask { // only Server thread write

		public final HashSet<DeliveryLine> lines = new HashSet<>();
		Pair<IResource, ModuleCrafter.CraftingChassieInformation> configurator;
		HashSet<Pair<IResource, ModuleCrafter.CraftingChassieInformation>> tools = new HashSet<>();
		// HashSet<Pair<WorldCoordinatesWrapper, EnumFacing>> inputInv, outputInv = null;
		final static HashSet<CraftingTask> registry = new HashSet<>();
		public CrafterBarrier queueManager;
		public Boolean alive = true;
		public final ModuleCrafter owner;
		ModuleCrafter activateRecipe;

		public CraftingTask(ModuleCrafter moduleCrafter, CrafterBarrier crafterBarrier, EnumFacing dir) {
			//			crafterBarrier.registry.put(this, true);
			owner = moduleCrafter;
			registry.add(this);
		}

		@Override
		public String toString() {
			return "CraftingTask{" +
					", queueManager=" + queueManager +
					", owner=" + owner +
					'}';
		}

		public void run() {
			// if (owner.getCraftedItem().toString().contains("after")) maxCycles = 0;
			if (lines.isEmpty()) return;
			DeliveryLine firstLine = lines.iterator().next();
			// make sure that there are enough orders for one quantum
			if (lines.stream().anyMatch(o -> o.availableProvided() < 1)) return;
			// ArrayList<Integer> ll=new ArrayList<>();for (DeliveryLine ii : lines) {ll.add(ii.orderSum());};ll;

			// make sure all inputs are empty
			if (!lines.stream().allMatch(o ->
					o.destination.cachedItems.values().stream().allMatch(a -> {
						ItemIdentifier itemID = ItemIdentifier.get(a);
						return a.isEmpty() || ModuleCrafter.isTool(itemID) || ModuleCrafter.isConfigurator(itemID) && configurator == null
								|| configurator != null && configurator.getValue1().matches(itemID, IResource.MatchSettings.NORMAL);
					})
			)) return;

			if (this.configurator != null && lines.stream().noneMatch(o ->
					o.destination.cachedItems.values().stream().anyMatch(a -> {
						ItemIdentifier itemID = ItemIdentifier.get(a);
						return configurator.getValue1().matches(itemID, IResource.MatchSettings.NORMAL);
					})
			)) return;

			// assign target slots
			lines.stream()
					.collect(Collectors.groupingBy(o -> o.destination))
					.forEach((k, v) -> {
						AtomicInteger x = new AtomicInteger(0);
						v.forEach(a -> {
							// a.assignedSlot = x.getAndAdd(1);
							if (a.fluidStack == null) {
								a.orders.keySet().stream()
										.filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER)
										.findFirst().ifPresent(firstOrder -> {
									ItemIdentifier t = ((LogisticsItemOrder) firstOrder).getResource().getItem();
									a.itemStack = t.makeNormalStack(t.getMaxStackSize());
									a.destination.incomingItems.put(a, 0);
								});
							} else {
								a.destination.incomingFluids.put(a, 0);
							}
						});

					});

			activateRecipe = owner;

			System.out.println("Start of batch " + owner.getCraftedItem());

			while (alive && lines.stream().allMatch(o -> o.fluidStack == null && o.assignedSlot == null))
				try {firstLine.destination.latch.await(500, TimeUnit.MILLISECONDS);} catch (InterruptedException e) {e.printStackTrace();}

			AtomicInteger sumCycles = new AtomicInteger();
			while (alive) {
				// boolean assignTimeout = false;
				//				ListenableFuture<Object> waitTick = Minecraft.getMinecraft().addScheduledTask(() -> {});

				//				if (lines.stream().anyMatch(o -> o.fluidStack == null && o.assignedSlot == null)) {
				//					try { Thread.sleep(250);} catch (InterruptedException ignored) {}
				//					continue;
				//				}
				lines.stream()
						.map(v -> new Pair<>(v, v.availableSpace()))
						.filter(v -> v.getValue2() != null)
						.mapToInt(v -> Math.min(v.getValue1().availableProvided(), v.getValue2()))
						.min().ifPresent(maxCycles -> {
					if (maxCycles > 0) {
						sumCycles.addAndGet(maxCycles);
						System.out.println("increments +" + maxCycles + "/" + sumCycles.get() + ", " + owner.getCraftedItem());
						lines.forEach(v -> v.extend(maxCycles));
					}
				});

				if (sumCycles.get() > 0 && lines.stream().allMatch(DeliveryLine::delivered)) {
					System.out.println("End of batch " + owner.getCraftedItem());
					break;
				}

				try {firstLine.destination.latch.await(500, TimeUnit.MILLISECONDS);} catch (InterruptedException e) {e.printStackTrace();}

				//				try { Thread.sleep(50);} catch (InterruptedException ignored) {}
			}
			lines.forEach(v -> {
				v.insertedCount.set(0);
				v.destination.incomingItems.remove(v);
				v.destination.incomingFluids.remove(v);
			});

			// this.tools.contains(ItemIdentifier.get(a.getValue1()))
		}
	}

	public static class InventoryLink {

		public final World posW;
		//public final IntegerCoordinates posB;
		public final BlockPos posB;
		public final Integer routerID;
		public final EnumFacing dir;
		public Map<Integer, ItemStack> cachedItems = new HashMap<>();
		public final static ConcurrentHashMap<Triplet<World, BlockPos, EnumFacing>, InventoryLink> registry1 = new ConcurrentHashMap<>();
		public final static ConcurrentHashMap<Pair<Integer, EnumFacing>, InventoryLink> registry2 = new ConcurrentHashMap<>();
		public Map<DeliveryLine, Integer> incomingItems = new ConcurrentHashMap<>();
		public Map<DeliveryLine, Integer> incomingFluids = new ConcurrentHashMap<>();
		public Map<DeliveryLine, Integer> expectedFluids = new ConcurrentHashMap<>();
		public FluidStack topFluid = null;
		public CountDownLatch latch = new CountDownLatch(1);
		// public int inputSlots = 0;
		public int age = 6;
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		public boolean assignMany = false;

		public InventoryLink(World posW, BlockPos posB, Integer routerID, EnumFacing dir) {
			this.posW = posW;
			this.posB = posB;
			this.routerID = routerID;
			this.dir = dir;
			registry1.put(new Triplet<>(posW, posB, dir), this);
			registry2.put(new Pair<>(routerID, dir), this);
		}

		public static InventoryLink getOrCreate(World posW, int routerID, BlockPos posB, EnumFacing dir) {
			//Pair<Integer, EnumFacing> key = new Pair<>(router.getRouterId(), dir);
			Pair<Integer, EnumFacing> key = new Pair<>(routerID, dir);
			if (registry2.containsKey(key))
				return registry2.get(key);
			// return new InventoryLink(router.getWorld(), router.getPos().offset(dir), router.getRouterId(), dir);
			return new InventoryLink(posW, posB, routerID, dir);
		}

		public void update() {    // LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeWrappers(null).iterator().next().getIngredients();
			if (age-- > 0) return;
			age = 5 + rng.nextInt(3);
			if (!posW.isBlockLoaded(posB)) return; // just in case, skip unloaded
			// TileEntity te = new WorldCoordinatesWrapper(this.posW, this.posB).getTileEntity();
			TileEntity te = posW.getTileEntity(posB);

			if (te == null)
				return;

			incomingItems.keySet().stream().forEach(o -> {
				ModuleCrafter module = o.group.activateRecipe;
				o.group.activateRecipe = null;
				if (module == null) return;
				if (te instanceof LogisticsCraftingTableTileEntity) {
					LogisticsCraftingTableTileEntity table = ((LogisticsCraftingTableTileEntity) te);
					ItemStack[] arrItems = new ItemStack[9];
					for (int i = 0; i < 9; i++) {
						ItemIdentifierStack v = module.getMaterials(i);
						arrItems[i] = v == null ? ItemStack.EMPTY : v.makeNormalStack();
					}
					table.handleNEIRecipePacket(arrItems);
					System.out.println(te + "@{" + posB.getX() + ":" + posB.getY() + ":" + posB.getZ() + "} Changed recipe:" + module.getCraftedItem());
				} else if (te.getClass().getName().equals("xt9.deepmoblearning.common.tiles.TileEntityExtractionChamber")) { // if (te instanceof TileEntityExtractionChamber) {
					try {
						Class<? extends TileEntity> cl = te.getClass();
						cl.getMethod("finishCraft", boolean.class).invoke(te, true);
						cl.getField("resultingItem").set(te, module.getCraftedItem().makeNormalStack());
						cl.getMethod("updateState").invoke(te);
						//					TileEntityExtractionChamber chamber = ((TileEntityExtractionChamber) te);
						//					chamber.finishCraft(true);
						//					chamber.resultingItem = module.getCraftedItem().makeNormalStack();
						//					chamber.updateState();
						System.out.println(te + "@{" + posB.getX() + ":" + posB.getY() + ":" + posB.getZ() + "} Changed recipe:" + module.getCraftedItem());
					} catch (Exception e) {
						System.out.println(te + "@{" + posB.getX() + ":" + posB.getY() + ":" + posB.getZ() + "} not changed recipe ..");
					}
				} else
					assignMany = true;
				// incomingItems.keySet().forEach(oo -> { oo.group.activateRecipe = null; });
			});

			ItemStack testItem = new ItemStack(Item.getItemById(1), 64);
			IItemHandler itemHandler = te.getCapability(LogisticsPipes.ITEM_HANDLER_CAPABILITY, this.dir);
			if (itemHandler != null) {
				Map<Integer, ItemStack> newCachedItems = new HashMap<>();
				for (int i = 0; i < itemHandler.getSlots(); i++) {
					ItemStack nStack = itemHandler.getStackInSlot(i).copy();
					// ItemStack nStack = itemHandler.extractItem(i,64,true).getCount();
					if (!nStack.isEmpty())
						newCachedItems.put(i, nStack);
					//					if (itemHandler.insertItem(i, testItem, true).getCount() < 64)
					//						inputSlots = i + 1;
					//					else
					//						inputSlots = i - 1;
				}
				cachedItems = newCachedItems;
				incomingItems.putAll(
						incomingItems.keySet().stream().filter(a -> a.itemStack != null)
								//.filter(a -> a.assignedSlot != null && a.itemStack != null)
								.collect(Collectors.toMap(a -> a, o -> {
									int n = itemHandler.getSlots();
									int l = (o.assignedSlot == null ? 0 : o.assignedSlot);
									for (int k = 0; k < n; k++) {
										int i = (l + k) % n;
										if (incomingItems.keySet().stream().anyMatch(p -> p != o && p.assignedSlot != null && i == p.assignedSlot)) continue;
										int v = o.itemStack.getCount() - itemHandler.insertItem(i, o.itemStack, true).getCount();
										if (v > 0) {
											if (o.assignedSlot == null || o.assignedSlot != i)
												System.out.println("Asssigned: " + o.cmSlot + "->" + i + ", " + Arrays.toString(incomingItems.keySet().stream().map(x -> x.assignedSlot).toArray()));
											o.assignedSlot = i;
											return v;
										}
										if (!assignMany && o.assignedSlot != null)
											return 0;
									}
									o.assignedSlot = null;
									return 0;
								})));
			}

			IFluidHandler fluidHandler = te.getCapability(LogisticsPipes.FLUID_HANDLER_CAPABILITY, this.dir);
			if (fluidHandler != null) {
				incomingFluids.putAll(
						incomingFluids.keySet().stream().filter(a -> a.fluidStack != null)
								.collect(Collectors.toMap(o -> o, o -> fluidHandler.fill(o.fluidStack, false))));
				{
					FluidStack fl = fluidHandler.drain(1, false);
					topFluid = fl == null ? null : fl.copy();
				}
				expectedFluids.putAll(
						expectedFluids.keySet().stream().filter(a -> a.fluidStack != null)
								.collect(Collectors.toMap(o -> o, o -> {
									FluidStack fl = fluidHandler.drain(o.fluidStack, false);
									return fl == null ? 0 : fl.amount;
								})));
			}

			latch.countDown();
			latch = new CountDownLatch(1);
		}
	}

	@StaticResolve
	public static class SetRecipePacket extends IntegerCoordinatesPacket {

		Map<Integer, Pair<Boolean, ItemStack>> itemsJEI;
		Map<Integer, Pair<Boolean, FluidStack>> fluidsJEI;
		boolean inHand;

		public SetRecipePacket(int id) {
			super(id);
		}

		@Override
		public ModernPacket template() {
			return new SetRecipePacket(getId());
		}

		@Override
		protected void targetNotFound(String message) {}

		@Override
		public void processPacket(EntityPlayer player) {
			LogisticsModule module;
			if (inHand) {
				if (player.openContainer instanceof DummyModuleContainer) {
					module = ((DummyModuleContainer) player.openContainer).getModule();
					if (module instanceof ModuleCrafter)
						((ModuleCrafter) module).setRecipe(itemsJEI, fluidsJEI);
				}
			} else {
				LogisticsTileGenericPipe pipe = this.getPipe(player.world, LTGPCompletionCheck.PIPE);
				if (pipe != null) {
					module = pipe.getRoutingPipe().getLogisticsModule().getSubModule(getInteger());
					if (module instanceof ModuleCrafter)
						((ModuleCrafter) module).setRecipe(itemsJEI, fluidsJEI);
				}
			}
		}

		public SetRecipePacket setRecipe(boolean _inHand, Map<Integer, Pair<Boolean, ItemStack>> _itemsJEI, Map<Integer, Pair<Boolean, FluidStack>> _fluidsJEI) {
			inHand = _inHand;
			itemsJEI = _itemsJEI;
			fluidsJEI = _fluidsJEI;
			return this;
		}

		@Override
		public void readData(LPDataInput input) {
			super.readData(input);
			inHand = input.readBoolean();
			itemsJEI = IntStream.range(0, input.readInt()).boxed()
					.collect(Collectors.toMap(a -> input.readInt(), o -> new Pair<>(input.readBoolean(), input.readItemStack())));
			fluidsJEI = IntStream.range(0, input.readInt()).boxed()
					.collect(Collectors.toMap(a -> input.readInt(), o -> new Pair<>(input.readBoolean(), input.readFluidStack())));
		}

		@Override
		public void writeData(LPDataOutput output) {
			super.writeData(output);
			output.writeBoolean(inHand);
			output.writeInt(itemsJEI.size());
			itemsJEI.forEach((key, value) -> {
				output.writeInt(key);
				output.writeBoolean(value.getValue1());
				output.writeItemStack(value.getValue2() == null ? ItemStack.EMPTY : value.getValue2());
			});
			output.writeInt(fluidsJEI.size());
			fluidsJEI.forEach((key, value) -> {
				output.writeInt(key);
				output.writeBoolean(value.getValue1());
				output.writeFluidStack(value.getValue2());
			});
		}
	}

	@StaticResolve
	public static class GlobalResetPacket extends RequestPacket {

		public GlobalResetPacket(int id) {
			super(id);
			TeleportEntityEventHandler.reg();
		}

		@Override
		public ModernPacket template() {
			return new GlobalResetPacket(getId());
		}

		@Override
		public void processPacket(EntityPlayer player) {
			CraftingTask.registry.forEach(o -> {o.alive = false;});
			CraftingTask.registry.clear();
			InventoryLink.registry1.clear();
			InventoryLink.registry2.clear();
			CrafterBarrier.globalBarrierRegistry.clear();
			ServerRouter.clearOrders();
			System.out.println("Orders cleared");
		}
	}

	@StaticResolve
	public static class CraftingPipeToggleSatelliteRedirectionPacket extends IntegerModuleCoordinatesPacket {

		public CraftingPipeToggleSatelliteRedirectionPacket(int id) {
			super(id);
		}

		@Override
		public void processPacket(EntityPlayer player) {
			ModuleCrafter module = this.getLogisticsModule(player, ModuleCrafter.class);
			if (module == null) {
				return;
			}
			module.redirectToSatellite[getInteger()] = !module.redirectToSatellite[getInteger()];
		}

		@Override
		public ModernPacket template() {
			return new CraftingPipeToggleSatelliteRedirectionPacket(getId());
		}
	}

}
