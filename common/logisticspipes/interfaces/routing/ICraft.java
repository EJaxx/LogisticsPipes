package logisticspipes.interfaces.routing;

import java.util.List;

import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;

public interface ICraft extends IProvide {

	void registerExtras(IPromise promise);

	List<ICraftingTemplate> addCrafting(IResource type);

	boolean canCraft(IResource toCraft);

	int getTodo();
}
