package com.enderdevelopment.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.Action;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private static final String HOME_TITLE = "Pocket Phone";
    private static final String CONTACTS_TITLE = "Phone Contacts";
    private NamespacedKey phoneKey;
    private NamespacedKey appKey;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    @Override
    public void onEnable() {
        phoneKey = new NamespacedKey(this, "phone");
        appKey = new NamespacedKey(this, "phone_app");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        pendingRequests.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("phone")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command is for players.", NamedTextColor.RED));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
                player.getInventory().addItem(createPhone());
                player.sendMessage(Component.text("Phone added to your inventory.", NamedTextColor.GREEN));
            } else {
                openHome(player);
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (sender instanceof Player player) {
                acceptRequest(player);
            } else {
                sender.sendMessage(Component.text("This command is for players.", NamedTextColor.RED));
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (sender instanceof Player player) {
                denyRequest(player);
            } else {
                sender.sendMessage(Component.text("This command is for players.", NamedTextColor.RED));
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPhoneUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (isPhone(item)) {
            event.setCancelled(true);
            openHome(event.getPlayer());
        }
    }

    @EventHandler
    public void onPhoneMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = plainTitle(event.getView().title());
        if (!title.equals(HOME_TITLE) && !title.equals(CONTACTS_TITLE)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (title.equals(HOME_TITLE) && event.getRawSlot() == 13) {
            openContacts(player);
            return;
        }
        if (title.equals(CONTACTS_TITLE)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String targetName = meta.getPersistentDataContainer().get(appKey, PersistentDataType.STRING);
            if (targetName == null) return;
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || target.equals(player)) {
                player.sendMessage(Component.text("That contact is no longer available.", NamedTextColor.RED));
                openContacts(player);
                return;
            }
            sendRequest(player, target);
        }
    }

    private ItemStack createPhone() {
        ItemStack phone = new ItemStack(Material.CLOCK);
        ItemMeta meta = phone.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Pocket Phone", NamedTextColor.AQUA));
            meta.getPersistentDataContainer().set(phoneKey, PersistentDataType.BYTE, (byte) 1);
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            phone.setItemMeta(meta);
        }
        return phone;
    }

    private boolean isPhone(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(phoneKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void openHome(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(HOME_TITLE, NamedTextColor.DARK_AQUA));
        ItemStack contacts = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = contacts.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Contacts", NamedTextColor.GREEN));
            contacts.setItemMeta(meta);
        }
        inventory.setItem(13, contacts);
        player.openInventory(inventory);
    }

    private void openContacts(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text(CONTACTS_TITLE, NamedTextColor.DARK_AQUA));
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || slot >= inventory.getSize()) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta == null) continue;
            meta.displayName(Component.text(target.getName(), NamedTextColor.YELLOW));
            meta.lore(java.util.List.of(Component.text("Click to request a teleport.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(appKey, PersistentDataType.STRING, target.getName());
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }
        player.openInventory(inventory);
    }

    private void sendRequest(Player requester, Player target) {
        UUID previous = pendingRequests.put(target.getUniqueId(), requester.getUniqueId());
        if (previous != null) {
            Player oldRequester = Bukkit.getPlayer(previous);
            if (oldRequester != null) oldRequester.sendMessage(Component.text("Your previous teleport request was replaced.", NamedTextColor.RED));
        }
        requester.sendMessage(Component.text("Teleport request sent to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(requester.getName() + " wants to teleport to you. Use /tpaccept or /tpdeny.", NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (requester.getUniqueId().equals(pendingRequests.get(target.getUniqueId()))) {
                pendingRequests.remove(target.getUniqueId());
                if (requester.isOnline()) requester.sendMessage(Component.text("Your teleport request expired.", NamedTextColor.RED));
                if (target.isOnline()) target.sendMessage(Component.text("The teleport request expired.", NamedTextColor.RED));
            }
        }, 1200L);
    }

    private void acceptRequest(Player target) {
        UUID requesterId = pendingRequests.remove(target.getUniqueId());
        Player requester = requesterId == null ? null : Bukkit.getPlayer(requesterId);
        if (requester == null) {
            target.sendMessage(Component.text("You have no pending teleport request.", NamedTextColor.RED));
            return;
        }
        requester.teleport(target.getLocation());
        requester.sendMessage(Component.text("Teleport request accepted.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Teleport request accepted.", NamedTextColor.GREEN));
    }

    private void denyRequest(Player target) {
        UUID requesterId = pendingRequests.remove(target.getUniqueId());
        Player requester = requesterId == null ? null : Bukkit.getPlayer(requesterId);
        if (requester == null) {
            target.sendMessage(Component.text("You have no pending teleport request.", NamedTextColor.RED));
            return;
        }
        requester.sendMessage(Component.text(target.getName() + " denied your teleport request.", NamedTextColor.RED));
        target.sendMessage(Component.text("Teleport request denied.", NamedTextColor.YELLOW));
    }

    private String plainTitle(Component title) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
    }
}
