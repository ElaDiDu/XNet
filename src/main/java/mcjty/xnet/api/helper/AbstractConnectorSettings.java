package mcjty.xnet.api.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mcjty.xnet.api.channels.Color;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.channels.RSMode;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.apiimpl.EnumStringTranslators;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractConnectorSettings implements IConnectorSettings {

    public static final String TAG_RS = "rs";
    public static final String TAG_COLOR = "color";
    public static final String TAG_FACING = "facing";
    public static final String TAG_COLOR_OPERATOR = "coloroperator";
    public enum ColorOperator {
        AND("AND"), OR("OR"), NAND("!AND"), NOR("!OR");
        private final String label;
        ColorOperator(String label) {
            this.label = label;
        }
    }
    private static final ColorOperator[] COLOR_OPERATORS = ColorOperator.values();
    private RSMode rsMode = RSMode.IGNORED;
    private int prevPulse = 0;
    private Color[] colors = new Color[]{Color.OFF, Color.OFF, Color.OFF, Color.OFF};
    private int colorsMask = 0;
    private ColorOperator colorOperator = ColorOperator.AND;

    // Cache for advanced mode
    protected boolean advanced = false;

    @Nonnull
    private final EnumFacing side;
    @Nullable
    private EnumFacing facingOverride = null; // Only available on advanced connectors

    public AbstractConnectorSettings(@Nonnull EnumFacing side) {
        this.side = side;
    }

    @Nonnull
    public EnumFacing getFacing() {
        return facingOverride == null ? side : facingOverride;
    }

    public RSMode getRsMode() {
        return rsMode;
    }

    public int getPrevPulse() {
        return prevPulse;
    }

    public void setPrevPulse(int prevPulse) {
        this.prevPulse = prevPulse;
    }

    public Color[] getColors() {
        return colors;
    }

    private void calculateColorsMask() {
        colorsMask = 0;
        for (Color color : colors) {
            if (color != null && color != Color.OFF) {
                colorsMask |= 1 << color.ordinal();
            }
        }
    }

    public int getColorsMask() {
        return colorsMask;
    }
    public ColorOperator getColorOperator() {return colorOperator;}

    private static ColorOperator getColorOperator(String name) {
        if (name != null) {
            for (ColorOperator operator : COLOR_OPERATORS) {
                if (operator.name().equalsIgnoreCase(name) || operator.label.equalsIgnoreCase(name)) {
                    return operator;
                }
            }
        }
        return ColorOperator.AND;
    }

    private static ColorOperator getColorOperator(int ordinal) {
        return ordinal >= 0 && ordinal < COLOR_OPERATORS.length ? COLOR_OPERATORS[ordinal] : ColorOperator.AND;
    }

    private static boolean matchesAnyColor(IControllerContext context, int mask) {
        while (mask != 0) {
            int bit = mask & -mask;
            if (context.matchColor(bit)) {
                return true;
            }
            mask ^= bit;
        }
        return false;
    }

    public boolean matchesColor(IControllerContext context) {
        int mask = colorsMask;
        if (mask == 0) {
            return true;
        }
        switch (colorOperator) {
            case OR:
                return matchesAnyColor(context, mask);
            case NAND:
                return !context.matchColor(mask);
            case NOR:
                return !matchesAnyColor(context, mask);
            case AND:
            default:
                return context.matchColor(mask);
        }
    }

    @Override
    public void update(Map<String, Object> data) {
        if (data.containsKey(TAG_RS)) {
            rsMode = RSMode.valueOf(((String) data.get(TAG_RS)).toUpperCase());
        } else {
            rsMode = RSMode.IGNORED;
        }
        if (data.containsKey(TAG_COLOR + "0")) {
            colors[0] = Color.colorByValue((Integer) data.get(TAG_COLOR + "0"));
        } else {
            colors[0] = Color.OFF;
        }
        if (data.containsKey(TAG_COLOR + "1")) {
            colors[1] = Color.colorByValue((Integer) data.get(TAG_COLOR + "1"));
        } else {
            colors[1] = Color.OFF;
        }
        if (data.containsKey(TAG_COLOR + "2")) {
            colors[2] = Color.colorByValue((Integer) data.get(TAG_COLOR + "2"));
        } else {
            colors[2] = Color.OFF;
        }
        if (data.containsKey(TAG_COLOR + "3")) {
            colors[3] = Color.colorByValue((Integer) data.get(TAG_COLOR + "3"));
        } else {
            colors[3] = Color.OFF;
        }
        calculateColorsMask();
        Object operator = data.get(TAG_COLOR_OPERATOR);
        colorOperator = operator instanceof String ? getColorOperator((String) operator) : ColorOperator.AND;
        String facing = (String) data.get(TAG_FACING);
        facingOverride = facing == null ? null : EnumFacing.byName(facing);
    }

    @Override
    public void sanitizeSettings(boolean advanced) {
        this.advanced = advanced;
        facingOverride = advanced ? facingOverride : null;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    protected static <T extends Enum<T>> void setEnumSafe(JsonObject object, String tag, T value) {
        if (value != null) {
            object.add(tag, new JsonPrimitive(value.name()));
        }
    }

    protected static <T extends Enum<T>> T getEnumSafe(JsonObject object, String tag, Function<String, T> translator) {
        if (object.has(tag)) {
            return translator.apply(object.get(tag).getAsString());
        } else {
            return null;
        }
    }

    protected static void setIntegerSafe(JsonObject object, String tag, Integer value) {
        if (value != null) {
            object.add(tag, new JsonPrimitive(value));
        }
    }

    protected static Integer getIntegerSafe(JsonObject object, String tag) {
        if (object.has(tag)) {
            return object.get(tag).getAsInt();
        } else {
            return null;
        }
    }

    protected static int getIntegerNotNull(JsonObject object, String tag) {
        if (object.has(tag)) {
            return object.get(tag).getAsInt();
        } else {
            return 0;
        }
    }

    protected static boolean getBoolSafe(JsonObject object, String tag) {
        if (object.has(tag)) {
            return object.get(tag).getAsBoolean();
        } else {
            return false;
        }
    }

    protected void writeToJsonInternal(JsonObject object) {
        setEnumSafe(object, "rsmode", rsMode);
        setEnumSafe(object, "color0", colors[0]);
        setEnumSafe(object, "color1", colors[1]);
        setEnumSafe(object, "color2", colors[2]);
        setEnumSafe(object, "color3", colors[3]);
        setEnumSafe(object, "coloroperator", colorOperator);
        setEnumSafe(object, "side", side);   // Informative, isn't used to load
        setEnumSafe(object, "facingoverride", facingOverride);
        object.add("advancedneeded", new JsonPrimitive(false));
    }

    protected void readFromJsonInternal(JsonObject object) {
        rsMode = getEnumSafe(object, "rsmode", EnumStringTranslators::getRSMode);
        colors[0] = getEnumSafe(object, "color0", EnumStringTranslators::getColor);
        colors[1] = getEnumSafe(object, "color1", EnumStringTranslators::getColor);
        colors[2] = getEnumSafe(object, "color2", EnumStringTranslators::getColor);
        colors[3] = getEnumSafe(object, "color3", EnumStringTranslators::getColor);
        colorOperator = object.has("coloroperator") && object.get("coloroperator").isJsonPrimitive() ? getColorOperator(object.get("coloroperator").getAsString()) : ColorOperator.AND;
        calculateColorsMask();
        facingOverride = getEnumSafe(object, "facingoverride", EnumFacing::byName);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        rsMode = RSMode.values()[tag.getByte("rsMode")];
        prevPulse = tag.getInteger("prevPulse");
        colors[0] = Color.values()[tag.getByte("color0")];
        colors[1] = Color.values()[tag.getByte("color1")];
        colors[2] = Color.values()[tag.getByte("color2")];
        colors[3] = Color.values()[tag.getByte("color3")];
        colorOperator = getColorOperator(tag.getByte("colorOperator"));
        calculateColorsMask();
        if (tag.hasKey("facingOverride")) {
            facingOverride = EnumFacing.VALUES[tag.getByte("facingOverride")];
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setByte("rsMode", (byte) rsMode.ordinal());
        tag.setInteger("prevPulse", prevPulse);
        tag.setByte("color0", (byte) colors[0].ordinal());
        tag.setByte("color1", (byte) colors[1].ordinal());
        tag.setByte("color2", (byte) colors[2].ordinal());
        tag.setByte("color3", (byte) colors[3].ordinal());
        tag.setByte("colorOperator", (byte) colorOperator.ordinal());
        if (facingOverride != null) {
            tag.setByte("facingOverride", (byte) facingOverride.ordinal());
        }
    }

    protected IEditorGui sideGui(IEditorGui gui) {
        return gui.choices(TAG_FACING, "Side from which to operate", facingOverride == null ? side : facingOverride, EnumFacing.VALUES);
    }

    protected IEditorGui colorsGui(IEditorGui gui) {
        return gui
                .colors(TAG_COLOR + "0", "Enable on color", colors[0].getColor(), Color.COLORS)
                .colors(TAG_COLOR + "1", "Enable on color", colors[1].getColor(), Color.COLORS)
                .colors(TAG_COLOR + "2", "Enable on color", colors[2].getColor(), Color.COLORS)
                .colors(TAG_COLOR + "3", "Enable on color", colors[3].getColor(), Color.COLORS);
    }

    protected IEditorGui colorOperatorGui(IEditorGui gui) {
        return gui.choices(TAG_COLOR_OPERATOR, "Logic operator for colormask", colorOperator.label, "AND", "OR", "!AND", "!OR");
    }

    protected IEditorGui redstoneGui(IEditorGui gui) {
        return gui.redstoneMode(TAG_RS, rsMode);
    }
}
