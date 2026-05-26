package com.github.singularityme.gui;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import com.github.singularityme.tile.TileSingularityStorageBus;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;

/**
 * GUI for the Singularity Storage Bus.
 *
 * <p>
 * Mirrors AE2's {@code GuiStorageBus}: 7 rows × 9 columns of ghost filter slots (CAPACITY-gated),
 * clear/partition/rwMode/extractionMode/storageFilter/fuzzyMode buttons, and a priority tab.
 *
 * <p>
 * GUI height is 251px (same as AE2 StorageBus). Slot Y starts at 29 (AE2: yo=-133, 9*18+yo=29).
 */
public class GuiSingularityStorageBus extends GuiUpgradeable {

    /** Max columns per row. */
    private static final int COLS = 9;
    /** Max rows (5 CAPACITY cards → 7 rows × 9 = 63 slots). */
    private static final int MAX_ROWS = 7;

    /** All 63 possible virtual slots; only the first (18 + 9*capacity) are shown. */
    protected final VirtualMEPhantomSlot[] virtualSlots = new VirtualMEPhantomSlot[63];

    private GuiImgButton btnClear;
    private GuiImgButton btnPartition;
    private GuiImgButton btnRwMode;
    private GuiImgButton btnExtractionMode;
    private GuiImgButton btnStorageFilter;
    private GuiTabButton btnPriority;

    public GuiSingularityStorageBus(final InventoryPlayer ip, final TileSingularityStorageBus te) {
        super(new ContainerSingularityStorageBus(ip, te));
        this.ySize = 251;
    }

    @Override
    public void initGui() {
        super.initGui();
        initVirtualSlots();
    }

    private void initVirtualSlots() {
        final ContainerSingularityStorageBus c = (ContainerSingularityStorageBus) this.inventorySlots;
        final appeng.tile.inventory.IAEStackInventory inputInv = c.storageBus.getAEInventoryByName(StorageName.CONFIG);
        for (int row = 0; row < MAX_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                final int idx = row * COLS + col;
                final VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                    8 + 18 * col,
                    29 + 18 * row,
                    inputInv,
                    idx,
                    this::acceptType);
                this.virtualSlots[idx] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    @Override
    protected void addButtons() {
        btnClear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        btnPartition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.WRENCH);
        btnRwMode = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 48,
            Settings.ACCESS,
            AccessRestriction.READ_WRITE);
        btnExtractionMode = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 68,
            Settings.EXTRACTION_MODE,
            ExtractionMode.LOOSE);
        btnStorageFilter = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 88,
            Settings.STORAGE_FILTER,
            StorageFilter.EXTRACTABLE_ONLY);
        this.fuzzyMode = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 108,
            Settings.FUZZY_MODE,
            FuzzyMode.IGNORE_ALL);
        btnPriority = new GuiTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            GuiText.Priority.getLocal(),
            itemRender);

        this.buttonList.add(btnStorageFilter);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(btnRwMode);
        this.buttonList.add(btnExtractionMode);
        this.buttonList.add(btnPartition);
        this.buttonList.add(btnClear);
        this.buttonList.add(btnPriority);
    }

    @Override
    protected void handleButtonVisibility() {
        final ContainerSingularityStorageBus c = (ContainerSingularityStorageBus) this.inventorySlots;
        final int capacity = c.storageBus.getInstalledUpgrades(Upgrades.CAPACITY);
        final int hasFuzzy = c.storageBus.getInstalledUpgrades(Upgrades.FUZZY);

        if (btnClear != null) btnClear.setVisibility(true);
        if (btnPartition != null) btnPartition.setVisibility(true);
        if (btnRwMode != null) btnRwMode.setVisibility(true);
        if (btnExtractionMode != null) btnExtractionMode.setVisibility(true);
        if (btnStorageFilter != null) btnStorageFilter.setVisibility(true);
        if (this.fuzzyMode != null) this.fuzzyMode.setVisibility(hasFuzzy > 0);
        // btnPriority is a GuiTabButton — always visible, no setVisibility needed

        // Slot visibility: 18 base + 9 per CAPACITY card
        final int activeSlots = 18 + 9 * capacity;
        for (int i = 0; i < virtualSlots.length; i++) {
            if (virtualSlots[i] != null) {
                virtualSlots[i].setHidden(i >= activeSlots);
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        final ContainerSingularityStorageBus c = (ContainerSingularityStorageBus) this.inventorySlots;

        if (btnRwMode != null) btnRwMode.set(c.accessMode);
        if (btnStorageFilter != null) btnStorageFilter.set(c.storageFilter);
        if (btnExtractionMode != null) {
            btnExtractionMode.set(c.extractionMode);
            btnExtractionMode.setEnabled(c.accessMode == AccessRestriction.READ_WRITE);
        }
        if (this.fuzzyMode != null) this.fuzzyMode.set(c.fzMode);
        if (btnPartition != null) btnPartition.set(ActionItems.WRENCH);
        if (btnClear != null) btnClear.set(ActionItems.CLOSE);

        this.fontRendererObj.drawString("P: " + c.getPriorityValue(), 8, 6, 0x404040);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        final ContainerSingularityStorageBus c = (ContainerSingularityStorageBus) this.inventorySlots;
        final int capacity = c.storageBus.getInstalledUpgrades(Upgrades.CAPACITY);
        final int activeRows = 2 + capacity; // base 2 rows + 1 per CAPACITY card, max 7
        for (int i = 0; i < MAX_ROWS; i++) {
            if (i < activeRows) {
                // Active row: UV(7,28), 162×18
                this.drawTexturedModalRect(offsetX + 7, offsetY + 28 + 18 * i, 7, 28, 162, 18);
            } else {
                // Inactive row: UV(7,46), 162×18
                this.drawTexturedModalRect(offsetX + 7, offsetY + 28 + 18 * i, 7, 46, 162, 18);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);
        final boolean backwards = Mouse.isButtonDown(1);
        try {
            if (btn == btnPartition) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("ACTIONS", "WRENCH"));
            } else if (btn == btnClear) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("ACTIONS", "CLOSE"));
            } else if (btn == btnPriority) {
                NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
            } else if (btn == btnRwMode) {
                NetworkHandler.instance.sendToServer(new PacketConfigButton(btnRwMode.getSetting(), backwards));
            } else if (btn == btnExtractionMode) {
                NetworkHandler.instance.sendToServer(new PacketConfigButton(btnExtractionMode.getSetting(), backwards));
            } else if (btn == btnStorageFilter) {
                NetworkHandler.instance.sendToServer(new PacketConfigButton(btnStorageFilter.getSetting(), backwards));
            } else if (btn == this.fuzzyMode) {
                NetworkHandler.instance.sendToServer(new PacketConfigButton(this.fuzzyMode.getSetting(), backwards));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    protected boolean drawUpgrades() {
        return true;
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected String getName() {
        return "Singularity Storage Bus";
    }

    private boolean acceptType(final VirtualMEPhantomSlot slot, final IAEStackType<?> type, final int btn) {
        return type == ITEM_STACK_TYPE;
    }
}
