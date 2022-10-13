package net.blay09.mods.waystones.client.gui.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.client.gui.widget.ITooltipProvider;
import net.blay09.mods.waystones.client.gui.widget.RemoveWaystoneButton;
import net.blay09.mods.waystones.client.gui.widget.SortWaystoneButton;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.blay09.mods.waystones.container.WaystoneSelectionContainer;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneEditPermissions;
import net.blay09.mods.waystones.network.NetworkHandler;
import net.blay09.mods.waystones.network.message.RemoveWaystoneMessage;
import net.blay09.mods.waystones.network.message.RequestEditWaystoneMessage;
import net.blay09.mods.waystones.network.message.SelectWaystoneMessage;
import net.blay09.mods.waystones.network.message.SortWaystoneMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class WaystoneSelectionScreenBase extends ContainerScreen<WaystoneSelectionContainer> {

    private final List<IWaystone> waystones;
    private List<IWaystone> searchedWaystones;
    private final List<ITooltipProvider> tooltipProviders = new ArrayList<>();

    private Button btnPrevPage;
    private Button btnNextPage;
    private TextFieldWidget searchBox;
    private int pageOffset;
    private int headerY;
    private boolean isLocationHeaderHovered;
    private int buttonsPerPage;

    private static final int headerHeight = 64;
    private static final int footerHeight = 25;
    private static final int entryHeight = 25;

    public WaystoneSelectionScreenBase(WaystoneSelectionContainer container, PlayerInventory playerInventory, ITextComponent title) {
        super(container, playerInventory, title);
        waystones = container.getWaystones();
        searchedWaystones = new ArrayList<>(waystones);
        xSize = 270;
        ySize = 200;
    }

    @Override
    public void init() {
        final int maxContentHeight = (int) (height * 0.6f);
        final int maxButtonsPerPage = (maxContentHeight - headerHeight - footerHeight) / entryHeight;
        buttonsPerPage = Math.max(4, Math.min(maxButtonsPerPage, waystones.size()));
        final int contentHeight = headerHeight + buttonsPerPage * entryHeight + footerHeight;

        // Leave no space for JEI!
        xSize = width;
        ySize = contentHeight;

        super.init();

        tooltipProviders.clear();
        btnPrevPage = new Button(width / 2 - 100, height / 2 + 40, 95, 20, new TranslationTextComponent("gui.waystones.waystone_selection.previous_page"), button -> {
            pageOffset = Screen.hasShiftDown() ? 0 : pageOffset - 1;
            updateList();
        });
        addButton(btnPrevPage);

        btnNextPage = new Button(width / 2 + 5, height / 2 + 40, 95, 20, new TranslationTextComponent("gui.waystones.waystone_selection.next_page"), button -> {
            pageOffset = Screen.hasShiftDown() ? (waystones.size() - 1) / buttonsPerPage : pageOffset + 1;
            updateList();
        });
        addButton(btnNextPage);

        searchBox = new TextFieldWidget(font, width / 2 - 99, guiTop + headerHeight - 24, 198, 20, StringTextComponent.EMPTY);
        searchBox.setResponder(text -> {
            searchWaystones();
        });

        addListener(searchBox);

        updateList();
    }

    @Override
    protected <T extends Widget> T addButton(T button) {
        if (button instanceof ITooltipProvider) {
            tooltipProviders.add((ITooltipProvider) button);
        }
        return super.addButton(button);
    }

    private void updateList() {
        headerY = 0;

        btnPrevPage.active = pageOffset > 0;
        btnNextPage.active = pageOffset < (searchedWaystones.size() - 1) / buttonsPerPage;

        tooltipProviders.clear();
        buttons.removeIf(button -> button instanceof WaystoneButton || button instanceof SortWaystoneButton || button instanceof RemoveWaystoneButton);
        children.removeIf(button -> button instanceof WaystoneButton || button instanceof SortWaystoneButton || button instanceof RemoveWaystoneButton);

        int y = guiTop + headerHeight + headerY;
        for (int i = 0; i < buttonsPerPage; i++) {
            int entryIndex = pageOffset * buttonsPerPage + i;
            if (entryIndex >= 0 && entryIndex < searchedWaystones.size()) {
                IWaystone waystone = searchedWaystones.get(entryIndex);

                addButton(createWaystoneButton(y, waystone));

                if (allowSorting()) {
                    SortWaystoneButton sortUpButton = new SortWaystoneButton(width / 2 + 108, y + 2, -1, y, 20, it -> sortWaystone(entryIndex, -1));
                    if (entryIndex == 0) {
                        sortUpButton.active = false;
                    }
                    addButton(sortUpButton);

                    SortWaystoneButton sortDownButton = new SortWaystoneButton(width / 2 + 108, y + 13, 1, y, 20, it -> sortWaystone(entryIndex, 1));
                    if (entryIndex == searchedWaystones.size() - 1) {
                        sortDownButton.active = false;
                    }
                    addButton(sortDownButton);
                }

                if (allowDeletion()) {
                    RemoveWaystoneButton removeButton = new RemoveWaystoneButton(width / 2 + 122, y + 4, y, 20, waystone, button -> {
                        PlayerEntity player = Minecraft.getInstance().player;
                        PlayerWaystoneManager.deactivateWaystone(Objects.requireNonNull(player), waystone);
                        NetworkHandler.channel.sendToServer(new RemoveWaystoneMessage(waystone));
                        updateList();
                    });
                    // Only show the remove button for non-global waystones, or if the player is in creative mode
                    if (!waystone.isGlobal() || Minecraft.getInstance().player.abilities.isCreativeMode) {
                        addButton(removeButton);
                    }
                }

                y += 22;
            }
        }

        btnPrevPage.y = guiTop + headerY + headerHeight + buttonsPerPage * 22 + (searchedWaystones.size() > 0 ? 10 : 0);
        btnNextPage.y = guiTop + headerY + headerHeight + buttonsPerPage * 22 + (searchedWaystones.size() > 0 ? 10 : 0);
    }

    private WaystoneButton createWaystoneButton(int y, final IWaystone waystone) {
        IWaystone waystoneFrom = container.getWaystoneFrom();
        PlayerEntity player = Minecraft.getInstance().player;
        int xpLevelCost = Math.round(PlayerWaystoneManager.getExperienceLevelCost(Objects.requireNonNull(player), waystone, container.getWarpMode(), waystoneFrom));
        WaystoneButton btnWaystone = new WaystoneButton(width / 2 - 100, y, waystone, xpLevelCost, button -> onWaystoneSelected(waystone));
        if (waystoneFrom != null && waystone.getWaystoneUid().equals(waystoneFrom.getWaystoneUid())) {
            btnWaystone.active = false;
        }
        return btnWaystone;
    }

    protected void onWaystoneSelected(IWaystone waystone) {
        NetworkHandler.channel.sendToServer(new SelectWaystoneMessage(waystone));
    }

    private void sortWaystone(int index, int sortDir) {
        if (index < 0 || index >= waystones.size()) {
            return;
        }

        int otherIndex;
        if (Screen.hasShiftDown()) {
            otherIndex = sortDir == -1 ? -1 : waystones.size();
        } else {
            otherIndex = index + sortDir;
            if (otherIndex < 0 || otherIndex >= waystones.size()) {
                return;
            }
        }

        PlayerWaystoneManager.swapWaystoneSorting(Minecraft.getInstance().player, index, otherIndex);
        NetworkHandler.channel.sendToServer(new SortWaystoneMessage(index, otherIndex));
        searchWaystones();
    }

    private void searchWaystones() {
        searchedWaystones = waystones.stream().filter((waystone) -> waystone.getName().toLowerCase().contains(searchBox.getText().toLowerCase())).collect(Collectors.toList());
        int calculatedPageOffset = (searchedWaystones.size() - 1) / buttonsPerPage;
        if (!searchBox.getText().isEmpty() && calculatedPageOffset < pageOffset)
            pageOffset = calculatedPageOffset;

        updateList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (isLocationHeaderHovered && container.getWaystoneFrom() != null) {
            NetworkHandler.channel.sendToServer(new RequestEditWaystoneMessage(container.getWaystoneFrom()));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        renderHoveredTooltip(matrixStack, mouseX, mouseY);
        for (ITooltipProvider tooltipProvider : tooltipProviders) {
            if (tooltipProvider.shouldShowTooltip()) {
                func_243308_b(matrixStack, tooltipProvider.getTooltip(), mouseX, mouseY);
            }
        }

        searchBox.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(MatrixStack p_230450_1_, float p_230450_2_, int p_230450_3_, int p_230450_4_) {
    }

    @Override
    protected void drawGuiContainerForegroundLayer(MatrixStack matrixStack, int mouseX, int mouseY) {
        FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;

        RenderSystem.color4f(1f, 1f, 1f, 1f);
        IWaystone fromWaystone = container.getWaystoneFrom();
        drawCenteredString(matrixStack, fontRenderer, getTitle(), xSize / 2, headerY + (fromWaystone != null ? 20 : 0), 0xFFFFFF);
        if (fromWaystone != null) {
            drawLocationHeader(matrixStack, fromWaystone, mouseX, mouseY, xSize / 2, headerY);
        }

        if (waystones.size() == 0) {
            drawCenteredString(matrixStack, fontRenderer, TextFormatting.RED + I18n.format("gui.waystones.waystone_selection.no_waystones_activated"), xSize / 2, ySize / 2 - 20, 0xFFFFFF);
        }
    }

    private void drawLocationHeader(MatrixStack matrixStack, IWaystone waystone, int mouseX, int mouseY, int x, int y) {
        FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;

        String locationPrefix = TextFormatting.YELLOW + I18n.format("gui.waystones.waystone_selection.current_location") + " ";
        int locationPrefixWidth = fontRenderer.getStringWidth(locationPrefix);

        String effectiveName = waystone.getName();
        if (effectiveName.isEmpty()) {
            effectiveName = I18n.format("gui.waystones.waystone_selection.unnamed_waystone");
        }
        int locationWidth = fontRenderer.getStringWidth(effectiveName);

        int fullWidth = locationPrefixWidth + locationWidth;

        int startX = x - fullWidth / 2 + locationPrefixWidth;
        int startY = y + guiTop;
        isLocationHeaderHovered = mouseX >= startX && mouseX < startX + locationWidth + 16
                && mouseY >= startY && mouseY < startY + fontRenderer.FONT_HEIGHT;

        PlayerEntity player = Minecraft.getInstance().player;
        WaystoneEditPermissions waystoneEditPermissions = PlayerWaystoneManager.mayEditWaystone(player, player.world, waystone);

        String fullText = locationPrefix + TextFormatting.WHITE;
        if (isLocationHeaderHovered && waystoneEditPermissions == WaystoneEditPermissions.ALLOW) {
            fullText += TextFormatting.UNDERLINE;
        }
        fullText += effectiveName;

        drawString(matrixStack, fontRenderer, fullText, x - fullWidth / 2, y, 0xFFFFFF);

        if (isLocationHeaderHovered && waystoneEditPermissions == WaystoneEditPermissions.ALLOW) {
            RenderSystem.pushMatrix();
            RenderSystem.translated(x + fullWidth / 2f + 4, y, 0f);
            float scale = 0.5f;
            RenderSystem.scalef(scale, scale, scale);
            Minecraft.getInstance().getTextureManager().bindTexture(AtlasTexture.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getInstance().getItemRenderer().renderItemAndEffectIntoGUI(new ItemStack(Items.WRITABLE_BOOK), 0, 0);
            RenderSystem.popMatrix();
        }
    }

    protected boolean allowSorting() {
        return true;
    }

    protected boolean allowDeletion() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.searchBox.isFocused() || (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc())) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
    }
}
