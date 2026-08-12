package com.owner.wilddogmilk.power;

import com.owner.wilddogmilk.WildDogMilkMod;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerRewind {
    private static final int SNAPSHOT_INTERVAL = 20;
    private static final int HISTORY_TICKS = 65 * 20;
    private static final Map<UUID, Deque<PlayerSnapshot>> HISTORY = new HashMap<>();

    private PlayerRewind() {
    }

    public static void record(ServerPlayer player) {
        if (player.tickCount % SNAPSHOT_INTERVAL != 0) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Deque<PlayerSnapshot> snapshots = HISTORY.computeIfAbsent(
                player.getUUID(),
                ignored -> new ArrayDeque<>()
        );
        int currentTick = server.getTickCount();
        snapshots.addLast(PlayerSnapshot.capture(player, currentTick));

        while (!snapshots.isEmpty()
                && currentTick - snapshots.peekFirst().serverTick() > HISTORY_TICKS) {
            snapshots.removeFirst();
        }
    }

    public static void rewind(ServerPlayer player, int seconds) {
        MinecraftServer server = player.getServer();
        Deque<PlayerSnapshot> snapshots = HISTORY.get(player.getUUID());
        if (server == null || snapshots == null || snapshots.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.wild_dog_milk.rewind_no_history"),
                    true
            );
            return;
        }

        int targetTick = server.getTickCount() - seconds * 20;
        PlayerSnapshot chosen = snapshots.peekFirst();
        for (PlayerSnapshot snapshot : snapshots) {
            if (snapshot.serverTick() > targetTick) {
                break;
            }
            chosen = snapshot;
        }

        chosen.restore(player, server);
        consumeOneSausage(player.getInventory());
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(
                Component.translatable("message.wild_dog_milk.rewound", seconds),
                false
        );

        int restoredTick = chosen.serverTick();
        snapshots.removeIf(snapshot -> snapshot.serverTick() > restoredTick);
    }

    public static void forget(UUID playerId) {
        HISTORY.remove(playerId);
    }

    public static void clear() {
        HISTORY.clear();
    }

    private static void consumeOneSausage(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(WildDogMilkMod.SPRING_AUTUMN_SAUSAGE.get())) {
                stack.shrink(1);
                return;
            }
        }
    }

    private record PlayerSnapshot(
            int serverTick,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Vec3 velocity,
            float health,
            float absorption,
            int food,
            float saturation,
            float exhaustion,
            int experienceLevel,
            int totalExperience,
            float experienceProgress,
            int selectedSlot,
            int remainingFireTicks,
            int airSupply,
            ListTag inventory,
            List<MobEffectInstance> effects
    ) {
        private static PlayerSnapshot capture(ServerPlayer player, int serverTick) {
            ListTag savedInventory = player.getInventory().save(new ListTag());
            Collection<MobEffectInstance> currentEffects = player.getActiveEffects();
            List<MobEffectInstance> savedEffects = new ArrayList<>(currentEffects.size());
            for (MobEffectInstance effect : currentEffects) {
                savedEffects.add(new MobEffectInstance(effect));
            }

            return new PlayerSnapshot(
                    serverTick,
                    player.level().dimension(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getDeltaMovement(),
                    player.getHealth(),
                    player.getAbsorptionAmount(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel(),
                    player.getFoodData().getExhaustionLevel(),
                    player.experienceLevel,
                    player.totalExperience,
                    player.experienceProgress,
                    player.getInventory().selected,
                    player.getRemainingFireTicks(),
                    player.getAirSupply(),
                    savedInventory,
                    savedEffects
            );
        }

        private void restore(ServerPlayer player, MinecraftServer server) {
            ServerLevel destination = server.getLevel(dimension);
            if (destination != null) {
                player.teleportTo(destination, x, y, z, yaw, pitch);
            }

            player.setDeltaMovement(velocity);
            player.setHealth(Math.max(1.0F, Math.min(health, player.getMaxHealth())));
            player.setAbsorptionAmount(absorption);
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
            player.getFoodData().setExhaustion(exhaustion);
            player.experienceLevel = experienceLevel;
            player.totalExperience = totalExperience;
            player.experienceProgress = experienceProgress;
            player.connection.send(new ClientboundSetExperiencePacket(
                    experienceProgress,
                    totalExperience,
                    experienceLevel
            ));
            player.getInventory().load(inventory.copy());
            player.getInventory().selected = selectedSlot;
            player.setRemainingFireTicks(remainingFireTicks);
            player.setAirSupply(airSupply);
            player.fallDistance = 0.0F;

            player.removeAllEffects();
            for (MobEffectInstance effect : effects) {
                player.addEffect(new MobEffectInstance(effect));
            }
        }
    }
}
