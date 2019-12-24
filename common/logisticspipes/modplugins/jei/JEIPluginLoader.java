package logisticspipes.modplugins.jei;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import mezz.jei.api.ingredients.*;
import org.apache.commons.io.output.FileWriterWithEncoding;

import logisticspipes.LogisticsPipes;
import logisticspipes.utils.FinalNBTTagCompound;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.item.ItemIdentifierStack;

@JEIPlugin
public class JEIPluginLoader implements IModPlugin, Runnable {

	private boolean runme = true;
	Map<String, List<Ingredients>> recipes = new HashMap<>();

	@Override
	public void register(IModRegistry registry) {
		IRecipeTransferRegistry recipeTransferRegistry = registry.getRecipeTransferRegistry();
		recipeTransferRegistry.addUniversalRecipeTransferHandler(new RecipeTransferHandler(registry.getJeiHelpers().recipeTransferHandlerHelper()));
		registry.addGhostIngredientHandler(LogisticsBaseGuiScreen.class, new GhostIngredientHandler());
	}

	class Ingredients implements IIngredients {

		public List<ItemStack> inputItems = new ArrayList<>();
		public List<ItemStack> outputItems = new ArrayList<>();
		public List<List<ItemStack>> inputListItems = new ArrayList<>();
		public List<List<FluidStack>> inputListFluids = new ArrayList<>();
		public List<FluidStack> inputFluids = new ArrayList<>();
		public List<FluidStack> outputFluids = new ArrayList<>();

		@Override
		public <T> void setInput(IIngredientType<T> iIngredientType, T t) {}

