package dev.astrail.eye.mixin;

import dev.astrail.eye.AstrailEyeClient;
import dev.astrail.eye.api.service.InputAction;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void astraileye$mergeSyntheticInput(CallbackInfo callback) {
        boolean forward = keyPresses.forward();
        boolean backward = keyPresses.backward();
        boolean left = keyPresses.left();
        boolean right = keyPresses.right();
        if (!forward && !backward) {
            forward = AstrailEyeClient.isSyntheticInputPressed(InputAction.FORWARD);
            backward = AstrailEyeClient.isSyntheticInputPressed(InputAction.BACKWARD);
        }
        if (!left && !right) {
            left = AstrailEyeClient.isSyntheticInputPressed(InputAction.LEFT);
            right = AstrailEyeClient.isSyntheticInputPressed(InputAction.RIGHT);
        }
        boolean shift = keyPresses.shift() || AstrailEyeClient.isSyntheticInputPressed(InputAction.SNEAK);
        boolean jump = keyPresses.jump() || AstrailEyeClient.isSyntheticInputPressed(InputAction.JUMP);

        keyPresses = new Input(forward, backward, left, right, jump, shift, keyPresses.sprint());
        moveVector = new Vec2(impulse(left, right), impulse(forward, backward)).normalized();
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
