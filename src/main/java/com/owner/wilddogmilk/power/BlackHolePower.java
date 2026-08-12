package com.owner.wilddogmilk.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlackHolePower {
    private static final int LIFETIME_TICKS = 30 * 20;
    private static final int ENTITY_QUERY_INTERVAL = 2;
    private static final int BLOCK_PULL_INTERVAL = 4;
    private static final int BLOCKS_PER_BATCH = 8;
    private static final int MAX_PULLED_BLOCKS = 1000;
    private static final int BLOCK_SEARCH_ATTEMPTS = 48;
    private static final int MAX_VERTICAL_SEARCH = 12;

    private static final double MAX_PULL_RADIUS = 48.0D;
    private static final double MAX_BLOCK_RADIUS = 32.0D;
    private static final double CORE_RADIUS = 1.75D;
    private static final float COLLAPSE_POWER = 14.0F;

    private static final Map<UUID, ActiveBlackHole> ACTIVE_BLACK_HOLES = new HashMap<>();

    private BlackHolePower() {
    }

    public static void create(ServerPlayer owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }

        ACTIVE_BLACK_HOLES.put(
                owner.getUUID(),
                new ActiveBlackHole(owner.getUUID(), server.getTickCount(), new HashSet<>())
        );
        owner.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                10,
                0,
                false,
                false,
                false
        ));
        owner.level().playSound(
                null,
                owner.blockPosition(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.PLAYERS,
                1.8F,
                0.4F
        );
        owner.displayClientMessage(
                Component.translatable("message.wild_dog_milk.black_hole_created"),
                false
        );
    }

    public static boolean isActive(Entity entity) {
        return ACTIVE_BLACK_HOLES.containsKey(entity.getUUID());
    }

    public static void tick(MinecraftServer server) {
        Iterator<ActiveBlackHole> iterator = ACTIVE_BLACK_HOLES.values().iterator();
        while (iterator.hasNext()) {
            ActiveBlackHole blackHole = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(blackHole.ownerId());
            if (owner == null) {
                iterator.remove();
                continue;
            }

            int age = server.getTickCount() - blackHole.createdAtTick();
            if (age >= LIFETIME_TICKS) {
                iterator.remove();
                collapse(owner);
                continue;
            }

            maintainInvulnerability(owner);
            Vec3 center = corePosition(owner);
            render(owner.serverLevel(), center, age);

            if (age % ENTITY_QUERY_INTERVAL == 0) {
                pullEntities(owner.serverLevel(), owner, center, age);
            }
            if (age % BLOCK_PULL_INTERVAL == 0
                    && blackHole.pulledBlocks().size() < MAX_PULLED_BLOCKS) {
                pullBlocks(owner.serverLevel(), blackHole, center, age);
            }
        }
    }

    public static void clear() {
        ACTIVE_BLACK_HOLES.clear();
    }

    private static void maintainInvulnerability(ServerPlayer owner) {
        owner.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                10,
                0,
                false,
                false,
                false
        ));
        owner.setHealth(owner.getMaxHealth());
        owner.clearFire();
        owner.setAirSupply(owner.getMaxAirSupply());
        owner.fallDistance = 0.0F;
    }

    private static Vec3 corePosition(ServerPlayer owner) {
        return owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D);
    }

    private static void pullEntities(ServerLevel level, ServerPlayer owner, Vec3 center, int age) {
        double radius = Math.min(MAX_PULL_RADIUS, 10.0D + age * 0.12D);
        AABB influenceArea = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);

        for (Entity entity : level.getEntitiesOfClass(
                Entity.class,
                influenceArea,
                entity -> entity.isAlive()
                        && entity != owner
                        && !EternityPower.isActive(entity)
        )) {
            Vec3 targetPoint = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 towardCore = center.subtract(targetPoint);
            double distance = towardCore.length();
            if (distance > radius) {
                continue;
            }

            if (distance <= CORE_RADIUS) {
                consume(level, entity);
                continue;
            }

            double proximity = 1.0D - distance / radius;
            double pullStrength = 0.14D + proximity * proximity * 0.9D;
            Vec3 radialPull = towardCore.normalize().scale(pullStrength);
            Vec3 spiral = new Vec3(-towardCore.z, 0.0D, towardCore.x);
            if (spiral.lengthSqr() > 0.001D) {
                spiral = spiral.normalize().scale(0.08D + proximity * 0.12D);
            }

            entity.setDeltaMovement(entity.getDeltaMovement()
                    .scale(0.7D)
                    .add(radialPull)
                    .add(spiral));
            entity.hasImpulse = true;
        }
    }

    private static void pullBlocks(
            ServerLevel level,
            ActiveBlackHole blackHole,
            Vec3 center,
            int age
    ) {
        double radius = currentBlockRadius(age);
        BlockPos centerBlock = BlockPos.containing(center);
        int pulledThisBatch = 0;

        for (int attempt = 0;
             attempt < BLOCK_SEARCH_ATTEMPTS && pulledThisBatch < BLOCKS_PER_BATCH;
             attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double horizontalDistance = 2.0D
                    + Math.sqrt(level.random.nextDouble()) * Math.max(1.0D, radius - 2.0D);
            int xOffset = (int) Math.round(Math.cos(angle) * horizontalDistance);
            int zOffset = (int) Math.round(Math.sin(angle) * horizontalDistance);
            int verticalSearch = Math.min(MAX_VERTICAL_SEARCH, Math.max(4, (int) Math.ceil(radius * 0.5D)));
            BlockPos candidate = findPullableBlock(
                    level,
                    blackHole,
                    centerBlock,
                    xOffset,
                    zOffset,
                    verticalSearch
            );
            if (candidate == null) {
                continue;
            }

            PulledBlockKey blockKey = new PulledBlockKey(level.dimension().location().toString(), candidate.asLong());
            BlockState state = level.getBlockState(candidate);

            blackHole.pulledBlocks().add(blockKey);
            FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, candidate, state);
            fallingBlock.dropItem = false;
            fallingBlock.setHurtsEntities(0.0F, 0);
            Vec3 towardCore = center.subtract(fallingBlock.position()).normalize();
            fallingBlock.setDeltaMovement(towardCore.scale(0.68D));
            fallingBlock.hasImpulse = true;
            pulledThisBatch++;
        }
    }

    private static BlockPos findPullableBlock(
            ServerLevel level,
            ActiveBlackHole blackHole,
            BlockPos center,
            int xOffset,
            int zOffset,
            int verticalSearch
    ) {
        String dimension = level.dimension().location().toString();
        for (int yOffset = verticalSearch; yOffset >= -verticalSearch; yOffset--) {
            BlockPos candidate = center.offset(xOffset, yOffset, zOffset);
            PulledBlockKey blockKey = new PulledBlockKey(dimension, candidate.asLong());
            if (blackHole.pulledBlocks().contains(blockKey)) {
                continue;
            }

            BlockState state = level.getBlockState(candidate);
            if (!state.isAir()
                    && state.getFluidState().isEmpty()
                    && state.getDestroySpeed(level, candidate) >= 0.0F
                    && !state.hasBlockEntity()) {
                return candidate;
            }
        }
        return null;
    }

    private static double currentBlockRadius(int age) {
        return Math.min(MAX_BLOCK_RADIUS, 6.0D + age * 0.08D);
    }

    private static void consume(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity living) {
            living.hurt(level.damageSources().fellOutOfWorld(), 1000.0F);
            return;
        }
        entity.discard();
    }

    private static void render(ServerLevel level, Vec3 center, int age) {
        if (age % 2 != 0) {
            return;
        }

        level.sendParticles(
                ParticleTypes.SQUID_INK,
                center.x,
                center.y,
                center.z,
                10,
                0.45D,
                0.45D,
                0.45D,
                0.01D
        );
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.x,
                center.y,
                center.z,
                26,
                2.8D,
                1.7D,
                2.8D,
                0.22D
        );

        double rotation = age * 0.16D;
        for (int index = 0; index < 12; index++) {
            double angle = rotation + index * Math.PI * 2.0D / 12.0D;
            double radius = index % 2 == 0 ? 2.4D : 3.25D;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + Math.sin(angle * 2.0D) * 0.35D;
            level.sendParticles(
                    index % 3 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.PORTAL,
                    x,
                    y,
                    z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }

        if (age % 4 == 0) {
            double boundaryRadius = currentBlockRadius(age);
            for (int index = 0; index < 16; index++) {
                double angle = -rotation * 0.45D + index * Math.PI * 2.0D / 16.0D;
                double x = center.x + Math.cos(angle) * boundaryRadius;
                double z = center.z + Math.sin(angle) * boundaryRadius;
                double y = center.y + Math.sin(angle * 3.0D) * 1.2D;
                level.sendParticles(
                        index % 4 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.REVERSE_PORTAL,
                        x,
                        y,
                        z,
                        2,
                        0.18D,
                        0.18D,
                        0.18D,
                        0.02D
                );
            }
        }

        if (age % 20 == 0) {
            level.playSound(
                    null,
                    center.x,
                    center.y,
                    center.z,
                    SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS,
                    1.2F,
                    0.45F
            );
        }
    }

    private static void collapse(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        Vec3 center = corePosition(owner);

        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x,
                center.y,
                center.z,
                8,
                1.2D,
                1.2D,
                1.2D,
                0.0D
        );
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.x,
                center.y,
                center.z,
                420,
                6.0D,
                6.0D,
                6.0D,
                0.9D
        );
        level.explode(
                owner,
                center.x,
                center.y,
                center.z,
                COLLAPSE_POWER,
                Level.ExplosionInteraction.BLOCK
        );

        owner.displayClientMessage(
                Component.translatable("message.wild_dog_milk.black_hole_collapsed"),
                true
        );
        if (owner.isAlive()) {
            owner.kill();
        }
    }

    private record ActiveBlackHole(
            UUID ownerId,
            int createdAtTick,
            Set<PulledBlockKey> pulledBlocks
    ) {
    }

    private record PulledBlockKey(String dimension, long position) {
    }
}