		@Override
		public <T> void setInputs(IIngredientType<T> iIngredientType, List<T> list) {
			setInput(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setInputLists(IIngredientType<T> iIngredientType, List<List<T>> list) {
			setInputLists(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setOutput(IIngredientType<T> iIngredientType, T t) { }

		@Override
		public <T> void setOutputs(IIngredientType<T> iIngredientType, List<T> list) {
			setOutputs(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setOutputLists(IIngredientType<T> iIngredientType, List<List<T>> list) { }

		@Override
		public <T> List<List<T>> getInputs(IIngredientType<T> iIngredientType) {
			return null;
		}

		@Override
		public <T> List<List<T>> getOutputs(IIngredientType<T> iIngredientType) {
			return null;
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setInput(Class<? extends T> aClass, T t) { }

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setInputs(Class<? extends T> aClass, List<T> list) {
			if (aClass.equals(ItemStack.class)) list.forEach(o -> inputItems.add((ItemStack) o));
			if (aClass.equals(FluidStack.class)) list.forEach(o -> inputFluids.add((FluidStack) o));
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setInputLists(Class<? extends T> aClass, List<List<T>> list) {
			if (aClass.equals(ItemStack.class)) list.forEach(o -> {
				ArrayList<ItemStack> ls = new ArrayList<>();
				inputListItems.add(ls);
				o.forEach(a -> ls.add((ItemStack) a));
			});
			if (aClass.equals(FluidStack.class)) list.forEach(o -> {
				ArrayList<FluidStack> ls = new ArrayList<>();
				inputListFluids.add(ls);
				o.forEach(a -> ls.add((FluidStack) a));
			});
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutput(Class<? extends T> aClass, T t) { }

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutputs(Class<? extends T> aClass, List<T> list) {
			if (aClass.equals(ItemStack.class)) list.forEach(o -> outputItems.add((ItemStack) o));
			if (aClass.equals(FluidStack.class)) list.forEach(o -> outputFluids.add((FluidStack) o));
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutputLists(Class<? extends T> aClass, List<List<T>> list) { }

		@SuppressWarnings("deprecation")
		@Override
		public <T> List<List<T>> getInputs(Class<? extends T> aClass) {
			return null;
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> List<List<T>> getOutputs(Class<? extends T> aClass) {
			return null;
		}

		void fmtItem(Object _stack, AtomicReference<String> s) {
			try {
				if (_stack instanceof ItemStack) {
					ItemIdentifierStack stack = ItemIdentifierStack.getFromStack((ItemStack) _stack);
					FinalNBTTagCompound tag = stack.getItem().tag;
					String ch = tag != null && tag.hasKey("chance")
							? String.format("%d%% x%d ", tag.getInteger("chance") / 100, stack.getStackSize())
							: "x" + stack.getStackSize() + " ";
					Stream.of(stack.getItem().toString().split("[:,]")).skip(1).findFirst().ifPresent(o ->
							s.set((s.get().isEmpty() ? "" : s + " + ") + ch + o));
					return;
				}
				if (_stack == null) {
					s.accumulateAndGet("[null]", String::concat);
					return;
				}
				s.accumulateAndGet(_stack.getClass().getName(), String::concat);
			} catch (Exception e) {
				s.accumulateAndGet("[" + e.getClass().getName() + "]", String::concat);
			}
		}

		void fmtFluid(Object _stack, AtomicReference<String> s) {
			if (_stack instanceof FluidStack) {
				s.set((s.get().isEmpty() ? "" : s + " + ") +
						String.format("x%d %s", ((FluidStack) _stack).amount, ((FluidStack) _stack).getLocalizedName()));
				return;
			}
			s.accumulateAndGet(_stack.getClass().getName(), String::concat);
		}

		public String toStringOne() {
			//if (inputItems.isEmpty() && inputFluids.isEmpty() || !inputListItems.isEmpty()) return null;
			AtomicReference<String> s1 = new AtomicReference<>("");
			inputItems.forEach(i -> fmtItem(i, s1));
			inputListItems.forEach(i -> fmtItem(i.isEmpty() ? ItemStack.EMPTY : i.iterator().next(), s1));
			inputFluids.forEach(i -> fmtFluid(i, s1));
			inputListFluids.forEach(i -> fmtFluid(i.isEmpty() ? null : i.iterator().next(), s1));
			AtomicReference<String> s2 = new AtomicReference<>("");
			outputItems.forEach(i -> fmtItem(i, s2));
			outputFluids.forEach(i -> fmtFluid(i, s2));
			return s1.get() + " -> " + s2.get();
		}

		public List<String> toStringList() {
			List<String> res = new ArrayList<>();
			//			AtomicInteger nn = new AtomicInteger();
			//			inputListItems.forEach(o -> {
			//				AtomicReference<String> s1 = new AtomicReference<>("");
			//				o.forEach(i -> fmtItem(i, s1));
			//				if (!inputListFluids.isEmpty())
			//					inputListFluids.get(nn.getAndIncrement()).forEach(i -> fmtFluid(i, s1));
			//				else
			//					inputFluids.forEach(i -> fmtFluid(i, s1));
			//				if (!inputListFluids.isEmpty() && inputListItems.size() != inputListFluids.size())
			//					System.err.println("inputListFluids in toStringList not empty");
			//				AtomicReference<String> s2 = new AtomicReference<>("");
			//				outputItems.forEach(i -> fmtItem(i, s2));
			//				outputFluids.forEach(i -> fmtFluid(i, s2));
			//				res.add(s1.get() + " -> " + s2.get());
			//			});
			String v = toStringOne();
			if (v != null)
				res.add(v);
			return res;
		}

	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		LogisticsPipes.jeiRuntime = jeiRuntime;
 		(new Thread(this, "JEIPlugin")).start();
	}

	@Override
	public void run() {
		while (true) {
			try {Thread.sleep(1000);} catch (InterruptedException ignored) {}
			// if (runme) try {runTick();} catch (Exception e) {e.printStackTrace();}
			runme = false;
		}
	}

	private void runTick1() {
		LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeCategories().forEach(a -> {
			LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeCatalysts(a).forEach(o->{
				// System.err.println("Cat: "+a+", "+o);
			});
			LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeWrappers(a).forEach(o->{
				Field f = null;
				try {
					f = o.getClass().getDeclaredField("recipe");
					f.setAccessible(true);
					System.err.println("Recipe: "+f.get(o));
				} catch (NoSuchFieldException | IllegalAccessException ignored) {}
				// System.err.println("Wr: "+a+", "+o);
			});
		});
	}

	private void runTick2() {
		recipes.clear();
		try {
			FileWriterWithEncoding out = new FileWriterWithEncoding("RecipeDump.txt", "utf8");
			List<IRecipeCategory> cp = new ArrayList<>(LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeCategories());
			for (IRecipeCategory<IRecipeWrapper> category : cp) {
				// System.out.println(category.getUid());
				//LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeWrapper()
				List<Ingredients> ls = new ArrayList<>();
				recipes.put(category.getUid(), ls);
				if (category.getUid().startsWith("gregtech:") && !category.getUid().equals("EIOTank") && !category.getUid().startsWith("minecraft:") && !category.getUid().contains("arc_") && !category.getUid().contains("forge_hammer") && !category.getUid().contains("separator") && !category.getUid()
						.contains("replicator"))
					for (IRecipeWrapper recipe : LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeWrappers(category)) {
						Ingredients ing = new Ingredients();
						ls.add(ing);
						recipe.getIngredients(ing);
						ing.toStringList().forEach(s -> {
							try {
								out.write(category.getUid() + "=" + s + "\n");
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
					}
			}
			out.close();
		} catch (IOException ignored) {}
	}

}
