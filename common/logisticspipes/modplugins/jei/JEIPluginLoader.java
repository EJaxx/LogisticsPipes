package logisticspipes.modplugins.jei;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import logisticspipes.LogisticsPipes;
import logisticspipes.modules.CrafterBarrier;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.utils.FinalNBTTagCompound;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

@JEIPlugin
public class JEIPluginLoader implements IModPlugin, Runnable {

	private boolean runme = true;
	Map<String, List<Ingredients>> recipes = new HashMap<>();
	private ArrayList<String> out;
	ArrayList<ItemIdentifier> itemIds;
	ArrayList<FluidIdentifier> fluidIds;

	@Override
	public void register(IModRegistry registry) {
		IRecipeTransferRegistry recipeTransferRegistry = registry.getRecipeTransferRegistry();
		recipeTransferRegistry.addUniversalRecipeTransferHandler(new RecipeTransferHandler(registry.getJeiHelpers().recipeTransferHandlerHelper()));
		registry.addGhostIngredientHandler(LogisticsBaseGuiScreen.class, new GhostIngredientHandler());
	}

	class Ingredients implements IIngredients {

		public List<Object> inputs = new ArrayList<>(), outputs = new ArrayList<>();

		@Override
		public <T> void setInput(IIngredientType<T> iIngredientType, T t) {
			//out.add("setInput(IIngredientType<T>,T) ->\n");
			setInput(iIngredientType.getIngredientClass(), t);
		}

