package bm.minecraft.elytra.plus;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds, detects and splits combined elytra items. */
final class BmMinecraftElytraPlusItems {
    private final NamespacedKey combinedKey;
    private final NamespacedKey elytraKey;
    private final NamespacedKey armorKey;
    private final NamespacedKey pendingArmorKey;
    private final BmMinecraftElytraPlusLanguageManager language;

    BmMinecraftElytraPlusItems(BmMinecraftElytraPlusPlugin plugin) {
        this.language = plugin.language();
        this.combinedKey = new NamespacedKey(plugin, "combined");
        this.elytraKey = new NamespacedKey(plugin, "elytra");
        this.armorKey = new NamespacedKey(plugin, "armor");
        this.pendingArmorKey = new NamespacedKey(plugin, "pending-armor");
    }

    boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    boolean isElytra(ItemStack item) {
        return !isEmpty(item) && item.getType() == Material.ELYTRA;
    }

    boolean isChestplate(ItemStack item) {
        return !isEmpty(item) && item.getType().name().endsWith("_CHESTPLATE");
    }

    boolean isCombined(ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(combinedKey, PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    boolean isCombinedChestplate(ItemStack item) {
        return isCombined(item) && isChestplate(item);
    }

    boolean hasVanishingCurse(ItemStack item) {
        return isElytra(item) && item.getEnchantmentLevel(Enchantment.VANISHING_CURSE) > 0;
    }

    ItemStack combine(ItemStack first, ItemStack second) {
        if (isCombined(first) || isCombined(second)) {
            return null;
        }
        boolean firstElytra = isElytra(first);
        boolean firstArmor = isChestplate(first);
        boolean secondElytra = isElytra(second);
        boolean secondArmor = isChestplate(second);
        if (!(firstElytra && secondArmor || firstArmor && secondElytra)) {
            return null;
        }
        ItemStack elytra = firstElytra ? first : second;
        ItemStack armor = firstArmor ? first : second;
        ItemStack result = first.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }
        Map<Enchantment, Integer> enchants = new HashMap<>(elytra.getEnchantments());
        for (Map.Entry<Enchantment, Integer> entry : armor.getEnchantments().entrySet()) {
            enchants.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        for (Enchantment enchantment : List.copyOf(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        applyArmorAttributes(meta, armor);
        meta.setLore(List.of(language.get("combined-lore")));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(combinedKey, PersistentDataType.BYTE, (byte) 1);
        data.set(elytraKey, PersistentDataType.BYTE_ARRAY, elytra.clone().serializeAsBytes());
        data.set(armorKey, PersistentDataType.BYTE_ARRAY, armor.clone().serializeAsBytes());
        result.setItemMeta(meta);
        return result;
    }

    ItemStack splitPreview(ItemStack combined) {
        SplitResult split = split(combined);
        if (split == null) {
            return null;
        }
        ItemStack preview = hostOf(combined, split).clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.getPersistentDataContainer().set(pendingArmorKey, PersistentDataType.BYTE_ARRAY, extraOf(combined, split).serializeAsBytes());
        preview.setItemMeta(meta);
        return preview;
    }

    TakenSplit takeSplit(ItemStack preview) {
        if (isEmpty(preview) || !preview.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer data = preview.getItemMeta().getPersistentDataContainer();
        byte[] extraBytes = data.get(pendingArmorKey, PersistentDataType.BYTE_ARRAY);
        if (extraBytes == null) {
            return null;
        }
        ItemStack result = preview.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(pendingArmorKey);
            result.setItemMeta(meta);
        }
        return new TakenSplit(result, ItemStack.deserializeBytes(extraBytes));
    }

    SplitResult split(ItemStack combined) {
        if (!isCombined(combined)) {
            return null;
        }
        PersistentDataContainer data = combined.getItemMeta().getPersistentDataContainer();
        byte[] elytraBytes = data.get(elytraKey, PersistentDataType.BYTE_ARRAY);
        byte[] armorBytes = data.get(armorKey, PersistentDataType.BYTE_ARRAY);
        if (elytraBytes == null || armorBytes == null) {
            return null;
        }
        ItemStack elytra = ItemStack.deserializeBytes(elytraBytes);
        ItemStack armor = ItemStack.deserializeBytes(armorBytes);
        if (combined.getType() == Material.ELYTRA) {
            copyDamage(combined, elytra);
        } else {
            copyDamage(combined, armor);
        }
        return new SplitResult(elytra, armor);
    }

    ItemStack cleanse(ItemStack first, ItemStack second) {
        if (!isPlainVanishingElytra(first) || !isPlainVanishingElytra(second)) {
            return null;
        }
        ItemStack result = new ItemStack(Material.ELYTRA);
        int damage = Math.min(damageOf(first), damageOf(second));
        ItemMeta meta = result.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
            result.setItemMeta(meta);
        }
        return result;
    }

    private boolean isPlainVanishingElytra(ItemStack item) {
        return isElytra(item) && !isCombined(item) && hasVanishingCurse(item);
    }

    private void applyArmorAttributes(ItemMeta dest, ItemStack armor) {
        dest.setAttributeModifiers(null);
        ItemMeta armorMeta = armor.getItemMeta();
        if (armorMeta != null && armorMeta.hasAttributeModifiers()) {
            dest.setAttributeModifiers(armorMeta.getAttributeModifiers());
            return;
        }
        dest.setAttributeModifiers(armor.getType().getDefaultAttributeModifiers(EquipmentSlot.CHEST));
    }

    private void copyDamage(ItemStack from, ItemStack to) {
        if (!(from.getItemMeta() instanceof Damageable fromDamage) || !(to.getItemMeta() instanceof Damageable toDamage)) {
            return;
        }
        toDamage.setDamage(fromDamage.getDamage());
        if (from.getItemMeta().isUnbreakable()) {
            toDamage.setUnbreakable(true);
        }
        to.setItemMeta(toDamage);
    }

    private int damageOf(ItemStack item) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

    private ItemStack hostOf(ItemStack combined, SplitResult split) {
        return combined.getType() == Material.ELYTRA ? split.elytra() : split.armor();
    }

    private ItemStack extraOf(ItemStack combined, SplitResult split) {
        return combined.getType() == Material.ELYTRA ? split.armor() : split.elytra();
    }

    record SplitResult(ItemStack elytra, ItemStack armor) {
    }

    record TakenSplit(ItemStack result, ItemStack extra) {
    }
}
