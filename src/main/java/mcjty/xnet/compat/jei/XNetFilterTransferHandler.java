package mcjty.xnet.compat.jei;

import mcjty.lib.container.GenericContainer;
import mcjty.xnet.apiimpl.fluids.FluidConnectorSettings;
import mcjty.xnet.apiimpl.items.ItemConnectorSettings;
import mcjty.xnet.blocks.controller.gui.GuiController;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

public class XNetFilterTransferHandler implements IRecipeTransferHandler<GenericContainer> {

    private final IRecipeTransferHandlerHelper helper;

    public XNetFilterTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<GenericContainer> getContainerClass() {
        return GenericContainer.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(GenericContainer container,
                                               IRecipeLayout recipeLayout,
                                               EntityPlayer player,
                                               boolean maxTransfer,
                                               boolean doTransfer) {
        GuiController gui = findParentControllerGui();

        if (gui == null) {
            return helper.createInternalError();
        }

        if (!gui.canSetJeiRecipeFilters()) {
            return helper.createUserErrorWithTooltip("Select an item or fluid connector first");
        }

        ItemConnectorSettings.ItemMode itemMode = gui.getJeiRecipeFilterItemMode();
        if (itemMode != null) {
            XNetJeiItemFilterCollector.Mode mode = maxTransfer
                    ? XNetJeiItemFilterCollector.Mode.ADVANCED
                    : XNetJeiItemFilterCollector.Mode.NORMAL;

            XNetJeiItemFilterCollector.Target target =
                    itemMode == ItemConnectorSettings.ItemMode.EXT
                            ? XNetJeiItemFilterCollector.Target.OUTPUTS
                            : XNetJeiItemFilterCollector.Target.INPUTS;

            XNetJeiItemFilterCollector.Result result =
                    XNetJeiItemFilterCollector.collect(recipeLayout, mode, target);

            List<ItemStack> filters = result.getFilters();

            if (filters.isEmpty()) {
                return helper.createUserErrorWithTooltip(
                        target == XNetJeiItemFilterCollector.Target.OUTPUTS
                                ? "Recipe has no item outputs"
                                : "Recipe has no item inputs"
                );
            }

            int limit = gui.getJeiRecipeFilterLimit();
            if (filters.size() > limit) {
                return helper.createUserErrorWithTooltip(
                        "Recipe needs " + filters.size() + " filters, but this connector supports " + limit
                );
            }

            if (doTransfer) {
                gui.setJeiRecipeFilters(result);
            }

            return null;
        }

        FluidConnectorSettings.FluidMode fluidMode = gui.getJeiRecipeFilterFluidMode();
        if (fluidMode != null) {
            XNetJeiFluidFilterCollector.Target target =
                    fluidMode == FluidConnectorSettings.FluidMode.EXT
                            ? XNetJeiFluidFilterCollector.Target.OUTPUTS
                            : XNetJeiFluidFilterCollector.Target.INPUTS;

            XNetJeiFluidFilterCollector.Result result =
                    XNetJeiFluidFilterCollector.collect(recipeLayout, target);

            List<ItemStack> filters = result.getFilters();

            if (filters.isEmpty()) {
                return helper.createUserErrorWithTooltip(
                        target == XNetJeiFluidFilterCollector.Target.OUTPUTS
                                ? "Recipe has no fluid outputs"
                                : "Recipe has no fluid inputs"
                );
            }

            int limit = gui.getJeiRecipeFilterLimit();
            if (filters.size() > limit) {
                return helper.createUserErrorWithTooltip(
                        "Recipe needs " + filters.size() + " filters, but this connector supports " + limit
                );
            }

            if (doTransfer) {
                gui.setJeiFluidRecipeFilters(result);
            }

            return null;
        }

        return helper.createUserErrorWithTooltip("Select an item or fluid connector first");
    }

    @Nullable
    private static GuiController findParentControllerGui() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;

        if (screen instanceof GuiController) {
            return (GuiController) screen;
        }

        if (screen instanceof RecipesGui) {
            GuiScreen parent = ((RecipesGui) screen).getParentScreen();
            if (parent instanceof GuiController) {
                return (GuiController) parent;
            }
        }

        return null;
    }
}