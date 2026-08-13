package mcjty.xnet.blocks.generic;

import mcjty.lib.base.ModBase;
import mcjty.lib.gui.GenericGuiContainer;
import mcjty.lib.tileentity.GenericTileEntity;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public abstract class GenericXNetGuiContainer<T extends GenericTileEntity> extends GenericGuiContainer<T> {

    public GenericXNetGuiContainer(ModBase mod,
                                   SimpleNetworkWrapper network,
                                   T tileEntity,
                                   Container container,
                                   int manual,
                                   String manualNode) {
        super(mod, network, tileEntity, container, manual, manualNode);
    }

    @Override
    public List<Rectangle> getSideWindowBounds() {
        List<Rectangle> areas = new ArrayList<>(super.getSideWindowBounds());
        addMainWindowExtraAreas(areas);
        return areas;
    }
    public T getTileEntity() { return tileEntity; }

    private void addMainWindowExtraAreas(List<Rectangle> areas) {
        if (window == null || window.getToplevel() == null) {
            return;
        }

        Rectangle bounds = window.getToplevel().getBounds();

        int normalLeft = guiLeft;
        int normalTop = guiTop;
        int normalRight = guiLeft + xSize;
        int normalBottom = guiTop + ySize;

        int boundsLeft = bounds.x;
        int boundsTop = bounds.y;
        int boundsRight = bounds.x + bounds.width;
        int boundsBottom = bounds.y + bounds.height;

        if (boundsLeft < normalLeft) {
            areas.add(new Rectangle(
                    boundsLeft,
                    boundsTop,
                    normalLeft - boundsLeft,
                    bounds.height
            ));
        }

        if (boundsRight > normalRight) {
            areas.add(new Rectangle(
                    normalRight,
                    boundsTop,
                    boundsRight - normalRight,
                    bounds.height
            ));
        }

        if (boundsTop < normalTop) {
            areas.add(new Rectangle(
                    normalLeft,
                    boundsTop,
                    xSize,
                    normalTop - boundsTop
            ));
        }

        if (boundsBottom > normalBottom) {
            areas.add(new Rectangle(
                    normalLeft,
                    normalBottom,
                    xSize,
                    boundsBottom - normalBottom
            ));
        }
    }
}