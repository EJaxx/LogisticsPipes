package logisticspipes.request;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

public interface IReqCraftingTemplate extends ICraftingTemplate {

	void addRequirement(IResource requirement, IAdditionalTargetInformation info);

	void addByproduct(ItemIdentifierStack byproductItem);
}
