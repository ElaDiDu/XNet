package mcjty.xnet.blocks.controller.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcjty.lib.base.StyleConfig;
import mcjty.lib.client.RenderHelper;
import mcjty.lib.container.GenericContainer;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.WindowManager;
import mcjty.lib.gui.events.ButtonEvent;
import mcjty.lib.gui.events.DefaultSelectionEvent;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.gui.widgets.Button;
import mcjty.lib.gui.widgets.Label;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.gui.widgets.TextField;
import mcjty.lib.tileentity.GenericEnergyStorageTileEntity;
import mcjty.lib.typed.TypedMap;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.FluidTools;
import mcjty.lib.varia.Logging;
import mcjty.xnet.XNet;
import mcjty.xnet.api.channels.Color;
import mcjty.xnet.api.channels.IChannelType;
import mcjty.xnet.api.helper.AbstractConnectorSettings;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.api.keys.SidedPos;
import mcjty.xnet.apiimpl.logic.LogicConnectorSettings;
import mcjty.xnet.apiimpl.logic.Sensor;
import mcjty.xnet.blocks.controller.TileEntityController;
import mcjty.xnet.blocks.generic.GenericXNetGuiContainer;
import mcjty.xnet.clientinfo.ChannelClientInfo;
import mcjty.xnet.clientinfo.ConnectedBlockClientInfo;
import mcjty.xnet.clientinfo.ConnectorClientInfo;
import mcjty.xnet.network.PacketGetChannels;
import mcjty.xnet.network.PacketGetConnectedBlocks;
import mcjty.xnet.network.XNetMessages;
import mcjty.xnet.setup.GuiProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import mcjty.lib.varia.ItemStackList;
import mcjty.xnet.apiimpl.fluids.FluidConnectorSettings;
import mcjty.xnet.compat.jei.XNetJeiFluidFilterCollector;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.apiimpl.items.ItemConnectorSettings;
import mcjty.xnet.compat.jei.XNetJeiItemFilterCollector;
import yalter.mousetweaks.api.MouseTweaksIgnore;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static mcjty.xnet.apiimpl.items.ItemConnectorSettings.*;
import static mcjty.xnet.blocks.controller.TileEntityController.*;
import static mcjty.xnet.logic.ChannelInfo.MAX_CHANNELS;

@MouseTweaksIgnore
public class GuiController extends GenericXNetGuiContainer<TileEntityController> {

    public static final String TAG_ENABLED = "enabled";
    public static final String TAG_NAME = "name";

    private WidgetList connectorList;
    private List<SidedPos> connectorPositions = new ArrayList<>();
    private int listDirty;
    private TextField searchBar;
    private String rememberedSearchText = "";

    private int delayedSelectedChannel = -1;
    private int delayedSelectedLine = -1;
    private SidedPos delayedSelectedConnector = null;

    private Panel channelEditPanel;
    private Panel connectorEditPanel;

    private ToggleButton channelButtons[] = new ToggleButton[MAX_CHANNELS];

    private SidedPos editingConnector = null;
    private int editingChannel = -1;

    private int showingChannel = -1;
    private SidedPos showingConnector = null;

    private static GuiController openController = null;

    private EnergyBar energyBar;
    private Button copyConnector = null;
    private Window xnetSideHelpWindow = null;

    // From server.
    public static List<ChannelClientInfo> fromServer_channels = null;
    public static List<ConnectedBlockClientInfo> fromServer_connectedBlocks = null;
    private boolean needsRefresh = true;

    public GuiController(TileEntityController controller, GenericContainer container) {
        super(XNet.instance, XNetMessages.INSTANCE, controller, container, GuiProxy.GUI_MANUAL_XNET, "controller");
        openController = this;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        openController = null;
    }

