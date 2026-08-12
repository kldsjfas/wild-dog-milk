package com.owner.wilddogmilk;

import com.owner.wilddogmilk.effect.EternityEffect;
import com.owner.wilddogmilk.item.ChrysanthemumDrinkItem;
import com.owner.wilddogmilk.item.SpringAutumnSausageItem;
import com.owner.wilddogmilk.item.WildDogMilkItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(WildDogMilkMod.MOD_ID)
public final class WildDogMilkMod {
    public static final String MOD_ID = "wild_dog_milk";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<SoundEvent> DRINK_BGM = SOUNDS.register(
            "drink_bgm",
            () -> SoundEvent.createVariableRangeEvent(id("drink_bgm"))
    );

    public static final RegistryObject<SoundEvent> SAUSAGE_BGM = SOUNDS.register(
            "sausage_bgm",
            () -> SoundEvent.createVariableRangeEvent(id("sausage_bgm"))
    );

    public static final RegistryObject<MobEffect> ETERNITY = EFFECTS.register(
            "eternity",
            () -> new EternityEffect(MobEffectCategory.BENEFICIAL, 0x3AA6D8)
    );

    public static final RegistryObject<Item> WILD_DOG_MILK = ITEMS.register(
            "wild_dog_milk",
            () -> new WildDogMilkItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationMod(0.8F)
                            .alwaysEat()
                            .build()))
    );

    public static final RegistryObject<Item> SPRING_AUTUMN_SAUSAGE = ITEMS.register(
            "spring_autumn_sausage",
            () -> new SpringAutumnSausageItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .food(new FoodProperties.Builder()
                            .nutrition(8)
                            .saturationMod(1.0F)
                            .meat()
                            .alwaysEat()
                            .build()))
    );

    public static final RegistryObject<Item> CHRYSANTHEMUM_DRINK = ITEMS.register(
            "chrysanthemum_drink",
            () -> new ChrysanthemumDrinkItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .food(new FoodProperties.Builder()
                            .nutrition(2)
                            .saturationMod(0.2F)
                            .alwaysEat()
                            .build()))
    );

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wild_dog_milk"))
                    .icon(() -> new ItemStack(WILD_DOG_MILK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(WILD_DOG_MILK.get());
                        output.accept(SPRING_AUTUMN_SAUSAGE.get());
                        output.accept(CHRYSANTHEMUM_DRINK.get());
                    })
                    .build()
    );

    public WildDogMilkMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        CREATIVE_TABS.register(modBus);
        MinecraftForge.EVENT_BUS.register(GameEvents.class);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
