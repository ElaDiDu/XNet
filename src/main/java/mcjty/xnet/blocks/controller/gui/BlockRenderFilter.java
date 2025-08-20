package mcjty.xnet.blocks.controller.gui;

import mcjty.lib.gui.widgets.BlockRender;
import mcjty.lib.gui.widgets.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import java.util.function.Consumer;


public class BlockRenderFilter extends BlockRender
{
    private Consumer<Integer> onMouseWheel = (i) -> {};
    private Consumer<Integer> onClick = (i) -> {};

    public BlockRenderFilter(Minecraft mc, Gui gui)
    {
        super(mc, gui);
    }

    @Override
    public boolean mouseWheel(int amount, int x, int y)
    {
        if (this.isEnabledAndVisible())
        {
            this.onMouseWheel.accept(amount);
            return true;
        }
        return false;
    }

    public void setOnMouseWheel(Consumer<Integer> onMouseWheel)
    {
        this.onMouseWheel = onMouseWheel;
    }

    public void setOnClick(Consumer<Integer> onClick)
    {
        this.onClick = onClick;
    }

    @Override
    public Widget<?> mouseClick(int x, int y, int button)
    {
        if (super.mouseClick(x, y, button) == null)
            return null;

        onClick.accept(button);
        return this;
    }
}
