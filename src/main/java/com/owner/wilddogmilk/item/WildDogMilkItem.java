package com.owner.wilddogmilk.item;

import com.owner.wilddogmilk.WildDogMilkMod;
import com.owner.wilddogmilk.power.EternityPower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WildDogMilkItem extends Item {
    private static final String[] DRINK_MESSAGES = {
            "message.wild_dog_milk.drink.0",
            "message.wild_dog_milk.drink.1",
            "message.wild_dog_milk.drink.2",
            "message.wild_dog_milk.drink.3"
    };

    public WildDogMilkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 36;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!(livingEntity instanceof Player player)) {
            return result;
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!level.isClientSide) {
            EternityPower.grant((net.minecraft.server.level.ServerPlayer) player);
            level.playSound(
                    null,
                    player.blockPosition(),
                    WildDogMilkMod.DRINK_BGM.get(),
                    SoundSource.RECORDS,
                    1.25F,
                    1.0F
            );

            RandomSource random = player.getRandom();
            String messageKey = DRINK_MESSAGES[random.nextInt(DRINK_MESSAGES.length)];
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        return result;
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (!(target instanceof Wolf wolf)) {
            return InteractionResult.PASS;
        }
        return feedWolf(stack, player, wolf);
    }

    public static InteractionResult feedWolf(ItemStack milk, Player player, Wolf wolf) {
        if (!wolf.isTame()) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.wild_dog_milk.wolf_not_tamed"),
                        true
                );
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!wolf.isOwnedBy(player)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.wild_dog_milk.wolf_not_owned"),
                        true
                );
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        EternityPower.grantToWolf(wolf, (net.minecraft.server.level.ServerPlayer) player);
        wolf.setOrderedToSit(false);
        if (!player.getAbilities().instabuild) {
            milk.shrink(1);
        }

        player.level().playSound(
                null,
                wolf.blockPosition(),
                WildDogMilkMod.DRINK_BGM.get(),
                SoundSource.RECORDS,
                1.1F,
                1.0F
        );
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HEART,
                    wolf.getX(),
                    wolf.getY() + 0.8D,
                    wolf.getZ(),
                    12,
                    0.35D,
                    0.35D,
                    0.35D,
                    0.05D
            );
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.shelf_life")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.origin")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.eternity")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.wolf")
                .withStyle(ChatFormatting.AQUA));
    }
}
