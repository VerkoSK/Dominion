package dev.nationsforge.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Nation Coin — the universal currency of the Dominion economy.
 *
 * Crafted from gold:  1 Gold Ingot → 9 Nation Coins (and back again).
 * Nations accrue coins automatically each server tick-cycle based on
 * territory, active members and diplomatic relations.
 *
 * Players deposit coins into their national treasury via:
 *   /nation deposit <amount>
 * Sovereign / Chancellor ranks may withdraw via:
 *   /nation withdraw <amount>
 */
public class NationCoinItem extends Item {

    public NationCoinItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§6National currency of the Dominion economy."));
        tooltip.add(Component.literal("§8───────────────────────────────"));
        tooltip.add(Component.literal("§7Craft: §e1 Gold Ingot §7→ §69 Coins"));
        tooltip.add(Component.literal("§7Deposit: §e/nation deposit <amount>"));
        tooltip.add(Component.literal("§7Withdraw: §e/nation withdraw <amount>"));
    }
}
