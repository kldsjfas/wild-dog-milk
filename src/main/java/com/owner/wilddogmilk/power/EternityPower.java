package com.owner.wilddogmilk.power;

import com.owner.wilddogmilk.WildDogMilkMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

public final class EternityPower {
    private static final String ACTIVE_TAG = "wild_dog_milk_eternity";
    private static final String SAUSAGE_EXECUTION_TAG = "wild_dog_milk_sausage_execution";

    private EternityPower() {
    }

    public static void grant(ServerPlayer player) {
        grant((LivingEntity) player);
        player.getPersistentData().remove(SAUSAGE_EXECUTION_TAG);
        player.displayClientMessage(
                Component.translatable("message.wild_dog_milk.eternity_granted"),
                false
        );
    }

    public static void grant(LivingEntity entity) {
        entity.getPersistentData().putBoolean(ACTIVE_TAG, true);
        refreshMarker(entity);
        refreshNightVision(entity);
        entity.setHealth(entity.getMaxHealth());
    }

    public static void grantToWolf(LivingEntity wolf, ServerPlayer owner) {
        grant(wolf);
        owner.displayClientMessage(
                Component.translatable("message.wild_dog_milk.wolf_eternity_granted", wolf.getDisplayName()),
                false
        );
    }

    public static boolean isActive(Entity entity) {
        return entity instanceof LivingEntity livingEntity
                && livingEntity.getPersistentData().getBoolean(ACTIVE_TAG);
    }

    public static boolean canDie(Entity entity) {
        return entity instanceof LivingEntity livingEntity
                && livingEntity.getPersistentData().getBoolean(SAUSAGE_EXECUTION_TAG);
    }

    public static boolean isProtectedAlly(LivingEntity powerOwner, LivingEntity target) {
        if (powerOwner == target || isActive(target)) {
            return true;
        }
        if (powerOwner instanceof ServerPlayer player
                && target instanceof TamableAnimal pet
                && pet.isOwnedBy(player)) {
            return true;
        }
        if (powerOwner instanceof TamableAnimal pet) {
            if (pet.getOwnerUUID() == null) {
                return false;
            }
            if (pet.getOwnerUUID().equals(target.getUUID())) {
                return true;
            }
            return target instanceof TamableAnimal otherPet
                    && pet.getOwnerUUID().equals(otherPet.getOwnerUUID());
        }
        return false;
    }

    public static void maintain(LivingEntity entity) {
        if (!isActive(entity)) {
            return;
        }

        refreshMarker(entity);
        refreshNightVision(entity);
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), 4.0F));
        entity.clearFire();
        entity.setAirSupply(entity.getMaxAirSupply());
    }

    public static void preventDeath(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
        entity.clearFire();
        entity.fallDistance = 0.0F;
    }

    public static void executeWithSausage(ServerPlayer player) {
        player.getPersistentData().remove(ACTIVE_TAG);
        player.removeEffect(WildDogMilkMod.ETERNITY.get());
        player.getPersistentData().putBoolean(SAUSAGE_EXECUTION_TAG, true);
        player.displayClientMessage(
                Component.translatable("message.wild_dog_milk.sausage_breaks_eternity"),
                false
        );

        player.kill();
    }

    public static void copyAfterClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath) {
        if (!oldPlayer.getPersistentData().getBoolean(ACTIVE_TAG)) {
            return;
        }

        newPlayer.getPersistentData().putBoolean(ACTIVE_TAG, true);
        refreshMarker(newPlayer);
        refreshNightVision(newPlayer);
        if (wasDeath) {
            newPlayer.connection.disconnect(
                    Component.translatable("disconnect.wild_dog_milk.forced_death")
            );
        }
    }

    private static void refreshMarker(LivingEntity entity) {
        MobEffectInstance marker = entity.getEffect(WildDogMilkMod.ETERNITY.get());
        if (marker == null || marker.getDuration() < 40) {
            entity.addEffect(new MobEffectInstance(
                    WildDogMilkMod.ETERNITY.get(),
                    220,
                    0,
                    false,
                    true,
                    true
            ));
        }
    }

    private static void refreshNightVision(LivingEntity entity) {
        MobEffectInstance nightVision = entity.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 220) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    320,
                    0,
                    false,
                    true,
                    true
            ));
        }
    }
}
