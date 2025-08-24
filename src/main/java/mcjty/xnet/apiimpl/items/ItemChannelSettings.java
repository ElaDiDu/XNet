package mcjty.xnet.apiimpl.items;

import com.google.common.collect.ImmutableList;
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
import mcjty.xnet.apiimpl.EnumStringTranslators;
import mcjty.xnet.api.keys.ConsumerId;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.compat.RFToolsSupport;
import mcjty.xnet.config.ConfigSetup;
import mcjty.xnet.setup.ModSetup;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class ItemChannelSettings extends DefaultChannelSettings implements IChannelSettings {

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    public static final String TAG_MODE = "mode";

    // Cache data
    private Map<SidedConsumer, ItemConnectorSettings> itemExtractors = null;
    private List<Pair<SidedConsumer, ItemConnectorSettings>> itemConsumers = null;


    public enum ChannelMode {
        PRIORITY,
        ROUNDROBIN
    }

    private ChannelMode channelMode = ChannelMode.PRIORITY;
    private int delay = 0;
    private int roundRobinOffset = 0;
    private Map<ConsumerId, Integer> currentIndices = new HashMap<>();

    public ChannelMode getChannelMode() {
        return channelMode;
    }

    @Override
    public int getColors() {
        return 0;
    }

    @Override
    public JsonObject writeToJson() {
        JsonObject object = new JsonObject();
        object.add("mode", new JsonPrimitive(channelMode.name()));
        return object;
    }

    @Override
    public void readFromJson(JsonObject data) {
        channelMode = EnumStringTranslators.getItemChannelMode(data.get("mode").getAsString());
    }


    @Override
    public void readFromNBT(NBTTagCompound tag) {
        channelMode = ChannelMode.values()[tag.getByte("mode")];
        delay = tag.getInteger("delay");
        roundRobinOffset = tag.getInteger("offset");
        int[] cons = tag.getIntArray("extidx");
        for (int idx = 0 ; idx < cons.length ; idx += 2) {
            currentIndices.put(new ConsumerId(cons[idx]), cons[idx+1]);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setByte("mode", (byte) channelMode.ordinal());
        tag.setInteger("delay", delay);
        tag.setInteger("offset", roundRobinOffset);

        if (!currentIndices.isEmpty()) {
            int[] cons = new int[currentIndices.size() * 2];
            int idx = 0;
            for (Map.Entry<ConsumerId, Integer> entry : currentIndices.entrySet()) {
                cons[idx++] = entry.getKey().getId();
                cons[idx++] = entry.getValue();
            }
            tag.setIntArray("extidx", cons);
        }
    }

    private int getExtractIndex(ConsumerId consumer) {
        return currentIndices.getOrDefault(consumer, 0);
    }

    private void rememberExtractIndex(ConsumerId consumer, int index) {
        currentIndices.put(consumer, index);
    }

    private static Random random = new Random();

    @Override
    public void tick(int channel, IControllerContext context) {
        delay--;
        if (delay <= 0) {
            delay = 200*6;      // Multiply of the different speeds we have
        }
        if (delay % 5 != 0) {
            return;
        }
        int d = delay/5;

        updateCache(channel, context);
        World world = context.getControllerWorld();
        for (Map.Entry<SidedConsumer, ItemConnectorSettings> entry : itemExtractors.entrySet()) {
            ItemConnectorSettings settings = entry.getValue();
            if (d % settings.getSpeed() != 0) {
                continue;
            }

            ConsumerId consumerId = entry.getKey().getConsumerId();
            BlockPos extractorPos = context.findConsumerPosition(consumerId);
            if (extractorPos != null) {
                EnumFacing side = entry.getKey().getSide();
                BlockPos pos = extractorPos.offset(side);
                if (!WorldTools.chunkLoaded(world, pos)) {
                    continue;
                }

                if (checkRedstone(world, settings, extractorPos)) {
                    continue;
                }
                if (!context.matchColor(settings.getColorsMask())) {
                    continue;
                }

                TileEntity te = world.getTileEntity(pos);

                if (ModSetup.rftools && RFToolsSupport.isStorageScanner(te)) {
                    RFToolsSupport.tickStorageScanner(context, settings, te, this);
                } else {
                    IItemHandler handler = getItemHandlerAt(te, settings.getFacing());
                    if (handler != null) {
                        int idx = getStartExtractIndex(settings, consumerId, handler);
                        idx = tickItemHandler(context, settings, handler, idx);
                        if (handler.getSlots() > 0) {
                            rememberExtractIndex(consumerId, (idx + 1) % handler.getSlots());
                        }
                    }
                }
            }
        }
    }

    private int getStartExtractIndex(ItemConnectorSettings settings, ConsumerId consumerId, IItemHandler handler) {
        switch (settings.getExtractMode()) {
            case FIRST:
                return 0;
            case RND: {
                if (handler.getSlots() <= 0) {
                    return 0;
                }
                // Try 5 times to find a non empty slot
                for (int i = 0 ; i < 5 ; i++) {
                    int idx = random.nextInt(handler.getSlots());
                    if (!handler.getStackInSlot(idx).isEmpty()) {
                        return idx;
                    }
                }
                // Otherwise use a more complicated algorithm
                List<Integer> slots = new ArrayList<>();
                for (int i = 0 ; i < handler.getSlots() ; i++) {
                    if (!handler.getStackInSlot(i).isEmpty()) {
                        slots.add(i);
                    }
                }
                if (slots.isEmpty()) {
                    return 0;
                }
                return slots.get(random.nextInt(slots.size()));
            }
            case ORDER:
                return getExtractIndex(consumerId);
            case SLOT:
                return settings.getSlot() == null ? -1 : settings.getSlot();
        }
        return 0;
    }


    private int tickItemHandler(IControllerContext context, ItemConnectorSettings settings, IItemHandler handler, int startIdx)
    {
        Predicate<ItemStack> extractMatcher = settings.getMatcher();

        Integer count = settings.getCount();
        int amount = 0;
        if (count != null)
        {
            amount = countItems(handler, extractMatcher);
            if (amount < count)
            {
                return startIdx;
            }
        }

        if (context.checkAndConsumeRF(ConfigSetup.controllerOperationRFT.get()))
        {

            int slots = handler.getSlots();
            // Exact slot
            if (settings.getExtractMode() == ItemConnectorSettings.ExtractMode.SLOT && startIdx >= 0)
            {
                if (slots <= startIdx)
                    return 0;
                ItemStack stack = getSimulateExtractStack(settings, handler, startIdx, extractMatcher, amount);
                boolean transferred = transferStack(handler, stack, settings, startIdx, context);
                return startIdx;
            }
            // If negative exact slot, do regular all slot extract
            else if (startIdx < 0)
                startIdx = 0;
            for (int i = startIdx; i < startIdx + slots; i++)
            {
                int idx = i % slots;
                ItemStack stack = getSimulateExtractStack(settings, handler, idx, extractMatcher, amount);
                if (stack.isEmpty())
                    continue;

                boolean transferred = transferStack(handler, stack, settings, idx, context);
                if (transferred)
                    return idx;

            }
        }

        return startIdx;
    }

    private ItemStack getSimulateExtractStack(ItemConnectorSettings settings, IItemHandler handler, int idx, Predicate<ItemStack> extractMatcher, int amount)
    {
        ItemStack stack = fetchItem(handler, true,
                extractMatcher,
                settings.getStackMode(),
                settings.getExtractAmount(),
                Integer.MAX_VALUE,
                idx);
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        // Exact mode doesn't allow stacks smaller than count
        if (settings.getStackMode() == ItemConnectorSettings.StackMode.COUNTE && stack.getCount() < settings.getExtractAmount())
            return ItemStack.EMPTY;

        // Now that we have a stack we first reduce the amount of the stack if we want to keep a certain
        // number of items
        int toextract = stack.getCount();
        Integer count = settings.getCount();
        if (count != null)
        {
            int canextract = amount - count;
            if (canextract <= 0)
            {
                return ItemStack.EMPTY;
            }
            if (canextract < toextract)
            {
                toextract = canextract;
                stack = stack.copy();
                stack.setCount(toextract);
            }
        }
        return stack;
    }

    /**
     * Alters input stack to its size after inserting.
     * @return true if something was transferred
     */
    public boolean transferStack(@Nonnull IItemHandler from, @Nonnull ItemStack stack,
                              ItemConnectorSettings extractSettings, int extractIdx,
                              @Nonnull IControllerContext context)
    {
        World world = context.getControllerWorld();
        if (channelMode == ChannelMode.PRIORITY)
            roundRobinOffset = 0;       // Always start at 0

        int originalCount = stack.getCount();
        for (int j = 0; j < itemConsumers.size(); j++)
        {
            roundRobinOffset = roundRobinOffset % itemConsumers.size();
            int i = roundRobinOffset;
            roundRobinOffset++;
            Pair<SidedConsumer, ItemConnectorSettings> entry = itemConsumers.get(i);
            ItemConnectorSettings insertSettings = entry.getValue();

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

            int remaining;
            if (ModSetup.rftools && RFToolsSupport.isStorageScanner(te))
            {
                remaining = insertToStorageScanner(from, te, stack, extractSettings, insertSettings, extractIdx);
            }
            else
            {
                IItemHandler handler = getItemHandlerAt(te, insertSettings.getFacing());
                if (handler == null)
                    continue;

                remaining = insertToHandler(from, handler, stack, extractSettings, insertSettings, extractIdx);
            }
            // Round robin inserts to 1 inventory only
            if (channelMode == ChannelMode.ROUNDROBIN && originalCount != remaining)
                return true;
            if (remaining <= 0)
                return true;
            stack.setCount(remaining);
        }

        return originalCount != stack.getCount();
    }

    /**
     * Returns how many items remaining
     */
    public int insertToHandler(@Nonnull IItemHandler from, @Nonnull IItemHandler to, @Nonnull ItemStack stack,
                               ItemConnectorSettings extractSettings, ItemConnectorSettings insertSettings,
                               int extractIdx)
    {
        Integer count = insertSettings.getCount();
        int slots = to.getSlots();
        int total = stack.getCount();
        int toInsert = total;
        if (count != null)
        {
            int amount = countItems(to, insertSettings.getMatcher());
            int canInsert = count - amount;
            if (canInsert <= 0)
                return total;

            toInsert = Math.min(toInsert, canInsert);
        }
        List<Integer> prioritySlots = ImmutableList.of();
        if (!insertSettings.isBlacklist() && insertSettings.isCountMode())
        {
            ItemFilterCache.ItemsNeededLocations neededAndLocations = insertSettings.itemsNeededToSatisfyFilter(to, stack);
            if (neededAndLocations != null)
            {
                prioritySlots = neededAndLocations.existingStackLocations;
                toInsert = Math.min(toInsert, neededAndLocations.needed);
                if (count != null)
                    count = Math.min(count, neededAndLocations.needed);
            }
        }

        if (toInsert <= 0)
            return total;
        ItemStack stackToInsert = stack.copy();
        stackToInsert.setCount(toInsert);

        int slotExact = insertSettings.getSlot() == null ? -1 : insertSettings.getSlot();
        // Has exact slot chosen
        if (slotExact >= 0)
        {
            if (slots <= slotExact)
                return total;

            return total - toInsert + insertToSlot(from, to, stackToInsert, extractSettings, extractIdx, slotExact);
        }

        // Priority slots to stack already existing items with the limited item filter, as opposed to fragmenting them in multiple slots.
        for (int slot : prioritySlots)
        {
            int remaining = insertToSlot(from, to, stackToInsert, extractSettings, extractIdx, slot);
            // We inserted as much as we wanted/could, finish
            if (remaining == 0)
                return total - toInsert + remaining;
            // We have leftover, keep going to next slots and try to insert it
            stackToInsert.setCount(remaining);
        }
        boolean hasPrioritySlots = !prioritySlots.isEmpty();
        for (int slot = 0; slot < slots; slot++)
        {
            // Small optimization for count filter, 1 boolean check first should mean almost no overhead for non count filter
            if (hasPrioritySlots && prioritySlots.contains(slot))
                continue;
            int remaining = insertToSlot(from, to, stackToInsert, extractSettings, extractIdx, slot);
            // We inserted as much as we wanted/could, finish
            if (remaining == 0)
                return total - toInsert + remaining;
            // We have leftover, keep going to next slots and try to insert it
            stackToInsert.setCount(remaining);
        }

        return total - toInsert + stackToInsert.getCount();
    }

    /**
     * Returns remaining item count that wasn't inserted, if the count is the same as stackToInsert, no insertion happened.
     * @return remaining item count that wasn't inserted
     */
    private int insertToSlot(@Nonnull IItemHandler from, @Nonnull IItemHandler to, @Nonnull ItemStack stackToInsert,
                             ItemConnectorSettings extractSettings, int extractIdx, int slot)
    {
        int toInsert = stackToInsert.getCount();
        ItemStack remaining = to.insertItem(slot, stackToInsert, true);

        // Stack inserted successfully
        if (remaining.getCount() < stackToInsert.getCount())
        {
            // Assume the result of both the extract and insert simulate is the same, otherwise... that mod's problem

            // Extract the exact amount for real
            int itemsInserted = toInsert - remaining.getCount();
            ItemStack realInsert = fetchItem(from, false,
                    extractSettings.getMatcher(),
                    extractSettings.getStackMode(),
                    extractSettings.getExtractAmount(),
                    itemsInserted,
                    extractIdx);
            // Insert for real
            to.insertItem(slot, realInsert, false);
        }
        return remaining.getCount();
    }

    /**
     * Returns how many items remaining
     */
    public int insertToStorageScanner(@Nonnull IItemHandler from, @Nonnull TileEntity to, @Nonnull ItemStack stack,
                                      ItemConnectorSettings extractSettings, ItemConnectorSettings insertSettings,
                                      int extractIdx)
    {
        Integer count = insertSettings.getCount();
        int total = stack.getCount();
        int toInsert = total;
        if (count != null)
        {
            int amount = RFToolsSupport.countItems(to, insertSettings.getMatcher(), count);
            int canInsert = count - amount;
            if (canInsert <= 0)
                return stack.getCount();

            toInsert = Math.min(toInsert, canInsert);
        }

        ItemStack stackToInsert = stack.copy();
        stackToInsert.setCount(toInsert);

        ItemStack remaining = RFToolsSupport.insertItem(to, stack, true);
        // Stack didn't insert successfully
        // Quick identity check for handlers that return the same item, equal function can be expensive.
        if (remaining == stackToInsert && ItemStack.areItemStacksEqual(remaining, stackToInsert))
            return remaining.getCount();

        // Assume the result of both the extract and insert simulate is the same, otherwise... that mod's problem
        // Extract the exact amount for real
        int itemsInserted = toInsert - remaining.getCount();
        ItemStack realInsert = fetchItem(from, false,
                extractSettings.getMatcher(),
                extractSettings.getStackMode(),
                extractSettings.getExtractAmount(),
                itemsInserted,
                extractIdx);
        // Insert for real
        RFToolsSupport.insertItem(to, realInsert, false);

        return total - itemsInserted;
    }

    // Returns what could not be inserted
    public int insertStackSimulate(@Nonnull List<Pair<SidedConsumer, ItemConnectorSettings>> inserted, @Nonnull IControllerContext context, @Nonnull ItemStack stack) {
        World world = context.getControllerWorld();
        if (channelMode == ChannelMode.PRIORITY) {
            roundRobinOffset = 0;       // Always start at 0
        }
        int total = stack.getCount();
        for (int j = 0 ; j < itemConsumers.size() ; j++) {
            int i = (j + roundRobinOffset) % itemConsumers.size();
            Pair<SidedConsumer, ItemConnectorSettings> entry = itemConsumers.get(i);
            ItemConnectorSettings settings = entry.getValue();

            if (settings.getMatcher().test(stack)) {
                BlockPos consumerPos = context.findConsumerPosition(entry.getKey().getConsumerId());
                if (consumerPos != null) {
                    if (!WorldTools.chunkLoaded(world, consumerPos)) {
                        continue;
                    }

                    if (checkRedstone(world, settings, consumerPos)) {
                        continue;
                    }
                    if (!context.matchColor(settings.getColorsMask())) {
                        continue;
                    }

                    EnumFacing side = entry.getKey().getSide();
                    BlockPos pos = consumerPos.offset(side);
                    TileEntity te = world.getTileEntity(pos);
                    int actuallyinserted = 0;
                    int toinsert = total;
                    ItemStack remaining;
                    Integer count = settings.getCount();

                    if (ModSetup.rftools && RFToolsSupport.isStorageScanner(te)) {
                        if (count != null) {
                            int amount = RFToolsSupport.countItems(te, settings.getMatcher(), count);
                            int caninsert = count - amount;
                            if (caninsert <= 0) {
                                continue;
                            }
                            toinsert = Math.min(toinsert, caninsert);
                        }

                        stack = stack.copy();
                        if (toinsert < 0)
                            stack.setCount(0);
                        else
                            stack.setCount(toinsert);
                        remaining = RFToolsSupport.insertItem(te, stack, true);
                    } else {
                        IItemHandler handler = getItemHandlerAt(te, settings.getFacing());
                        if (handler != null) {
                            if (count != null) {
                                int amount = countItems(handler, settings.getMatcher());
                                int caninsert = count - amount;
                                if (caninsert <= 0) {
                                    continue;
                                }
                                toinsert = Math.min(toinsert, caninsert);
                            }

                            stack = stack.copy();
                            if (toinsert < 0)
                                stack.setCount(0);
                            else
                                stack.setCount(toinsert);
                            remaining = ItemHandlerHelper.insertItem(handler, stack, true);
                        } else {
                            continue;
                        }
                    }

                    actuallyinserted = toinsert - remaining.getCount();

                    if (actuallyinserted > 0) {
                        inserted.add(entry);
                        total -= actuallyinserted;
                        if (total <= 0) {
                            return 0;
                        }
                    }
                }
            }
        }
        return total;
    }

    public void insertStackReal(@Nonnull IControllerContext context, @Nonnull List<Pair<SidedConsumer, ItemConnectorSettings>> inserted, @Nonnull ItemStack stack) {
        int total = stack.getCount();
        for (Pair<SidedConsumer, ItemConnectorSettings> entry : inserted) {
            BlockPos consumerPosition = context.findConsumerPosition(entry.getKey().getConsumerId());
            EnumFacing side = entry.getKey().getSide();
            ItemConnectorSettings settings = entry.getValue();
            BlockPos pos = consumerPosition.offset(side);
            TileEntity te = context.getControllerWorld().getTileEntity(pos);
            if (ModSetup.rftools && RFToolsSupport.isStorageScanner(te)) {
                int toinsert = total;
                Integer count = settings.getCount();
                if (count != null) {
                    int amount = RFToolsSupport.countItems(te, settings.getMatcher(), count);
                    int caninsert = count - amount;
                    if (caninsert <= 0) {
                        continue;
                    }
                    toinsert = Math.min(toinsert, caninsert);
                }

                stack = stack.copy();
                if (toinsert < 0)
                    stack.setCount(0);
                else
                    stack.setCount(toinsert);

                ItemStack remaining = RFToolsSupport.insertItem(te, stack, false);
                int actuallyinserted = toinsert - remaining.getCount();

                if (actuallyinserted > 0) {
                    roundRobinOffset = (roundRobinOffset + 1) % itemConsumers.size();
                    total -= actuallyinserted;
                    if (total <= 0) {
                        return;
                    }
                }

            } else {
                IItemHandler handler = getItemHandlerAt(te, settings.getFacing());

                int toinsert = total;
                Integer count = settings.getCount();
                if (count != null) {
                    int amount = countItems(handler, settings.getMatcher());
                    int caninsert = count - amount;
                    if (caninsert <= 0) {
                        continue;
                    }
                    toinsert = Math.min(toinsert, caninsert);
                }

                stack = stack.copy();
                if (toinsert < 0)
                    stack.setCount(0);
                else
                    stack.setCount(toinsert);

                ItemStack remaining = ItemHandlerHelper.insertItem(handler, stack, false);
                int actuallyinserted = toinsert - remaining.getCount();

                if (actuallyinserted > 0) {
                    roundRobinOffset = (roundRobinOffset + 1) % itemConsumers.size();
                    total -= actuallyinserted;
                    if (total <= 0) {
                        return;
                    }
                }
            }
        }
    }

    private int countItems(IItemHandler handler, Predicate<ItemStack> matcher) {
        int cnt = 0;
        for (int i = 0 ; i < handler.getSlots() ; i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                if (matcher.test(s)) {
                    cnt += s.getCount();
                }
            }
        }
        return cnt;
    }


    private ItemStack fetchItem(IItemHandler handler, boolean simulate, Predicate<ItemStack> matcher, ItemConnectorSettings.StackMode stackMode, int extractAmount, int maxamount, int idx)
    {
        if (handler.getSlots() <= 0)
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = handler.getStackInSlot(idx);
        if (!stack.isEmpty())
        {
            int s = 0;
            switch (stackMode)
            {
                case SINGLE:
                    s = 1;
                    break;
                case STACK:
                    s = stack.getMaxStackSize();
                    break;
                case COUNTM:
                case COUNTE:
                    s = extractAmount;
                    break;
                case HIGHEST:
                    s = Integer.MAX_VALUE;
                    break;
            }
            s = Math.min(s, maxamount);
            stack = handler.extractItem(idx, s, simulate);
            if (!stack.isEmpty() && matcher.test(stack))
            {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private void updateCache(int channel, IControllerContext context) {
        if (itemExtractors == null) {
            itemExtractors = new HashMap<>();
            itemConsumers = new ArrayList<>();
            Map<SidedConsumer, IConnectorSettings> connectors = context.getConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                ItemConnectorSettings con = (ItemConnectorSettings) entry.getValue();
                if (con.getItemMode() == ItemConnectorSettings.ItemMode.EXT) {
                    itemExtractors.put(entry.getKey(), con);
                } else {
                    itemConsumers.add(Pair.of(entry.getKey(), con));
                }
            }
            connectors = context.getRoutedConnectors(channel);
            for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
                ItemConnectorSettings con = (ItemConnectorSettings) entry.getValue();
                if (con.getItemMode() == ItemConnectorSettings.ItemMode.INS) {
                    itemConsumers.add(Pair.of(entry.getKey(), con));
                }
            }

            itemConsumers.sort((o1, o2) -> o2.getRight().getPriority().compareTo(o1.getRight().getPriority()));
        }
    }

    @Override
    public void cleanCache() {
        itemExtractors = null;
        itemConsumers = null;
    }

    @Override
    public boolean isEnabled(String tag) {
        return true;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        return new IndicatorIcon(iconGuiElements, 0, 80, 11, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    @Override
    public void createGui(IEditorGui gui) {
        gui.nl().choices(TAG_MODE, "Item distribution mode", channelMode, ChannelMode.values());
    }

    @Override
    public void update(Map<String, Object> data) {
        channelMode = ChannelMode.valueOf(((String)data.get(TAG_MODE)).toUpperCase());
        roundRobinOffset = 0;
    }

    @Nullable
    public static IItemHandler getItemHandlerAt(@Nullable TileEntity te, EnumFacing intSide) {
        if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, intSide)) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, intSide);
            if (handler != null) {
                return handler;
            }
        } else if (te instanceof ISidedInventory) {
            // Support for old inventory
            ISidedInventory sidedInventory = (ISidedInventory) te;
            return new SidedInvWrapper(sidedInventory, intSide);
        } else if (te instanceof IInventory) {
            // Support for old inventory
            IInventory inventory = (IInventory) te;
            return new InvWrapper(inventory);
        }
        return null;
    }


}
