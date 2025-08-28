package mcjty.xnet.blocks.controller.gui;

import mcjty.lib.gui.widgets.BlockRender;
import mcjty.lib.gui.widgets.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;


public class BlockRenderFilter extends BlockRender
{
    private Consumer<Integer> onMouseWheel = (i) -> {};
    private Consumer<Integer> onClick = (i) -> {};
    private Consumer<ItemStack> onGhostClick = (s) -> {};

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

    public void setOnGhostClick(Consumer<ItemStack> onGhostClick)
    {
        this.onGhostClick = onGhostClick;
    }

    public Consumer<ItemStack> getOnGhostClick()
    {
        return onGhostClick;
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
