package mcjty.xnet.apiimpl.fluids;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mcjty.lib.varia.FluidTools;
import mcjty.lib.varia.ItemStackList;
import mcjty.lib.varia.ItemStackTools;
import mcjty.xnet.XNet;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.helper.AbstractConnectorSettings;
import mcjty.xnet.apiimpl.EnumStringTranslators;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class FluidConnectorSettings extends AbstractConnectorSettings {

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    public static final String TAG_MODE = "mode";
    public static final String TAG_RATE = "rate";
    public static final String TAG_MINMAX = "minmax";
    public static final String TAG_PRIORITY = "priority";
    public static final String TAG_FILTER = "flt";
    public static final String TAG_BLACKLIST = "blacklist";
    public static final String TAG_AMOUNTMODE = "amountmode";
    public static final String TAG_SPEED = "speed";
    public static final String TAG_EXTRACT = "extract";
    public static final String TAG_TANK = "tank";

    public static final int FILTER_SIZE = 18;
    public static final IntList SPEEDS = new IntArrayList(new int[]{2, 6, 10, 20});
    public static final IntList ADVANCED_SPEEDS = new IntArrayList(new int[]{1, 2, 6, 10, 20, 60, 120});

    public enum FluidMode {
        INS,
        EXT
    }

    public enum ExtractMode
    {
        FIRST,
        RND,
        ORDER,
        SLOT
    }

    public enum AmountMode
    {
        BUCKET,
        RATE,
        HIGHEST
    }

    private FluidMode fluidMode = FluidMode.INS;
    @Nullable private Integer priority = 0;
    @Nullable private Integer rate = null;
    @Nullable private Integer minmax = null;
    private int speed = 2;
    private ExtractMode extractMode = ExtractMode.FIRST;
    private AmountMode amountMode = AmountMode.BUCKET;
    @Nullable private Integer extractTank = null;
    private boolean blacklist = false;

    private ItemStackList filters = ItemStackList.create(FILTER_SIZE);

    private Predicate<FluidStack> matcher = null;

    public FluidConnectorSettings(@Nonnull EnumFacing side) {
        super(side);
    }

    public FluidMode getFluidMode() {
        return fluidMode;
    }

    public int getSpeed() {
        return speed;
    }

    @Nonnull
    public Integer getPriority() {
        return priority == null ? 0 : priority;
    }

    @Nullable
    public Integer getRate() {
        return rate;
    }

    @Nullable
    public Integer getMinmax() {
        return minmax;
    }

    public ExtractMode getExtractMode()
    {
        return extractMode;
    }
    public AmountMode getAmountMode() {return amountMode; }
    @Nullable
    public Integer getExtractTank() {return extractTank; }
    public ItemStackList getFilters()
    {
        return filters;
    }

    public boolean isBlacklist() {
        return blacklist;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        switch (fluidMode) {
            case INS:
                return new IndicatorIcon(iconGuiElements, 0, 70, 13, 10);
            case EXT:
                return new IndicatorIcon(iconGuiElements, 13, 70, 13, 10);
        }
        return null;
    }

    @Override
    @Nullable
    public String getIndicator() {
        return null;
    }

    // if someone tried the dirty jar on their save, help them
    private static AmountMode readAmountModeFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("amountMode")) {
            return AmountMode.BUCKET;
        }

        int rawMode = tag.getByte("amountMode") & 255;
        AmountMode[] values = AmountMode.values();

        if (rawMode < values.length) {
            return values[rawMode];
        }
        // Crash-safety for old dev-alpha saves where HIGHEST was ordinal 3.
        if (rawMode == 3) {
            return AmountMode.HIGHEST;
        }

        return AmountMode.BUCKET;
    }

    private void sanitizeFluidTransferSettings() {
        if (amountMode == null) {
            amountMode = AmountMode.BUCKET;
        }
    }

    @Override
    public void createGui(IEditorGui gui) {
        advanced = gui.isAdvanced();
        sanitizeFluidTransferSettings();
        String[] speeds;
        if (advanced) {
            speeds = new String[] { "10", "20", "60", "100", "200", "600", "1200" };
        } else {
            speeds = new String[] { "20", "60", "100", "200" };
        }

        sideGui(gui);
        colorsGui(gui);
        redstoneGui(gui);

        if (fluidMode == FluidMode.EXT) {
            gui.nl()
                    .choices(TAG_MODE, "Insert or extract mode", fluidMode, FluidMode.values())
                    .choices(TAG_AMOUNTMODE, "Extraction amount|Bucket, Rate, Highest", amountMode, AmountMode.values());

            if (amountMode == AmountMode.RATE) {
                gui.integer(TAG_RATE, "Fluid extraction rate|per operation|(empty = max)", rate, 36);
            }

            gui.shift(2)
                    .choices(TAG_SPEED, "Number of ticks for each operation", Integer.toString(speed * 10), speeds)
                    .nl()

                    .label("Pri").integer(TAG_PRIORITY, "Insertion priority", priority, 36)
                    .shift(5)
                    .choices(TAG_EXTRACT, "Extract mode (first available,|random slot, round robin or slot)", extractMode, ExtractMode.values());

            if (extractMode == ExtractMode.SLOT) {
                gui.shift(5)
                        .integer(TAG_TANK, "Tank to extract from|(blank = 0)", extractTank, 30);
            }

            gui.nl()

                    .toggleText(TAG_BLACKLIST, "Enable blacklist mode", "BL", blacklist).shift(2)
                    .shift(20)
                    .label("Min")
                    .integer(TAG_MINMAX, "Keep this amount of|fluid in tank", minmax, 68)
                    .nl();
        } else {
            gui.nl()
                    .choices(TAG_MODE, "Insert or extract mode", fluidMode, FluidMode.values())
                    .integer(TAG_RATE, "Fluid insertion rate|per operation|(empty = max)", rate, 54)
                    .choices(TAG_SPEED, "Number of ticks for each operation", Integer.toString(speed * 10), speeds)
                    .nl()

                    .label("Pri").integer(TAG_PRIORITY, "Insertion priority", priority, 36)
                    .nl()

                    .toggleText(TAG_BLACKLIST, "Enable blacklist mode", "BL", blacklist).shift(2)
                    .shift(20)
                    .label("Max")
                    .integer(TAG_MINMAX, "Disable insertion if|fluid level is too high", minmax, 66)
                    .nl();
        }

        for (int i = 0 ; i < FILTER_SIZE; i++) {
            gui.ghostSlot(TAG_FILTER + i, filters.get(i));
        }
    }

    private static Set<String> INSERT_TAGS = ImmutableSet.of(
            TAG_MODE, TAG_RS,
            TAG_COLOR+"0", TAG_COLOR+"1", TAG_COLOR+"2", TAG_COLOR+"3",
            TAG_RATE, TAG_MINMAX, TAG_PRIORITY, TAG_BLACKLIST
    );
    private static final Set<String> EXTRACT_TAGS = ImmutableSet.of(
            TAG_MODE, TAG_RS,
            TAG_COLOR+"0", TAG_COLOR+"1", TAG_COLOR+"2", TAG_COLOR+"3",
            TAG_RATE, TAG_MINMAX, TAG_PRIORITY, TAG_SPEED, TAG_EXTRACT, TAG_BLACKLIST, TAG_AMOUNTMODE, TAG_TANK
    );

    @Override
    public boolean isEnabled(String tag) {
        if (tag.startsWith(TAG_FILTER)) {
            return true;
        }
        if (tag.equals(TAG_FACING)) {
            return advanced;
        }
        if (fluidMode == FluidMode.INS) {
            return INSERT_TAGS.contains(tag);
        } else {
            if (tag.equals(TAG_RATE)) {
                return amountMode == AmountMode.RATE;
            }
            if (tag.equals(TAG_TANK)) {
                return extractMode == ExtractMode.SLOT;
            }
            return EXTRACT_TAGS.contains(tag);
        }
    }

    @Nonnull
    public Predicate<FluidStack> getMatcher()
    {
        if (matcher != null)
            return matcher;

        // @todo optimize/cache this?
        if (!filters.isEmpty())
        {
            ItemStackList filterList = ItemStackList.create();
            for (ItemStack filterStack : filters)
            {
                if (!filterStack.isEmpty())
                    filterList.add(filterStack);
            }
            if (filterList.isEmpty())
                matcher = fluidStack -> true;
            else
            {
                matcher = fluidStack ->
                {
                    boolean match = false;
                    for (ItemStack filterStack : filterList)
                        if (fluidStack.equals(FluidTools.convertBucketToFluid(filterStack))) {
                            match = true;
                            break;
                        }
                    return blacklist ? !match : match;
                };
            }

            return matcher;
        }
        else
        {
            return (stack) -> true;
        }
    }


    @Override
    public void update(Map<String, Object> data) {
        super.update(data);
        fluidMode = FluidMode.valueOf(((String) data.get(TAG_MODE)).toUpperCase());

        Object amountModeObj = data.get(TAG_AMOUNTMODE);
        if (amountModeObj != null) {
            amountMode = AmountMode.valueOf(((String) amountModeObj).toUpperCase());
        }

        if (data.containsKey(TAG_RATE)) {
            rate = (Integer) data.get(TAG_RATE);
        }
        minmax = (Integer) data.get(TAG_MINMAX);
        priority = (Integer) data.get(TAG_PRIORITY);
        blacklist = Boolean.TRUE.equals(data.get(TAG_BLACKLIST));

        speed = Integer.parseInt((String) data.get(TAG_SPEED)) / 10;
        if (speed == 0) {
            speed = 2;
        }

        Object extractModeObj = data.get(TAG_EXTRACT);
        if (extractModeObj != null) {
            extractMode = ExtractMode.valueOf(((String) extractModeObj).toUpperCase());
        }
        if (data.containsKey(TAG_TANK)) {
            extractTank = (Integer) data.get(TAG_TANK);
        }
        if (extractTank != null && extractTank < 0) {
            extractTank = null;
        }
        for (int i = 0; i < FILTER_SIZE; i++) {
            filters.set(i, (ItemStack) data.get(TAG_FILTER + i));
        }
        sanitizeFluidTransferSettings();
        matcher = null;
    }

    @Override
    public void sanitizeSettings(boolean advanced)
    {
        super.sanitizeSettings(advanced);
        int minSpeed = advanced ? ADVANCED_SPEEDS.get(0) : SPEEDS.get(0);
        speed = Math.max(speed, minSpeed);
        sanitizeFluidTransferSettings();
    }

    @Override
    public JsonObject writeToJson() {
        JsonObject object = new JsonObject();
        super.writeToJsonInternal(object);
        setEnumSafe(object, "fluidmode", fluidMode);
        setEnumSafe(object, "amountmode", amountMode);
        setIntegerSafe(object, "priority", priority);
        setIntegerSafe(object, "rate", rate);
        setIntegerSafe(object, "minmax", minmax);
        setIntegerSafe(object, "speed", speed);
        setIntegerSafe(object, "tank", extractTank);
        object.add("blacklist", new JsonPrimitive(blacklist));
        for (int i = 0; i < FILTER_SIZE; i++)
        {
            if (!filters.get(i).isEmpty())
            {
                object.add("filter" + i, ItemStackTools.itemStackToJson(filters.get(i)));
            }
        }
        if (speed == 1) {
            object.add("advancedneeded", new JsonPrimitive(true));
        }
        setEnumSafe(object, "extractmode", extractMode);
        return object;
    }

    @Override
    public void readFromJson(JsonObject object) {
        super.readFromJsonInternal(object);
        fluidMode = getEnumSafe(object, "fluidmode", EnumStringTranslators::getFluidMode);
        amountMode = object.has("amountmode") ? getEnumSafe(object, "amountmode", EnumStringTranslators::getFluidAmountMode) : AmountMode.BUCKET;
        priority = getIntegerSafe(object, "priority");
        rate = getIntegerSafe(object, "rate");
        minmax = getIntegerSafe(object, "minmax");
        speed = getIntegerNotNull(object, "speed");
        extractTank = getIntegerSafe(object, "tank");
        if (extractTank != null && extractTank < 0) {
            extractTank = null;
        }
        blacklist = getBoolSafe(object, "blacklist");
        for (int i = 0 ; i < FILTER_SIZE ; i++)
        {
            if (object.has("filter" + i))
            {
                filters.set(i, ItemStackTools.jsonToItemStack(object.get("filter" + i).getAsJsonObject()));
            } else
            {
                filters.set(i, ItemStack.EMPTY);
            }
        }
        extractMode = getEnumSafe(object, "extractmode", EnumStringTranslators::getFluidExtractMode);
        sanitizeFluidTransferSettings();
        matcher = null;
    }


    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        super.readFromNBT(tag);
        fluidMode = FluidMode.values()[tag.getByte("fluidMode")];
        amountMode = readAmountModeFromNBT(tag);
        if (tag.hasKey("priority"))
        {
            priority = tag.getInteger("priority");
        } else
        {
            priority = null;
        }
        blacklist = tag.getBoolean("blacklist");
        if (tag.hasKey("rate"))
        {
            rate = tag.getInteger("rate");
        } else
        {
            rate = null;
        }
        if (tag.hasKey("minmax"))
        {
            minmax = tag.getInteger("minmax");
        } else
        {
            minmax = null;
        }
        speed = tag.getInteger("speed");
        if (speed == 0)
        {
            speed = 2;
        }
        if (tag.hasKey("tank")) {
            extractTank = tag.getInteger("tank");
            if (extractTank < 0) {
                extractTank = null;
            }
        } else {
            extractTank = null;
        }
        // Old 1 item filter check for compat
        if (tag.hasKey("filter"))
        {
            NBTTagCompound itemTag = tag.getCompoundTag("filter");
            filters.set(0, new ItemStack(itemTag));
            for (int i = 1; i < FILTER_SIZE; i++)
                filters.set(i, ItemStack.EMPTY);
        }
        else
        {
            for (int i = 0; i < FILTER_SIZE; i++)
            {
                if (tag.hasKey("filter" + i))
                {
                    NBTTagCompound itemTag = tag.getCompoundTag("filter" + i);
                    ItemStack stack = new ItemStack(itemTag);
                    filters.set(i, stack);
                }
                else
                {
                    filters.set(i, ItemStack.EMPTY);
                }
            }
            if (tag.hasKey("extractMode"))
            {
                extractMode = ExtractMode.values()[tag.getByte("extractMode")];
            }
            else
            {
                extractMode = ExtractMode.FIRST;
            }
        }
        sanitizeFluidTransferSettings();
        matcher = null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("fluidMode", (byte) fluidMode.ordinal());
        tag.setByte("amountMode", (byte) amountMode.ordinal());
        tag.setBoolean("blacklist", blacklist);
        if (priority != null) {
            tag.setInteger("priority", priority);
        }
        if (rate != null) {
            tag.setInteger("rate", rate);
        }
        if (minmax != null) {
            tag.setInteger("minmax", minmax);
        }
        tag.setInteger("speed", speed);
        for (int i = 0 ; i < FILTER_SIZE ; i++) {
            if (!filters.get(i).isEmpty()) {
                NBTTagCompound itemTag = new NBTTagCompound();
                filters.get(i).writeToNBT(itemTag);
                tag.setTag("filter" + i, itemTag);
            }
        }
        tag.setByte("extractMode", (byte) extractMode.ordinal());
        if (extractTank != null) {
            tag.setInteger("tank", extractTank);
        }
    }
}