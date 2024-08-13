package xaeroplus.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.gui.GuiWaypoints;
import xaero.common.minimap.waypoints.Waypoint;
import xaeroplus.settings.XaeroPlusSettingRegistry;

import java.text.NumberFormat;
import java.util.ArrayList;

@Mixin(targets = "xaero.common.gui.GuiWaypoints$List", remap = false)
public abstract class MixinGuiWaypointsList {
    private GuiWaypoints thisGuiWaypoints;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void init(final GuiWaypoints this$0, final CallbackInfo ci) throws NoSuchFieldException, IllegalAccessException {
        thisGuiWaypoints = this$0;
    }

    /**
     * @author rfresh2
     * @reason search support
     */
    @Inject(method = "getWaypointCount", at = @At("HEAD"), cancellable = true)
    public void getWaypointCount(final CallbackInfoReturnable<Integer> cir) {
        try {
            int size = ((AccessorGuiWaypoints) thisGuiWaypoints).getWaypointsSorted().size();
            cir.setReturnValue(size);
        } catch (final NullPointerException e) {
            // fall through
        }
    }

    @Redirect(method = "getWaypoint", at = @At(
        value = "INVOKE",
        target = "Lxaero/common/minimap/waypoints/WaypointSet;getList()Ljava/util/ArrayList;"
    ))
    public ArrayList<Waypoint> getWaypointList(final xaero.common.minimap.waypoints.WaypointSet waypointSet) {
        return ((AccessorGuiWaypoints) thisGuiWaypoints).getWaypointsSorted();
    }

    @Inject(method = "drawWaypointSlot", at = @At(
        value = "INVOKE",
        target = "Lxaero/common/minimap/waypoints/Waypoint;isGlobal()Z"
    ), remap = false)
    public void shiftIconsLeft(final PoseStack guiGraphics, final Waypoint w, final int x, final int y, final CallbackInfo ci,
                               @Local(name = "rectX") LocalIntRef rectX) {
        rectX.set(rectX.get() - 30);
    }

    @Inject(method = "drawWaypointSlot", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"
    ), remap = true)
    public void drawWaypointDistances(final PoseStack guiGraphics, final Waypoint w, final int x, final int y, final CallbackInfo ci) {
        if (XaeroPlusSettingRegistry.showWaypointDistances.getValue()) {
            Entity renderViewEntity = Minecraft.getInstance().getCameraEntity();
            final double playerX = renderViewEntity.getX();
            final double playerZ = renderViewEntity.getZ();
            final double playerY = renderViewEntity.getY();
            final double dimensionDivision = GuiWaypoints.distanceDivided;
            final int wpX = w.getX(dimensionDivision);
            final int wpY = w.getY();
            final int wpZ = w.getZ(dimensionDivision);
            final double distance = Math.sqrt(Math.pow(playerX - wpX, 2) + Math.pow(playerY - wpY, 2) + Math.pow(playerZ - wpZ, 2));
            final String text = NumberFormat.getIntegerInstance().format(distance) + "m";
            final Font fontRenderer = Minecraft.getInstance().font;
            GuiComponent.drawString(guiGraphics, fontRenderer, text, x + 250, y + 1, 0xFFFFFF);
        }
    }
}
