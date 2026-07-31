package dev.astrail.eye.feature.macro.eye;

import dev.astrail.eye.api.event.ClientTickEvent;
import dev.astrail.eye.api.event.ChatMessageEvent;
import dev.astrail.eye.api.module.BackgroundRunning;
import dev.astrail.eye.api.module.DisableReason;
import dev.astrail.eye.api.module.HudStatsProvider;
import dev.astrail.eye.api.module.ModuleCategory;
import dev.astrail.eye.api.module.ModuleMetadata;
import dev.astrail.eye.api.service.InputAction;
import dev.astrail.eye.api.service.InputLease;
import dev.astrail.eye.api.service.InputService;
import dev.astrail.eye.api.service.InteractionService;
import dev.astrail.eye.api.service.RotationLease;
import dev.astrail.eye.api.service.RotationService;
import dev.astrail.eye.api.setting.BooleanSetting;
import dev.astrail.eye.api.setting.ConfirmActionSetting;
import dev.astrail.eye.api.setting.NumberSetting;
import dev.astrail.eye.core.event.EventBus;
import dev.astrail.eye.core.module.AbstractModule;
import dev.astrail.eye.core.module.ModuleScope;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.phys.Vec3;

/**
 * Farms Zealots for Summoning Eyes from a fixed firing position (Stationary
 * mode only). The Stationary branch of the Astrail Auto Eye module, adapted
 * verbatim; route patrol and melee chase machinery are intentionally absent.
 */
public final class AutoEyeModule extends AbstractModule implements BackgroundRunning, HudStatsProvider {
    public static final String ID = "astrail-eye.auto_eye";

    private static final int ATTACK_INTERVAL_TICKS = 5;
    private static final double AIM_COSINE = Math.cos(Math.toRadians(2.5D));
    private static final int AREA_MEMORY_TICKS = 200;
    private static final int VIEW_RESTORE_TIMEOUT_TICKS = 40;
    private static final int ATTACK_ONCE_RESCAN_TICKS = 20;
    private static final double COUNTER_MAX = 1_000_000_000.0D;

    private final NumberSetting weaponSlot = new NumberSetting(
        "weapon_slot", "Weapon Slot", 1.0D, 1.0D, 9.0D, 1.0D
    );
    private final BooleanSetting attackOnce = new BooleanSetting(
        "attack_once", "Attack Once", true
    );
    private final NumberSetting attackSpeed = new NumberSetting(
        "attack_speed", "Attack Speed", 4.0D, 1.0D, 10.0D, 0.5D
    );
    private final BooleanSetting alwaysSneak = new BooleanSetting(
        "always_sneak", "Always Sneak", false
    );
    private final NumberSetting storedZealotKills = new NumberSetting(
        "counter_zealot", "Stored Zealot Kills", 0.0D, 0.0D, COUNTER_MAX, 1.0D
    );
    private final NumberSetting storedBruiserKills = new NumberSetting(
        "counter_bruiser", "Stored Bruiser Kills", 0.0D, 0.0D, COUNTER_MAX, 1.0D
    );
    private final NumberSetting storedSummoningEyes = new NumberSetting(
        "counter_summoning_eyes", "Stored Summoning Eyes", 0.0D, 0.0D, COUNTER_MAX, 1.0D
    );
    private final ConfirmActionSetting resetCounter = new ConfirmActionSetting(
        "reset_counter", "Reset Counter", this::resetCounters
    );
    private final EventBus events;
    private final InputService inputs;
    private final RotationService rotations;
    private final InteractionService interactions;
    private final Runnable configuration;

    private RotationLease rotationLease;
    private InputLease sneakLease;

    private final AttackSweepTracker attackSweep = new AttackSweepTracker(ATTACK_ONCE_RESCAN_TICKS);
    private int targetId = -1;
    private int attackCooldown;
    private int attackOnceCooldown;
    private final SkillShotTracker skillShot = new SkillShotTracker();
    private final ZealotKillCounter killCounter = new ZealotKillCounter();
    private int areaMemoryTicks;
    private int bruiserAreaMemoryTicks;
    private int specialAlertTicks;
    private boolean restoreStationaryView;
    private int viewRestoreTicks;

