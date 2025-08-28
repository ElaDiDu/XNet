package mcjty.xnet.compat.jei;

import mcjty.lib.gui.GenericGuiContainer;
import mcjty.xnet.blocks.controller.gui.GuiController;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

@JEIPlugin
public class JEISupport implements IModPlugin
{
    @Override
    public void register(IModRegistry registry)
    {
        registry.addGhostIngredientHandler(GenericGuiContainer.class, new GhostSlotHandler());
    }
}
