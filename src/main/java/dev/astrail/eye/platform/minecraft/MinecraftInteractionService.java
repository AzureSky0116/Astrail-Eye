package dev.astrail.eye.platform.minecraft;

import dev.astrail.eye.api.service.InteractionService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.Entity;

public final class MinecraftInteractionService implements InteractionService {
    @Override
    public boolean useMainHand() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public boolean attack(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || target == null || !target.isAlive()) {
            return false;
        }
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    @Override
    public boolean clickContainerSlot(int containerId, int slot, ContainerInput input) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        client.gameMode.handleContainerInput(containerId, slot, 0, input, client.player);
        return true;
    }

    @Override
    public boolean closeScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() == null) {
            return false;
        }
        client.gui.setScreen(null);
        return true;
    }
}