    public AutoEyeModule(EventBus events, InputService inputs, RotationService rotations,
                         InteractionService interactions, Runnable configuration) {
        super(new ModuleMetadata(
            ID,
            "Auto Eye",
            "Farms Summoning Eyes from a fixed position",
            ModuleCategory.MACRO,
            true,
            false
        ));
        this.events = events;
        this.inputs = inputs;
        this.rotations = rotations;
        this.interactions = interactions;
        this.configuration = configuration;
        weaponSlot.presentation("Combat", "Hotbar slot containing the attack weapon (1-9).");
        attackOnce.presentation("Combat", "Attack each Zealot once, then immediately select another target.");
        attackSpeed.presentation("Combat", "Maximum attacks per second while Attack Once is enabled.");
        alwaysSneak.presentation("Movement", "Keep sneak held while standing still.");
        resetCounter.presentation("Statistics", "Click Reset, then Confirm within five seconds to clear all totals.");
        attackSpeed.visibleWhen(this::attackOnceEnabled);
        storedZealotKills.visibleWhen(() -> false);
        storedBruiserKills.visibleWhen(() -> false);
        storedSummoningEyes.visibleWhen(() -> false);
        addSettings(
            alwaysSneak, weaponSlot, attackOnce, attackSpeed,
            storedZealotKills, storedBruiserKills, storedSummoningEyes, resetCounter
        );
    }

    @Override
    protected void onEnable(ModuleScope scope) {
        rotationLease = scope.own(rotations.acquire(ID, 55));
        sneakLease = scope.own(inputs.acquire(ID + ".always_sneak", InputAction.SNEAK));
        sneakLease.setPressed(alwaysSneakActive());
        killCounter.restoreTotals(storedZealotKills.intValue(), storedBruiserKills.intValue());
        resetState(Minecraft.getInstance().player);
        scope.own(events.subscribe(ClientTickEvent.class, ID, this::onTick));
        scope.own(events.subscribe(ChatMessageEvent.class, ID, this::onChatMessage));
    }

    @Override
    protected void onDisable(DisableReason reason) {
        if (sneakLease != null) sneakLease.setPressed(false);
        if (rotationLease != null) rotationLease.clear();
        targetId = -1;
        skillShot.clear();
        attackSweep.clear();
        killCounter.clearSession();
    }

    @Override
    public String hudStatsTitle() {
        return "Auto Eye";
    }

    @Override
    public int hudStatCount() {
        return 3;
    }

