package com.owner.wilddogmilk.item;

import com.owner.wilddogmilk.WildDogMilkMod;
import com.owner.wilddogmilk.power.EternityPower;
import com.owner.wilddogmilk.power.PlayerRewind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SpringAutumnSausageItem extends Item {
    public SpringAutumnSausageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!(livingEntity instanceof net.minecraft.server.level.ServerPlayer player)) {
            return result;
        }

        level.playSound(
                null,
                player.blockPosition(),
                WildDogMilkMod.SAUSAGE_BGM.get(),
                SoundSource.RECORDS,
                1.25F,
                1.0F
        );

        if (EternityPower.isActive(player)) {
            EternityPower.executeWithSausage(player);
            return result;
        }

        int rewindSeconds = 10 + player.getRandom().nextInt(51);
        PlayerRewind.rewind(player, rewindSeconds);
        return result;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.sausage")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.wild_dog_milk.sausage_eternity")
                .withStyle(ChatFormatting.GRAY));
    }
}
