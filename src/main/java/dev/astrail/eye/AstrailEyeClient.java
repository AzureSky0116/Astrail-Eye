package dev.astrail.eye;

import dev.astrail.eye.api.service.InputAction;
import dev.astrail.eye.core.AstrailEyeRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.resources.Identifier;

public final class AstrailEyeClient implements ClientModInitializer {
    private static final Identifier INTER_UI_ATLAS = Identifier.fromNamespaceAndPath(
        "astrail-eye", "textures/font/inter_ui_atlas.png"
    );

    private static AstrailEyeRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new AstrailEyeRuntime();
        runtime.initialize();
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
            client.getTextureManager().getTexture(INTER_UI_ATLAS)
        );
    }

    public static boolean isSyntheticInputPressed(InputAction action) {
        return runtime != null && runtime.inputs().isPressed(action);
    }

    public static boolean shouldKeepWorldRunning() {
        return runtime != null && runtime.shouldKeepWorldRunning();
    }
}
