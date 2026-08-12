package com.owner.wilddogmilk;

import com.owner.wilddogmilk.item.WildDogMilkItem;
import com.owner.wilddogmilk.power.EternityPower;
import com.owner.wilddogmilk.power.BlackHolePower;
import com.owner.wilddogmilk.power.PlayerRewind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class GameEvents {
    private static final double ETERNITY_AURA_RADIUS = 48.0D;
    private static final double ENDER_DRAGON_AURA_RADIUS = 192.0D;
    private static final float ETERNITY_AURA_DAMAGE = 60.0F;
    private static final long EXTRA_DAYTIME_TICKS = 99L;
    private static final String LAST_AURA_INTERVAL_TAG = "wild_dog_milk_last_aura_interval";
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Long>
            LAST_TIME_ACCELERATION = new HashMap<>();
    private static final Set<String> DOG_MILK_CHESTS = Set.of(
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/buried_treasure",
            "minecraft:chests/desert_pyramid",
            "minecraft:chests/ruined_portal",
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/stronghold_library"
    );

    private GameEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWolfFed(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Wolf wolf)
                || !event.getItemStack().is(WildDogMilkMod.WILD_DOG_MILK.get())) {
            return;
        }

        InteractionResult result = WildDogMilkItem.feedWolf(
                event.getItemStack(),
                event.getEntity(),
                wolf
        );
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        EternityPower.maintain(player);
        accelerateTime(player);
        applyEternityAura(player);
        PlayerRewind.record(player);
        attractWolves(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BlackHolePower.tick(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }
        EternityPower.maintain(event.getEntity());
        accelerateTime(event.getEntity());
        applyEternityAura(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (BlackHolePower.isActive(event.getEntity())
                || (EternityPower.isActive(event.getEntity())
                && !EternityPower.canDie(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (BlackHolePower.isActive(event.getEntity())
                || (EternityPower.isActive(event.getEntity())
                && !EternityPower.canDie(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (BlackHolePower.isActive(event.getEntity())
                || (EternityPower.isActive(event.getEntity())
                && !EternityPower.canDie(event.getEntity()))) {
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (BlackHolePower.isActive(event.getEntity())) {
            event.setCanceled(true);
            EternityPower.preventDeath(event.getEntity());
            return;
        }
        if (!EternityPower.isActive(event.getEntity()) || EternityPower.canDie(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        EternityPower.preventDeath(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!EternityPower.isActive(event.getEntity())) {
            return;
        }

        if (event.getEffect() == WildDogMilkMod.ETERNITY.get()
                || event.getEffect() == MobEffects.NIGHT_VISION) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldPlayer
                && event.getEntity() instanceof ServerPlayer newPlayer) {
            event.getOriginal().reviveCaps();
            EternityPower.copyAfterClone(oldPlayer, newPlayer, event.isWasDeath());
            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerRewind.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        PlayerRewind.clear();
        BlackHolePower.clear();
        LAST_TIME_ACCELERATION.clear();
    }

    @SubscribeEvent
    public static void addWildDogMilkToLoot(LootTableLoadEvent event) {
        if (!DOG_MILK_CHESTS.contains(event.getName().toString())) {
            return;
        }

        LootPool pool = LootPool.lootPool()
                .name("wild_dog_milk_bonus")
                .when(LootItemRandomChanceCondition.randomChance(0.18F))
                .add(LootItem.lootTableItem(WildDogMilkMod.WILD_DOG_MILK.get()))
                .build();
        event.getTable().addPool(pool);
    }

    @SubscribeEvent
    public static void addWanderingTraderOffer(WandererTradesEvent event) {
        event.getGenericTrades().add((trader, random) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, 5),
                new ItemStack(WildDogMilkMod.WILD_DOG_MILK.get()),
                5,
                4,
                0.05F
        ));
    }

    private static void attractWolves(ServerPlayer player) {
        if (player.tickCount % 10 != 0 || player.isSpectator()) {
            return;
        }
        boolean holdingMilk = player.getMainHandItem().is(WildDogMilkMod.WILD_DOG_MILK.get())
                || player.getOffhandItem().is(WildDogMilkMod.WILD_DOG_MILK.get());
        if (!holdingMilk) {
            return;
        }

        for (Wolf wolf : player.level().getEntitiesOfClass(
                Wolf.class,
                player.getBoundingBox().inflate(16.0D),
                wolf -> !wolf.isOrderedToSit() && (!wolf.isTame() || wolf.isOwnedBy(player))
        )) {
            wolf.getNavigation().moveTo(player, 1.25D);
        }
    }

    private static void applyEternityAura(LivingEntity owner) {
        if (!EternityPower.isActive(owner)
                || !(owner.level() instanceof ServerLevel level)
                || level.getGameTime() % 20L != 0L) {
            return;
        }

        long auraInterval = level.getGameTime() / 20L;
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(ETERNITY_AURA_RADIUS),
                target -> target.isAlive()
                        && !(target instanceof EnderDragon)
                        && !EternityPower.isProtectedAlly(owner, target)
        )) {
            if (target.getPersistentData().getLong(LAST_AURA_INTERVAL_TAG) == auraInterval) {
                continue;
            }
            target.getPersistentData().putLong(LAST_AURA_INTERVAL_TAG, auraInterval);
            target.hurt(level.damageSources().magic(), ETERNITY_AURA_DAMAGE);
        }

        hurtNearbyEnderDragons(owner, level, auraInterval);

    }

    private static void hurtNearbyEnderDragons(
            LivingEntity owner,
            ServerLevel level,
            long auraInterval
    ) {
        DamageSource dragonDamage = dragonDamageSource(owner, level);
        for (EnderDragon dragon : level.getEntitiesOfClass(
                EnderDragon.class,
                owner.getBoundingBox().inflate(ENDER_DRAGON_AURA_RADIUS),
                EnderDragon::isAlive
        )) {
            if (!isDragonPartWithinRange(owner, dragon)
                    || dragon.getPersistentData().getLong(LAST_AURA_INTERVAL_TAG) == auraInterval) {
                continue;
            }
            dragon.getPersistentData().putLong(LAST_AURA_INTERVAL_TAG, auraInterval);
            dragon.hurt(dragon.head, dragonDamage, ETERNITY_AURA_DAMAGE);
        }
    }

    private static boolean isDragonPartWithinRange(LivingEntity owner, EnderDragon dragon) {
        double maximumDistanceSquared = ENDER_DRAGON_AURA_RADIUS * ENDER_DRAGON_AURA_RADIUS;
        if (owner.distanceToSqr(dragon) <= maximumDistanceSquared) {
            return true;
        }
        for (net.minecraft.world.entity.boss.EnderDragonPart part : dragon.getSubEntities()) {
            if (owner.distanceToSqr(part) <= maximumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static DamageSource dragonDamageSource(LivingEntity owner, ServerLevel level) {
        if (owner instanceof ServerPlayer player) {
            return level.damageSources().playerAttack(player);
        }
        if (owner instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer player) {
            return level.damageSources().playerAttack(player);
        }
        return level.damageSources().genericKill();
    }

    private static void accelerateTime(LivingEntity owner) {
        if (!EternityPower.isActive(owner)
                || !(owner.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTick = level.getGameTime();
        Long lastAcceleratedTick = LAST_TIME_ACCELERATION.put(level.dimension(), gameTick);
        if (lastAcceleratedTick == null || lastAcceleratedTick != gameTick) {
            level.setDayTime(level.getDayTime() + EXTRA_DAYTIME_TICKS);
        }
    }
}
