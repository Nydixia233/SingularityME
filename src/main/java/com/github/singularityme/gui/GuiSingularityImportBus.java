package com.github.singularityme.gui;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import com.github.singularityme.tile.TileSingularityImportBus;

import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * GUI for the Singularity Import Bus.
 *
 * <p>
 * Displays up to 9 ghost filter slots (CAPACITY-gated), a redstone mode toggle,
 * and a fuzzy mode toggle (visible only when FUZZY card is installed).
 */
public class GuiSingularityImportBus extends GuiUpgradeable {

    private static final int[] SLOT_SEQUENCE = new int[] { 5, 3, 6, 1, 0, 2, 7, 4, 8 };

    protected final VirtualMEPhantomSlot[] virtualSlots = new VirtualMEPhantomSlot[9];

    public GuiSingularityImportBus(final InventoryPlayer ip, final TileSingularityImportBus te) {
        super(new ContainerSingularityImportBus(ip, te));
    }

    @Override
    public void initGui() {
        super.initGui();
        initVirtualSlots();
    }

    private void initVirtualSlots() {
        final ContainerSingularityImportBus c = (ContainerSingularityImportBus) this.inventorySlots;
        final appeng.tile.inventory.IAEStackInventory inputInv = c.importBus.getAEInventoryByName(StorageName.CONFIG);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                final int idx = SLOT_SEQUENCE[x + y * 3];
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                    62 + 18 * x,
                    22 + 18 * y,
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
        this.redstoneMode = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 8,
            Settings.REDSTONE_CONTROLLED,
            RedstoneMode.IGNORE);
        this.buttonList.add(this.redstoneMode);

        this.fuzzyMode = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 28,
            Settings.FUZZY_MODE,
            FuzzyMode.IGNORE_ALL);
        this.buttonList.add(this.fuzzyMode);

        this.oreFilter = new GuiImgButton(
            this.guiLeft - 18,
            this.guiTop + 28,
            Settings.ACTIONS,
            ActionItems.ORE_FILTER);
        this.buttonList.add(this.oreFilter);
    }

    @Override
    protected void handleButtonVisibility() {
        final ContainerSingularityImportBus c = (ContainerSingularityImportBus) this.inventorySlots;
        final int hasRedstone = c.importBus.getInstalledUpgrades(Upgrades.REDSTONE);
        final int hasFuzzy = c.importBus.getInstalledUpgrades(Upgrades.FUZZY);
        final boolean hasOreFilter = c.importBus.getInstalledUpgrades(Upgrades.ORE_FILTER) > 0;
        final int capacity = c.importBus.getInstalledUpgrades(Upgrades.CAPACITY);

        if (this.redstoneMode != null) this.redstoneMode.setVisibility(hasRedstone > 0);
        if (this.fuzzyMode != null) this.fuzzyMode.setVisibility(hasFuzzy > 0 && !hasOreFilter);
        if (this.oreFilter != null) this.oreFilter.setVisibility(hasOreFilter);

        // AE2 slot visibility: slot 0 always; slots 1-4 need CAPACITY>0; slots 5-8 need CAPACITY>1
        final boolean firstTier = capacity > 0 && !hasOreFilter;
        final boolean secondTier = capacity > 1 && !hasOreFilter;
        if (virtualSlots[0] != null) virtualSlots[0].setHidden(hasOreFilter);
        for (int i = 1; i <= 4; i++) {
            if (virtualSlots[i] != null) virtualSlots[i].setHidden(!firstTier);
        }
        for (int i = 5; i <= 8; i++) {
            if (virtualSlots[i] != null) virtualSlots[i].setHidden(!secondTier);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        final ContainerSingularityImportBus c = (ContainerSingularityImportBus) this.inventorySlots;
        if (this.redstoneMode != null) {
            this.redstoneMode.set(c.rsMode);
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(c.fzMode);
        }
    }

    @Override
    protected void actionPerformed(final net.minecraft.client.gui.GuiButton btn) {
        super.actionPerformed(btn);
        final boolean backwards = Mouse.isButtonDown(1);
        if (btn == this.redstoneMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.redstoneMode.getSetting(), backwards));
        } else if (btn == this.fuzzyMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.fuzzyMode.getSetting(), backwards));
        } else if (btn == this.oreFilter) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_ORE_FILTER));
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        final ContainerSingularityImportBus c = (ContainerSingularityImportBus) this.inventorySlots;
        final int capacity = c.importBus.getInstalledUpgrades(Upgrades.CAPACITY);
        final boolean hasOreFilter = c.importBus.getInstalledUpgrades(Upgrades.ORE_FILTER) > 0;
        final boolean firstTier = capacity > 0 && !hasOreFilter;
        final boolean secondTier = capacity > 1 && !hasOreFilter;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                final int slotIdx = SLOT_SEQUENCE[x + y * 3];
                final boolean active = !hasOreFilter && (slotIdx == 0 || (firstTier && slotIdx <= 4) || secondTier);
                final int u = active ? 61 : 79;
                this.drawTexturedModalRect(offsetX + 61 + 18 * x, offsetY + 21 + 18 * y, u, 21, 18, 18);
            }
        }
    }

    @Override
    protected boolean drawUpgrades() {
        return true;
    }

    @Override
    protected String getBackground() {
        return "guis/bus.png";
    }

    @Override
    protected String getName() {
        return "Singularity Import Bus";
    }

    private boolean acceptType(final VirtualMEPhantomSlot slot, final IAEStackType<?> type, final int btn) {
        return type == ITEM_STACK_TYPE;
    }
}
