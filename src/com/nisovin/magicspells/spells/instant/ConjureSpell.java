package com.nisovin.magicspells.spells.instant;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.command.ScrollSpell;
import com.nisovin.magicspells.spells.command.TomeSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.Util;

public class ConjureSpell extends InstantSpell implements TargetedEntitySpell, TargetedLocationSpell {

	Random rand = new Random();
	
	static ExpirationHandler expirationHandler = null;
	
	private boolean addToInventory;
	private boolean addToEnderChest;
	private boolean dropIfInventoryFull;
	private boolean powerAffectsQuantity;
	private boolean powerAffectsChance;
	private boolean calculateDropsIndividually;
	private boolean autoEquip;
	private boolean stackExisting;
	private boolean ignoreMaxStackSize;
	//private boolean allowParameters;
	private double expiration;
	private int requiredSlot;
	private int preferredSlot;
	List<String> itemList;
	private ItemStack[] itemTypes;
	private int[] itemMinQuantities;
	private int[] itemMaxQuantities;
	private double[] itemChances;
	private float randomVelocity;
	
	public ConjureSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		addToInventory = getConfigBoolean("add-to-inventory", false);
		addToEnderChest = getConfigBoolean("add-to-ender-chest", false);
		dropIfInventoryFull = getConfigBoolean("drop-if-inventory-full", true);
		powerAffectsQuantity = getConfigBoolean("power-affects-quantity", false);
		powerAffectsChance = getConfigBoolean("power-affects-chance", true);
		calculateDropsIndividually = getConfigBoolean("calculate-drops-individually", true);
		autoEquip = getConfigBoolean("auto-equip", false);
		stackExisting = getConfigBoolean("stack-existing", true);
		ignoreMaxStackSize = getConfigBoolean("ignore-max-stack-size", false);
		//allowParameters = getConfigBoolean("allow-parameters", true);
		expiration = getConfigDouble("expiration", 0L);
		requiredSlot = getConfigInt("required-slot", -1);
		preferredSlot = getConfigInt("preferred-slot", -1);
		itemList = getConfigStringList("items", null);
		randomVelocity = getConfigFloat("random-velocity", 0);
	}
	
	@Override
	public void initialize() {
		super.initialize();

		if (expiration > 0 && expirationHandler == null) {
			expirationHandler = new ExpirationHandler();
		}
		
		if (itemList != null && itemList.size() > 0) {
			itemTypes = new ItemStack[itemList.size()];
			itemMinQuantities = new int[itemList.size()];
			itemMaxQuantities = new int[itemList.size()];
			itemChances = new double[itemList.size()];
			
			for (int i = 0; i < itemList.size(); i++) {
				try {
					String[] data = Util.splitParams(itemList.get(i));
					String[] quantityData = data.length == 1 ? new String[]{"1"} : data[1].split("-");
					
					if (data[0].startsWith("TOME:")) {
						String[] tomeData = data[0].split(":");
						TomeSpell tomeSpell = (TomeSpell)MagicSpells.getSpellByInternalName(tomeData[1]);
						Spell spell = MagicSpells.getSpellByInternalName(tomeData[2]);
						int uses = tomeData.length > 3 ? Integer.parseInt(tomeData[3]) : -1;
						itemTypes[i] = tomeSpell.createTome(spell, uses, null);
					} else if (data[0].startsWith("SCROLL:")) {
						String[] scrollData = data[0].split(":");
						ScrollSpell scrollSpell = (ScrollSpell)MagicSpells.getSpellByInternalName(scrollData[1]);
						Spell spell = MagicSpells.getSpellByInternalName(scrollData[2]);
						int uses = scrollData.length > 3 ? Integer.parseInt(scrollData[3]) : -1;
						itemTypes[i] = scrollSpell.createScroll(spell, uses, null);
					} else {
						itemTypes[i] = Util.getItemStackFromString(data[0]);
					}
					if (itemTypes[i] == null) {
						MagicSpells.error("Conjure spell '" + internalName + "' has specified invalid item (e1): " + itemList.get(i));
						continue;
					}
					
					if (quantityData.length == 1) {
						itemMinQuantities[i] = Integer.parseInt(quantityData[0]);
						itemMaxQuantities[i] = itemMinQuantities[i];
					} else {
						itemMinQuantities[i] = Integer.parseInt(quantityData[0]);
						itemMaxQuantities[i] = Integer.parseInt(quantityData[1]);	
					}
					
					if (data.length > 2) {
						itemChances[i] = Double.parseDouble(data[2].replace("%", ""));
					} else {
						itemChances[i] = 100;
					}
				} catch (Exception e) {
					MagicSpells.error("Conjure spell '" + internalName + "' has specified invalid item (e2): " + itemList.get(i));
					itemTypes[i] = null;
				}
			}
		}
		itemList = null;
	}

	@Override
	public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
		if (itemTypes == null) return PostCastAction.ALREADY_HANDLED;
		if (state == SpellCastState.NORMAL) {
			conjureItems(player, power);
		}
		return PostCastAction.HANDLE_NORMALLY;
		
	}
	
	@SuppressWarnings("deprecation")
	private void conjureItems(Player player, float power) {
		// get items to drop
		List<ItemStack> items = new ArrayList<ItemStack>();
		if (calculateDropsIndividually) {
			individual(items, rand, power);
		} else {
			together(items, rand, power);
		}
		
		// drop items
		Location loc = player.getEyeLocation().add(player.getLocation().getDirection());
		boolean updateInv = false;
		for (ItemStack item : items) {
			boolean added = false;
			PlayerInventory inv = player.getInventory();
			if (autoEquip && item.getAmount() == 1) {
				if (item.getType().name().endsWith("HELMET") && inv.getHelmet() == null) {
					inv.setHelmet(item);
					added = true;
				} else if (item.getType().name().endsWith("CHESTPLATE") && inv.getChestplate() == null) {
					inv.setChestplate(item);
					added = true;
				} else if (item.getType().name().endsWith("LEGGINGS") && inv.getLeggings() == null) {
					inv.setLeggings(item);
					added = true;
				} else if (item.getType().name().endsWith("BOOTS") && inv.getBoots() == null) {
					inv.setBoots(item);
					added = true;
				}
			}
			if (!added) {
				if (addToEnderChest) {
					added = Util.addToInventory(player.getEnderChest(), item, stackExisting, ignoreMaxStackSize);
				}
				if (!added && addToInventory) {
					if (requiredSlot >= 0) {
						ItemStack old = inv.getItem(requiredSlot);
						if (old != null && old.isSimilar(item)) {
							item.setAmount(item.getAmount() + old.getAmount());
						}
						inv.setItem(requiredSlot, item);
						added = true;
						updateInv = true;
					} else if (preferredSlot >= 0 && inv.getItem(preferredSlot) == null) {
						inv.setItem(preferredSlot, item);
						added = true;
						updateInv = true;
					} else if (preferredSlot >= 0 && inv.getItem(preferredSlot).isSimilar(item) && inv.getItem(preferredSlot).getAmount() + item.getAmount() < item.getType().getMaxStackSize()) {
						item.setAmount(item.getAmount() + inv.getItem(preferredSlot).getAmount());
						inv.setItem(preferredSlot, item);
						added = true;
						updateInv = true;
					} else if (!added) {
						added = Util.addToInventory(inv, item, stackExisting, ignoreMaxStackSize);
						if (added) updateInv = true;
					}
				}
				if (!added && (dropIfInventoryFull || !addToInventory)) {
					player.getWorld().dropItem(loc, item).setItemStack(item);
				}
			} else {
				updateInv = true;
			}
		}
		if (updateInv) {
			player.updateInventory();
		}
		
		playSpellEffects(EffectPosition.CASTER, player);
	}
	
	private void individual(List<ItemStack> items, Random rand, float power) {
		for (int i = 0; i < itemTypes.length; i++) {
			double r = rand.nextDouble() * 100;
			if (powerAffectsChance) r = r / power;
			if (itemTypes[i] != null && r < itemChances[i]) {
				addItem(i, items, rand, power);
			}
		}
	}
	
	private void together(List<ItemStack> items, Random rand, float power) {
		double r = rand.nextDouble() * 100;
		double m = 0;
		for (int i = 0; i < itemTypes.length; i++) {
			if (itemTypes[i] != null && r < itemChances[i] + m) {
				addItem(i, items, rand, power);
				return;
			} else {
				m += itemChances[i];
			}
		}
	}
	
	private void addItem(int i, List<ItemStack> items, Random rand, float power) {
		int quant = itemMinQuantities[i];
		if (itemMaxQuantities[i] > itemMinQuantities[i]) {
			quant = rand.nextInt(itemMaxQuantities[i] - itemMinQuantities[i]) + itemMinQuantities[i];
		}
		if (powerAffectsQuantity) {
			quant = Math.round(quant * power);
		}
		if (quant > 0) {
			ItemStack item = itemTypes[i].clone();
			item.setAmount(quant);
			if (expiration > 0) {
				expirationHandler.addExpiresLine(item, expiration);
			}
			items.add(item);
		}
	}

	@Override
	public boolean castAtLocation(Player caster, Location target, float power) {
		return castAtLocation(target, power);
	}
	
	@Override
	public boolean castAtLocation(Location target, float power) {
		List<ItemStack> items = new ArrayList<ItemStack>();
		if (calculateDropsIndividually) {
			individual(items, rand, power);
		} else {
			together(items, rand, power);
		}
		Location loc = target.clone();
		if (loc.getBlock().getType() != Material.AIR) {
			loc.add(0, 1, 0);
		}
		if (loc.getBlock().getType() != Material.AIR) {
			loc.add(0, 1, 0);
		}
		for (ItemStack item : items) {
			Item dropped = loc.getWorld().dropItem(loc, item);
			dropped.setItemStack(item);
			if (randomVelocity > 0) {
				Vector v = new Vector(rand.nextDouble() - .5, rand.nextDouble() / 2, rand.nextDouble() - .5);
				v.normalize().multiply(randomVelocity);
				dropped.setVelocity(v);
			}
		}
		return true;
	}

	@Override
	public boolean castAtEntity(Player caster, LivingEntity target, float power) {
		return castAtEntity(target, power);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (target instanceof Player) {
			conjureItems((Player)target, power);
			return true;
		} else {
			return false;
		}		
	}
	
	@Override
	public void turnOff() {
		expirationHandler = null;
	}
	
	class ExpirationHandler implements Listener {
		private final String expPrefix =  ChatColor.BLACK.toString() + ChatColor.MAGIC.toString() + "MSExp:";
		
		public ExpirationHandler() {
			MagicSpells.registerEvents(this);
		}
		
		public void addExpiresLine(ItemStack item, double expireHours) {
			ItemMeta meta = item.getItemMeta();
			List<String> lore;
			if (meta.hasLore()) {
				lore = new ArrayList<String>(meta.getLore());
			} else {
				lore = new ArrayList<String>();
			}
			long expiresAt = System.currentTimeMillis() + (long)(expireHours * 60L * 60L * 1000L);
			lore.add(getExpiresText(expiresAt));
			lore.add(expPrefix + expiresAt);
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
		
		@EventHandler(priority = EventPriority.LOWEST)
		void onJoin(PlayerJoinEvent event) {
			PlayerInventory inv = event.getPlayer().getInventory();
			processInventory(inv);
			ItemStack[] armor = inv.getArmorContents();
			processInventoryContents(armor);
			inv.setArmorContents(armor);
		}
		
		@EventHandler(priority = EventPriority.LOWEST)
		void onInvOpen(InventoryOpenEvent event) {
			processInventory(event.getInventory());
		}
		
		@EventHandler(priority = EventPriority.LOWEST)
		void onRightClick(PlayerInteractEvent event) {
			if (event.hasItem()) {
				ItemStack item = event.getPlayer().getItemInHand();
				ExpirationResult result = updateExpiresLineIfNeeded(item);
				if (result == ExpirationResult.EXPIRED) {
					
					event.getPlayer().setItemInHand(null);
					event.setCancelled(true);
				}
			}
		}
		
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onPickup(PlayerPickupItemEvent event) {
			processItemDrop(event.getItem());
			if (event.getItem().isDead()) {
				event.setCancelled(true);
			}
		}
		
		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		void onDrop(PlayerDropItemEvent event) {
			processItemDrop(event.getItemDrop());
		}
		
		@EventHandler(priority = EventPriority.LOWEST)
		void onItemSpawn(ItemSpawnEvent event) {
			processItemDrop(event.getEntity());
		}
		
		private void processInventory(Inventory inv) {
			ItemStack[] contents = inv.getContents();
			processInventoryContents(contents);
			inv.setContents(contents);
		}
		
		private void processInventoryContents(ItemStack[] contents) {
			for (int i = 0; i < contents.length; i++) {
				ExpirationResult result = updateExpiresLineIfNeeded(contents[i]);
				if (result == ExpirationResult.EXPIRED) {
					contents[i] = null;
				}
			}
		}
		
		private boolean processItemDrop(Item drop) {
			ItemStack item = drop.getItemStack();
			ExpirationResult result = updateExpiresLineIfNeeded(item);
			if (result == ExpirationResult.UPDATE) {
				drop.setItemStack(item);
			} else if (result == ExpirationResult.EXPIRED) {
				drop.remove();
				return true;
			}
			return false;
		}
		
		private ExpirationResult updateExpiresLineIfNeeded(ItemStack item) {
			if (item == null) return ExpirationResult.NO_UPDATE;
			if (!item.hasItemMeta()) return ExpirationResult.NO_UPDATE;
			ItemMeta meta = item.getItemMeta();
			if (!meta.hasLore()) return ExpirationResult.NO_UPDATE;
			ArrayList<String> lore = new ArrayList<String>(meta.getLore());
			if (lore.size() < 2) return ExpirationResult.NO_UPDATE;
			String lastLine = lore.get(lore.size() - 1);
			if (!lastLine.startsWith(expPrefix)) return ExpirationResult.NO_UPDATE;
			long expiresAt = Long.parseLong(lastLine.replace(expPrefix, ""));
			if (expiresAt < System.currentTimeMillis()) {
				return ExpirationResult.EXPIRED;
			} else {
				lore.set(lore.size() - 2, getExpiresText(expiresAt));
				meta.setLore(lore);
				item.setItemMeta(meta);
				return ExpirationResult.UPDATE;
			}
		}
	
		private String getExpiresText(long expiresAt) {
			if (expiresAt < System.currentTimeMillis()) {
				return ChatColor.GRAY + "Expired";
			} else {
				double hours = (expiresAt - System.currentTimeMillis()) / 3600000D;
				if (hours / 24 >= 15) {
					return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + ((long)hours / 168L) + ChatColor.GRAY + " weeks";
				} else if (hours / 24 >= 3) {
					return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + ((long)hours / 24L) + ChatColor.GRAY + " days";
				} else if (hours >= 2) {
					return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + (long)hours + ChatColor.GRAY + " hours";
				} else {
					return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + "1" + ChatColor.GRAY + " hour";
				}
			}
		}		
		
	}
	
	private enum ExpirationResult {
		NO_UPDATE, UPDATE, EXPIRED
	}
	

}
