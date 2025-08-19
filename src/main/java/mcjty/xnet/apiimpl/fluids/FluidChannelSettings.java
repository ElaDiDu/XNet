package mcjty.xnet.apiimpl.fluids;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mcjty.lib.varia.WorldTools;
import mcjty.xnet.XNet;
import mcjty.xnet.api.channels.IChannelSettings;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.helper.DefaultChannelSettings;
import mcjty.xnet.api.keys.ConsumerId;
import mcjty.xnet.apiimpl.EnumStringTranslators;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.apiimpl.MInteger;
import mcjty.xnet.config.ConfigSetup;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class FluidChannelSettings extends DefaultChannelSettings implements IChannelSettings
{

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    public static final String TAG_MODE = "mode";

    public enum ChannelMode
    {
        PRIORITY,
        ROUNDROBIN,
        DISTRIBUTE
    }

    private ChannelMode channelMode = ChannelMode.PRIORITY;
    private int delay = 0;
    private int roundRobinOffset = 0;

    // Cache data
    private Map<SidedConsumer, FluidConnectorSettings> fluidExtractors = null;
    private List<Pair<SidedConsumer, FluidConnectorSettings>> fluidConsumers = null;
    private Map<ConsumerId, Integer> extractIndices = new HashMap<>();

    public ChannelMode getChannelMode()
    {
        return channelMode;
    }

    @Override
    public JsonObject writeToJson()
    {
        JsonObject object = new JsonObject();
        object.add("mode", new JsonPrimitive(channelMode.name()));
        return object;
    }

    @Override
    public void readFromJson(JsonObject data)
    {
        channelMode = EnumStringTranslators.getFluidChannelMode(data.get("mode").getAsString());
    }


    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        channelMode = ChannelMode.values()[tag.getByte("mode")];
        delay = tag.getInteger("delay");
        roundRobinOffset = tag.getInteger("offset");
        int[] cons = tag.getIntArray("extidx");
        for (int idx = 0 ; idx < cons.length ; idx += 2) {
            extractIndices.put(new ConsumerId(cons[idx]), cons[idx+1]);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag)
    {
        tag.setByte("mode", (byte) channelMode.ordinal());
        tag.setInteger("delay", delay);
        tag.setInteger("offset", roundRobinOffset);

        if (!extractIndices.isEmpty()) {
            int[] cons = new int[extractIndices.size() * 2];
            int idx = 0;
            for (Map.Entry<ConsumerId, Integer> entry : extractIndices.entrySet()) {
                cons[idx++] = entry.getKey().getId();
                cons[idx++] = entry.getValue();
            }
            tag.setIntArray("extidx", cons);
        }
    }

    @Override
    public void tick(int channel, IControllerContext context)
    {
        delay--;
        if (delay <= 0)
        {
            delay = 200 * 6;      // Multiply of the different speeds we have
        }
        if (delay % 10 != 0)
        {
            return;
        }
        int d = delay / 10;

        updateCache(channel, context);
        // @todo optimize
        World world = context.getControllerWorld();
        for (Map.Entry<SidedConsumer, FluidConnectorSettings> entry : fluidExtractors.entrySet())
        {
            FluidConnectorSettings settings = entry.getValue();
            if (d % settings.getSpeed() != 0)
            {
                continue;
            }

            ConsumerId consumerId = entry.getKey().getConsumerId();
            BlockPos extractorPos = context.findConsumerPosition(consumerId);
            if (extractorPos != null)
            {
                EnumFacing side = entry.getKey().getSide();
                BlockPos pos = extractorPos.offset(side);
                if (!WorldTools.chunkLoaded(world, pos))
                    continue;
                if (checkRedstone(world, settings, extractorPos))
                    continue;
                if (!context.matchColor(settings.getColorsMask()))
                    continue;

                TileEntity te = world.getTileEntity(pos);
                IFluidHandler handler = getFluidHandlerAt(te, settings.getFacing());
                if (handler == null)
                    continue;

                int idx = getStartExtractIndex(settings, consumerId, handler);
                idx = tickFluidHandler(context, settings, handler, idx);
                if (handler.getTankProperties().length > 0)
                {
                    rememberExtractIndex(consumerId, (idx + 1) % handler.getTankProperties().length);
                }

            }
        }
    }

    private static Random random = new Random();
    private int getStartExtractIndex(FluidConnectorSettings settings, ConsumerId consumerId, IFluidHandler handler)
    {
        switch (settings.getExtractMode())
        {
            case FIRST:
                return 0;
            case RND:
            {
                IFluidTankProperties[] tanks = handler.getTankProperties();
                int slotCount = tanks.length;
                if (slotCount == 0)
                    return 0;

                // Try 5 times to find a non empty slot
                for (int i = 0; i < 5; i++)
                {
                    int idx = random.nextInt(slotCount);
                    if (tanks[idx].getContents() != null)
                    {
                        return idx;
                    }
                }
                // Otherwise use a more complicated algorithm
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < slotCount; i++)
                {
                    if (tanks[i].getContents() != null)
                    {
                        slots.add(i);
                    }
                }
                if (slots.isEmpty())
                {
                    return 0;
                }
                return slots.get(random.nextInt(slots.size()));
            }
            case ORDER:
                return getExtractIndex(consumerId);
        }
        return 0;
    }


    private int getExtractIndex(ConsumerId consumer) {
        return extractIndices.getOrDefault(consumer, 0);
    }

    private void rememberExtractIndex(ConsumerId consumer, int index) {
        extractIndices.put(consumer, index);
    }

    private int tickFluidHandler(IControllerContext context, FluidConnectorSettings settings, IFluidHandler handler, int startIdx) {
        Predicate<FluidStack> extractMatcher = settings.getMatcher();

        Integer count = settings.getMinmax();
        int amount = 0;
        if (count != null) {
            amount = countFluid(handler, extractMatcher);
            if (amount < count) {
                return startIdx;
            }
        }

        if (context.checkAndConsumeRF(ConfigSetup.controllerOperationRFT.get()))
        {
            int slots = handler.getTankProperties().length;
            for (int i = startIdx; i < startIdx + slots; i++)
            {
                int idx = i % slots;
                FluidStack stack = fetchFluid(handler, true, extractMatcher, settings.getRate(), idx);
                if (stack != null)
                {
                    // Now that we have a stack we first reduce the amount of the stack if we want to keep a certain
                    // number of items
                    int toextract = stack.amount;
                    if (count != null)
                    {
                        int canextract = amount - count;
                        if (canextract <= 0)
                        {
                            continue;
                        }
                        if (canextract < toextract)
                        {
                            toextract = canextract;
                            stack = stack.copy();
                            stack.amount = toextract;
                        }
                    }

                    boolean transferred = transferStack(handler, stack, settings, idx, context);
                    if (transferred)
                        return idx;
                }
            }
        }
        return startIdx;
    }


    @Override
    public void cleanCache() {
        fluidExtractors = null;
        fluidConsumers = null;
    }

    @Nullable
    private FluidStack fetchFluid(IFluidHandler handler, boolean simulate, Predicate<FluidStack> matcher , int extractAmount, int idx)
    {
        IFluidTankProperties[] tanks = handler.getTankProperties();
        int slots = tanks.length;
        if (slots == 0)
        {
            return null;
        }
        FluidStack stack = tanks[idx].getContents();
        if (stack != null && tanks[idx].canDrain())
        {
            // No need for a copy, it's already a copy from getContents().
            stack.amount = extractAmount;
            stack = handler.drain(stack, !simulate);
            if (stack != null && matcher.test(stack))
            {
                return stack;
            }
        }
        return null;
    }

    /**
     * Alters input stack to its size after inserting.
     * @return true if the index needs to be increased
     */
    public boolean transferStack(@Nonnull IFluidHandler from, @Nonnull FluidStack stack,
                                 FluidConnectorSettings extractSettings, int extractIdx,
                                 @Nonnull IControllerContext context)
    {
        if (channelMode == ChannelMode.DISTRIBUTE)
        {
            Map<Pair<SidedConsumer, FluidConnectorSettings>, Integer> distribution = new HashMap<>();
            int overallFilled = getOverallAndDistribution(distribution, context, stack);
            int extracted = fillDistribute(distribution, overallFilled, context, stack);
            if (extracted > 0)
            {
                // Insert then extract, should still be consistent?
                FluidStack realInsert = fetchFluid(from, false,
                        extractSettings.getMatcher(),
                        extracted,
                        extractIdx);
                if (realInsert.amount != extracted)
                    throw new RuntimeException("Expected to extract " + extracted + " fluid but extracted " + realInsert.amount);
            }
            return true;
        }

        World world = context.getControllerWorld();
        if (channelMode == ChannelMode.PRIORITY)
            roundRobinOffset = 0;       // Always start at 0

        int originalCount = stack.amount;
        for (int j = 0; j < fluidConsumers.size(); j++)
        {
            roundRobinOffset = roundRobinOffset % fluidConsumers.size();
            int i = roundRobinOffset;
            roundRobinOffset++;
            Pair<SidedConsumer, FluidConnectorSettings> entry = fluidConsumers.get(i);
            FluidConnectorSettings insertSettings = entry.getValue();

            if (!insertSettings.getMatcher().test(stack))
                continue;
            BlockPos consumerPos = context.findConsumerPosition(entry.getKey().getConsumerId());
            if (consumerPos == null)
                continue;
            if (!WorldTools.chunkLoaded(world, consumerPos))
                continue;
            if (checkRedstone(world, insertSettings, consumerPos))
                continue;
            if (!context.matchColor(insertSettings.getColorsMask()))
                continue;

            EnumFacing side = entry.getKey().getSide();
            BlockPos pos = consumerPos.offset(side);
            TileEntity te = world.getTileEntity(pos);


            IFluidHandler handler = getFluidHandlerAt(te, insertSettings.getFacing());
            if (handler == null)
                continue;

            int remaining = insertToHandler(from, handler, stack, extractSettings, insertSettings, extractIdx);

            // Round robin inserts to 1 inventory only
            if (channelMode == ChannelMode.ROUNDROBIN && originalCount != remaining)
                return true;
            if (remaining <= 0)
                return true;
            stack.amount = remaining;
        }

        return originalCount != stack.amount;
    }

    /**
     * Returns how much fluid remains
     */
    public int insertToHandler(@Nonnull IFluidHandler from, @Nonnull IFluidHandler to, @Nonnull FluidStack stack,
                               FluidConnectorSettings extractSettings, FluidConnectorSettings insertSettings,
                               int extractIdx)
    {
        Integer count = insertSettings.getMinmax();
        int total = stack.amount;
        int toInsert = total;
        if (count != null)
        {
            int amount = countFluid(to, insertSettings.getMatcher());
            int canInsert = count - amount;
            if (canInsert <= 0)
                return stack.amount;

            toInsert = Math.min(toInsert, canInsert);
        }

        FluidStack stackToInsert = stack.copy();
        stackToInsert.amount = toInsert;
        int filled = to.fill(stackToInsert, false);
        // Stack inserted successfully
        if (filled > 0)
        {
            // Assume the result of both the extract and insert simulate is the same, otherwise... that mod's problem

            // Extract the exact amount for real
            FluidStack realInsert = fetchFluid(from, false,
                    extractSettings.getMatcher(),
                    filled,
                    extractIdx);
            // Insert for real
            to.fill(realInsert, true);

            return total - filled;
        }

        return stackToInsert.amount;
    }



    private int getOverallAndDistribution(Map<Pair<SidedConsumer, FluidConnectorSettings>, Integer> distribution,
                                          @Nonnull IControllerContext context, @Nonnull FluidStack stack)
    {
        World world = context.getControllerWorld();
        int filledOverall = 0;
        Map<Pair<SidedConsumer, FluidConnectorSettings>, Integer> fillPossible = new HashMap<>();
        int total = stack.amount;
        for (int i = 0; i < fluidConsumers.size(); i++)
        {
            Pair<SidedConsumer, FluidConnectorSettings> entry = fluidConsumers.get(i);
            FluidConnectorSettings settings = entry.getValue();

            if (settings.getMatcher().test(stack))
            {
                BlockPos consumerPos = context.findConsumerPosition(entry.getKey().getConsumerId());
                if (consumerPos == null)
                    continue;

                if (!WorldTools.chunkLoaded(world, consumerPos))
                    continue;
                if (checkRedstone(world, settings, consumerPos))
                    continue;
                if (!context.matchColor(settings.getColorsMask()))
                    continue;


                EnumFacing side = entry.getKey().getSide();
                BlockPos pos = consumerPos.offset(side);
                TileEntity te = world.getTileEntity(pos);
                IFluidHandler handler = getFluidHandlerAt(te, settings.getFacing());
                if (handler == null)
                    continue;

                Integer count = settings.getMinmax();
                int toInsert = Math.min(settings.getRate(), total);
                if (count != null)
                {
                    int amount = countFluid(handler, settings.getMatcher());
                    int canInsert = count - amount;
                    if (canInsert <= 0)
                        continue;
                    toInsert = Math.min(toInsert, canInsert);
                }

                FluidStack copy = stack.copy();
                copy.amount = toInsert;
                int possible = handler.fill(copy, false);
                if (possible < 0)
                    continue;
                filledOverall += possible;
                fillPossible.put(entry, possible);
            }
        }

        int amountExtracted = 0;
        for (Map.Entry<Pair<SidedConsumer, FluidConnectorSettings>, Integer> entry : fillPossible.entrySet())
        {
            Pair<SidedConsumer, FluidConnectorSettings> consumerConnector = entry.getKey();
            FluidConnectorSettings settings = consumerConnector.getValue();
            BlockPos consumerPos = context.findConsumerPosition(consumerConnector.getKey().getConsumerId());
            EnumFacing side = consumerConnector.getKey().getSide();
            BlockPos pos = consumerPos.offset(side);
            TileEntity te = world.getTileEntity(pos);
            IFluidHandler handler = getFluidHandlerAt(te, settings.getFacing());

            FluidStack copy = stack.copy();
            int toInsert = (int)Math.ceil(copy.amount * ((double)entry.getValue() / filledOverall));
            if (toInsert > total)
                toInsert = total;
            // Extracting too much (because of rounding) cap it to what's left
            if (toInsert + amountExtracted > total)
                toInsert = total - amountExtracted;

            copy.amount = toInsert;
            int filled = handler.fill(copy, false);
            distribution.put(entry.getKey(), filled);
            amountExtracted += filled;
        }

        return amountExtracted;
    }
    private int fillDistribute(Map<Pair<SidedConsumer, FluidConnectorSettings>, Integer> fillPer, int filledOverall,
                               @Nonnull IControllerContext context, @Nonnull FluidStack stack)
    {
        World world = context.getControllerWorld();
        int amountExtracted = 0;
        for (Map.Entry<Pair<SidedConsumer, FluidConnectorSettings>, Integer> entry : fillPer.entrySet())
        {
            Pair<SidedConsumer, FluidConnectorSettings> consumerConnector = entry.getKey();
            FluidConnectorSettings settings = consumerConnector.getValue();
            BlockPos consumerPos = context.findConsumerPosition(consumerConnector.getKey().getConsumerId());
            EnumFacing side = consumerConnector.getKey().getSide();
            BlockPos pos = consumerPos.offset(side);
            TileEntity te = world.getTileEntity(pos);
            IFluidHandler handler = getFluidHandlerAt(te, settings.getFacing());

            Integer count = settings.getMinmax();
            int toInsert = Math.min(settings.getRate(), stack.amount);
            if (count != null)
            {
                int amount = countFluid(handler, settings.getMatcher());
                int canInsert = count - amount;
                if (canInsert <= 0)
                    continue;
                toInsert = Math.min(toInsert, canInsert);
            }

            FluidStack copy = stack.copy();
            // For double connection inserts check with toInsert/minmax
            copy.amount = Math.min(toInsert, entry.getValue());
            int filled = handler.fill(copy, true);
            amountExtracted += filled;
        }

        return amountExtracted;
    }

    private int countFluid(IFluidHandler handler, Predicate<FluidStack> matcher) {
        int cnt = 0;
        for (IFluidTankProperties properties : handler.getTankProperties()) {
            if (properties.getContents() != null && (matcher == null || matcher.test(properties.getContents()))) {
                cnt += properties.getContents().amount;
            }
        }
        return cnt;
    }

    private void updateCache(int channel, IControllerContext context) {
        if (fluidExtractors == null) {
            fluidExtractors = new HashMap<>();
            fluidConsumers = new ArrayList<>();
            Map<SidedConsumer, IConnectorSettings> connectors = context.getConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                FluidConnectorSettings con = (FluidConnectorSettings) entry.getValue();
                if (con.getFluidMode() == FluidConnectorSettings.FluidMode.EXT) {
                    fluidExtractors.put(entry.getKey(), con);
                } else {
                    fluidConsumers.add(Pair.of(entry.getKey(), con));
                }
            }

            connectors = context.getRoutedConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                FluidConnectorSettings con = (FluidConnectorSettings) entry.getValue();
                if (con.getFluidMode() == FluidConnectorSettings.FluidMode.INS) {
                    fluidConsumers.add(Pair.of(entry.getKey(), con));
                }
            }

            fluidConsumers.sort((o1, o2) -> o2.getRight().getPriority().compareTo(o1.getRight().getPriority()));
        }
    }

    @Override
    public boolean isEnabled(String tag) {
        return true;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        return new IndicatorIcon(iconGuiElements, 22, 80, 11, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    @Override
    public void createGui(IEditorGui gui) {
        gui.nl().choices(TAG_MODE, "Fluid distribution mode", channelMode, ChannelMode.values());
    }

    @Override
    public void update(Map<String, Object> data) {
        channelMode = ChannelMode.valueOf(((String)data.get(TAG_MODE)).toUpperCase());
    }

    @Override
    public int getColors() {
        return 0;
    }

    @Nullable
    public static IFluidHandler getFluidHandlerAt(@Nullable TileEntity te, EnumFacing intSide) {
        if (te != null && te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, intSide)) {
            IFluidHandler handler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, intSide);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}
