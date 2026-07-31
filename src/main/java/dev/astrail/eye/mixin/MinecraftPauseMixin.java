package dev.astrail.eye.mixin;

import dev.astrail.eye.AstrailEyeClient;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
abstract class MinecraftPauseMixin {
    @Inject(method = "isPausing", at = @At("HEAD"), cancellable = true)
    private void astraileye$keepAutomationRunning(CallbackInfoReturnable<Boolean> result) {
        if (AstrailEyeClient.shouldKeepWorldRunning()) {
            result.setReturnValue(false);
        }
    }
}