    @Override
    public String hudStatLabel(int index) {
        return switch (index) {
            case 0 -> "Zealot";
            case 1 -> "Bruiser";
            case 2 -> "Eyes";
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public int hudStatValue(int index) {
        return switch (index) {
            case 0 -> killCounter.zealotKills();
            case 1 -> killCounter.bruiserKills();
            case 2 -> storedSummoningEyes.intValue();
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    private void onTick(ClientTickEvent event) {
        Minecraft client = event.client();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            if (sneakLease != null) sneakLease.setPressed(false);
            return;
        }
        sneakLease.setPressed(alwaysSneakActive());
        if (attackOnceEnabled()) {
            if (attackOnceCooldown > 0) attackOnceCooldown--;
        } else {
            attackOnceCooldown = 0;
        }
        updateKillCounter(client);
        if (!attackOnceEnabled()) attackSweep.clear();
        ZealotArea area = detectZealotArea(client);
        if (area.active()) areaMemoryTicks = AREA_MEMORY_TICKS;
        else if (areaMemoryTicks > 0) areaMemoryTicks--;
        if (area.bruiser()) bruiserAreaMemoryTicks = AREA_MEMORY_TICKS;
        else if (area.active()) bruiserAreaMemoryTicks = 0;
        else if (bruiserAreaMemoryTicks > 0) bruiserAreaMemoryTicks--;
        if (areaMemoryTicks <= 0) {
            clearTarget(player);
            return;
        }
        if (specialAlertTicks > 0) specialAlertTicks--;

        LivingEntity target = currentTarget(client, player);
        LivingEntity best = findBestTarget(client, player);
        if (best != null && (target == null || isSpecial(best) && !isSpecial(target))) {
            target = best;
            targetId = best.getId();
            attackCooldown = 0;
            skillShot.clear();
            restoreStationaryView = false;
        }

        if (target != null) {
            engage(player, target);
            return;
        }

        if (attackOnceEnabled()) attackSweep.tickExhausted();
        clearTarget(player);
        restoreStationaryView(player);
    }

    private void engage(LocalPlayer player, LivingEntity target) {
        int slot = weaponSlot.intValue() - 1;
        if (slot < 0 || slot > 8 || player.getInventory().getItem(slot).isEmpty()) {
            return;
        }
        player.getInventory().setSelectedSlot(slot);

        Vec3 aim = predictedAim(player, target, true);
        rotationLease.request(aim, 20.0F, 0.58F);
        if (!isLookingAt(player, aim)) return;
        if (attackCooldown > 0) attackCooldown--;
        if (attackOnceEnabled() && attackOnceCooldown > 0) return;
        if (attackCooldown > 0) return;
        interactions.useMainHand();
        skillShot.recordShot(
            target.getId(), target.getHealth(), Math.sqrt(player.distanceToSqr(target))
        );
        killCounter.recordAttack(
            target.getUUID(), target.getId(), zealotKind(target, bruiserAreaMemoryTicks > 0)
        );
        attackCooldown = ATTACK_INTERVAL_TICKS;
        if (attackOnceEnabled()) {
            attackSweep.record(target.getUUID());
            targetId = -1;
            attackCooldown = 0;
            attackOnceCooldown = AttackRate.intervalTicks(attackSpeed.get());
            skillShot.clear();
            restoreStationaryView = true;
            viewRestoreTicks = 0;
        }
    }

    private LivingEntity currentTarget(Minecraft client, LocalPlayer player) {
        if (targetId < 0) return null;
        Entity entity = client.level.getEntity(targetId);
        if (!(entity instanceof LivingEntity living)
            || !isTargetZealot(living)
            || !living.isAlive()
            || living.isRemoved()) {
            return null;
        }
        if (attackOnceEnabled() && attackSweep.contains(living.getUUID())) return null;
        if (!player.hasLineOfSight(living)) return null;
        return living;
    }

    private LivingEntity findBestTarget(Minecraft client, LocalPlayer player) {
        Comparator<LivingEntity> comparator = specialAlertTicks > 0
            ? Comparator.comparing((LivingEntity entity) -> !isSpecial(entity))
                .thenComparingDouble(player::distanceToSqr)
                .thenComparingDouble(entity -> angularDistance(player, entity))
            : Comparator.comparing((LivingEntity entity) -> !isSpecial(entity))
                .thenComparingDouble(entity -> angularDistance(player, entity))
                .thenComparingDouble(player::distanceToSqr);
        LivingEntity best = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isTargetZealot(living) || !living.isAlive()) continue;
            if (attackOnceEnabled() && attackSweep.contains(living.getUUID())) continue;
            if (!player.hasLineOfSight(living)) continue;
            if (best == null || comparator.compare(living, best) < 0) best = living;
        }
        return best;
    }

    private void restoreStationaryView(LocalPlayer player) {
        // View restore disabled — keep the look direction on the last target.
        if (rotationLease != null) rotationLease.clear();
        restoreStationaryView = false;
        viewRestoreTicks = 0;
    }

    private void clearTarget(LocalPlayer player) {
        if (targetId >= 0) {
            restoreStationaryView = true;
            viewRestoreTicks = 0;
        }
        targetId = -1;
        attackCooldown = 0;
        skillShot.clear();
    }

    private void resetState(LocalPlayer player) {
        targetId = -1;
        attackCooldown = 0;
        attackOnceCooldown = 0;
        skillShot.clear();
        attackSweep.clear();
        killCounter.clearSession();
        areaMemoryTicks = 0;
        bruiserAreaMemoryTicks = 0;
        restoreStationaryView = false;
        viewRestoreTicks = 0;
        specialAlertTicks = 0;
    }

    private boolean attackOnceEnabled() {
        return attackOnce.get();
    }

    /** Sneak is only useful while standing still; this module never travels. */
    private boolean alwaysSneakActive() {
        return alwaysSneak.get();
    }

    private static ZealotArea detectZealotArea(Minecraft client) {
        boolean active = false;
        boolean bruiser = false;
        var scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            String title = sidebar.getDisplayName().getString();
            active = containsDragonNest(title) || containsBruiserHideout(title);
            bruiser = containsBruiserHideout(title);
            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                String owner = entry.owner();
                String display = entry.display() == null ? "" : entry.display().getString();
                if (containsDragonNest(owner) || containsDragonNest(display)) active = true;
                if (containsBruiserHideout(owner) || containsBruiserHideout(display)) {
                    active = true;
                    bruiser = true;
                }
            }
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isZealot(living)) continue;
            active = true;
            if (zealotKind(living, false) == ZealotKind.BRUISER) bruiser = true;
        }
        return new ZealotArea(active, bruiser);
    }

    private static boolean containsDragonNest(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replace("'", "");
        return normalized.contains("dragons nest");
    }

    private static boolean containsBruiserHideout(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return normalized.contains("zealot bruiser hideout");
    }

    private static boolean isZealot(LivingEntity entity) {
        return entity instanceof EnderMan && zealotLabel(entity).toLowerCase(Locale.ROOT).contains("zealot");
    }

    private boolean isTargetZealot(LivingEntity entity) {
        if (!(entity instanceof EnderMan)) return false;
        if (bruiserAreaMemoryTicks > 0) return true;
        if (areaMemoryTicks > 0) return true;
        return isZealot(entity);
    }

    private static boolean isSpecial(LivingEntity entity) {
        if (!(entity instanceof EnderMan enderman)) return false;
        var carried = enderman.getCarriedBlock();
        return carried != null && carried.is(Blocks.END_PORTAL_FRAME);
    }

    private static boolean hasZealotName(Entity entity) {
        return entity.getName().getString().toLowerCase(Locale.ROOT).contains("zealot");
    }

    private static ZealotKind zealotKind(LivingEntity entity, boolean bruiserArea) {
        return ZealotKind.fromLabel(zealotLabel(entity), bruiserArea);
    }

    private static String zealotLabel(LivingEntity entity) {
        String direct = entity.getName().getString();
        if (hasZealotName(entity) || !(entity instanceof EnderMan enderman)) return direct;
        for (ArmorStand label : enderman.level().getEntitiesOfClass(
            ArmorStand.class,
            enderman.getBoundingBox().inflate(1.0D),
            AutoEyeModule::hasZealotName
        )) {
            return label.getName().getString();
        }
        return direct;
    }

    private void updateKillCounter(Minecraft client) {
        int zealotsBefore = killCounter.zealotKills();
        int bruisersBefore = killCounter.bruiserKills();
        killCounter.tick(entityId -> {
            Entity entity = client.level.getEntity(entityId);
            if (!(entity instanceof LivingEntity living)) return ZealotKillCounter.TargetState.MISSING;
            return !living.isAlive() || living.isRemoved() || living.getHealth() <= 0.0F
                ? ZealotKillCounter.TargetState.DEAD
                : ZealotKillCounter.TargetState.ALIVE;
        });
        if (killCounter.zealotKills() != zealotsBefore || killCounter.bruiserKills() != bruisersBefore) {
            syncKillTotals();
        }
    }

    private static Vec3 predictedAim(LocalPlayer player, LivingEntity target, boolean lead) {
        Vec3 center = new Vec3(
            target.getX(),
            target.getBoundingBox().minY + target.getBbHeight() * 0.62D,
            target.getZ()
        );
        if (!lead) return center;
        double travelTicks = Math.min(8.0D, Math.sqrt(player.distanceToSqr(target)) / 1.35D);
        Vec3 velocity = target.getDeltaMovement();
        return center.add(velocity.x * travelTicks, 0.0D, velocity.z * travelTicks);
    }

    private static boolean isLookingAt(LocalPlayer player, Vec3 target) {
        Vec3 direction = target.subtract(player.getEyePosition());
        return direction.lengthSqr() < 1.0E-8D
            || player.getViewVector(1.0F).normalize().dot(direction.normalize()) >= AIM_COSINE;
    }

    private static double angularDistance(LocalPlayer player, LivingEntity entity) {
        Vec3 direction = predictedAim(player, entity, false).subtract(player.getEyePosition()).normalize();
        return 1.0D - player.getViewVector(1.0F).normalize().dot(direction);
    }

    private void onChatMessage(ChatMessageEvent event) {
        int eyes = SummoningEyeDropDetector.count(event.text(), event.overlay());
        if (eyes > 0) {
            double previous = storedSummoningEyes.get();
            storedSummoningEyes.set(previous + eyes);
            if (storedSummoningEyes.get() != previous) configuration.run();
        }
        String message = event.text().toLowerCase(Locale.ROOT);
        if (message.contains("special") && message.contains("zealot") && message.contains("spawned nearby")) {
            specialAlertTicks = 60;
            targetId = -1;
            attackCooldown = 0;
            skillShot.clear();
        }
    }

    private void syncKillTotals() {
        storedZealotKills.set((double) killCounter.zealotKills());
        storedBruiserKills.set((double) killCounter.bruiserKills());
        configuration.run();
    }

    private void resetCounters() {
        killCounter.resetTotals();
        storedZealotKills.set(0.0D);
        storedBruiserKills.set(0.0D);
        storedSummoningEyes.set(0.0D);
        configuration.run();
    }

    private record ZealotArea(boolean active, boolean bruiser) {
    }
}
