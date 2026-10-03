package bm.minecraft.elytra.plus;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Combines elytra with chestplates, splits them, and cleanses vanishing curses. */
public final class BmMinecraftElytraPlusPlugin extends JavaPlugin implements Listener {
    public static final String USE_PERMISSION = "bm-minecraft-elytra-plus.use";
    private static final String ADMIN_PERMISSION = "bm-minecraft-elytra-plus.admin";

    private BmMinecraftElytraPlusLanguageManager languageManager;
    private BmMinecraftElytraPlusItems items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new BmMinecraftElytraPlusLanguageManager(this);
        languageManager.reload();
        items = new BmMinecraftElytraPlusItems(this);
        PluginCommand command = getCommand("bm-minecraft-elytra-plus");
        if (command == null) {
            throw new IllegalStateException(languageManager.getConsole("missing-command")
                    .replace("{command}", "bm-minecraft-elytra-plus"));
        }
        BmMinecraftElytraPlusCommand executor = new BmMinecraftElytraPlusCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(languageManager.getConsole("enabled").replace("{version}", getPluginMeta().getVersion()));
    }

    @Override
    public void onDisable() {
        if (languageManager != null) {
            getLogger().info(languageManager.getConsole("disabled"));
        }
    }

    public BmMinecraftElytraPlusLanguageManager language() {
        return languageManager;
    }

    public boolean isFeatureEnabled() {
        return getConfig().getBoolean("enabled", true);
    }

    public boolean canManage(CommandSender sender) {
        return !(sender instanceof Player)
                || !getConfig().getBoolean("admin-require-op", true)
                || sender.isOp()
                || sender.hasPermission(ADMIN_PERMISSION);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isFeatureEnabled() || !canUse(event.getView().getPlayer())) {
            return;
        }
        AnvilInventory inventory = event.getInventory();
        ItemStack result = items.combine(inventory.getFirstItem(), inventory.getSecondItem());
        if (result == null) {
            return;
        }
        event.setResult(result);
        int cost = Math.max(0, getConfig().getInt("anvil-cost", 1));
        if (event.getView() instanceof AnvilView view) {
            view.setRepairCost(cost);
            view.setMaximumRepairCost(Math.max(cost, 40));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!isFeatureEnabled() || !canUse(event.getView().getPlayer())) {
            return;
        }
        GrindstoneInventory inventory = event.getInventory();
        ItemStack combined = singleCombined(inventory.getUpperItem(), inventory.getLowerItem());
        if (combined == null) {
            return;
        }
        event.setResult(items.splitPreview(combined));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!isFeatureEnabled() || !canUse(event.getView().getPlayer())) {
            return;
        }
        ItemStack result = cleanseFromMatrix(event.getInventory().getMatrix());
        if (result != null) {
            event.getInventory().setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isFeatureEnabled() || !(event.getWhoClicked() instanceof Player player) || !canUse(player)) {
            return;
        }
        ItemStack result = cleanseFromMatrix(event.getInventory().getMatrix());
        if (result == null) {
            return;
        }
        languageManager.sendItem(player, "cleansed", "elytra", result);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isFeatureEnabled() || !(event.getWhoClicked() instanceof Player player) || !canUse(player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getRawSlot() != 2) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (items.isEmpty(current) || event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() < 0) {
            return;
        }
        if (event.getInventory() instanceof AnvilInventory && items.isCombined(current)) {
            BmMinecraftElytraPlusItems.SplitResult split = items.split(current);
            if (split != null) {
                languageManager.sendItems(player, "combined", pairItems(split.elytra(), split.armor()));
            }
            return;
        }
        if (!(event.getInventory() instanceof GrindstoneInventory)) {
            return;
        }
        BmMinecraftElytraPlusItems.TakenSplit split = items.takeSplit(current);
        if (split == null) {
            return;
        }
        event.setCurrentItem(split.result());
        giveOrDrop(player, split.extra());
        languageManager.sendItems(player, "separated", separatedItems(split));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFeatureEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isGliding()
                || player.isOnGround()
                || player.isFlying()
                || player.isInWater()
                || player.isInLava()
                || player.getGameMode() == GameMode.SPECTATOR
                || !items.isCombinedChestplate(player.getInventory().getChestplate())
                || player.getVelocity().getY() >= 0.0D
                || !player.getCurrentInput().isJump()) {
            return;
        }
        player.setGliding(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!isFeatureEnabled()
                || event.isGliding()
                || !(event.getEntity() instanceof Player player)
                || player.isOnGround()
                || player.isInWater()
                || player.isInLava()
                || !items.isCombinedChestplate(player.getInventory().getChestplate())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkBoost(PlayerInteractEvent event) {
        if (!isFeatureEnabled()
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.hasItem()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack rocket = event.getItem();
        if (!player.isGliding()
                || rocket == null
                || rocket.getType() != Material.FIREWORK_ROCKET
                || !items.isCombinedChestplate(player.getInventory().getChestplate())) {
            return;
        }
        if (player.fireworkBoost(rocket) == null) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        rocket.setAmount(rocket.getAmount() - 1);
    }

    private ItemStack cleanseFromMatrix(ItemStack[] matrix) {
        List<ItemStack> filled = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (!items.isEmpty(item)) {
                filled.add(item);
            }
        }
        if (filled.size() != 2) {
            return null;
        }
        return items.cleanse(filled.get(0), filled.get(1));
    }

    private ItemStack singleCombined(ItemStack first, ItemStack second) {
        boolean firstCombined = items.isCombined(first);
        boolean secondCombined = items.isCombined(second);
        if (firstCombined && items.isEmpty(second)) {
            return first;
        }
        if (secondCombined && items.isEmpty(first)) {
            return second;
        }
        return null;
    }

    private Map<String, ItemStack> separatedItems(BmMinecraftElytraPlusItems.TakenSplit split) {
        ItemStack result = split.result();
        ItemStack extra = split.extra();
        return pairItems(items.isElytra(result) ? result : extra, items.isChestplate(result) ? result : extra);
    }

    private Map<String, ItemStack> pairItems(ItemStack elytra, ItemStack armor) {
        Map<String, ItemStack> names = new HashMap<>();
        names.put("elytra", elytra);
        names.put("armor", armor);
        return names;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack remain : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remain);
        }
    }

    private boolean canUse(CommandSender sender) {
        return sender instanceof Player player && player.hasPermission(USE_PERMISSION);
    }
}
