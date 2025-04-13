package com.wtrmln;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class LightningSwordPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> lightningMode = new HashSet<>();
    private final Set<UUID> manualLightning = new HashSet<>();
    private final String SWORD_NAME = ChatColor.AQUA + "⚡ Lightning Sword ⚡";
    private final Random random = new Random();
    private boolean craftingEnabled = true;
    private ShapedRecipe lightningRecipe;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        createLightningSwordRecipe();
        getCommand("cooldown").setExecutor(this::handleCooldownCommand);
        getCommand("craft").setExecutor(this::handleCraftCommand);
        getCommand("lightning").setExecutor(this::handleLightningCommand);
    }

    private void createLightningSwordRecipe() {
        ItemStack result = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(SWORD_NAME);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);  // Makes the sword unbreakable
        result.setItemMeta(meta);

        lightningRecipe = new ShapedRecipe(new NamespacedKey(this, "lightning_sword"), result);
        lightningRecipe.shape("ABA", "CDC", "ABA");
        lightningRecipe.setIngredient('A', Material.NETHERITE_INGOT);
        lightningRecipe.setIngredient('B', Material.WITHER_SKELETON_SKULL);
        lightningRecipe.setIngredient('C', Material.TRIDENT);
        lightningRecipe.setIngredient('D', Material.BEACON);

        if (craftingEnabled) {
            Bukkit.addRecipe(lightningRecipe);
        }
    }

    private boolean handleCooldownCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (args.length == 1 && sender.isOp()) {
            try {
                long seconds = Long.parseLong(args[0]);
                cooldowns.put(uuid, System.currentTimeMillis() - (120000 - seconds * 1000));
                sender.sendMessage(ChatColor.GREEN + "Cooldown set to " + seconds + " seconds.");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number.");
            }
        } else {
            long now = System.currentTimeMillis();
            if (!cooldowns.containsKey(uuid) || now - cooldowns.get(uuid) >= 120000) {
                sender.sendMessage(ChatColor.GREEN + "Ability is ready!");
            } else {
                long secondsLeft = (120000 - (now - cooldowns.get(uuid))) / 1000;
                sender.sendMessage(ChatColor.YELLOW + "Cooldown remaining: " + secondsLeft + " seconds.");
            }
        }
        return true;
    }

    private boolean handleCraftCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return false;
        if (args.length == 2 && args[0].equalsIgnoreCase("lightningsword")) {
            if (args[1].equalsIgnoreCase("disable")) {
                Bukkit.removeRecipe(lightningRecipe.getKey());
                craftingEnabled = false;
                sender.sendMessage(ChatColor.RED + "Lightning Sword crafting disabled.");
            } else if (args[1].equalsIgnoreCase("enable")) {
                createLightningSwordRecipe();
                craftingEnabled = true;
                sender.sendMessage(ChatColor.GREEN + "Lightning Sword crafting enabled.");
            }
        }
        return true;
    }

    private boolean handleLightningCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && sender.isOp()) {
            manualLightning.add(player.getUniqueId());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Manual lightning mode: right-click to strike!");
        }
        return true;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = player.getInventory().getItemInMainHand();
        UUID uuid = player.getUniqueId();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            boolean hasSword = item.getType() == Material.NETHERITE_SWORD
                    && item.hasItemMeta()
                    && item.getItemMeta().hasDisplayName()
                    && item.getItemMeta().getDisplayName().equals(SWORD_NAME);

            // Apply permanent glowing effect when holding the sword
            if (hasSword) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, true, false, false));
            }

            if (manualLightning.contains(uuid)) {
                startLightningEffect(player, uuid);
                manualLightning.remove(uuid);
                return;
            }

            if (hasSword) {
                long now = System.currentTimeMillis();
                if (cooldowns.containsKey(uuid) && (now - cooldowns.get(uuid)) < 120000) {
                    long secondsLeft = (120000 - (now - cooldowns.get(uuid))) / 1000;
                    player.sendMessage(ChatColor.RED + "Ability is on cooldown for " + secondsLeft + " more seconds.");
                } else {
                    cooldowns.put(uuid, now);
                    startLightningEffect(player, uuid);
                }
            }
        }
    }

    private void startLightningEffect(Player player, UUID uuid) {
        lightningMode.add(uuid);
        player.sendMessage(ChatColor.GOLD + "Lightning storm activated for 10 seconds!");

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!lightningMode.contains(uuid)) {
                    cancel();
                    return;
                }
                if (ticks >= 10) {
                    lightningMode.remove(uuid);
                    player.sendMessage(ChatColor.GRAY + "Lightning storm ended.");
                    cancel();
                    return;
                }

                if (random.nextDouble() <= 0.8) {
                    Location playerLocation = player.getLocation();
                    World world = playerLocation.getWorld();

                    for (Entity entity : world.getNearbyEntities(playerLocation, 6, 6, 6)) {
                        if (entity instanceof LivingEntity living && !living.getUniqueId().equals(uuid)) {
                            for (int i = 0; i < 3; i++) {
                                double offsetX = (random.nextDouble() - 0.5) * 4;
                                double offsetZ = (random.nextDouble() - 0.5) * 4;
                                Location strikeLocation = living.getLocation().add(offsetX, 0, offsetZ);
                                LightningStrike strike = world.strikeLightning(strikeLocation);
                                living.damage(5.0); // extra damage
                            }
                        }
                    }
                } else {
                    player.sendMessage(ChatColor.GRAY + "The lightning fizzled out...");
                }

                ticks++;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING && event.getEntity() instanceof LivingEntity) {
            event.setDamage(event.getDamage() * 2); // Double lightning damage
        }
    }
}
