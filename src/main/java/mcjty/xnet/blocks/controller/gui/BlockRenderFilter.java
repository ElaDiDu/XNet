package mcjty.xnet.blocks.controller.gui;

import mcjty.lib.client.RenderHelper;
import mcjty.lib.gui.widgets.BlockRender;
import mcjty.lib.gui.widgets.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;


public class BlockRenderFilter extends BlockRender
{
    private Consumer<Integer> onMouseWheel = (i) -> {};
    private Consumer<Integer> onClick = (i) -> {};
    private Consumer<ItemStack> onGhostClick = (s) -> {};
    private boolean acceptsFluidIngredient = false;
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

    public void setAcceptsFluidIngredient(boolean acceptsFluidIngredient) {this.acceptsFluidIngredient = acceptsFluidIngredient;}

    public boolean acceptsFluidIngredient() {return acceptsFluidIngredient;}

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

    // draw filtered items behind held items
    @Override
    public void draw(int x, int y)
    {
        if (!visible)
        {
            return;
        }
        drawBackground(x, y);
        Object renderItem = getRenderItem();
        if (renderItem == null)
        {
            return;
        }

        float previousZLevel = mc.getRenderItem().zLevel;
        try
        {
            int renderX = x + bounds.x + getOffsetX();
            int renderY = y + bounds.y + getOffsetY();

            if (renderItem instanceof ItemStack && ((ItemStack) renderItem).getCount() > 1)
            {
                ItemStack stack = (ItemStack) renderItem;
                mc.getRenderItem().zLevel = 100.0F;

                // Render the item, durability and cooldown without McJtyLib's count.
                RenderHelper.renderItemStack(mc, mc.getRenderItem(), stack, renderX, renderY, "", false);
                renderFilterCount(stack.getCount(), renderX, renderY);
            }
            else {
                RenderHelper.renderObject(mc, mc.getRenderItem(), renderX, renderY, renderItem, false, 100.0F);
            }
        }
        finally
        {
            mc.getRenderItem().zLevel = previousZLevel;
        }
    }

    private void renderFilterCount(int count, int x, int y)
    {
        String amount;
        if (count < 100000)
        {amount = String.valueOf(count);}
        else if (count < 1000000)
        {amount = String.valueOf(count / 1000) + "k";}
        else if (count < 1000000000)
        {amount = String.valueOf(count / 1000000) + "m";}
        else
        {amount = String.valueOf(count / 1000000000) + "g";}

        int scaled = amount.length() - 2;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 32.0F);

        if (scaled >= 2)
        {
            GlStateManager.pushMatrix();
            GlStateManager.scale(.5F, .5F, .5F);
            mc.fontRenderer.drawStringWithShadow(
                    amount,
                    (x + 17) * 2 - 3 - mc.fontRenderer.getStringWidth(amount),
                    y * 2 + 22,
                    16777215
            );
            GlStateManager.popMatrix();
        }
        else if (scaled == 1)
        {
            GlStateManager.pushMatrix();
            GlStateManager.scale(.75F, .75F, .75F);
            mc.fontRenderer.drawStringWithShadow(
                    amount,
                    (x - 2) * 1.34F + 22.66F - mc.fontRenderer.getStringWidth(amount),
                    y * 1.34F + 12.66F,
                    16777215
            );
            GlStateManager.popMatrix();
        }
        else
        {
            mc.fontRenderer.drawStringWithShadow(amount, x + 16 - mc.fontRenderer.getStringWidth(amount), y + 8, 16777215);
        }

        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }
}