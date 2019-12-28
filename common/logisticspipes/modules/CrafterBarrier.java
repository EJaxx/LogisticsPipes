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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
// import xt9.deepmoblearning.common.tiles.TileEntityExtractionChamber;

import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.IntegerModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.RequestPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestLog;
import logisticspipes.request.RequestTree;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.ServerRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
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
	public boolean alive = true;

	CrafterBarrier(World posW, BlockPos posB) {
		this.posW = posW;
		this.posB = posB;
		thread = new Thread(this, "CrafterBarrier@{" + posB.getX() + ":" + posB.getY() + ":" + posB.getZ() + "}");
		thread.start();
		System.out.println("Created " + posW.getWorldInfo().getWorldName() + ":" + posB);
	}

	public static CrafterBarrier GetOrCreateBarrier(CoreRoutedPipe pipe, EnumFacing dir) {
		return GetOrCreateBarrier(pipe.getWorld(), pipe.getPos().offset(dir));
	}

	@Override
	public String toString() {
		return "CrafterBarrier{" +
				", posB=" + posB +
				'}';
	}

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
		while (alive) {
			try { Thread.sleep(200);} catch (InterruptedException ignored) {}
			try {
				// CraftingTask.registry.forEach(CraftingTask::run);
				registry.keySet().forEach(o -> {
					o.run(this);
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
	final public static Set<ItemIdentifier> availableConfigurator = Collections.synchronizedSet(new HashSet<>());

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

	public static <X, Y, Z> Map<X, Z> transform(Map<? extends X, ? extends Y> input, Function<Y, Z> function) {
		return input.keySet().stream()
				.collect(Collectors.toMap(Function.identity(),
						key -> function.apply(input.get(key))));
	}

	public static class Recipe implements ISimpleInventoryEventHandler {

		public CrafterBarrier parent = null;
		public ItemIdentifierInventory inventory;
		public List<Element> elements = new ArrayList<>(9);

		public Recipe(ItemIdentifierInventory inventory) {
			//			this.inventory = inventory;
			//			for (int i = 0; i < 9; i++) {
			//				elements.add(new Element(null));
			//			}
		}

		@Override
		public void InventoryChanged(IInventory inventory) {
			System.err.println("!!! InventoryChanged !!!");
			//			if (!(inventory instanceof ItemIdentifierInventory))
			//				return;
			//			for (int i = 0; i < 9 && i < inventory.getSizeInventory(); i++) {
			//				ItemIdentifierStack nv = ((ItemIdentifierInventory) inventory).getIDStackInSlot(i);
			//				Element el = elements.get(i);
			//				if (el.stack == null || el.arrived == 0) {
			//					el.stack = nv;
			//				}
			//			}
		}

		public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
			//			ItemIdentifierStack res = inventory.getIDStackInSlot(9);
			//			if (res != null)
			//				probeInfo.text("Locked to: " + res.getItem().getFriendlyName());
			//
			//			Element it;
			//			for (int i = 0; i < 9 && i < inventory.getSizeInventory(); i++) {
			//				res = inventory.getIDStackInSlot(i);
			//				it = elements.get(i);
			//				if (it != null && res != null)
			//					probeInfo
			//							.horizontal().item(res.getItem().makeNormalStack(1), new ItemStyle().width(16).height(8)).text(res.getItem().getFriendlyName())
			//							.text(": a" + it.arrived
			//									+ "/t" + it.tickets.stream().mapToInt(o -> o.getItemIdentifierStack().getStackSize()).sum()
			//									+ "/p" + it.orders.stream().filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER).mapToInt(LogisticsItemOrder::getAmount).sum()
			//									+ "/c" + it.orders.stream().filter(o -> o.getType() != IOrderInfoProvider.ResourceType.PROVIDER).mapToInt(LogisticsItemOrder::getAmount).sum()
			//							);
			//			}
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
		public InventorySlotAllocation slotAllocation;

		public Integer getAssignedSlot() { return slotAllocation == null ? null : slotAllocation.assignedSlot; }

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
			if (slotAllocation == null || slotAllocation.freeSpace == null) return null;
			return (slotAllocation.freeSpace + insertedCount.get()) / quantumSize;
		}

		public void extend(int cycles) {
			orderBarrier.addAndGet(quantumSize * cycles);
			insertedCount.addAndGet(-quantumSize * cycles);
		}

		public boolean delivered() {
			if (orderBarrier.get() > 0) return false; // when all orders are completed
			if (travelSum() > 0 || (insertedCount.get() < 0)) return false; // and all the travelers arrived
			if (fluidStack == null && !destination.assignMany) {
				ItemStack c = destination.cachedItems.getOrDefault(slotAllocation.assignedSlot, ItemStack.EMPTY);
				return !c.equals(itemStack); // and all delivered materials are consumed
			}
			return true;
		}

		//		public boolean recvReady() {
		//			if (fluidStack == null)
		//				return slotAllocation.assignedSlot != null && destination.incomingItems.get(this) > 0;
		//			return destination.incomingFluids.get(slotAllocation) > 0;
		//		}

	}

	public static class CraftingTask {

		public final HashSet<DeliveryLine> lines = new HashSet<>();
		Pair<IResource, ModuleCrafter.CraftingChassieInformation> configurator;
		HashSet<Pair<IResource, ModuleCrafter.CraftingChassieInformation>> tools = new HashSet<>();
		final static HashSet<CraftingTask> registry = new HashSet<>();
		public CrafterBarrier queueManager;
		public Boolean alive = true;
		public final ModuleCrafter owner;
		ModuleCrafter activateRecipe;
		private AtomicReference<CrafterBarrier> lock = new AtomicReference<>();

		public CraftingTask(ModuleCrafter moduleCrafter) {
			owner = moduleCrafter;
			registry.add(this);
		}

		public void queueManager(CoreRoutedPipe pipe) {
			CrafterBarrier crafterBarrier = CrafterBarrier.GetOrCreateBarrier(pipe, pipe.getPointedOrientation());
			queueManager = crafterBarrier;
			queueManager.registry.put(this, true);
		}

		@Override
		public String toString() {
			return "CraftingTask{" +
					", queueManager=" + queueManager +
					", owner=" + owner +
					'}';
		}

		public void run(CrafterBarrier barrier) {
			// if (owner.getCraftedItem().toString().contains("after")) maxCycles = 0;
			if (lines.isEmpty()) return;
			DeliveryLine firstLine = lines.iterator().next();
			// make sure that there are enough orders for one quantum
			if (lines.stream().anyMatch(o -> o.availableProvided() < 1)) return;
			// ArrayList<Integer> ll=new ArrayList<>();for (DeliveryLine ii : lines) {ll.add(ii.orderSum());};ll;

			if (!lock.compareAndSet(null, barrier)) return;
			owner.lock = this;
			try {
				// make sure all inputs are empty
				if (!lines.stream().allMatch(o ->
						o.destination.cachedItems.values().stream().allMatch(a -> {
							ItemIdentifier itemID = ItemIdentifier.get(a);
							return a.isEmpty() || ModuleCrafter.isTool(itemID) || ModuleCrafter.isConfigurator(itemID) && configurator == null
									|| configurator != null && configurator.getValue1().matches(itemID, IResource.MatchSettings.NORMAL);
						})
				)) return;

				HashSet<InventorySlotAllocation> assignedSlots = new HashSet<>();
				if (configurator != null && lines.stream().noneMatch(o ->
						o.destination.cachedItems.values().stream().anyMatch(a -> {
							ItemIdentifier itemID = ItemIdentifier.get(a);
							return configurator.getValue1().matches(itemID, IResource.MatchSettings.NORMAL);
						})
				)) {
					if (availableConfigurator.contains(configurator.getValue1().getAsItem())) {
						DeliveryLine line = configurator.getValue2().deliveryLine;
						line.destination.requestOnce.add(configurator);
						System.err.println("+requestOnce " + stackToString(configurator.getValue1().getDisplayItem().makeNormalStack()));
						line.orderBarrier.incrementAndGet();
						availableConfigurator.remove(configurator.getValue1().getAsItem());
						assignedSlots.add(
								new InventorySlotAllocation(line.destination, line));
					} else
						return;
				}

				// assign slots for material
				lines.forEach(line -> assignedSlots.add(
						new InventorySlotAllocation(line.destination, line)));

				activateRecipe = owner;

				System.out.println("Start of batch " + owner.getCraftedItem());
				//owner.extractorBarrier.set(0);

				assignedSlots.forEach(o -> o.waitForAssign(this));

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
							owner.extractorBarrier.addAndGet(maxCycles); // Fluids ??
							System.out.println("+extractorBarrier " + owner + ", " + owner.hashCode() + " += " + maxCycles * owner.getCraftedItem().getStackSize());
						}
					});

					if (sumCycles.get() > 0 && lines.stream().anyMatch(o -> o.availableProvided() < 1) && lines.stream().allMatch(DeliveryLine::delivered)) {
						System.out.println("End of batch " + owner.getCraftedItem());
						break;
					}

					try {firstLine.destination.latch.await(500, TimeUnit.MILLISECONDS);} catch (InterruptedException e) {e.printStackTrace();}

					//				try { Thread.sleep(50);} catch (InterruptedException ignored) {}
				}
				assignedSlots.forEach(InventorySlotAllocation::finish);
			} finally {
				lock.set(null);
				owner.lock = null;
			}
		}
	}

	public static class InventorySlotAllocation {

		public final InventoryLink inventory;
		public final DeliveryLine line;
		public ItemStack itemStack;
		public FluidStack fluidStack;
		public Integer assignedSlot;
		public Integer freeSpace;

		public InventorySlotAllocation(InventoryLink inventory, DeliveryLine line) {
			this.inventory = inventory;
			this.line = line;
			line.slotAllocation = this;
			fluidStack = line.fluidStack;
			if (fluidStack == null) {
				inventory.incomingItems.add(this);
			} else {
				inventory.incomingFluids.add(this);
			}
		}

		public void waitForAssign(CraftingTask caller) {
			while (caller.alive && (fluidStack == null ? assignedSlot == null : freeSpace == null))
				try {inventory.latch.await(500, TimeUnit.MILLISECONDS);} catch (InterruptedException e) {e.printStackTrace();}
		}

		public void finish() {
			line.insertedCount.set(0);
			line.itemStack = null;
			if (line.fluidStack == null)
				inventory.incomingItems.remove(this);
			else
				inventory.incomingFluids.remove(this);
			line.slotAllocation = null;
		}
	}

	public static class InventoryLink {

		public final World posW;
		public final BlockPos posB;
		public final IRequest router;
		public final EnumFacing dir;
		public Map<Integer, ItemStack> cachedItems = new HashMap<>();
		public final static ConcurrentHashMap<Triplet<World, BlockPos, EnumFacing>, InventoryLink> registry1 = new ConcurrentHashMap<>();
		public final static ConcurrentHashMap<Pair<IRequest, EnumFacing>, InventoryLink> registry2 = new ConcurrentHashMap<>();
		public CopyOnWriteArraySet<InventorySlotAllocation> incomingItems = new CopyOnWriteArraySet<>();
		public CopyOnWriteArraySet<InventorySlotAllocation> incomingFluids = new CopyOnWriteArraySet<>();
		public Map<DeliveryLine, Integer> expectedFluids = new ConcurrentHashMap<>();
		public ConcurrentLinkedQueue<Pair<IResource, ModuleCrafter.CraftingChassieInformation>> requestOnce = new ConcurrentLinkedQueue<>();
		public FluidStack topFluid = null;
		public CountDownLatch latch = new CountDownLatch(1);
		// public int inputSlots = 0;
		public int age = 6;
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		public boolean assignMany = false;

		public InventoryLink(World posW, BlockPos posB, IRequest router, EnumFacing dir) {
			this.posW = posW;
			this.posB = posB;
			this.router = router;
			this.dir = dir;
			registry1.put(new Triplet<>(posW, posB, dir), this);
			registry2.put(new Pair<>(router, dir), this);
		}

		public static InventoryLink getOrCreate(IRequest router, EnumFacing dir) {
			//Pair<Integer, EnumFacing> key = new Pair<>(router.getRouterId(), dir);
			Pair<IRequest, EnumFacing> key = new Pair<>(router, dir);
			if (registry2.containsKey(key))
				return registry2.get(key);
			// return new InventoryLink(router.getWorld(), router.getPos().offset(dir), router.getRouterId(), dir);
			CoreRoutedPipe pipe = router.getRouter().getPipe();
			if (pipe.getPointedOrientation() == null)
				return null;
			return new InventoryLink(pipe.getWorld(), pipe.getPos().offset(pipe.getPointedOrientation()), router, dir);
		}

		public void update() {
			if (age-- > 0) return;
			age = 5 + rng.nextInt(3);
			if (!posW.isBlockLoaded(posB)) return; // just in case, skip unloaded
			// TileEntity te = new WorldCoordinatesWrapper(this.posW, this.posB).getTileEntity();
			TileEntity te = posW.getTileEntity(posB);

			if (te == null)
				return;

			Pair<IResource, ModuleCrafter.CraftingChassieInformation> req;
			while ((req = requestOnce.poll()) != null && router instanceof IRequestItems) {
				System.err.println("requestOnce .." + stackToString(req.getValue1().getDisplayItem().makeNormalStack()));
				RequestTree.request(req.getValue1().getAsItem().makeStack(1), (IRequestItems) router, new RequestLog() {

					@Override
					public void handleMissingItems(List<IResource> resources) {
						System.err.println("Missing items:");
						resources.forEach(o -> System.err.println(stackToString(o.getDisplayItem().makeNormalStack())));
					}

					@Override
					public void handleSucessfullRequestOf(IResource item, LinkedLogisticsOrderList parts) {
						System.err.println("Request done");
					}

					@Override
					public void handleSucessfullRequestOfList(List<IResource> resources, LinkedLogisticsOrderList parts) {}
				}, req.getValue2());
			}

			incomingItems.forEach(o -> {
				ModuleCrafter module;
				if ((module = o.line.group.activateRecipe) == null) return;
				o.line.group.activateRecipe = null;
				if (te instanceof LogisticsCraftingTableTileEntity && module.getSlot() == LogisticsModule.ModulePositionType.SLOT) {
					if (module.itemsJEI != null) {
						ItemStack[] ls = module.itemsJEI.values().stream().filter(Pair::getValue1).map(Pair::getValue2).toArray(ItemStack[]::new);
						if (ls.length == 9) ((LogisticsCraftingTableTileEntity) te).handleNEIRecipePacket(ls);
					} else {
						LogisticsCraftingTableTileEntity table = ((LogisticsCraftingTableTileEntity) te);
						ItemStack[] arrItems = new ItemStack[9];
						for (int i = 0; i < 9; i++) {
							ItemIdentifierStack v = module.getMaterials(i);
							arrItems[i] = v == null ? ItemStack.EMPTY : v.makeNormalStack();
						}
						table.handleNEIRecipePacket(arrItems);
					}
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
			});

			ItemStack testItem = new ItemStack(Item.getItemById(1), 64);
			IItemHandler itemHandler = te.getCapability(LogisticsPipes.ITEM_HANDLER_CAPABILITY, this.dir);
			if (itemHandler != null) {
				Map<Integer, ItemStack> newCachedItems = new HashMap<>();
				for (int i = 0; i < itemHandler.getSlots(); i++) {
					ItemStack nStack = itemHandler.getStackInSlot(i).copy();
					if (!nStack.isEmpty())
						newCachedItems.put(i, nStack);
				}
				incomingItems.forEach(line -> {
					if (line.itemStack == null)
						line.line.orders.keySet().stream()
								.filter(o -> o.getType() == IOrderInfoProvider.ResourceType.PROVIDER)
								.findFirst().ifPresent(firstOrder -> {
							ItemIdentifier t = ((LogisticsItemOrder) firstOrder).getResource().getItem();
							line.itemStack = t.makeNormalStack(t.getMaxStackSize());
						});
					if (line.itemStack != null) {
						int n = itemHandler.getSlots();
						int l = (line.assignedSlot == null ? 0 : line.assignedSlot);
						for (int k = 0; k < n; k++) {
							int i = (l + k) % n;
							if (incomingItems.stream().anyMatch(p -> p != line && p.assignedSlot != null && i == p.assignedSlot)) continue;
							int v = line.itemStack.getCount() - itemHandler.insertItem(i, line.itemStack, true).getCount();
							if (v > 0) {
								if (line.assignedSlot == null || line.assignedSlot != i)
									System.out.println("Assigned: " + line.line.cmSlot + "->" + i + ", " + Arrays.toString(incomingItems.stream().map(x -> x.assignedSlot).toArray()));
								line.assignedSlot = i;
								line.freeSpace = v;
								// incomingItems.put(line, v);
								return;
							}
							if (line.assignedSlot != null) { // !assignMany &&
								// incomingItems.put(line, 0);
								line.freeSpace = v;
								return;
							}
						}
						line.assignedSlot = null;
						// incomingItems.put(line, 0);
					}
				});

				cachedItems = newCachedItems;
			}

			IFluidHandler fluidHandler = te.getCapability(LogisticsPipes.FLUID_HANDLER_CAPABILITY, this.dir);
			if (fluidHandler != null) {
				incomingFluids.forEach(line -> {
					if (line.fluidStack != null)
						line.freeSpace = fluidHandler.fill(line.fluidStack, false);
				});
				{
					FluidStack fl = fluidHandler.drain(1, false);
					topFluid = fl == null ? null : fl.copy();
				}
				expectedFluids.keySet().forEach(line -> {
					if (line.fluidStack != null) {
						FluidStack fl = fluidHandler.drain(line.fluidStack, false);
						expectedFluids.put(line, fl == null ? 0 : fl.amount);
					}
				});
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
		boolean craftingGrid;
		Object recipeResult;

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
						((ModuleCrafter) module).setRecipe(itemsJEI, fluidsJEI, craftingGrid, recipeResult);
				}
			} else {
				LogisticsTileGenericPipe pipe = this.getPipe(player.world, LTGPCompletionCheck.PIPE);
				if (pipe != null) {
					module = pipe.getRoutingPipe().getLogisticsModule().getSubModule(getInteger());
					if (module instanceof ModuleCrafter)
						((ModuleCrafter) module).setRecipe(itemsJEI, fluidsJEI, craftingGrid, recipeResult);
				}
			}
		}

		public SetRecipePacket setRecipe(boolean _inHand, boolean _craftingGrid, Map<Integer, Pair<Boolean, ItemStack>> _itemsJEI, Map<Integer, Pair<Boolean, FluidStack>> _fluidsJEI, Object _recipeResult) {
			inHand = _inHand;
			craftingGrid = _craftingGrid;
			itemsJEI = _itemsJEI;
			fluidsJEI = _fluidsJEI;
			recipeResult = _recipeResult;
			return this;
		}

		@Override
		public void readData(LPDataInput input) {
			super.readData(input);
			inHand = input.readBoolean();
			recipeResult = input.readBoolean() ? input.readItemStack() : input.readFluidStack();
			craftingGrid = input.readBoolean();
			itemsJEI = IntStream.range(0, input.readInt()).boxed()
					.collect(Collectors.toMap(a -> input.readInt(), o -> new Pair<>(input.readBoolean(), input.readItemStack())));
			fluidsJEI = IntStream.range(0, input.readInt()).boxed()
					.collect(Collectors.toMap(a -> input.readInt(), o -> new Pair<>(input.readBoolean(), input.readFluidStack())));
		}

		@Override
		public void writeData(LPDataOutput output) {
			super.writeData(output);
			output.writeBoolean(inHand);
			output.writeBoolean(recipeResult instanceof ItemStack);
			if (recipeResult instanceof ItemStack)
				output.writeItemStack((ItemStack) recipeResult);
			else
				output.writeFluidStack((FluidStack) recipeResult);
			output.writeBoolean(craftingGrid);
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
			// TeleportEntityEventHandler.reg();
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
			CrafterBarrier.globalBarrierRegistry.values().forEach(o -> {o.alive = false;});
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

	public static ItemStack stripTags(ItemStack v, String... ls) {
		NBTTagCompound tag = v.getTagCompound();
		if (tag == null) return v;
		v = v.copy();
		for (String s : ls)
			tag.removeTag(s);
		if (tag.hasNoTags())
			v.setTagCompound(null);
		return v;
	}

	public static String stackToString(ItemStack stack) {
		if (stack == null) return "[null]";
		return stack.toString() + (stack.hasTagCompound() ? " " + stack.getTagCompound() : "");
	}

	public static String stackToString(FluidStack stack) {
		if (stack == null) return "[null]";
		return stack.getUnlocalizedName() + (stack.tag == null ? "" : " " + stack.tag);
	}
}
