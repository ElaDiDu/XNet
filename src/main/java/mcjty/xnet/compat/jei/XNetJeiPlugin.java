package mcjty.xnet.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.config.Constants;

@JEIPlugin
public class XNetJeiPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        IRecipeTransferHandlerHelper helper =
                registry.getJeiHelpers().recipeTransferHandlerHelper();

        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
                new XNetFilterTransferHandler(helper),
                Constants.UNIVERSAL_RECIPE_TRANSFER_UID
        );
    }
}