package xaeroplus.fabric.util.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.Version;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class IncompatibleMinimapWarningScreen extends WarningScreen {

    private static Component getMessage(final Optional<Version> currentVersion, final Version compatibleMinimapVersion) {
        var msg = Component.empty();
        currentVersion.ifPresent(cv -> {
            msg
                .withStyle(ChatFormatting.RESET)
                .append(Component.translatable("xaeroplus.gui.minimap_incompatible.currently_installed_version"))
                .append(Component.literal(cv.getFriendlyString()).withStyle(ChatFormatting.RED))
                .append(Component.literal("\n"));
        });
        msg.append(
            Component.translatable("xaeroplus.gui.minimap_incompatible.required_version")
                .withStyle(ChatFormatting.RESET)
                .append(Component.literal(compatibleMinimapVersion.getFriendlyString()).withStyle(ChatFormatting.AQUA))
        );
        return msg;
    }
    public IncompatibleMinimapWarningScreen(Optional<Version> currentVersion, final Version compatibleMinimapVersion) {
        super(
            Component.translatable("xaeroplus.gui.minimap_incompatible.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
            getMessage(currentVersion, compatibleMinimapVersion),
            getMessage(currentVersion, compatibleMinimapVersion)
        );
    }

    @Override
    protected void initButtons(final int yOffset) {
        addRenderableWidget(
            new Button(
                width / 2 - 100 - 75,
                100 + yOffset,
                150,
                20,
                Component.translatable("xaeroplus.gui.minimap_incompatible.download_minimap"), button -> {
                    Util.getPlatform().openUri("https://modrinth.com/mod/xaeros-minimap/versions");
                    Minecraft.getInstance().close();
            })
        );
        addRenderableWidget(
            new Button(
                width / 2 + 100 - 75,
                100 + yOffset,
                150,
                20,
                Component.translatable("xaeroplus.gui.minimap_incompatible.exit"), button -> {
                Minecraft.getInstance().close();
            })
        );
    }

    @Override
    protected void renderTitle(PoseStack guiGraphics) {
        GuiComponent.drawCenteredString(guiGraphics, this.font, this.title, width / 2, 30, 16777215);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
