package ru.arzer0.issueisekai.plugin.command;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.ReportJson;

final class InventorySnapshotCapture {
    private static final int MAX_RAW_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TEXT = 8_192;
    private static final int MAX_COMPONENT_JSON = 65_536;

    private InventorySnapshotCapture() {}

    static CreateReportRequest.InventorySnapshot capture(
            Player player,
            String minecraftVersion,
            Logger logger) {
        int selectedSlot = player.getInventory().getHeldItemSlot();
        try {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] storage = copy(inventory.getStorageContents());
            ItemStack[] armor = copy(inventory.getArmorContents());
            ItemStack[] extra = copy(inventory.getExtraContents());
            List<CreateReportRequest.InventorySlot> slots = new ArrayList<>();
            append(slots, storage, InventorySnapshotCapture::storageSlot);
            append(slots, armor, InventorySnapshotCapture::armorSlot);
            if (extra.length > 0 && extra[0] != null && !extra[0].isEmpty()) {
                slots.add(item("offhand", extra[0]));
            }

            List<ItemStack> rawItems = new ArrayList<>(storage.length + armor.length + extra.length);
            java.util.Collections.addAll(rawItems, storage);
            java.util.Collections.addAll(rawItems, armor);
            java.util.Collections.addAll(rawItems, extra);
            byte[] raw = ItemStack.serializeItemsAsBytes(rawItems);
            if (raw.length > MAX_RAW_BYTES) {
                logger.warning("Inventory snapshot too large for " + player.getUniqueId());
                return snapshot(minecraftVersion, selectedSlot, slots, null, "TOO_LARGE");
            }
            logger.fine("Inventory snapshot captured for " + player.getUniqueId());
            return snapshot(
                    minecraftVersion,
                    selectedSlot,
                    slots,
                    Base64.getEncoder().encodeToString(raw),
                    null);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not capture inventory snapshot for " + player.getUniqueId(),
                    exception);
            return snapshot(
                    minecraftVersion,
                    selectedSlot,
                    List.of(),
                    null,
                    "SERIALIZATION_FAILED");
        }
    }

    private static CreateReportRequest.InventorySnapshot snapshot(
            String minecraftVersion,
            int selectedSlot,
            List<CreateReportRequest.InventorySlot> slots,
            String raw,
            String error) {
        return new CreateReportRequest.InventorySnapshot(
                2, minecraftVersion, selectedSlot, slots, raw, error);
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] result = contents.clone();
        for (int index = 0; index < result.length; index++) {
            if (result[index] != null) {
                result[index] = result[index].clone();
            }
        }
        return result;
    }

    private static void append(
            List<CreateReportRequest.InventorySlot> target,
            ItemStack[] contents,
            java.util.function.IntFunction<String> slotName) {
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item != null && !item.isEmpty()) {
                target.add(item(slotName.apply(index), item));
            }
        }
    }

    static String storageSlot(int index) {
        return index < 9 ? "hotbar_" + index : "storage_" + index;
    }

    static String armorSlot(int index) {
        return switch (index) {
            case 0 -> "boots";
            case 1 -> "leggings";
            case 2 -> "chestplate";
            case 3 -> "helmet";
            default -> throw new IllegalArgumentException("Unknown armor slot " + index);
        };
    }

    private static CreateReportRequest.InventorySlot item(String slot, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Integer damage = null;
        Integer maxDamage = null;
        if (meta instanceof Damageable damageable) {
            damage = damageable.getDamage();
            int maximum = damageable.hasMaxDamage()
                    ? damageable.getMaxDamage()
                    : item.getType().getMaxDurability();
            maxDamage = maximum > 0 ? maximum : null;
        }
        String model = meta.hasItemModel() ? meta.getItemModel().toString() : null;
        CreateReportRequest.CustomModelData customModelData = customModelData(meta);
        List<CreateReportRequest.ItemText> lore = meta.hasLore() && meta.lore() != null
                ? meta.lore().stream().limit(128).map(InventorySnapshotCapture::text).toList()
                : List.of();
        List<CreateReportRequest.Enchantment> enchantments = item.getEnchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> new CreateReportRequest.Enchantment(
                        entry.getKey().getKey().toString(), entry.getValue()))
                .toList();
        return new CreateReportRequest.InventorySlot(
                slot,
                item.getType().getKey().toString(),
                item.getAmount(),
                text(item.effectiveName()),
                lore,
                damage,
                maxDamage,
                model,
                customModelData,
                enchantments);
    }

    private static CreateReportRequest.CustomModelData customModelData(ItemMeta meta) {
        if (!meta.hasCustomModelDataComponent()) {
            return null;
        }
        var component = meta.getCustomModelDataComponent();
        return new CreateReportRequest.CustomModelData(
                component.getFloats().stream()
                        .limit(256)
                        .map(Float::doubleValue)
                        .toList(),
                component.getFlags().stream().limit(256).toList(),
                component.getStrings().stream()
                        .limit(256)
                        .map(value -> truncate(value, 1_024))
                        .toList(),
                component.getColors().stream()
                        .limit(256)
                        .map(color -> color.asRGB())
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private static CreateReportRequest.ItemText text(Component component) {
        String plain = truncate(
                PlainTextComponentSerializer.plainText().serialize(component), MAX_TEXT);
        String json = GsonComponentSerializer.gson().serialize(component);
        Map<String, Object> structured = json.length() <= MAX_COMPONENT_JSON
                ? ReportJson.read(json, Map.class)
                : Map.of("text", plain);
        return new CreateReportRequest.ItemText(plain, structured);
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
