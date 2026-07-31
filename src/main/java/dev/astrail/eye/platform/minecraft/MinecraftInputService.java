package dev.astrail.eye.platform.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.astrail.eye.api.service.InputAction;
import dev.astrail.eye.api.service.InputLease;
import dev.astrail.eye.api.service.InputService;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MinecraftInputService implements InputService {
    private final Map<InputAction, Set<Lease>> leases = new EnumMap<>(InputAction.class);
    private boolean attackSyntheticActive;

    public MinecraftInputService() {
        for (InputAction action : InputAction.values()) {
            leases.put(action, new HashSet<>());
        }
    }

    @Override
    public synchronized InputLease acquire(String owner, InputAction action) {
        Lease lease = new Lease(owner, action);
        leases.get(action).add(lease);
        return lease;
    }

    @Override
    public synchronized void clear() {
        for (Set<Lease> actionLeases : leases.values()) {
            for (Lease lease : actionLeases) {
                lease.closed = true;
                lease.pressed = false;
            }
            actionLeases.clear();
        }
        if (attackSyntheticActive) {
            applyAttack(false);
            attackSyntheticActive = false;
        }
    }

    @Override
    public synchronized boolean isPressed(InputAction action) {
        return activeOwner(action) != null;
    }

    /** Owner of the first lease currently holding the action, or null when none is. */
    private String activeOwner(InputAction action) {
        for (Lease lease : leases.get(action)) {
            if (lease.pressed && !lease.closed) return lease.owner;
        }
        return null;
    }

    private void reconcile(InputAction action) {
        String owner = activeOwner(action);
        if (action == InputAction.ATTACK) {
            boolean syntheticPressed = owner != null;
            if (syntheticPressed) {
                applyAttack(true);
                attackSyntheticActive = true;
            } else if (attackSyntheticActive) {
                applyAttack(false);
                attackSyntheticActive = false;
            }
        }
    }

    private static void applyAttack(boolean syntheticPressed) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        KeyMapping mapping = client.options.keyAttack;
        mapping.setDown(syntheticPressed || isPhysicallyPressed(client, mapping));
    }

    private static boolean isPhysicallyPressed(Minecraft client, KeyMapping mapping) {
        if (client.gui.screen() != null || !client.isWindowActive()) {
            return false;
        }
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        return switch (key.getType()) {
            case KEYSYM -> key != InputConstants.UNKNOWN
                && InputConstants.isKeyDown(client.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    private final class Lease implements InputLease {
        private final String owner;
        private final InputAction action;
        private boolean pressed;
        private boolean closed;

        private Lease(String owner, InputAction action) {
            this.owner = owner;
            this.action = action;
        }

        @Override
        public void setPressed(boolean pressed) {
            synchronized (MinecraftInputService.this) {
                if (closed) {
                    return;
                }
                this.pressed = pressed;
                reconcile(action);
            }
        }

        @Override
        public void close() {
            synchronized (MinecraftInputService.this) {
                if (closed) {
                    return;
                }
                closed = true;
                pressed = false;
                leases.get(action).remove(this);
                reconcile(action);
            }
        }

        @Override
        public String toString() {
            return owner + ":" + action;
        }
    }
}
