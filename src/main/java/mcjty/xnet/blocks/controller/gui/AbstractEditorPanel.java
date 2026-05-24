package mcjty.xnet.blocks.controller.gui;

import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.lib.typed.TypedMap;
import mcjty.xnet.XNet;
import mcjty.xnet.api.channels.RSMode;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.network.XNetMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractEditorPanel implements IEditorGui {

    public static final ResourceLocation iconGuiElements = new ResourceLocation(XNet.MODID, "textures/gui/guielements.png");

    public static final int LEFTMARGIN = 3;
    public static final int TOPMARGIN = 3;

    private static final int FILTER_COUNT_STEP_NORMAL = 1;
    private static final int FILTER_COUNT_STEP_SHIFT = 10;
    private static final int FILTER_COUNT_STEP_CTRL = 100;
    private static final int FILTER_COUNT_STEP_CTRL_SHIFT = 1000;
    private static final int MAX_FILTER_COUNT = Integer.MAX_VALUE;

    private final Panel panel;
    private final Minecraft mc;
    private final GuiController gui;
    protected final Map<String, Object> data;
    protected final Map<String, Widget<?>> components = new HashMap<>();

    private int x;
    private int y;

    protected abstract void update(String tag, Object value);

    public Widget<?> getComponent(String tag) {
        return components.get(tag);
    }

    protected void performUpdate(TypedMap.Builder builder, int i, String cmd) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object o = entry.getValue();
            if (o instanceof String) {
                builder.put(new Key<>(entry.getKey(), Type.STRING), (String) o);
            } else if (o instanceof Integer) {
                builder.put(new Key<>(entry.getKey(), Type.INTEGER), (Integer) o);
            } else if (o instanceof Boolean) {
                builder.put(new Key<>(entry.getKey(), Type.BOOLEAN), (Boolean) o);
            } else if (o instanceof Double) {
                builder.put(new Key<>(entry.getKey(), Type.DOUBLE), (Double) o);
            } else if (o instanceof ItemStack) {
                builder.put(new Key<>(entry.getKey(), Type.ITEMSTACK), (ItemStack) o);
            } else {
                builder.put(new Key<>(entry.getKey(), Type.STRING), o == null ? null : o.toString());
            }
        }

        gui.sendServerCommand(XNetMessages.INSTANCE, cmd, builder.build());
        gui.refresh();
    }

    public AbstractEditorPanel(Panel panel, Minecraft mc, GuiController gui) {
        this.panel = panel;
        this.mc = mc;
        this.gui = gui;
        x = LEFTMARGIN;
        y = TOPMARGIN;
        data = new HashMap<>();
    }

    @Override
    public IEditorGui move(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public IEditorGui move(int x) {
        this.x = x;
        return this;
    }

    @Override
    public IEditorGui shift(int x) {
        this.x += x;
        return this;
    }

    private void fitWidth(int w) {
        if (x + w > panel.getBounds().width) {
            nl();
        }
    }

    private String[] parseTooltips(String tooltip) {
        return StringUtils.split(tooltip, '|');
    }

    @Override
    public IEditorGui label(String txt) {
        int w = mc.fontRenderer.getStringWidth(txt)+5;
        fitWidth(w);
        Label label = new Label(mc, gui).setText(txt);
        label.setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        panel.addChild(label);
        x += w;
        return this;
    }

    @Override
    public IEditorGui text(String tag, String tooltip, String value, int width) {
        int w = width;
        fitWidth(w);
        TextField text = new TextField(mc, gui).setText(value)
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, value);
        text.addTextEnterEvent((parent, newText) -> update(tag, newText));
        text.addTextEvent((parent, newText) -> update(tag, newText));
        panel.addChild(text);
        components.put(tag, text);
        x += w;
        return this;
    }

    private Integer parseInt(String i, Integer maximum) {
        if (i == null || i.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(i);
            if (maximum != null && v > maximum) {
                v = maximum;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public IEditorGui integer(String tag, String tooltip, Integer value, int width) {
        return integer(tag, tooltip, value, width, null);
    }

    @Override
    public IEditorGui integer(String tag, String tooltip, Integer value, int width, Integer maximum) {
        int w = width;
        fitWidth(w);
        TextField text = new TextField(mc, gui).setText(value == null ? "" : value.toString())
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, value);
        text.addTextEnterEvent((parent, newText) -> update(tag, parseInt(newText, maximum)));
        text.addTextEvent((parent, newText) -> update(tag, parseInt(newText, maximum)));
        panel.addChild(text);
        components.put(tag, text);
        x += w;
        return this;
    }

    private Double parseDouble(String i) {
        if (i == null || i.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(i);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public IEditorGui real(String tag, String tooltip, Double value, int width) {
        int w = width;
        fitWidth(w);
        TextField text = new TextField(mc, gui).setText(value == null ? "" : value.toString())
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, value);
        text.addTextEnterEvent((parent, newText) -> update(tag, parseDouble(newText)));
        text.addTextEvent((parent, newText) -> update(tag, parseDouble(newText)));
        panel.addChild(text);
        components.put(tag, text);
        x += w;
        return this;
    }

    @Override
    public IEditorGui toggle(String tag, String tooltip, boolean value) {
        int w = 12;
        fitWidth(w);
        ToggleButton toggle = new ToggleButton(mc, gui).setCheckMarker(true).setPressed(value)
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, value);
        toggle.addButtonEvent(parent -> update(tag, toggle.isPressed()));
        panel.addChild(toggle);
        components.put(tag, toggle);
        x += w;
        return this;
    }

    @Override
    public IEditorGui toggleText(String tag, String tooltip, String text, boolean value) {
        int w = mc.fontRenderer.getStringWidth(text) + 10;
        fitWidth(w);
        ToggleButton toggle = new ToggleButton(mc, gui).setCheckMarker(false).setPressed(value)
                .setText(text)
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, value);
        toggle.addButtonEvent(parent -> update(tag, toggle.isPressed()));
        panel.addChild(toggle);
        components.put(tag, toggle);
        x += w;
        return this;
    }

    @Override
    public IEditorGui colors(String tag, String tooltip, Integer current, Integer... colors) {
        int w = 14;
        fitWidth(w);
        ColorChoiceLabel choice = new ColorChoiceLabel(mc, gui).addColors(colors).setCurrentColor(current)
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, current);
        choice.addChoiceEvent((parent, newChoice) -> update(tag, newChoice));
        panel.addChild(choice);
        components.put(tag, choice);
        x += w;
        return this;
    }

    @Override
    public IEditorGui choices(String tag, String tooltip, String current, String... values) {
        int w = 10;
        for (String s : values) {
            w = Math.max(w, mc.fontRenderer.getStringWidth(s) + 14);
        }

        fitWidth(w);
        ChoiceLabel choice = new ChoiceLabel(mc, gui).addChoices(values).setChoice(current)
                .setTooltips(parseTooltips(tooltip))
                .setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, current);
        choice.addChoiceEvent((parent, newChoice) -> update(tag, newChoice));
        panel.addChild(choice);
        components.put(tag, choice);
        x += w;
        return this;
    }

    @Override
    public <T extends Enum<T>> IEditorGui choices(String tag, String tooltip, T current, T... values) {
        String[] strings = new String[values.length];
        int i = 0;
        for (T s : values) {
            strings[i++] = StringUtils.capitalize(s.toString().toLowerCase());
        }
        return choices(tag, tooltip, StringUtils.capitalize(current.toString().toLowerCase()), strings);
    }

    @Override
    public IEditorGui redstoneMode(String tag, RSMode current) {
        int w = 14;
        fitWidth(w);
        ImageChoiceLabel redstoneMode = new ImageChoiceLabel(mc, gui)
                .addChoice("Ignored", "Redstone mode:\nIgnored", iconGuiElements, 1, 1)
                .addChoice("Off", "Redstone mode:\nOff to activate", iconGuiElements, 17, 1)
                .addChoice("On", "Redstone mode:\nOn to activate", iconGuiElements, 33, 1)
                .addChoice("Pulse", "Do one operation\non a pulse", iconGuiElements, 49, 1);
        switch (current) {
            case IGNORED:
                redstoneMode.setCurrentChoice("Ignored");
                break;
            case OFF:
                redstoneMode.setCurrentChoice("Off");
                break;
            case ON:
                redstoneMode.setCurrentChoice("On");
                break;
            case PULSE:
                redstoneMode.setCurrentChoice("Pulse");
                break;
        }
        redstoneMode.setLayoutHint(new PositionalLayout.PositionalHint(x, y, w, 14));
        data.put(tag, current.name());
        redstoneMode.addChoiceEvent((parent, newChoice) -> update(tag, newChoice));
        panel.addChild(redstoneMode);
        components.put(tag, redstoneMode);
        x += w;
        return this;
    }

    @Override
    public IEditorGui ghostSlot(String tag, ItemStack stack) {
        int w = 16;
        fitWidth(w);
        BlockRenderFilter blockRender = new BlockRenderFilter(mc, gui);
        blockRender.setRenderItem(stack)
                .setDesiredWidth(18).setDesiredHeight(18)
                .setFilledRectThickness(-1).setFilledBackground(0xff888888);
        blockRender.setOnClick(button -> clickOnItemFilter(button, tag, blockRender));
        blockRender.setOnMouseWheel(amount -> wheelOnItemFilter(amount, tag, blockRender));
        blockRender.setOnGhostClick(s ->
        {
            update(tag, s);
            blockRender.setRenderItem(s);
        });

        blockRender.setLayoutHint(new PositionalLayout.PositionalHint(x, y-1, 17, 17));
        data.put(tag, stack);
        panel.addChild(blockRender);
        components.put(tag, blockRender);
        x += w;
        return this;
    }

    @Override
    public IEditorGui nl() {
        y += 16;
        x = LEFTMARGIN;
        return this;
    }

    private void clickOnItemFilter(int button, String tag, BlockRenderFilter blockRender)
    {
        ItemStack holding = Minecraft.getMinecraft().player.inventory.getItemStack();
        // Click with empty hand
        if (holding.isEmpty())
        {
            // Require a modifier (keyboard button) to alter the stack instead of deleting it.
            if (hasFilterCountEditModifier())
            {
                if (button == 0)
                {
                    Object renderItem = blockRender.getRenderItem();
                    if (renderItem instanceof ItemStack currStack)
                    {
                        alterStackCount(currStack, true);
                        if (currStack.getCount() <= 0)
                        {
                            update(tag, ItemStack.EMPTY);
                            blockRender.setRenderItem(null);
                        } else
                        {
                            update(tag, currStack);
                            blockRender.setRenderItem(currStack);
                        }
                    }
                }
                else if (button == 1)
                {
                    Object renderItem = blockRender.getRenderItem();
                    if (renderItem instanceof ItemStack currStack)
                    {
                        alterStackCount(currStack, false);
                        if (currStack.getCount() <= 0)
                        {
                            update(tag, ItemStack.EMPTY);
                            blockRender.setRenderItem(null);
                        } else
                        {
                            update(tag, currStack);
                            blockRender.setRenderItem(currStack);
                        }
                    }
                }
            }
            else
            {
                // Delete stack
                update(tag, ItemStack.EMPTY);
                blockRender.setRenderItem(null);
            }
        }
        else
        {
            Object renderItem = blockRender.getRenderItem();
            // Holding the same stack as the filter, add/remove the filter count
            if (renderItem instanceof ItemStack currStack &&
                    holding.getItem() == currStack.getItem() && holding.getMetadata() == currStack.getMetadata() &&
                    Objects.equals(holding.getTagCompound(), currStack.getTagCompound()))
            {
                if (button == 0)
                    alterStackCount(currStack, true);
                else if (button == 1)
                    alterStackCount(currStack, false);

                if (currStack.getCount() <= 0)
                {
                    update(tag, ItemStack.EMPTY);
                    blockRender.setRenderItem(null);
                }
                else
                {
                    update(tag, currStack);
                    blockRender.setRenderItem(currStack);
                }
            }
            else
            {
                // Holding different stack, replace filter with it
                ItemStack copy = holding.copy();
                update(tag, copy);
                blockRender.setRenderItem(copy);
            }
        }
    }

    private void wheelOnItemFilter(int amount, String tag, BlockRenderFilter blockRender) {
        ItemStack stack = (ItemStack) blockRender.getRenderItem();
        if (stack == null) {
            return;
        }

        alterStackCount(stack, amount > 0);

        // Mouse wheel should never clear the ghost filter.
        // It is too easy to accidentally scroll the wrong direction while editing counts.
        if (stack.getCount() <= 0) {
            stack.setCount(1);
        }

        update(tag, stack);
        blockRender.setRenderItem(stack);
    }

    private static boolean isKeyDown(int leftKey, int rightKey) {
        return Keyboard.isKeyDown(leftKey) || Keyboard.isKeyDown(rightKey);
    }

    private static boolean isShiftDown() {
        return isKeyDown(Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT);
    }

    private static boolean isControlDown() {
        return isKeyDown(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL);
    }

    private static boolean isAltDown() {
        return isKeyDown(Keyboard.KEY_LMENU, Keyboard.KEY_RMENU);
    }

    private static boolean hasFilterCountEditModifier() {
        return isAltDown() || isShiftDown() || isControlDown();
    }

    private static int getFilterCountStep() {
        boolean shift = isShiftDown();
        boolean control = isControlDown();

        if (shift && control) {
            return FILTER_COUNT_STEP_CTRL_SHIFT;
        } else if (control) {
            return FILTER_COUNT_STEP_CTRL;
        } else if (shift) {
            return FILTER_COUNT_STEP_SHIFT;
        } else {
            return FILTER_COUNT_STEP_NORMAL;
        }
    }

    private void alterStackCount(ItemStack stack, boolean increase) {
        long newCount = stack.getCount();

        if (isAltDown()) {
            if (increase) {
                newCount *= 2L;
            } else {
                newCount /= 2L;
            }
        } else {
            int step = getFilterCountStep();
            newCount += increase ? step : -step;
        }

        if (newCount > MAX_FILTER_COUNT) {
            newCount = MAX_FILTER_COUNT;
        } else if (newCount < 0) {
            newCount = 0;
        }

        stack.setCount((int) newCount);
    }
}