    private static List<ItemStack> mergeItemFilters(ItemStackList existingFilters, List<ItemStack> addedFilters) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack stack : existingFilters) {
            if (!stack.isEmpty()) {
                merged.add(stack.copy());
            }
        }

        for (ItemStack stack : addedFilters) {
            if (!stack.isEmpty()) {
                mergeExactItemFilter(merged, stack);
            }
        }

        return merged;
    }

    private static void mergeExactItemFilter(List<ItemStack> merged, ItemStack stack) {
        for (ItemStack existing : merged) {
            if (ItemStack.areItemsEqual(existing, stack)
                    && ItemStack.areItemStackTagsEqual(existing, stack)) {
                existing.setCount(Math.max(existing.getCount(), stack.getCount()));
                return;
            }
        }

        merged.add(stack.copy());
    }

    private static List<ItemStack> mergeFluidFilters(ItemStackList existingFilters, List<ItemStack> addedFilters) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack stack : existingFilters) {
            if (!stack.isEmpty()) {
                merged.add(stack.copy());
            }
        }

        for (ItemStack stack : addedFilters) {
            if (!stack.isEmpty()) {
                mergeExactItemFilter(merged, stack);
            }
        }

        return merged;
    }

    @Override
    protected void registerWindows(WindowManager mgr) {
        super.registerWindows(mgr);

        if (xnetSideHelpWindow == null) {
            xnetSideHelpWindow = createXNetSideHelpWindow();
        }

        mgr.addWindow(xnetSideHelpWindow);
    }

    @Override
    public List<Rectangle> getSideWindowBounds() {
        List<Rectangle> bounds = new ArrayList<>(super.getSideWindowBounds());

        if (xnetSideHelpWindow == null) {
            xnetSideHelpWindow = createXNetSideHelpWindow();
        }

        if (xnetSideHelpWindow.getToplevel() != null) {
            bounds.add(xnetSideHelpWindow.getToplevel().getBounds());
        }

        return bounds;
    }

    @Override
    public void initGui() {
        // JEI can temporarily replace this screen and then return to the same
        // GuiController instance. Preserve the selected connector and search text across re-init.
        SidedPos previousEditingConnector = editingConnector;
        int previousEditingChannel = editingChannel;
        String previousSearchText = searchBar == null ? rememberedSearchText : searchBar.getText();

        openController = this;
        xnetSideHelpWindow = null;

        window = new Window(this, tileEntity, XNetMessages.INSTANCE, new ResourceLocation(XNet.MODID, "gui/controller.gui"));
        super.initGui();

        initializeFields();
        setupEvents();

        rememberedSearchText = previousSearchText == null ? "" : previousSearchText;
        searchBar.setText(rememberedSearchText);

        if (previousEditingConnector != null && previousEditingChannel >= 0) {
            editingConnector = previousEditingConnector;
            editingChannel = previousEditingChannel;

            delayedSelectedConnector = previousEditingConnector;
            delayedSelectedChannel = previousEditingChannel;
            delayedSelectedLine = -1;
        } else {
            editingConnector = null;
            editingChannel = -1;
        }

        refresh();
        listDirty = 0;

        tileEntity.requestRfFromServer(XNet.MODID);
    }

    private void setupEvents() {
        window.event("searchbar", (source, params) -> {
            rememberedSearchText = searchBar.getText();
            needsRefresh = true;
        });

        for (int i = 0 ; i < MAX_CHANNELS ; i++) {
            String channel = "channel" + (i+1);
            int finalI = i;
            window.event(channel, (source, params) -> selectChannelEditor(finalI));
        }
    }

    private void initializeFields() {
        channelEditPanel = window.findChild("channeleditpanel");
        connectorEditPanel = window.findChild("connectoreditpanel");

        searchBar = window.findChild("searchbar");
        searchBar.setTooltips(
                TextFormatting.GREEN + "Search connected blocks and connector names",
                TextFormatting.WHITE + "Prefix " + TextFormatting.YELLOW + "!" + TextFormatting.WHITE + " to search connector names only",
                TextFormatting.WHITE + "Prefix " + TextFormatting.YELLOW + "?" + TextFormatting.WHITE + " to search filters",
                TextFormatting.WHITE + "Prefix " + TextFormatting.YELLOW + "%" + TextFormatting.WHITE + " to search channel <number>",
                TextFormatting.WHITE + "Prefix " + TextFormatting.YELLOW + "&" + TextFormatting.WHITE + " to search logical colors",
                TextFormatting.WHITE + "Prefix " + TextFormatting.YELLOW + "-" + TextFormatting.WHITE + " to exclude a search condition"
        );
        connectorList = window.findChild("connectors");

        connectorList.addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void doubleClick(Widget<?> parent, int index) {
                hilightSelectedContainer(index);
            }
        });

        for (int i = 0 ; i < MAX_CHANNELS ; i++) {
            String name = "channel" + (i+1);
            channelButtons[i] = window.findChild(name);
        }

        long currentRF = GenericEnergyStorageTileEntity.getCurrentRF();
        energyBar = window.findChild("energybar");
        energyBar.setMaxValue(tileEntity.getCapacity());
        energyBar.setValue(currentRF);
    }

    private void hilightSelectedContainer(int index) {
        if (index < 0 || index >= connectorPositions.size()) {
            return;
        }

        SidedPos sidedPos = connectorPositions.get(index);
        XNet.instance.clientInfo.hilightBlock(sidedPos.getPos(), System.currentTimeMillis() + 1000 * 5);
        Logging.message(mc.player, "The block is now highlighted");
        //mc.player.closeScreen();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (handleClipboard(keyCode)) {
            return;
        }
        if (handleKeyUpDown(keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void keyTypedFromEvent(char typedChar, int keyCode) {
        if (handleClipboard(keyCode)) {
            return;
        }
        if (handleKeyUpDown(keyCode)) {
            return;
        }
        super.keyTypedFromEvent(typedChar, keyCode);
    }

    private boolean handleKeyUpDown(int keyCode) {
        if (getSelectedChannel() == -1) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP) {
            int sel = connectorList.getSelected();
            if (sel > 0) {
                sel--;
                connectorList.setSelected(sel);
                selectConnectorEditor(connectorPositions.get(sel), getSelectedChannel());
            }
            return true;
        } else if (keyCode == Keyboard.KEY_DOWN) {
            int sel = connectorList.getSelected();
            if (sel != -1) {
                if (sel < connectorList.getChildCount() - 1) {
                    sel++;
                    connectorList.setSelected(sel);
                    selectConnectorEditor(connectorPositions.get(sel), getSelectedChannel());
                }
            }
            return true;
        }
        return false;
    }


    private boolean handleClipboard(int keyCode) {
        if (window.getTextFocus() instanceof TextField) {
            return false;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            if (keyCode == Keyboard.KEY_C) {
                if (getSelectedChannel() != -1) {
                    copyConnector();
                } else {
                    showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Nothing selected!");
                }
                return true;
            } else if (keyCode == Keyboard.KEY_V) {
                if (getSelectedChannel() != -1) {
                    pasteConnector();
                } else {
                    showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Nothing selected!");
                }
                return true;
            }
        }
        return false;
    }

    private void selectChannelEditor(int finalI) {
        editingChannel = -1;
        showingConnector = null;
        for (int j = 0 ; j < MAX_CHANNELS ; j++) {
            if (j != finalI) {
                channelButtons[j].setPressed(false);
                editingChannel = finalI;
            }
        }
    }

    private void removeConnector(SidedPos sidedPos) {
        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_REMOVECONNECTOR,
                TypedMap.builder()
                        .put(PARAM_CHANNEL, getSelectedChannel())
                        .put(PARAM_POS, sidedPos.getPos())
                        .put(PARAM_SIDE, sidedPos.getSide().ordinal())
                        .build());
        refresh();
    }

    private void createConnector(SidedPos sidedPos) {
        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_CREATECONNECTOR,
                TypedMap.builder()
                        .put(PARAM_CHANNEL, getSelectedChannel())
                        .put(PARAM_POS, sidedPos.getPos())
                        .put(PARAM_SIDE, sidedPos.getSide().ordinal())
                        .build());
        refresh();
    }

    private void removeChannel() {
        showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Really remove channel " + (getSelectedChannel() + 1) + "?", parent -> {
            sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_REMOVECHANNEL,
                    TypedMap.builder()
                            .put(PARAM_INDEX, getSelectedChannel())
                            .build());
            refresh();
        });
    }

    private void createChannel(String typeId) {
        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_CREATECHANNEL,
                TypedMap.builder()
                        .put(PARAM_INDEX, getSelectedChannel())
                        .put(PARAM_TYPE, typeId)
                        .build());
        refresh();
    }

    public void refresh() {
        fromServer_channels = null;
        fromServer_connectedBlocks = null;
        showingChannel = -1;
        showingConnector = null;
        needsRefresh = true;
        listDirty = 3;
        requestListsIfNeeded();
    }

    private void selectConnectorEditor(SidedPos sidedPos, int finalI) {
        editingConnector = sidedPos;
        selectChannelEditor(finalI);

        int line = findConnectorListIndex(sidedPos);
        if (line >= 0 && line < connectorList.getChildCount()) {
            connectorList.setSelected(line);
        }
    }

    private void restoreConnectorListSelectionIfCleared() {
        if (connectorList == null || editingConnector == null) {
            return;
        }

        if (connectorList.getSelected() != -1) {
            return;
        }

        int line = findConnectorListIndex(editingConnector);
        if (line >= 0 && line < connectorList.getChildCount()) {
            connectorList.setSelected(line);
        }
    }

    private void refreshChannelEditor() {
        if (!listsReady()) {
            return;
        }
        if (editingChannel != -1 && showingChannel != editingChannel) {
            showingChannel = editingChannel;
            channelButtons[editingChannel].setPressed(true);

            copyConnector = null;
            channelEditPanel.removeChildren();
            if (channelButtons[editingChannel].isPressed()) {
                ChannelClientInfo info = fromServer_channels.get(editingChannel);
                if (info != null) {
                    ChannelEditorPanel editor = new ChannelEditorPanel(channelEditPanel, mc, this, editingChannel);
                    editor.label("Channel " + (editingChannel + 1))
                            .shift(5)
                            .toggle(TAG_ENABLED, "Enable processing on this channel", info.isEnabled())
                            .shift(5)
                            .text(TAG_NAME, "Channel name", info.getChannelName(), 65);
                    info.getChannelSettings().createGui(editor);

                    Button remove = new Button(mc, this).setText("x")
                            .setTextOffset(0, -1)
                            .setTooltips("Remove this channel")
                            .setLayoutHint(new PositionalLayout.PositionalHint(151, 1, 9, 10))
                            .addButtonEvent(parent -> removeChannel());
                    channelEditPanel.addChild(remove);
                    editor.setState(info.getChannelSettings());

                    Button copyChannel = new Button(mc, this)
                            .setText("C")
                            .setTooltips("Copy this channel to", "the clipboard")
                            .setLayoutHint(new PositionalLayout.PositionalHint(134, 19, 25, 14))
                            .addButtonEvent(parent -> copyChannel());
                    channelEditPanel.addChild(copyChannel);

                    copyConnector = new Button(mc, this)
                            .setText("C")
                            .setTooltips("Copy this connector", "to the clipboard")
                            .setLayoutHint(new PositionalLayout.PositionalHint(114, 19, 25, 14))
                            .addButtonEvent(parent -> copyConnector());
                    channelEditPanel.addChild(copyConnector);

                } else {
                    ChoiceLabel type = new ChoiceLabel(mc, this)
                            .setLayoutHint(new PositionalLayout.PositionalHint(5, 3, 95, 14));
                    for (IChannelType channelType : XNet.xNetApi.getChannels().values()) {
                        type.addChoices(channelType.getID());       // Show names?
                    }
                    Button create = new Button(mc, this)
                            .setText("Create")
                            .setLayoutHint(new PositionalLayout.PositionalHint(100, 3, 53, 14))
                            .addButtonEvent(parent -> createChannel(type.getCurrentChoice()));

                    Button paste = new Button(mc, this)
                            .setText("Paste")
                            .setTooltips("Create a new channel", "from the clipboard")
                            .setLayoutHint(new PositionalLayout.PositionalHint(100, 17, 53, 14))
                            .addButtonEvent(parent -> pasteChannel());

                    channelEditPanel.addChild(type).addChild(create).addChild(paste);
                }
            }
        } else if (showingChannel != -1 && editingChannel == -1) {
            showingChannel = -1;
            channelEditPanel.removeChildren();
        }
    }

    public static void showMessage(Minecraft mc, Gui gui, WindowManager windowManager, String title) {
        showMessage(mc, gui, windowManager, title, null);
    }

    public static void showMessage(Minecraft mc, Gui gui, WindowManager windowManager, String title, ButtonEvent okEvent) {
        final int popupWidth = 200;
        final int popupHeight = 40;
        Panel ask = new Panel(mc, gui)
                .setLayout(new VerticalLayout())
                .setFilledBackground(0xff666666, 0xffaaaaaa)
                .setFilledRectThickness(1);
        GuiController controller = (GuiController) gui;
        int popupX = controller.guiLeft + (controller.xSize - popupWidth) / 2;
        int popupY = controller.guiTop + (controller.ySize - popupHeight) / 2 - 20;
        ask.setBounds(new Rectangle(popupX, popupY, popupWidth, popupHeight));

        Window askWindow = windowManager.createModalWindow(ask);
        ask.addChild(new Label(mc, gui).setText(title));
        Panel buttons = new Panel(mc, gui).setLayout(new HorizontalLayout()).setDesiredWidth(100).setDesiredHeight(18);
        if (okEvent != null) {
            buttons.addChild(new Button(mc, gui).setText("Cancel").addButtonEvent((parent -> {
                windowManager.closeWindow(askWindow);
            })));
            buttons.addChild(new Button(mc, gui).setText("OK").addButtonEvent(parent -> {
                windowManager.closeWindow(askWindow);
                okEvent.buttonClicked(parent);
            }));
        } else {
            buttons.addChild(new Button(mc, gui).setText("OK").addButtonEvent((parent -> {
                windowManager.closeWindow(askWindow);
            })));
        }
        ask.addChild(buttons);
    }

    private void copyConnector() {
        if (editingConnector != null) {
            sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_COPYCONNECTOR,
                    TypedMap.builder()
                            .put(PARAM_INDEX, getSelectedChannel())
                            .put(PARAM_POS, editingConnector.getPos())
                            .put(PARAM_SIDE, editingConnector.getSide().ordinal())
                            .build());
        }
    }


    private void copyChannel() {
        showMessage(mc, this, getWindowManager(), TextFormatting.GREEN + "Copied channel");
        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_COPYCHANNEL,
                TypedMap.builder()
                        .put(PARAM_INDEX, getSelectedChannel())
                        .build());
    }

    public static void showError(String error) {
        if (openController != null) {
            Minecraft mc = Minecraft.getMinecraft();
            showMessage(mc, openController, openController.getWindowManager(), TextFormatting.RED + error);
        }
    }

    public static void toClipboard(String json) {
        try {
            StringSelection selection = new StringSelection(json);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        } catch (Exception e) {
            showError("Error copying to clipboard!");
        }
    }

    private void pasteConnector() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String json = (String) clipboard.getData(DataFlavor.stringFlavor);
            if (json.length() > 26000) {
                showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Clipboard too large!");
                return;
            }
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(json).getAsJsonObject();
            String type = root.get("type").getAsString();
            IChannelType channelType = XNet.xNetApi.findType(type);
            if (channelType == null) {
                showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Unsupported channel type: " + type + "!");
                return;
            }
            sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_PASTECONNECTOR,
                    TypedMap.builder()
                            .put(PARAM_INDEX, getSelectedChannel())
                            .put(PARAM_POS, editingConnector.getPos())
                            .put(PARAM_SIDE, editingConnector.getSide().ordinal())
                            .put(PARAM_JSON, json)
                            .build());
            if (connectorList.getSelected() != -1) {
                delayedSelectedChannel = getSelectedChannel();
                delayedSelectedLine = connectorList.getSelected();
                delayedSelectedConnector = connectorPositions.get(connectorList.getSelected());
            }
            refresh();
        } catch (UnsupportedFlavorException e) {
            showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Clipboard does not contain connector!");
        } catch (Exception e) {
            showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Error reading from clipboard!");
        }
    }

    private void pasteChannel() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String json = (String) clipboard.getData(DataFlavor.stringFlavor);
            if (json.length() > 26000) {
                showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Clipboard too large!");
                return;
            }
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(json).getAsJsonObject();
            String type = root.get("type").getAsString();
            IChannelType channelType = XNet.xNetApi.findType(type);
            if (channelType == null) {
                showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Unsupported channel type: " + type + "!");
                return;
            }
            sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_PASTECHANNEL,
                    TypedMap.builder()
                            .put(PARAM_INDEX, getSelectedChannel())
                            .put(PARAM_JSON, json)
                            .build());
            refresh();
        } catch (UnsupportedFlavorException e) {
            showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Clipboard does not contain channel!");
        } catch (Exception e) {
            showMessage(mc, this, getWindowManager(), TextFormatting.RED + "Error reading from clipboard!");
        }
    }

    private ConnectorClientInfo findClientInfo(ChannelClientInfo info, SidedPos p) {
        for (ConnectorClientInfo connector : info.getConnectors().values()) {
            if (connector.getPos().equals(p)) {
                return connector;
            }
        }
        return null;
    }

    private void refreshConnectorEditor() {
        if (!listsReady()) {
            return;
        }
        if (editingConnector != null && !editingConnector.equals(showingConnector)) {
            showingConnector = editingConnector;
            connectorEditPanel.removeChildren();
            ChannelClientInfo info = fromServer_channels.get(editingChannel);
            if (info != null) {
                ConnectorClientInfo clientInfo = findClientInfo(info, editingConnector);
                if (clientInfo != null) {
                    EnumFacing side = clientInfo.getPos().getSide();
                    SidedConsumer sidedConsumer = new SidedConsumer(clientInfo.getConsumerId(), side.getOpposite());
                    ConnectorClientInfo connectorInfo = info.getConnectors().get(sidedConsumer);

                    Button remove = new Button(mc, this).setText("x")
                            .setTextOffset(0, -1)
                            .setTooltips("Remove this connector")
                            .setLayoutHint(new PositionalLayout.PositionalHint(151, 1, 9, 10))
                            .addButtonEvent(parent -> removeConnector(editingConnector));

                    ConnectorEditorPanel editor = new ConnectorEditorPanel(connectorEditPanel, mc, this, editingChannel, editingConnector);

                    IConnectorSettings connectorSettings = connectorInfo.getConnectorSettings();

                    connectorSettings.createGui(editor);
                    connectorEditPanel.addChild(remove);
                    editor.setState(connectorSettings);
                } else {
                    Button create = new Button(mc, this)
                            .setText("Create")
                            .setLayoutHint(new PositionalLayout.PositionalHint(85, 20, 60, 14))
                            .addButtonEvent(parent -> createConnector(editingConnector));
                    connectorEditPanel.addChild(create);

                    Button paste = new Button(mc, this)
                            .setText("Paste")
                            .setTooltips("Create a new connector", "from the clipboard")
                            .setLayoutHint(new PositionalLayout.PositionalHint(85, 40, 60, 14))
                            .addButtonEvent(parent -> pasteConnector());
                    connectorEditPanel.addChild(paste);
                }
            }
        } else if (showingConnector != null && editingConnector == null) {
            showingConnector = null;
            connectorEditPanel.removeChildren();
        }
    }



    private void requestListsIfNeeded() {
        if (fromServer_channels != null && fromServer_connectedBlocks != null) {
            return;
        }
        listDirty--;
        if (listDirty <= 0) {
            XNetMessages.INSTANCE.sendToServer(new PacketGetChannels(tileEntity.getPos()));
            XNetMessages.INSTANCE.sendToServer(new PacketGetConnectedBlocks(tileEntity.getPos()));
            listDirty = 10;
            showingChannel = -1;
            showingConnector = null;
        }
    }

    private int getSelectedChannel() {
        for (int i = 0 ; i < MAX_CHANNELS ; i++) {
            if (channelButtons[i].isPressed()) {
                return i;
            }
        }
        return -1;
    }

    private static final class SearchQuery {
        private String plainText = "";
        private final List<String> connectorSearches = new ArrayList<>();
        private final List<String> excludedConnectorSearches = new ArrayList<>();
        private final List<String> filterSearches = new ArrayList<>();
        private final List<String> excludedFilterSearches = new ArrayList<>();
        private int channelMask = 0;
        private int excludedChannelMask = 0;
        private final List<Integer> colorSearches = new ArrayList<>();
        private final List<Integer> excludedColorSearches = new ArrayList<>();
        private boolean anyColor = false;
        private boolean noColor = false;
        private boolean anyConnector = false;
        private boolean noConnector = false;
        private boolean invalid = false;
    }

    private int getSearchPrefixIndex(String token) {
        if (token.isEmpty()) {
            return -1;
        }
        char c = token.charAt(0);
        if (c == '!' || c == '?' || c == '%' || c == '&') {
            return 0;
        }
        if (c == '-' && token.length() > 1) {
            c = token.charAt(1);
            if (c == '!' || c == '?' || c == '%' || c == '&') {
                return 1;
            }
        }
        return -1;
    }

    private int getColorSearchMask(String value) {
        String search = value.toUpperCase(Locale.ROOT);
        int mask = 0;
        for (Color color : Color.values()) {
            if (color != Color.OFF && color.name().startsWith(search)) {
                mask |= 1 << color.ordinal();
            }
        }
        return mask;
    }

    private SearchQuery parseSearch(String selectedText) {
        SearchQuery query = new SearchQuery();
        if (selectedText.isEmpty()) {
            return query;
        }

        boolean usesAdvancedSyntax = false;
        for (int i = 0; i < selectedText.length(); i++) {
            if (i > 0 && !Character.isWhitespace(selectedText.charAt(i - 1))) {
                continue;
            }
            char c = selectedText.charAt(i);
            if (c == '?' || c == '%' || c == '&' || (c == '!' && i > 0)
                    || (c == '-' && i + 1 < selectedText.length()
                    && (selectedText.charAt(i + 1) == '!' || selectedText.charAt(i + 1) == '?'
                    || selectedText.charAt(i + 1) == '%' || selectedText.charAt(i + 1) == '&'))) {
                usesAdvancedSyntax = true;
                break;
            }
        }

        if (!usesAdvancedSyntax) {
            if (selectedText.startsWith("!")) {
                query.connectorSearches.add(selectedText.substring(1).trim());
            } else {
                query.plainText = selectedText;
            }
            return query;
        }

        String[] tokens = selectedText.split("\\s+");
        StringBuilder plain = new StringBuilder();

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int prefixIndex = getSearchPrefixIndex(token);

            if (prefixIndex == -1) {
                if (plain.length() > 0) {
                    plain.append(' ');
                }
                plain.append(token);
                continue;
            }

            boolean excluded = prefixIndex == 1;
            char prefix = token.charAt(prefixIndex);

            if (prefix == '!' || prefix == '?') {
                StringBuilder value = new StringBuilder(token.substring(prefixIndex + 1));
                while (i + 1 < tokens.length && getSearchPrefixIndex(tokens[i + 1]) == -1) {
                    if (value.length() > 0) {
                        value.append(' ');
                    }
                    value.append(tokens[++i]);
                }

                if (prefix == '!') {
                    (excluded ? query.excludedConnectorSearches : query.connectorSearches).add(value.toString());
                } else {
                    (excluded ? query.excludedFilterSearches : query.filterSearches).add(value.toString());
                }
            } else if (prefix == '%') {
                String value = token.substring(prefixIndex + 1);
                if (value.isEmpty()) {
                    if (excluded) {
                        query.noConnector = true;
                    } else {
                        query.anyConnector = true;
                    }
                    continue;
                }
                boolean numeric = true;
                for (int j = 0; j < value.length(); j++) {
                    numeric = value.charAt(j) >= '0' && value.charAt(j) <= '9';
                }
                if (!numeric) {
                    query.invalid = true;
                    continue;
                }

                try {
                    int channel = Integer.parseInt(value);
                    if (channel < 1 || channel > MAX_CHANNELS) {
                        query.invalid = true;
                    } else if (excluded) {
                        query.excludedChannelMask |= 1 << (channel - 1);
                    } else {
                        query.channelMask |= 1 << (channel - 1);
                    }
                } catch (NumberFormatException e) {
                    query.invalid = true;
                }
            } else if (prefix == '&') {
                String value = token.substring(prefixIndex + 1);
                if (value.isEmpty()) {
                    if (excluded) {
                        query.noColor = true;
                    } else {
                        query.anyColor = true;
                    }
                    continue;
                }

                int mask = getColorSearchMask(value);
                if (mask == 0) {
                    query.invalid = true;
                } else if (excluded) {
                    query.excludedColorSearches.add(mask);
                } else {
                    query.colorSearches.add(mask);
                }
            }
        }
        query.plainText = plain.toString();
        return query;
    }

    private boolean matchesFilterStack(ItemStack stack, String search, boolean fluid) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (search.isEmpty()) {
            return true;
        }

        String displayName = stack.getDisplayName();
        if (displayName != null && displayName.toLowerCase().contains(search)) {
            return true;
        }

        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName != null && registryName.toString().toLowerCase().contains(search)) {
            return true;
        }
        if (fluid) {
            FluidStack fluidStack = FluidTools.convertBucketToFluid(stack);
            if (fluidStack != null && fluidStack.getFluid() != null) {
                if (fluidStack.getFluid().getName().toLowerCase().contains(search)) {
                    return true;
                }
                String localizedName = fluidStack.getLocalizedName();
                return localizedName != null && localizedName.toLowerCase().contains(search);
            }
        }
        return false;
    }

    private boolean matchesConfiguredFilter(IConnectorSettings settings, String search) {
        if (settings instanceof ItemConnectorSettings) {
            for (ItemStack stack : ((ItemConnectorSettings) settings).getFilters()) {
                if (matchesFilterStack(stack, search, false)) {
                    return true;
                }
            }
            return false;
        }

        if (settings instanceof FluidConnectorSettings) {
            for (ItemStack stack : ((FluidConnectorSettings) settings).getFilters()) {
                if (matchesFilterStack(stack, search, true)) {
                    return true;
                }
            }
            return false;
        }

        if (settings instanceof LogicConnectorSettings) {
            LogicConnectorSettings logic = (LogicConnectorSettings) settings;
            if (logic.getLogicMode() != LogicConnectorSettings.LogicMode.SENSOR) {
                return false;
            }
            for (Sensor sensor : logic.getSensors()) {
                if (sensor.getSensorMode() == Sensor.SensorMode.ITEM) {
                    if (matchesFilterStack(sensor.getFilter(), search, false)) {
                        return true;
                    }
                } else if (sensor.getSensorMode() == Sensor.SensorMode.FLUID) {
                    if (matchesFilterStack(sensor.getFilter(), search, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int getConfiguredColorMask(IConnectorSettings settings) {
        int mask = 0;

        if (settings instanceof AbstractConnectorSettings) {
            mask |= ((AbstractConnectorSettings) settings).getColorsMask();
        }
        if (settings instanceof LogicConnectorSettings) {
            LogicConnectorSettings logic = (LogicConnectorSettings) settings;
            if (logic.getLogicMode() == LogicConnectorSettings.LogicMode.SENSOR) {
                for (Sensor sensor : logic.getSensors()) {
                    Color outputColor = sensor.getOutputColor();
                    if (sensor.getSensorMode() != Sensor.SensorMode.OFF && outputColor != null && outputColor != Color.OFF) {
                        mask |= 1 << outputColor.ordinal();
                    }
                }
            }
        }
        return mask;
    }

    private boolean matchesSearch(SearchQuery query, String blockName, String connectorName, SidedPos sidedPos, @Nullable ConnectorClientInfo[] rowConnectors) {
        if (query.invalid) {
            return false;
        }

        String lowerConnectorName = "";
        if (!query.connectorSearches.isEmpty() || !query.excludedConnectorSearches.isEmpty() || !query.plainText.isEmpty()) {
            lowerConnectorName = connectorName == null ? "" : connectorName.toLowerCase();
        }

        for (String connectorSearch : query.connectorSearches) {
            if (connectorSearch.isEmpty()) {
                if (lowerConnectorName.isEmpty()) {
                    return false;
                }
            } else if (!lowerConnectorName.contains(connectorSearch)) {
                return false;
            }
        }

        for (String connectorSearch : query.excludedConnectorSearches) {
            if (connectorSearch.isEmpty()) {
                if (!lowerConnectorName.isEmpty()) {
                    return false;
                }
            } else if (lowerConnectorName.contains(connectorSearch)) {
                return false;
            }
        }

        if (!query.plainText.isEmpty()) {
            String lowerBlockName = blockName == null ? "" : blockName.toLowerCase();
            if (!lowerBlockName.contains(query.plainText) && !lowerConnectorName.contains(query.plainText)) {
                return false;
            }
        }

        if (rowConnectors == null) {
            return true;
        }

        for (int i = 0; i < MAX_CHANNELS; i++) {
            ChannelClientInfo info = fromServer_channels.get(i);
            rowConnectors[i] = info == null ? null : findClientInfo(info, sidedPos);
        }

        if (query.anyConnector) {
            boolean found = false;
            for (ConnectorClientInfo connector : rowConnectors) {
                if (connector != null) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        if (query.noConnector) {
            for (ConnectorClientInfo connector : rowConnectors) {
                if (connector != null) {
                    return false;
                }
            }
        }

        for (int i = 0; i < MAX_CHANNELS; i++) {
            if ((query.channelMask & (1 << i)) != 0 && rowConnectors[i] == null) {
                return false;
            }
            if ((query.excludedChannelMask & (1 << i)) != 0 && rowConnectors[i] != null) {
                return false;
            }
        }

        int scopeMask = query.channelMask == 0 ? (1 << MAX_CHANNELS) - 1 : query.channelMask;

        boolean hasPositiveConnectorConditions = !query.filterSearches.isEmpty() || query.anyColor || !query.colorSearches.isEmpty();
        if (hasPositiveConnectorConditions) {
            boolean found = false;

            for (int i = 0; i < MAX_CHANNELS; i++) {
                if ((scopeMask & (1 << i)) == 0 || rowConnectors[i] == null) {
                    continue;
                }
                IConnectorSettings settings = rowConnectors[i].getConnectorSettings();
                boolean matches = true;

                for (String filterSearch : query.filterSearches) {
                    if (!matchesConfiguredFilter(settings, filterSearch)) {
                        matches = false;
                        break;
                    }
                }
                if (matches && (query.anyColor || !query.colorSearches.isEmpty())) {
                    int configuredColors = getConfiguredColorMask(settings);

                    if (query.anyColor && configuredColors == 0) {
                        matches = false;
                    }

                    if (matches) {
                        for (int colorSearch : query.colorSearches) {
                            if ((configuredColors & colorSearch) == 0) {
                                matches = false;
                                break;
                            }
                        }
                    }
                }
                if (matches) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        if (!query.excludedFilterSearches.isEmpty() || query.noColor || !query.excludedColorSearches.isEmpty()) {
            for (int i = 0; i < MAX_CHANNELS; i++) {
                if ((scopeMask & (1 << i)) == 0 || rowConnectors[i] == null) {
                    continue;
                }
                IConnectorSettings settings = rowConnectors[i].getConnectorSettings();

                for (String filterSearch : query.excludedFilterSearches) {
                    if (matchesConfiguredFilter(settings, filterSearch)) {
                        return false;
                    }
                }
                if (query.noColor || !query.excludedColorSearches.isEmpty()) {
                    int configuredColors = getConfiguredColorMask(settings);
                    if (query.noColor && configuredColors != 0) {
                        return false;
                    }
                    for (int colorSearch : query.excludedColorSearches) {
                        if ((configuredColors & colorSearch) != 0) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void populateList() {
        if (!listsReady()) {
            return;
        }
        if (!needsRefresh) {
            return;
        }
        needsRefresh = false;

        connectorList.removeChildren();
        connectorPositions.clear();

        int sel = connectorList.getSelected();
        BlockPos prevPos = null;

        String selectedText = searchBar.getText().trim().toLowerCase();
        SearchQuery searchQuery = parseSearch(selectedText);
        boolean searchNeedsConnectorData = searchQuery.anyConnector || searchQuery.noConnector
                || searchQuery.channelMask != 0 || searchQuery.excludedChannelMask != 0
                || !searchQuery.filterSearches.isEmpty() || !searchQuery.excludedFilterSearches.isEmpty()
                || searchQuery.anyColor || searchQuery.noColor || !searchQuery.colorSearches.isEmpty() || !searchQuery.excludedColorSearches.isEmpty();
        ConnectorClientInfo[] rowConnectors = searchNeedsConnectorData ? new ConnectorClientInfo[MAX_CHANNELS] : null;

        for (ConnectedBlockClientInfo connectedBlock : fromServer_connectedBlocks) {
            SidedPos sidedPos = connectedBlock.getPos();
            BlockPos coordinate = sidedPos.getPos();
            String name = connectedBlock.getName();
            String blockUnlocName = connectedBlock.getBlockUnlocName();
            String blockName = I18n.format(blockUnlocName).trim();

            int color = StyleConfig.colorTextInListNormal;

            if (!matchesSearch(searchQuery, blockName, name, sidedPos, rowConnectors)) {
                continue;
            }
            Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout().setHorizontalMargin(0).setSpacing(0));

            BlockRender br;
            if (coordinate.equals(prevPos)) {
                br = new BlockRender(mc, this);
            } else {
                br = new BlockRender(mc, this).setRenderItem(connectedBlock.getConnectedBlock());
                prevPos = coordinate;
            }
            br.setUserObject("block");
            panel.addChild(br);
            if (!name.isEmpty()) {
                br.setTooltips(TextFormatting.GREEN + "Connector: " + TextFormatting.WHITE + name,
                        TextFormatting.GREEN + "Block: " + TextFormatting.WHITE + blockName,
                        TextFormatting.GREEN + "Position: " + TextFormatting.WHITE + BlockPosTools.toString(coordinate),
                        TextFormatting.WHITE + "(doubleclick to highlight)");
            } else {
                br.setTooltips(TextFormatting.GREEN + "Block: " + TextFormatting.WHITE + blockName,
                        TextFormatting.GREEN + "Position: " + TextFormatting.WHITE + BlockPosTools.toString(coordinate),
                        TextFormatting.WHITE + "(doubleclick to highlight)");
            }

            panel.addChild(new Label(mc, this).setText(sidedPos.getSide().getName().substring(0, 1).toUpperCase()).setColor(color).setDesiredWidth(18));
            for (int i = 0 ; i < MAX_CHANNELS ; i++) {
                Button but = new Button(mc, this).setDesiredWidth(14);
                ChannelClientInfo info = fromServer_channels.get(i);
                if (info != null) {
                    ConnectorClientInfo clientInfo = rowConnectors == null ? findClientInfo(info, sidedPos) : rowConnectors[i];
                    if (clientInfo != null) {
                        IndicatorIcon icon = clientInfo.getConnectorSettings().getIndicatorIcon();
                        if (icon != null) {
                            but.setImage(icon.getImage(), icon.getU(), icon.getV(), icon.getIw(), icon.getIh());
                        }
                        String indicator = clientInfo.getConnectorSettings().getIndicator();
                        but.setText(indicator != null ? indicator : "");
                    }
                }
                int finalI = i;
                but.addButtonEvent(parent -> selectConnectorEditor(sidedPos, finalI));
                panel.addChild(but);
            }
            connectorList.addChild(panel);
            connectorPositions.add(sidedPos);
        }

        if (sel >= connectorList.getChildCount()) {
            sel = connectorList.getChildCount() - 1;
        }

        connectorList.setSelected(sel);

        if (delayedSelectedChannel != -1 && delayedSelectedConnector != null) {
            int line = findConnectorListIndex(delayedSelectedConnector);

            if (line >= 0 && line < connectorList.getChildCount()) {
                connectorList.setSelected(line);
            } else if (delayedSelectedLine >= 0 && delayedSelectedLine < connectorList.getChildCount()) {
                connectorList.setSelected(delayedSelectedLine);
            }

            selectConnectorEditor(delayedSelectedConnector, delayedSelectedChannel);
        }

        delayedSelectedChannel = -1;
        delayedSelectedLine = -1;
        delayedSelectedConnector = null;
    }

    private boolean listsReady() {
        return fromServer_channels != null && fromServer_connectedBlocks != null;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int x1, int x2) {
        requestListsIfNeeded();
        populateList();
        restoreConnectorListSelectionIfCleared();
        refreshChannelEditor();
        refreshConnectorEditor();
        if (listsReady() && copyConnector != null && editingChannel != -1) {
            ChannelClientInfo info = fromServer_channels.get(editingChannel);
            ConnectorClientInfo clientInfo = findClientInfo(info, editingConnector);
            copyConnector.setEnabled(clientInfo != null);
        }
        if (fromServer_channels != null) {
            for (int i = 0; i < MAX_CHANNELS; i++) {
                String channel = String.valueOf(i + 1);
                ChannelClientInfo info = fromServer_channels.get(i);
                if (info != null) {
                    IndicatorIcon icon = info.getChannelSettings().getIndicatorIcon();
                    if (icon != null) {
                        channelButtons[i].setImage(icon.getImage(), icon.getU(), icon.getV(), icon.getIw(), icon.getIh());
                    }
                    String indicator = info.getChannelSettings().getIndicator();
                    if (indicator != null) {
                        channelButtons[i].setText(indicator + channel);
                    } else {
                        channelButtons[i].setText(channel);
                    }
                } else {
                    channelButtons[i].setImage(null, 0, 0, 0, 0);
                    channelButtons[i].setText(channel);
                }
            }
        }
        drawWindow();
        int channel = getSelectedChannel();
        if (channel != -1) {
            int x = (int) window.getToplevel().getBounds().getX();
            int y = (int) window.getToplevel().getBounds().getY();
            RenderHelper.drawVerticalGradientRect(x+channel * 14 + 41, y+22, x+channel * 14 + 41+12, y+230, 0x33aaffff, 0x33aaffff);
        }
        long currentRF = GenericEnergyStorageTileEntity.getCurrentRF();
        energyBar.setValue(currentRF);
        tileEntity.requestRfFromServer(XNet.MODID);
    }

    @Override
    protected void drawStackTooltips(int mouseX, int mouseY) {
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        Widget<?> widget = window.getToplevel().getWidgetAtPosition(x, y);
        if (widget instanceof BlockRender) {
            if ("block".equals(widget.getUserObject())) {
                //System.out.println("GuiController.drawStackTooltips");
                return;     // Don't do the normal tooltip rendering
            }
        }
        super.drawStackTooltips(mouseX, mouseY);
    }

    public void sendStackAsFilter(@Nonnull ItemStack stack)
    {
        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_ADDTOFILTER,
                TypedMap.builder()
                        .put(PARAM_CHANNEL, getSelectedChannel())
                        .put(PARAM_POS, editingConnector.getPos())
                        .put(PARAM_SIDE, editingConnector.getSide().ordinal())
                        .put(PARAM_STACK, stack)
                        .build());
        refresh();
    }

    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int mouseButton, ClickType type)
    {
        if (slotIn != null && type == ClickType.QUICK_MOVE && editingConnector != null && slotIn.getHasStack())
        {
            ItemStack stack = slotIn.getStack();
            sendStackAsFilter(stack);
        }
        else
            super.handleMouseClick(slotIn, slotId, mouseButton, type);
    }

    @Nullable
    private ItemConnectorSettings getEditingItemConnectorSettings() {
        if (!listsReady()) {
            return null;
        }

        if (editingConnector == null || editingChannel < 0) {
            return null;
        }

        ChannelClientInfo info = fromServer_channels.get(editingChannel);
        if (info == null) {
            return null;
        }

        ConnectorClientInfo clientInfo = findClientInfo(info, editingConnector);
        if (clientInfo == null) {
            return null;
        }

        IConnectorSettings settings = clientInfo.getConnectorSettings();
        if (!(settings instanceof ItemConnectorSettings)) {
            return null;
        }

        return (ItemConnectorSettings) settings;
    }

    @Nullable
    private FluidConnectorSettings getEditingFluidConnectorSettings() {
        if (!listsReady()) {
            return null;
        }

        if (editingConnector == null || editingChannel < 0) {
            return null;
        }

        ChannelClientInfo info = fromServer_channels.get(editingChannel);
        if (info == null) {
            return null;
        }

        ConnectorClientInfo clientInfo = findClientInfo(info, editingConnector);
        if (clientInfo == null) {
            return null;
        }

        IConnectorSettings settings = clientInfo.getConnectorSettings();
        if (!(settings instanceof FluidConnectorSettings)) {
            return null;
        }

        return (FluidConnectorSettings) settings;
    }

    public boolean canSetJeiRecipeFilters() {
        return getEditingItemConnectorSettings() != null
                || getEditingFluidConnectorSettings() != null;
    }

    @Nullable
    public ItemConnectorSettings.ItemMode getJeiRecipeFilterItemMode() {
        ItemConnectorSettings settings = getEditingItemConnectorSettings();
        return settings == null ? null : settings.getItemMode();
    }

    @Nullable
    public FluidConnectorSettings.FluidMode getJeiRecipeFilterFluidMode() {
        FluidConnectorSettings settings = getEditingFluidConnectorSettings();
        return settings == null ? null : settings.getFluidMode();
    }

    public int getJeiRecipeFilterLimit() {
        if (getEditingItemConnectorSettings() != null) {
            return ItemConnectorSettings.FILTER_SIZE;
        }

        if (getEditingFluidConnectorSettings() != null) {
            return FluidConnectorSettings.FILTER_SIZE;
        }

        return 0;
    }

    public void setJeiRecipeFilters(XNetJeiItemFilterCollector.Result result) {
        List<ItemStack> addedFilters = result.getFilters();
        ItemConnectorSettings settings = getEditingItemConnectorSettings();

        if (settings == null) {
            showError("Select an item connector first!");
            return;
        }

        List<ItemStack> filters = mergeItemFilters(settings.getFilters(), addedFilters);

        if (addedFilters.isEmpty()) {
            showError(result.isOutputs() ? "Recipe has no item outputs!" : "Recipe has no item inputs!");
            return;
        }

        if (filters.size() > ItemConnectorSettings.FILTER_SIZE) {
            showError("Recipe needs " + filters.size()
                    + " filters after merging, but this connector supports "
                    + ItemConnectorSettings.FILTER_SIZE + "!");
            return;
        }

        TypedMap.Builder builder = TypedMap.builder()
                .put(PARAM_CHANNEL, editingChannel)
                .put(PARAM_POS, editingConnector.getPos())
                .put(PARAM_SIDE, editingConnector.getSide().ordinal());

        putString(builder, TAG_MODE, settings.getItemMode().name().toLowerCase(Locale.ROOT));
        putString(builder, TAG_STACK, settings.getStackMode().name().toLowerCase(Locale.ROOT));
        putString(builder, TAG_EXTRACT, settings.getExtractMode().name().toLowerCase(Locale.ROOT));
        putString(builder, TAG_SPEED, Integer.toString(settings.getSpeed() * 5));

        putBoolean(builder, TAG_BLACKLIST, settings.isBlacklist());
        putBoolean(builder, TAG_OREDICT, settings.isOredictMode());

        if (result.isAdvanced()) {
            putBoolean(builder, TAG_COUNTMODE,
                    settings.getItemMode() == ItemConnectorSettings.ItemMode.INS || settings.isCountMode());

            putBoolean(builder, TAG_META, settings.isMetaMode() || result.needsMeta());
            putBoolean(builder, TAG_NBT, settings.isNbtMode() || result.needsNbt());
        } else {
            putBoolean(builder, TAG_COUNTMODE, settings.isCountMode());
            putBoolean(builder, TAG_META, settings.isMetaMode());
            putBoolean(builder, TAG_NBT, settings.isNbtMode());
        }

        if (settings.getCount() != null) {
            putInteger(builder, TAG_COUNT, settings.getCount());
        }

        if (settings.getExtractAmountSetting() != null) {
            putInteger(builder, TAG_EXTRACT_AMOUNT, settings.getExtractAmountSetting());
        }

        putInteger(builder, TAG_PRIORITY, settings.getPriority());

        if (settings.getSlot() != null) {
            putInteger(builder, TAG_SLOT, settings.getSlot());
        }

        for (int i = 0; i < ItemConnectorSettings.FILTER_SIZE; i++) {
            ItemStack stack = i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY;
            putItemStack(builder, TAG_FILTER + i, stack);
        }

        rememberSelectedConnectorAfterRefresh();

        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_UPDATECONNECTOR, builder.build());
        refresh();
    }

    public void setJeiFluidRecipeFilters(XNetJeiFluidFilterCollector.Result result) {
        List<ItemStack> addedFilters = result.getFilters();
        FluidConnectorSettings settings = getEditingFluidConnectorSettings();

        if (settings == null) {
            showError("Select a fluid connector first!");
            return;
        }

        List<ItemStack> filters = mergeFluidFilters(settings.getFilters(), addedFilters);

        if (addedFilters.isEmpty()) {
            showError(result.isOutputs() ? "Recipe has no fluid outputs!" : "Recipe has no fluid inputs!");
            return;
        }

        if (filters.size() > FluidConnectorSettings.FILTER_SIZE) {
            showError("Recipe needs " + filters.size()
                    + " filters after merging, but this connector supports "
                    + FluidConnectorSettings.FILTER_SIZE + "!");
            return;
        }

        TypedMap.Builder builder = TypedMap.builder()
                .put(PARAM_CHANNEL, editingChannel)
                .put(PARAM_POS, editingConnector.getPos())
                .put(PARAM_SIDE, editingConnector.getSide().ordinal());

        putString(builder, FluidConnectorSettings.TAG_MODE,
                settings.getFluidMode().name().toLowerCase(Locale.ROOT));
        putString(builder, FluidConnectorSettings.TAG_SPEED,
                Integer.toString(settings.getSpeed() * 10));

        putString(builder, FluidConnectorSettings.TAG_EXTRACT,
                settings.getExtractMode().name().toLowerCase(Locale.ROOT));
        putString(builder, FluidConnectorSettings.TAG_AMOUNTMODE,
                settings.getAmountMode().name().toLowerCase(Locale.ROOT));

        putBoolean(builder, FluidConnectorSettings.TAG_BLACKLIST, settings.isBlacklist());
        putInteger(builder, FluidConnectorSettings.TAG_PRIORITY, settings.getPriority());

        if (settings.getRate() != null) {
            putInteger(builder, FluidConnectorSettings.TAG_RATE, settings.getRate());
        }

        if (settings.getMinmax() != null) {
            putInteger(builder, FluidConnectorSettings.TAG_MINMAX, settings.getMinmax());
        }

        if (settings.getExtractTank() != null) {
            putInteger(builder, FluidConnectorSettings.TAG_TANK, settings.getExtractTank());
        }

        for (int i = 0; i < FluidConnectorSettings.FILTER_SIZE; i++) {
            ItemStack stack = i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY;
            putItemStack(builder, FluidConnectorSettings.TAG_FILTER + i, stack);
        }

        rememberSelectedConnectorAfterRefresh();

        sendServerCommand(XNetMessages.INSTANCE, TileEntityController.CMD_UPDATECONNECTOR, builder.build());
        refresh();
    }

    private static void putString(TypedMap.Builder builder, String name, String value) {
        builder.put(new Key<>(name, Type.STRING), value);
    }

    private static void putBoolean(TypedMap.Builder builder, String name, boolean value) {
        builder.put(new Key<>(name, Type.BOOLEAN), value);
    }

    private static void putInteger(TypedMap.Builder builder, String name, int value) {
        builder.put(new Key<>(name, Type.INTEGER), value);
    }

    private static void putItemStack(TypedMap.Builder builder, String name, ItemStack value) {
        builder.put(new Key<>(name, Type.ITEMSTACK), value);
    }

    private void rememberSelectedConnectorAfterRefresh() {
        if (editingConnector == null || editingChannel < 0) {
            return;
        }

        delayedSelectedChannel = editingChannel;
        delayedSelectedConnector = editingConnector;
        delayedSelectedLine = findConnectorListIndex(editingConnector);
    }

    private int findConnectorListIndex(SidedPos sidedPos) {
        for (int i = 0; i < connectorPositions.size(); i++) {
            if (sidedPos.equals(connectorPositions.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private Window createXNetSideHelpWindow() {
        Button jeiHelpButton = new Button(mc, this)
                .setText("J")
                .setLayoutHint(new PositionalLayout.PositionalHint(1, 1, 16, 16))
                .setTooltips(
                        TextFormatting.GREEN + "JEI recipe fill",
                        "",
                        TextFormatting.YELLOW + "Selected INS connector",
                        TextFormatting.WHITE + "  + adds inputs to filters",
                        "",
                        TextFormatting.YELLOW + "Selected EXT connector",
                        TextFormatting.WHITE + "  + adds outputs to filters",
                        "",
                        TextFormatting.YELLOW + "Normal fill (+)",
                        TextFormatting.WHITE + "  Uses 1x representative stacks",
                        "",
                        TextFormatting.YELLOW + "Advanced item fill (Shift +)",
                        TextFormatting.WHITE + "  Uses recipe stack counts",
                        TextFormatting.WHITE + "  Enables Meta/NBT if needed",
                        TextFormatting.WHITE + "  INS also forces Count mode ON"
                );

        Button filterHelpButton = new Button(mc, this)
                .setText("F")
                .setLayoutHint(new PositionalLayout.PositionalHint(1, 19, 16, 16))
                .setTooltips(
                        TextFormatting.GREEN + "Filter count controls",
                        TextFormatting.YELLOW + "Mouse wheel / click",
                        TextFormatting.WHITE + "  Shift: +/- 10",
                        TextFormatting.WHITE + "  Ctrl: +/- 100",
                        TextFormatting.WHITE + "  Ctrl + Shift: +/- 1000",
                        TextFormatting.WHITE + "  Alt: double / halve"
                );

        Panel sidePanel = new Panel(mc, this)
                .setLayout(new PositionalLayout())
                .addChild(jeiHelpButton)
                .addChild(filterHelpButton);

        int sideLeft = guiLeft + xSize;

        // McJtyLib's [?]/[s] side window is centered here:
        int mcjtySideTop = guiTop + (ySize - 20) / 2 - 8;

        // Put [J]/[F] directly above it.
        int sideTop = mcjtySideTop - 40;

        sidePanel.setBounds(new Rectangle(sideLeft, sideTop, 20, 40));

        return new Window(this, sidePanel);
    }
}
