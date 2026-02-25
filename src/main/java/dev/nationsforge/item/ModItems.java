package dev.nationsforge.item;

import dev.nationsforge.NationsForge;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Central registry for all Dominion items.
 * Register via {@code ModItems.ITEMS.register(modBus)} in the mod constructor.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NationsForge.MOD_ID);

    /** The national currency — crafted from gold, deposited into the treasury. */
    public static final RegistryObject<Item> NATION_COIN = ITEMS.register("nation_coin",
            () -> new NationCoinItem(new Item.Properties().stacksTo(64)));
}