		@Override
		public <T> void setInputs(IIngredientType<T> iIngredientType, List<T> list) {
			//out.add("setInputs(IIngredientType<T>,List<T>) ->\n");
			setInput(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setInputLists(IIngredientType<T> iIngredientType, List<List<T>> list) {
			//out.add("setInputLists(IIngredientType<T>,List<List<T>>) ->\n");
			setInputLists(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setOutput(IIngredientType<T> iIngredientType, T t) {
			//out.add("setOutput(IIngredientType<T>,T) ->\n");
			setOutput(iIngredientType.getIngredientClass(), t);
		}

		@Override
		public <T> void setOutputs(IIngredientType<T> iIngredientType, List<T> list) {
			//out.add("setOutputs(IIngredientType<T>,List<T>) ->\n");
			setOutputs(iIngredientType.getIngredientClass(), list);
		}

		@Override
		public <T> void setOutputLists(IIngredientType<T> iIngredientType, List<List<T>> list) {
			//out.add("setOutputLists(IIngredientType<T>,List<List<T>>) ->\n");
			setOutputLists(iIngredientType.getIngredientClass(), list);
		}

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
		public <T> void setInput(Class<? extends T> aClass, T t) {
			//out.add("setInput(Class<T>,T)\n");
			if (t instanceof List)
				inputs.addAll((List<Object>) t);
			else
				inputs.add(t);
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setInputs(Class<? extends T> aClass, List<T> list) {
			//out.add("setInputs(Class<T>,List<T>)\n");
			inputs.addAll(list);
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setInputLists(Class<? extends T> aClass, List<List<T>> list) {
			//out.add("setInputLists(Class<T>,List<List<T>>)\n");
			inputs.addAll(list);
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutput(Class<? extends T> aClass, T t) {
			//out.add("setOutput(Class<T>," + t.getClass().getName() + ")\n");
			if (t instanceof List)
				outputs.addAll((List<Object>) t);
			else
				outputs.add(t);
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutputs(Class<? extends T> aClass, List<T> list) {
			//out.add("setOutputs(Class<T>,List<T>)\n");
			outputs.addAll(list);
		}

		@SuppressWarnings("deprecation")
		@Override
		public <T> void setOutputLists(Class<? extends T> aClass, List<List<T>> list) {
			//out.add("setOutputLists(Class<T>,List<List<T>>)\n");
			list.forEach(o -> outputs.add(new ArrayList<>(o)));
		}

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

		String fmtList(List _list, String delimiter) {
			if (_list == null) {
				return "[null]";
			}
			ArrayList<String> res = new ArrayList<>();
			for (Object stack : _list) {
				res.add(fmtObject(stack));
			}
			return "{" + String.join(delimiter, res) + "}";
		}

		String fmtObject(Object _stack) {
			try {
				if (_stack instanceof List && ((List) _stack).size() == 1)
					_stack = ((List) _stack).iterator().next();
				if (_stack instanceof List) {
					return fmtList((List) _stack, "/");
				}
				if (_stack instanceof ItemStack) {
					ItemIdentifierStack stack = ItemIdentifierStack.getFromStack(CrafterBarrier.stripTags((ItemStack) _stack));
					ItemIdentifier it = stack.getItem();
					if (!itemIds.contains(it)) {
						itemIds.add(it);
						out.add("ItemId " + itemIds.indexOf(it) + " " + CrafterBarrier.stackToString(it.makeNormalStack(1)) + " " + it + "\n");
					}
					FinalNBTTagCompound tag = stack.getItem().tag;
					String ch = tag != null && tag.hasKey("chance")
							? "x" + tag.getInteger("chance") / 100
							: "";
					return "i" + itemIds.indexOf(it) + 'x' + stack.getStackSize() + ch;
				}
				if (_stack instanceof FluidStack) {
					FluidIdentifier fl = FluidIdentifier.get((FluidStack) _stack);
					if (!fluidIds.contains(fl)) {
						fluidIds.add(fl);
						out.add("FluidId " + fluidIds.indexOf(fl) + " " + ((FluidStack) _stack).getLocalizedName() + "\n");
					}
					//return String.format("x%d %s", ((FluidStack) _stack).amount, ((FluidStack) _stack).getLocalizedName());
					return "f" + fluidIds.indexOf(fl) + "x" + ((FluidStack) _stack).amount;
				}
				if (_stack == null) {
					return "[null]";
				}
				return "[" + _stack.getClass().getName() + "]";
			} catch (Exception e) {
				return "[" + e.getClass().getName() + "]";
			}
		}

		public String toString() {
			return fmtList(inputs, "+") + " " + fmtList(outputs, "+");
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
			try {Thread.sleep(3000);} catch (InterruptedException ignored) {}
			if (runme) try {runTick2();} catch (Exception e) {e.printStackTrace();}
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
			out = new ArrayList<>();
			itemIds = new ArrayList<>();
			fluidIds = new ArrayList<>();
			List<IRecipeCategory> cp = new ArrayList<>(LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeCategories());
			for (IRecipeCategory<IRecipeWrapper> category : cp) {
				List<Ingredients> ls = new ArrayList<>();
				String uid = category.getUid();
				recipes.put(uid, ls);
				for (IRecipeWrapper recipe : LogisticsPipes.jeiRuntime.getRecipeRegistry().getRecipeWrappers(category)) {
					Ingredients ing = new Ingredients();
					ls.add(ing);
					recipe.getIngredients(ing);
					boolean fr = recipe.getClass().getName().contains("GTFuelRecipeWrapper");
					if (recipe.getClass().getName().contains("GTRecipeWrapper") || fr) {
						try {
							Field recipeField = recipe.getClass().getDeclaredField("recipe");
							recipeField.setAccessible(true);
							Object recipeVal = recipeField.get(recipe);
							Field Field1 = recipeVal.getClass().getDeclaredField(fr ? "minVoltage" : "EUt");
							Field1.setAccessible(true);
							Field Field2 = recipeVal.getClass().getDeclaredField("duration");
							Field2.setAccessible(true);
							out.add(String.format("GTRecipe %s %s %s %s\n", uid, ing.toString(), fr ? Field1.getLong(recipeVal) : Field1.getInt(recipeVal), Field2.getInt(recipeVal)));
						} catch (NoSuchFieldException | IllegalAccessException ignored) {
							out.add(String.format("Recipe %s %s\n", uid, ing.toString()));
						}
					} else {
						out.add(String.format("Recipe %s %s\n", uid, ing.toString()));
					}
				}
			}
			//File fl = new File("RecipeDump.txt_tmp");
			try (FileWriter outFile = new FileWriter("RecipeDump.txt")) {
				outFile.write(String.join("", out));
			}
//			File fld = new File("RecipeDump.txt");
//			if (fld.exists())
//				fld.delete();
//			fl.renameTo(fld);

		} catch (IOException ignored) {}
	}

}
