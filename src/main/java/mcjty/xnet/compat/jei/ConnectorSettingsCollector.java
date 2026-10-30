package mcjty.xnet.compat.jei;

import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.RSMode;
import mcjty.xnet.api.gui.IEditorGui;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Headless editor used to collect the complete settings map expected by
 * IConnectorSettings.update().
 */
public final class ConnectorSettingsCollector implements IEditorGui {

    private final boolean advanced;
    private final Map<String, Object> values = new LinkedHashMap<>();

    private ConnectorSettingsCollector(boolean advanced) {
        this.advanced = advanced;
    }

    public static Map<String, Object> collect(IConnectorSettings settings, boolean advanced) {
        ConnectorSettingsCollector collector = new ConnectorSettingsCollector(advanced);
        settings.createGui(collector);
        return collector.copyValues();
    }

    private Map<String, Object> copyValues() {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(), value instanceof ItemStack ? ((ItemStack) value).copy() : value);
        }
        return copy;
    }

    @Override
    public boolean isAdvanced() {return advanced;}

    @Override
    public IEditorGui move(int x, int y) {return this;}

    @Override
    public IEditorGui move(int x) {return this;}

    @Override
    public IEditorGui shift(int x) {return this;}

    @Override
    public IEditorGui label(String txt) {return this;}

    @Override
    public IEditorGui text(String tag, String tooltip, String value, int width) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui integer(String tag, String tooltip, Integer value, int width, Integer maximum) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui integer(String tag, String tooltip, Integer value, int width) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui real(String tag, String tooltip, Double value, int width) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui toggle(String tag, String tooltip, boolean value) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui toggleText(String tag, String tooltip, String text, boolean value) {
        values.put(tag, value);
        return this;
    }

    @Override
    public IEditorGui colors(String tag, String tooltip, Integer current, Integer... colors) {
        values.put(tag, current);
        return this;
    }

    @Override
    public IEditorGui choices(String tag, String tooltip, String current, String... values) {
        this.values.put(tag, current);
        return this;
    }

    @Override
    public <T extends Enum<T>> IEditorGui choices(String tag, String tooltip, T current, T... values) {
        String lower = current.toString().toLowerCase(Locale.ROOT);
        String guiValue = lower.isEmpty() ? lower
                : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        this.values.put(tag, guiValue);
        return this;
    }

    @Override
    public IEditorGui redstoneMode(String tag, RSMode current) {
        values.put(tag, current.name());
        return this;
    }

    @Override
    public IEditorGui ghostSlot(String tag, ItemStack slot) {
        values.put(tag, slot == null ? ItemStack.EMPTY : slot.copy());
        return this;
    }

    @Override
    public IEditorGui nl() {return this;}
}