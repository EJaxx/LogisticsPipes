package logisticspipes.request;

import java.util.List;
import java.util.Set;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraft;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

public interface ICraftingTemplate extends Comparable<ICraftingTemplate> {

	List<Pair<IResource, IAdditionalTargetInformation>> getComponents(int nCraftingSets);

	List<IExtraPromise> getByproducts(int workSets);

	int getResultStackSize();

	IPromise generatePromise(int nCraftingSetsNeeded);

	ICraft getCrafter();

	int getPriority();

	boolean canCraft(IResource requestType);

	IResource getResultItem();

	int comparePriority(int priority);

	int compareStack(ItemIdentifierStack stack);

	int compareCrafter(ICraftItems crafter);

	Set<FluidIdentifier> getCheckList();
}
