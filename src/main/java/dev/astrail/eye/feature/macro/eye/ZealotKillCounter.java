package dev.astrail.eye.feature.macro.eye;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/** Tracks attacked Zealots until the client confirms their death or prompt removal. */
final class ZealotKillCounter {
    private static final int MISSING_CONFIRM_TICKS = 2;
    private static final int CONFIRM_TIMEOUT_TICKS = 200;

    private final Map<UUID, PendingKill> pending = new HashMap<>();
    private final Set<UUID> confirmed = new HashSet<>();
    private int zealotKills;
    private int bruiserKills;

    void recordAttack(UUID uuid, int entityId, ZealotKind kind) {
        if (confirmed.contains(uuid)) return;
        pending.put(uuid, new PendingKill(entityId, kind));
    }

    void tick(IntFunction<TargetState> stateLookup) {
        Iterator<Map.Entry<UUID, PendingKill>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingKill> entry = iterator.next();
            PendingKill kill = entry.getValue();
            kill.age++;
            TargetState state = stateLookup.apply(kill.entityId);
            if (state == TargetState.DEAD) {
                confirm(entry.getKey(), kill.kind);
                iterator.remove();
                continue;
            }
            if (state == TargetState.MISSING) {
                kill.missingTicks++;
                if (kill.missingTicks >= MISSING_CONFIRM_TICKS && kill.age <= CONFIRM_TIMEOUT_TICKS) {
                    confirm(entry.getKey(), kill.kind);
                    iterator.remove();
                    continue;
                }
            } else {
                kill.missingTicks = 0;
            }
            if (kill.age > CONFIRM_TIMEOUT_TICKS) iterator.remove();
        }
    }

    int zealotKills() {
        return zealotKills;
    }

    int bruiserKills() {
        return bruiserKills;
    }

    void clearSession() {
        pending.clear();
        confirmed.clear();
    }

    void restoreTotals(int zealotKills, int bruiserKills) {
        clearSession();
        this.zealotKills = Math.max(0, zealotKills);
        this.bruiserKills = Math.max(0, bruiserKills);
    }

    void resetTotals() {
        clearSession();
        zealotKills = 0;
        bruiserKills = 0;
    }

    private void confirm(UUID uuid, ZealotKind kind) {
        if (!confirmed.add(uuid)) return;
        if (kind == ZealotKind.BRUISER) bruiserKills++;
        else zealotKills++;
    }

    enum TargetState {
        ALIVE,
        DEAD,
        MISSING
    }

    private static final class PendingKill {
        private final int entityId;
        private final ZealotKind kind;
        private int age;
        private int missingTicks;

        private PendingKill(int entityId, ZealotKind kind) {
            this.entityId = entityId;
            this.kind = kind;
        }
    }
}
