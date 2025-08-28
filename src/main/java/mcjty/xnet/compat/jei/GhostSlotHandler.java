package mcjty.xnet.compat.jei;

import mcjty.lib.gui.widgets.Panel;
import mcjty.xnet.blocks.controller.gui.GuiController;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GhostSlotHandler implements IGhostIngredientHandler<GuiController>
{
    @Override
    public List<IGhostIngredientHandler.Target<Object>> getTargets(GuiController gui, Object o, boolean b)
    {
        if (gui.getEditingConnector() == null || gui.getConnectorEditPanel() == null)
            return new ArrayList<>();

        return new ArrayList<>(Collections.singleton(new Target<>()
        {
            @Override
            public Rectangle getArea()
            {
                Rectangle rect = new Rectangle(gui.getConnectorEditPanel().getBounds());
                rect.x += 15 + (gui.getGuiLeft()) / 2; //(gui.getSideWindowBounds().get(0).getWidth() / 2);
                rect.y += gui.getGuiTop();
                return rect;
            }

            @Override
            public void accept(Object o)
            {
                if (o instanceof ItemStack stack && gui.getEditingConnector() != null)
                {
                    gui.sendStackAsFilter(stack);
                }
            }
        }));
    }

    @Override
    public void onComplete()
    {

    }
}
