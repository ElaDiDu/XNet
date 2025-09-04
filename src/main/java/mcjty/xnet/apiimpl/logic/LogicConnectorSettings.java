package mcjty.xnet.apiimpl.logic;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicConnectorSettings extends AbstractConnectorSettings {

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    public static final String TAG_MODE = "mode";
    public static final String TAG_SPEED = "speed";
    public static final String TAG_REDSTONE_OUT = "rsout";
    public static final String TAG_CUSTOM_SPEED = "customspeed";

    public enum LogicMode {
        SENSOR,
        OUTPUT
    }

    public static final int SENSORS = 4;
    public static final IntList SPEEDS = new IntArrayList(new int[]{2, 4, 12, 20, 40});
    public static final IntList ADVANCED_SPEEDS = new IntArrayList(new int[]{1, 2, 4, 12, 20, 40});
    private static final int CUSTOM_SPEED = -1;
    private static final int DEFAULT_SPEED = 2;
    private static final String CUSTOM_SPEED_NAME = "Cstm";

    private LogicMode logicMode = LogicMode.SENSOR;
    private List<Sensor> sensors = null;

    private int colors;         // Current colormask
    private int speed = 2;
    private Integer redstoneOut;    // Redstone output value
    @Nullable private Integer customSpeed = null;

    public LogicConnectorSettings(@Nonnull EnumFacing side) {
        super(side);
        sensors = new ArrayList<>(SENSORS);
        for (int i = 0 ; i < SENSORS ; i++) {
            sensors.add(new Sensor(i));
        }
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setColorMask(int colors) {
        this.colors = colors;
    }

    public int getColorMask() {
        return colors;
    }

    public Integer getRedstoneOut() {
        return redstoneOut;
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        switch (logicMode) {
            case SENSOR:
                return new IndicatorIcon(iconGuiElements, 26, 70, 13, 10);
            case OUTPUT:
                return new IndicatorIcon(iconGuiElements, 39, 70, 13, 10);
        }
        return null;
    }



    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    private static Set<String> TAGS = ImmutableSet.of(TAG_REDSTONE_OUT, TAG_MODE, TAG_RS, TAG_COLOR+"0", TAG_COLOR+"1", TAG_COLOR+"2", TAG_COLOR+"3", TAG_CUSTOM_SPEED);

    @Override
    public boolean isEnabled(String tag) {
        if (tag.equals(TAG_FACING)) {
            return advanced && logicMode != LogicMode.OUTPUT;
        }
        if (tag.equals(TAG_SPEED)) {
            return true;
        }
        for (Sensor sensor : sensors) {
            if (sensor.isEnabled(tag)) {
                return true;
            }
        }

        return TAGS.contains(tag);
    }

    public int getSpeed() {
        return speed;
    }

    public int getTickSpeed()
    {
        if (speed == CUSTOM_SPEED)
        {
            if (customSpeed == null)
                return DEFAULT_SPEED * 5;
            else
                return customSpeed;
        }
        else
        {
            return speed * 5;
        }
    }

    public LogicMode getLogicMode() {
        return logicMode;
    }


    @Override
    public void createGui(IEditorGui gui) {
        advanced = gui.isAdvanced();
        String[] speeds;
        if (advanced) {
            speeds = new String[] { "5", "10", "20", "60", "100", "200", CUSTOM_SPEED_NAME };
        } else {
            speeds = new String[] { "10", "20", "60", "100", "200" };
        }
        sideGui(gui);
        colorsGui(gui);
        redstoneGui(gui);
        gui.nl()
                .choices(TAG_MODE, "Sensor or Output mode", logicMode, LogicMode.values())
                .choices(TAG_SPEED, (logicMode == LogicMode.SENSOR ? "Number of ticks for each check" : "Number of ticks for each operation"),
                        speed == CUSTOM_SPEED ? CUSTOM_SPEED_NAME : Integer.toString(speed * 5), speeds);

        if (speed == CUSTOM_SPEED)
            gui.integer(TAG_CUSTOM_SPEED, (logicMode == LogicMode.SENSOR ? "Number of ticks for each check" : "Number of ticks for each operation"), customSpeed, 30);
        gui.nl();

        if (logicMode == LogicMode.SENSOR) {
            for (Sensor sensor : sensors) {
                sensor.createGui(gui);
            }
        } else {
            gui.label("Redstone:")
                    .integer(TAG_REDSTONE_OUT, "Redstone output value", redstoneOut, 40, 16)
                    .nl();
        }
    }

    @Override
    public void update(Map<String, Object> data) {
        super.update(data);
        logicMode = LogicMode.valueOf(((String)data.get(TAG_MODE)).toUpperCase());
        String facing = (String) data.get(TAG_FACING);
        // @todo suspicious

        String speedStr = (String) data.get(TAG_SPEED);
        if (speedStr.equals(CUSTOM_SPEED_NAME))
        {
            speed = CUSTOM_SPEED;
        }
        else
        {
            speed = Integer.parseInt(speedStr) / 5;
            if (speed == 0)
                speed = DEFAULT_SPEED;
        }
        customSpeed = (Integer) data.get(TAG_CUSTOM_SPEED);
        if (logicMode == LogicMode.SENSOR) {
            for (Sensor sensor : sensors) {
                sensor.update(data);
            }
        } else {
            redstoneOut = (Integer) data.get(TAG_REDSTONE_OUT);
        }
    }

    @Override
    public void sanitizeSettings(boolean advanced)
    {
        super.sanitizeSettings(advanced);

        if (!advanced)
        {
            customSpeed = null;
            if (speed == CUSTOM_SPEED)
                speed = DEFAULT_SPEED;
        }
        if (speed == CUSTOM_SPEED)
        {
            if (customSpeed != null)
                customSpeed = Math.max(1, customSpeed);
        }
        else
        {
            IntList speeds = advanced ? ADVANCED_SPEEDS : SPEEDS;
            if (!speeds.contains(speed))
                speed = DEFAULT_SPEED;
        }
    }

    @Override
    public JsonObject writeToJson() {
        JsonObject object = new JsonObject();
        super.writeToJsonInternal(object);
        setEnumSafe(object, "logicmode", logicMode);
        setIntegerSafe(object, "speed", speed);
        setIntegerSafe(object, "customspeed", customSpeed);
        JsonArray sensorArray = new JsonArray();
        for (Sensor sensor : sensors) {
            JsonObject o = new JsonObject();
            setEnumSafe(o, "sensormode", sensor.getSensorMode());
            setEnumSafe(o, "outputcolor", sensor.getOutputColor());
            setEnumSafe(o, "operator", sensor.getOperator());
            setIntegerSafe(o, "amount", sensor.getAmount());
            if (!sensor.getFilter().isEmpty()) {
                o.add("filter", ItemStackTools.itemStackToJson(sensor.getFilter()));
            }
            sensorArray.add(o);
        }
        object.add("sensors", sensorArray);
        if (speed == 1 || speed == CUSTOM_SPEED) {
            object.add("advancedneeded", new JsonPrimitive(true));
        }
        return object;
    }

    @Override
    public void readFromJson(JsonObject object) {
        super.readFromJsonInternal(object);
        logicMode = getEnumSafe(object, "logicmode", EnumStringTranslators::getLogicMode);
        speed = getIntegerNotNull(object, "speed");
        customSpeed = getIntegerSafe(object, "customspeed");
        JsonArray sensorArray = object.get("sensors").getAsJsonArray();
        sensors.clear();
        for (JsonElement oe : sensorArray) {
            JsonObject o = oe.getAsJsonObject();
            Sensor sensor = new Sensor(sensors.size());
            sensor.setAmount(getIntegerNotNull(o, "amount"));
            sensor.setOperator(getEnumSafe(o, "operator", EnumStringTranslators::getOperator));
            sensor.setOutputColor(getEnumSafe(o, "outputcolor", EnumStringTranslators::getColor));
            sensor.setSensorMode(getEnumSafe(o, "sensormode", EnumStringTranslators::getSensorMode));
            if (o.has("filter")) {
                sensor.setFilter(ItemStackTools.jsonToItemStack(o.get("filter").getAsJsonObject()));
            } else {
                sensor.setFilter(ItemStack.EMPTY);
            }
            sensors.add(sensor);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        logicMode = LogicMode.values()[tag.getByte("logicMode")];
        speed = tag.getInteger("speed");
        if (speed == 0)
            speed = 2;
        colors = tag.getInteger("colors");
        for (Sensor sensor : sensors) {
            sensor.readFromNBT(tag);
        }
        redstoneOut = tag.getInteger("rsout");
        if (tag.hasKey("customSpeed"))
            customSpeed = tag.getInteger("customSpeed");
        else
            customSpeed = null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("logicMode", (byte) logicMode.ordinal());
        tag.setInteger("speed", speed);
        tag.setInteger("colors", colors);
        for (Sensor sensor : sensors) {
            sensor.writeToNBT(tag);
        }
        if (redstoneOut != null)
            tag.setInteger("rsout", redstoneOut);
        if (customSpeed != null)
            tag.setInteger("customSpeed", customSpeed);
    }

}
