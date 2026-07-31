package dev.astrail.eye.mixin;

import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
abstract class GuiRenderStateMixin {
    @Shadow
    @Final
    private List<?> strata;

    @Shadow
    private ScreenRectangle lastElementBounds;

    // Fast path for addGuiElement: while no blur stratum exists (strata.size() <= 1) the
    // up-chain walk always produces a fresh node above current, i.e. paint order == insertion
    // order. Priming lastElementBounds with the element's own bounds makes findAppropriateNode's
    // O(1) encompasses fast path succeed (a rectangle encompasses itself), so it calls up() and
    // the original body adds the element to the fresh node - identical rendering, no O(n) scan.
    // The priming is skipped as soon as a blur stratum exists (strata.size() > 1) to keep the
    // vanilla intersection walk intact for blur ordering.
    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void astrailEye$fastAddGuiElement(GuiElementRenderState state, CallbackInfo ci) {
        if (this.strata.size() <= 1 && state.bounds() != null) {
            this.lastElementBounds = state.bounds();
        }
    }
}
