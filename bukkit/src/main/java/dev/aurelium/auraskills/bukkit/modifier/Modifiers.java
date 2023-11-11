package dev.aurelium.auraskills.bukkit.modifier;

import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.stat.Stat;
import dev.aurelium.auraskills.api.stat.StatModifier;
import dev.aurelium.auraskills.api.util.NumberUtil;
import dev.aurelium.auraskills.bukkit.AuraSkills;
import dev.aurelium.auraskills.bukkit.util.ItemUtils;
import dev.aurelium.auraskills.common.message.type.CommandMessage;
import dev.aurelium.auraskills.common.util.text.TextUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class Modifiers {
    
    private final AuraSkills plugin;

    public Modifiers(AuraSkills plugin) {
        this.plugin = plugin;
    }

    public ItemStack addModifier(ModifierType type, ItemStack item, Stat stat, double value) {
        if (plugin.isNbtApiDisabled()) return item;
        NBTItem nbtItem = new NBTItem(item);
        NBTCompound compound = ItemUtils.getModifiersTypeCompound(nbtItem, type);
        compound.setDouble(getName(stat), value);
        return nbtItem.getItem();
    }

    public ItemStack convertFromLegacy(ItemStack item) {
        if (plugin.isNbtApiDisabled()) return item;
        NBTItem nbtItem = new NBTItem(item);
        boolean modified = false;
        for (ModifierType type : ModifierType.values()) {
            List<StatModifier> legacyModifiers = getLegacyModifiers(type, nbtItem);
            if (!legacyModifiers.isEmpty()) {
                NBTCompound compound = ItemUtils.getModifiersTypeCompound(nbtItem, type);
                for (StatModifier modifier : legacyModifiers) {
                    compound.setDouble(getName(modifier.stat()), modifier.value());
                    modified = true;
                }
            }
        }
        if (modified) {
            return nbtItem.getItem();
        } else {
            return item;
        }
    }

    public ItemStack removeModifier(ModifierType type, ItemStack item, Stat stat) {
        if (plugin.isNbtApiDisabled()) return item;
        NBTItem nbtItem = new NBTItem(item);
        NBTCompound compound = ItemUtils.getModifiersTypeCompound(nbtItem, type);
        compound.removeKey(getName(stat));
        ItemUtils.removeParentCompounds(compound);
        return nbtItem.getItem();
    }

    public ItemStack removeAllModifiers(ModifierType type, ItemStack item) {
        if (plugin.isNbtApiDisabled()) return item;
        NBTItem nbtItem = new NBTItem(item);
        NBTCompound compound = ItemUtils.getModifiersTypeCompound(nbtItem, type);
        for (String key : compound.getKeys()) {
            compound.removeKey(key);
        }
        ItemUtils.removeParentCompounds(compound);
        return nbtItem.getItem();
    }

    public List<StatModifier> getLegacyModifiers(ModifierType type, NBTItem nbtItem) {
        if (plugin.isNbtApiDisabled()) return new ArrayList<>();
        List<StatModifier> modifiers = new ArrayList<>();
        NBTCompound compound = ItemUtils.getLegacyModifiersTypeCompound(nbtItem, type);
        for (String key : compound.getKeys()) {
            Stat stat = plugin.getStatRegistry().getOrNull(NamespacedId.fromDefault(key));
            if (stat != null) {
                int value = nbtItem.getInteger(key);
                modifiers.add(new StatModifier(key, stat, value));
            }
        }
        return modifiers;
    }

    public List<StatModifier> getModifiers(ModifierType type, ItemStack item) {
        if (plugin.isNbtApiDisabled()) return new ArrayList<>();
        NBTItem nbtItem = new NBTItem(item);
        List<StatModifier> modifiers = new ArrayList<>();
        NBTCompound compound = ItemUtils.getModifiersTypeCompound(nbtItem, type);
        for (String key : compound.getKeys()) {
            Stat stat = plugin.getStatRegistry().getOrNull(NamespacedId.fromDefault(key));
            if (stat != null) {
                double value = compound.getDouble(key);
                if (type == ModifierType.ITEM) {
                    modifiers.add(new StatModifier("AuraSkills.Modifiers.Item." + getName(stat), stat, value));
                } else if (type == ModifierType.ARMOR) {
                    String slot = "Helmet";
                    String mat = item.getType().toString();
                    if (mat.contains("CHESTPLATE")) {
                        slot = "Chestplate";
                    } else if (mat.contains("LEGGINGS")) {
                        slot = "Leggings";
                    } else if (mat.contains("BOOTS")) {
                        slot = "Boots";
                    }
                    modifiers.add(new StatModifier("AuraSkills.Modifiers.Armor." + slot + "." + getName(stat), stat, value));
                }
            }
        }
        return modifiers;
    }

    public void addLore(ModifierType type, ItemStack item, Stat stat, double value, Locale locale) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore;
            if (meta.getLore() != null) {
                if (!meta.getLore().isEmpty()) lore = meta.getLore();
                else lore = new LinkedList<>();
            }
            else {
                lore = new LinkedList<>();
            }
            CommandMessage message;
            if (value >= 0) {
                message = CommandMessage.valueOf(type.name() + "_MODIFIER_ADD_LORE");
            } else {
                message = CommandMessage.valueOf(type.name() + "_MODIFIER_ADD_LORE_SUBTRACT");
            }
            lore.add(0, com.archyx.polyglot.util.TextUtil.applyColor(TextUtil.replace(plugin.getMsg(message, locale),
                    "{stat}", stat.getDisplayName(locale),
                    "{value}", NumberUtil.format1(Math.abs(value)),
                    "{color}", stat.getColor(locale))));
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    public void removeLore(ItemStack item, Stat stat, Locale locale) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) lore.removeIf(line -> line.contains(stat.getDisplayName(locale)));
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    private String getName(Stat stat) {
        return TextUtil.capitalize(stat.name().toLowerCase(Locale.ROOT));
    }
    
}
