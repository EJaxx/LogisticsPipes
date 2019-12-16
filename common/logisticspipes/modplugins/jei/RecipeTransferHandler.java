package logisticspipes.modplugins.jei;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import logisticspipes.gui.GuiCraftingPipe;
import logisticspipes.gui.GuiLogisticsCraftingTable;
import logisticspipes.gui.orderer.GuiRequestTable;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.NEISetCraftingRecipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.tuples.Pair;

public class RecipeTransferHandler implements IRecipeTransferHandler {

	private IRecipeTransferHandlerHelper recipeTransferHandlerHelper;

	public RecipeTransferHandler(IRecipeTransferHandlerHelper recipeTransferHandlerHelper) {
		this.recipeTransferHandlerHelper = recipeTransferHandlerHelper;
	}

	@Nonnull
	@Override
	public Class getContainerClass() {
		return DummyContainer.class;
	}

	static <X, Y, Z> Map<X, Z> transform(Map<? extends X, ? extends Y> input, Function<Y, Z> function) {
		return input.keySet().stream()
				.collect(Collectors.toMap(Function.identity(),
						key -> function.apply(input.get(key))));
	}

	@Nullable
	@Override
	public IRecipeTransferError transferRecipe(@Nonnull Container container, @Nonnull IRecipeLayout recipeLayout, @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
		if (container instanceof DummyContainer) {
			DummyContainer dContainer = (DummyContainer) container;

			LogisticsBaseGuiScreen gui = dContainer.guiHolderForJEI;

			if (gui instanceof GuiCraftingPipe) {
				if (!doTransfer) return null;
				GuiCraftingPipe cpGui = (GuiCraftingPipe) gui;

				AtomicInteger variantCnt = new AtomicInteger(0);
				ItemStack[][] stacks = new ItemStack[9][];
				int[] stacksMap = new int[9];

				recipeLayout.getItemStacks().getGuiIngredients().forEach((key, v) -> {
					List<ItemStack> a = v.getAllIngredients();
					if (a.size() > 1 && variantCnt.get() < 9) {
						int x = variantCnt.getAndAdd(1);
						stacksMap[x] = key;
						stacks[x] = a.toArray(new ItemStack[0]);
					}
				});

				Map<Integer, Pair<Boolean, ItemStack>> items = transform(recipeLayout.getItemStacks().getGuiIngredients(), v -> new Pair<>(v.isInput(), v.getDisplayedIngredient()));
				Map<Integer, Pair<Boolean, FluidStack>> fluids = transform(recipeLayout.getFluidStacks().getGuiIngredients(), v -> new Pair<>(v.isInput(), v.getDisplayedIngredient()));

				TileEntity tile = null;
				if (cpGui.get_pipe().getSlot() == LogisticsModule.ModulePositionType.SLOT)
					tile = cpGui.get_pipe().getRouter().getPipe().getWorld().getTileEntity(cpGui.get_pipe().getRouter().getPipe().getPos());

				if (variantCnt.get() > 0)
					gui.setSubGui(new GuiRecipeImport(tile, stacks, selected -> {
						for (int i = 0; i < variantCnt.get(); i++)
							items.get(stacksMap[i]).setValue2(selected[i]);
						cpGui.transferRecipe(items, fluids);
						return null;
					}));
				else
					cpGui.transferRecipe(items, fluids);
				return null;
			}

			if (gui instanceof GuiLogisticsCraftingTable || gui instanceof GuiRequestTable) {

				TileEntity tile;
				if (gui instanceof GuiLogisticsCraftingTable) {
					tile = ((GuiLogisticsCraftingTable) gui)._crafter;
				} else {
					tile = ((GuiRequestTable) gui)._table.container;
				}

				if (tile == null) {
					return recipeTransferHandlerHelper.createInternalError();
				}

				if (!recipeLayout.getRecipeCategory().getUid().equals(VanillaRecipeCategoryUid.CRAFTING)) {
					return recipeTransferHandlerHelper.createInternalError();
				}

				ItemStack[] stack = new ItemStack[9];
				ItemStack[][] stacks = new ItemStack[9][];
				boolean hasCanidates = false;
				NEISetCraftingRecipe packet = PacketHandler.getPacket(NEISetCraftingRecipe.class);

				IGuiItemStackGroup guiItemStackGroup = recipeLayout.getItemStacks();
				Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients = guiItemStackGroup.getGuiIngredients();

				if (doTransfer) {
					for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ps : guiIngredients.entrySet()) {
						if (!ps.getValue().isInput()) continue;

						int slot = ps.getKey() - 1;

						if (slot < 9) {
							stack[slot] = ps.getValue().getDisplayedIngredient();
							List<ItemStack> list = new ArrayList<>(ps.getValue().getAllIngredients());
							if (!list.isEmpty()) {
								Iterator<ItemStack> iter = list.iterator();
								while (iter.hasNext()) {
									ItemStack wildCardCheckStack = iter.next();
									if (wildCardCheckStack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
										iter.remove();
										NonNullList<ItemStack> secondList = NonNullList.create();
										wildCardCheckStack.getItem().getSubItems(wildCardCheckStack.getItem().getCreativeTab(), secondList);
										list.addAll(secondList);
										iter = list.iterator();
									}
								}
								stacks[slot] = list.toArray(new ItemStack[0]);
								if (stacks[slot].length > 1) {
									hasCanidates = true;
								} else if (stacks[slot].length == 1) {
									stack[slot] = stacks[slot][0];
								}
							}
						}
					}

					if (hasCanidates) {
						gui.setSubGui(new GuiRecipeImport(tile, stacks));
					} else {
						MainProxy.sendPacketToServer(packet.setContent(stack).setTilePos(tile));
					}
				}
				return null;
			}
		}
		return recipeTransferHandlerHelper.createInternalError();
	}
}
