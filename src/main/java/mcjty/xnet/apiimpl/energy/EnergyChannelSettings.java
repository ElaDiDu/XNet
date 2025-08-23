package mcjty.xnet.apiimpl.energy;

import cofh.core.util.helpers.EnergyHelper;
import cofh.redstoneflux.api.IEnergyHandler;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mcjty.lib.varia.EnergyTools;
import mcjty.lib.varia.WorldTools;
import mcjty.xnet.XNet;
import mcjty.xnet.api.channels.IChannelSettings;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.helper.DefaultChannelSettings;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.apiimpl.fluids.FluidConnectorSettings;
import mcjty.xnet.blocks.cables.ConnectorBlock;
import mcjty.xnet.blocks.cables.ConnectorTileEntity;
import mcjty.xnet.config.ConfigSetup;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnergyChannelSettings extends DefaultChannelSettings implements IChannelSettings {

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    // Cache data
    private List<Pair<SidedConsumer, EnergyConnectorSettings>> energyExtractors = null;
    private List<Pair<SidedConsumer, EnergyConnectorSettings>> energyConsumers = null;

    @Override
    public JsonObject writeToJson() {
        JsonObject object = new JsonObject();
        return object;
    }

    @Override
    public void readFromJson(JsonObject data) {
    }


    @Override
    public void readFromNBT(NBTTagCompound tag) {
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
    }

    @Override
    public int getColors() {
        return 0;
    }

    @Override
    public void tick(int channel, IControllerContext context)
    {
        updateCache(channel, context);

        World world = context.getControllerWorld();

        for (Pair<SidedConsumer, EnergyConnectorSettings> entry : energyExtractors)
        {
            EnergyConnectorSettings settings = entry.getValue();
            BlockPos connectorPos = context.findConsumerPosition(entry.getKey().getConsumerId());
            if (connectorPos == null)
                continue;

            EnumFacing side = entry.getKey().getSide();
            BlockPos energyPos = connectorPos.offset(side);
            if (!WorldTools.chunkLoaded(world, energyPos))
                continue;
            if (checkRedstone(world, settings, connectorPos))
                continue;
            if (!context.matchColor(settings.getColorsMask()))
                continue;

            TileEntity te = world.getTileEntity(energyPos);
            IEnergyStorage handler = getEnergyHandlerAt(te, settings.getFacing());
            if (handler == null)
                continue;

            ConnectorTileEntity connectorTile = null;
            if (world.getTileEntity(connectorPos) instanceof ConnectorTileEntity connectorTE)
                connectorTile = connectorTE;
            tickEnergyHandler(context, settings, connectorPos, handler, connectorTile, entry.getKey().getSide());
        }
    }

    public void tickEnergyHandler(IControllerContext context, EnergyConnectorSettings settings, BlockPos extractorPos,
                                  @Nonnull IEnergyStorage handler, @Nullable ConnectorTileEntity connectorTile, EnumFacing connectorSide)
    {
        World world = context.getControllerWorld();
        Integer count = settings.getMinmax();
        if (count != null) {
            if (handler.getEnergyStored() < count)
                return;
        }

        if (context.checkAndConsumeRF(ConfigSetup.controllerOperationRFT.get()))
        {
            int rate = getRate(settings, world, extractorPos);
            int extractedEnergy = fetchEnergy(handler, connectorTile, connectorSide, true, rate);
            if (extractedEnergy == 0)
                return;

            int toExtract = extractedEnergy;
            if (count != null)
            {
                int canExtract = handler.getEnergyStored() - count;
                if (canExtract <= 0)
                    return;
                if (canExtract < toExtract)
                    toExtract = canExtract;
            }

            transferEnergy(handler, toExtract, context, connectorTile, connectorSide);
        }
    }

    public void transferEnergy(@Nonnull IEnergyStorage from, int energyExtracted, @Nonnull IControllerContext context,
                               @Nullable ConnectorTileEntity connectorTile, EnumFacing connectorSide)
    {
        if (energyConsumers.isEmpty() || energyExtracted <= 0)
            return;

        List<Triple<IEnergyStorage, BlockPos, EnergyConnectorSettings>> canAcceptEnergy = new ArrayList<>();
        World world = context.getControllerWorld();
        int amountPerOutput = Math.max(1, energyExtracted / energyConsumers.size());
        for (Pair<SidedConsumer, EnergyConnectorSettings> entry : energyConsumers)
        {
            BlockPos consumerPos = context.findConsumerPosition(entry.getKey().getConsumerId());
            EnergyConnectorSettings insertSettings = entry.getValue();
            if (!WorldTools.chunkLoaded(world, consumerPos))
                continue;
            if (checkRedstone(world, insertSettings, consumerPos))
                continue;
            if (!context.matchColor(insertSettings.getColorsMask()))
                continue;

            EnumFacing side = entry.getKey().getSide();
            BlockPos pos = consumerPos.offset(side);
            TileEntity te = world.getTileEntity(pos);

            IEnergyStorage handler = getEnergyHandlerAt(te, insertSettings.getFacing());
            if (handler == null)
                continue;

            Integer count = insertSettings.getMinmax();
            int rate = getRate(insertSettings, world, consumerPos);
            int toInsert = Math.min(rate, energyExtracted);
            if (count != null)
            {
                int amount = handler.getEnergyStored();
                int canInsert = count - amount;
                if (canInsert <= 0)
                    continue;
                toInsert = Math.min(toInsert, canInsert);
            }
            if (toInsert <= 0)
                continue;

            int received = handler.receiveEnergy(amountPerOutput, true);
            if (received <= 0)
                continue;
            // After simulating receive, extract and receive for real
            fetchEnergy(from, connectorTile, connectorSide, false, received);
            handler.receiveEnergy(received, false);
            energyExtracted -= received;
            canAcceptEnergy.add(Triple.of(handler, consumerPos, insertSettings));
        }
        // All energy used
        if (energyExtracted == 0)
            return;

        // We have left over, insert in "first takes all" manner
        for (Triple<IEnergyStorage, BlockPos, EnergyConnectorSettings> entry : canAcceptEnergy)
        {
            EnergyConnectorSettings insertSettings = entry.getRight();
            Integer count = insertSettings.getMinmax();
            int rate = getRate(insertSettings, world, entry.getMiddle());
            IEnergyStorage handler = entry.getLeft();
            int toInsert = Math.min(rate, energyExtracted);
            if (count != null)
            {
                int amount = handler.getEnergyStored();
                int canInsert = count - amount;
                if (canInsert <= 0)
                    continue;
                toInsert = Math.min(toInsert, canInsert);
            }
            if (toInsert <= 0)
                continue;

            int received = handler.receiveEnergy(energyExtracted, true);
            if (received <= 0)
                continue;
            // After simulating receive, extract and receive for real
            fetchEnergy(from, connectorTile, connectorSide, false, received);
            handler.receiveEnergy(received, false);
            energyExtracted -= received;
            if (energyExtracted <= 0)
                return;
        }
    }

    private static int fetchEnergy(IEnergyStorage handler, @Nullable ConnectorTileEntity connectorTile,
                                   EnumFacing connectorSide, boolean simulate, int max)
    {
        int energy = 0;
        if (connectorTile != null)
            energy = connectorTile.extractEnergyFrom(connectorSide, max, simulate);
        if (energy == max)
            return energy;
        energy += handler.extractEnergy(max - energy, simulate);
        return energy;
    }

    private static int getRate(EnergyConnectorSettings connector, World world, BlockPos pos)
    {
        Integer rate = connector.getRate();
        if (rate != null)
            return Math.max(0, rate);

        return ConnectorBlock.isAdvancedConnector(world, pos) ? ConfigSetup.maxRfRateAdvanced.get() : ConfigSetup.maxRfRateNormal.get();
    }

    public static int getEnergyLevel(TileEntity tileEntity, @Nonnull EnumFacing side) {
        if (tileEntity != null && tileEntity.hasCapability(CapabilityEnergy.ENERGY, side)) {
            IEnergyStorage energy = tileEntity.getCapability(CapabilityEnergy.ENERGY, side);
            return energy.getEnergyStored();
        } else {
            return 0;
        }
    }

    public static boolean isEnergyTE(@Nullable TileEntity te, @Nonnull EnumFacing side) {
        if (te == null) {
            return false;
        }
        return te.hasCapability(CapabilityEnergy.ENERGY, side);
    }

    @Nullable
    public static IEnergyStorage getEnergyHandlerAt(@Nullable TileEntity te, EnumFacing facing)
    {
        if (te != null && te.hasCapability(CapabilityEnergy.ENERGY, facing))
        {
            IEnergyStorage handler = te.getCapability(CapabilityEnergy.ENERGY, facing);
            return handler;
        }
        return null;
    }

    @Override
    public void cleanCache() {
        energyExtractors = null;
        energyConsumers = null;
    }

    private void updateCache(int channel, IControllerContext context) {
        if (energyExtractors == null) {
            energyExtractors = new ArrayList<>();
            energyConsumers = new ArrayList<>();
            Map<SidedConsumer, IConnectorSettings> connectors = context.getConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                EnergyConnectorSettings con = (EnergyConnectorSettings) entry.getValue();
                if (con.getEnergyMode() == EnergyConnectorSettings.EnergyMode.EXT) {
                    energyExtractors.add(Pair.of(entry.getKey(), con));
                } else {
                    energyConsumers.add(Pair.of(entry.getKey(), con));
                }
            }

            connectors = context.getRoutedConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                EnergyConnectorSettings con = (EnergyConnectorSettings) entry.getValue();
                if (con.getEnergyMode() == EnergyConnectorSettings.EnergyMode.INS) {
                    energyConsumers.add(Pair.of(entry.getKey(), con));
                }
            }

            energyExtractors.sort((o1, o2) -> o2.getRight().getPriority().compareTo(o1.getRight().getPriority()));
            energyConsumers.sort((o1, o2) -> o2.getRight().getPriority().compareTo(o1.getRight().getPriority()));
        }
    }

    @Override
    public boolean isEnabled(String tag) {
        return true;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        return new IndicatorIcon(iconGuiElements, 11, 80, 11, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    @Override
    public void createGui(IEditorGui gui) {
    }

    @Override
    public void update(Map<String, Object> data) {
    }
}
