package xaeroplus.mixin.client;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.events.ForgeEventHandler;
import xaeroplus.settings.XaeroPlusSettingRegistry;

@Mixin(value = ForgeEventHandler.class, remap = false)
public abstract class MixinForgeEventHandler {
    @Inject(method = "handleClientSystemChatReceivedEvent", at = @At("HEAD"), cancellable = true)
    public void onSystemChatReceived(final Component component, final CallbackInfoReturnable<Boolean> cir) {
        if (component == null) return;
        if (XaeroPlusSettingRegistry.disableReceivingWaypoints.getValue()) {
            // cancelling at head so we avoid hitting the logic to parse the waypoint string
            cir.setReturnValue(false); // false will show the raw message in chat to the player
        }
    }
}
