package com.nisovin.magicspells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BlockIterator;

import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.mana.ManaChangeReason;
import com.nisovin.magicspells.mana.ManaHandler;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.spells.PassiveSpell;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.ExperienceUtils;
import com.nisovin.magicspells.util.IntMap;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.MoneyHandler;
import com.nisovin.magicspells.util.SpellReagents;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.variables.VariableManager;

public abstract class Spell implements Comparable<Spell>, Listener {

	private MagicConfig config;
	
	private boolean debug;
	protected String internalName;
	protected String name;
	protected String profilingKey;
	protected String[] aliases;
	protected boolean helperSpell;
	protected boolean alwaysGranted;
	protected String permName;
	protected List<String> incantations;
	
	protected String description;
	protected CastItem[] castItems;
	protected CastItem[] rightClickCastItems;
	protected CastItem[] consumeCastItems;
	protected boolean castWithLeftClick;
	protected boolean castWithRightClick;
	protected String danceCastSequence;
	protected boolean requireCastItemOnCommand;
	protected boolean bindable;
	protected HashSet<CastItem> bindableItems;
	protected ItemStack spellIcon;
	protected int broadcastRange;
	protected int experience;
	protected HashMap<EffectPosition, List<SpellEffect>> effects;
	
	protected int minRange;
	private int range;
	protected boolean spellPowerAffectsRange;
	protected boolean obeyLos;
	protected ValidTargetList validTargetList;
	protected boolean beneficial;
	private DamageCause targetDamageCause;
	private double targetDamageAmount;
	protected HashSet<Byte> losTransparentBlocks;

	protected int castTime;
	protected boolean interruptOnMove;
	protected boolean interruptOnTeleport;
	protected boolean interruptOnDamage;
	protected boolean interruptOnCast;
	protected String spellNameOnInterrupt;
	protected Spell spellOnInterrupt;
	
	protected SpellReagents reagents;
	
	protected float cooldown;
	protected List<String> rawSharedCooldowns;
	protected HashMap<Spell, Float> sharedCooldowns;
	protected boolean ignoreGlobalCooldown;
	protected int charges;
	protected String rechargeSound;

	private List<String> modifierStrings;
	private List<String> targetModifierStrings;
	protected ModifierSet modifiers;
	protected ModifierSet targetModifiers;
	
	protected List<String> prerequisites;
	protected List<String> replaces;
	protected List<String> precludes;
	protected Map<String, Integer> xpGranted;
	protected Map<String, Integer> xpRequired;
	protected List<String> worldRestrictions;
	
	protected Map<String, Double> variableModsCast;
	protected Map<String, Double> variableModsCasted;
	protected Map<String, Double> variableModsTarget;
	
	protected String strCost;
	protected String strCastSelf;
	protected String strCastOthers;
	protected String strOnCooldown;
	protected String strMissingReagents;
	protected String strCantCast;
	protected String strCantBind;
	protected String strWrongWorld;
	protected String strWrongCastItem;
	protected String strCastStart;
	protected String strInterrupted;
	protected String strModifierFailed;
	protected String strXpAutoLearned;
	
	private HashMap<String, Long> nextCast;
	private IntMap<String> chargesConsumed;
	
	public Spell(MagicConfig config, String spellName) {
		this.config = config;
		
		this.internalName = spellName;
		loadConfigData(config, spellName, "spells");
		
	}
	
	protected void loadConfigData(MagicConfig config, String spellName, String section) {
		this.debug = config.getBoolean(section + "." + spellName + ".debug", false);
		this.profilingKey = "Spell:" + this.getClass().getName().replace("com.nisovin.magicspells.spells.", "") + "-" + spellName;
		this.name = config.getString(section + "." + spellName + ".name", spellName);
		List<String> temp = config.getStringList(section + "." + spellName + ".aliases", null);
		if (temp != null) {
			aliases = new String[temp.size()];
			aliases = temp.toArray(aliases);
		}
		this.helperSpell = config.getBoolean(section + "." + spellName + ".helper-spell", false);
		this.alwaysGranted = config.getBoolean(section + "." + spellName + ".always-granted", false);
		this.permName = config.getString(section + "." + spellName + ".permission-name", spellName);
		this.incantations = config.getStringList(section + "." + spellName + ".incantations", null);
		
		// general options
		this.description = config.getString(section + "." + spellName + ".description", "");
		if (config.contains(section + "." + spellName + ".cast-item")) {
			String[] sItems = config.getString(section + "." + spellName + ".cast-item", "-5").trim().replace(" ", "").split(",");
			this.castItems = new CastItem[sItems.length];
			for (int i = 0; i < sItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems[i]);
				if (is != null) {
					this.castItems[i] = new CastItem(is);
				}
			}
		} else if (config.contains(section + "." + spellName + ".cast-items")) {
			List<String> sItems = config.getStringList(section + "." + spellName + ".cast-items", null);
			if (sItems == null) sItems = new ArrayList<String>();
			this.castItems = new CastItem[sItems.size()];
			for (int i = 0; i < castItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems.get(i));
				if (is != null) {
					this.castItems[i] = new CastItem(is);
				}
			}
		} else {
			this.castItems = new CastItem[0];
		}
		if (config.contains(section + "." + spellName + ".right-click-cast-item")) {
			String[] sItems = config.getString(section + "." + spellName + ".right-click-cast-item", "-5").trim().replace(" ", "").split(",");
			this.rightClickCastItems = new CastItem[sItems.length];
			for (int i = 0; i < sItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems[i]);
				if (is != null) {
					this.rightClickCastItems[i] = new CastItem(is);
				}
			}
		} else if (config.contains(section + "." + spellName + ".right-click-cast-items")) {
			List<String> sItems = config.getStringList(section + "." + spellName + ".right-click-cast-items", null);
			if (sItems == null) sItems = new ArrayList<String>();
			this.rightClickCastItems = new CastItem[sItems.size()];
			for (int i = 0; i < rightClickCastItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems.get(i));
				if (is != null) {
					this.rightClickCastItems[i] = new CastItem(is);
				}
			}
		} else {
			this.rightClickCastItems = new CastItem[0];
		}
		if (config.contains(section + "." + spellName + ".consume-cast-item")) {
			String[] sItems = config.getString(section + "." + spellName + ".consume-cast-item", "-5").trim().replace(" ", "").split(",");
			this.consumeCastItems = new CastItem[sItems.length];
			for (int i = 0; i < sItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems[i]);
				if (is != null) {
					this.consumeCastItems[i] = new CastItem(is);
				}
			}
		} else if (config.contains(section + "." + spellName + ".consume-cast-items")) {
			List<String> sItems = config.getStringList(section + "." + spellName + ".consume-cast-items", null);
			if (sItems == null) sItems = new ArrayList<String>();
			this.consumeCastItems = new CastItem[sItems.size()];
			for (int i = 0; i < consumeCastItems.length; i++) {
				ItemStack is = Util.getItemStackFromString(sItems.get(i));
				if (is != null) {
					this.consumeCastItems[i] = new CastItem(is);
				}
			}
		} else {
			this.consumeCastItems = new CastItem[0];
		}
		this.castWithLeftClick = config.getBoolean(section + "." + spellName + ".cast-with-left-click", MagicSpells.plugin.castWithLeftClick);
		this.castWithRightClick = config.getBoolean(section + "." + spellName + ".cast-with-right-click", MagicSpells.plugin.castWithRightClick);
		this.danceCastSequence = config.getString(section + "." + spellName + ".dance-cast-sequence", null);
		this.requireCastItemOnCommand = config.getBoolean(section + "." + spellName + ".require-cast-item-on-command", false);
		this.bindable = config.getBoolean(section + "." + spellName + ".bindable", true);
		List<String> bindables = config.getStringList(section + "." + spellName + ".bindable-items", null);
		if (bindables != null) {
			bindableItems = new HashSet<CastItem>();
			for (String s : bindables) {
				ItemStack is = Util.getItemStackFromString(s);
				if (is != null) {
					bindableItems.add(new CastItem(is));
				}
			}
		}
		String icontemp = config.getString(section + "." + spellName + ".spell-icon", null);
		if (icontemp == null) {
			spellIcon = null;
		} else {
			spellIcon = Util.getItemStackFromString(icontemp);
			if (spellIcon != null && spellIcon.getType() != Material.AIR) {
				spellIcon.setAmount(0);
				if (!icontemp.contains("|")) {
					ItemMeta iconMeta = spellIcon.getItemMeta();
					iconMeta.setDisplayName(MagicSpells.getTextColor() + name);
					spellIcon.setItemMeta(iconMeta);
				}
			}
		}
		this.broadcastRange = config.getInt(section + "." + spellName + ".broadcast-range", MagicSpells.plugin.broadcastRange);
		this.experience = config.getInt(section + "." + spellName + ".experience", 0);

		// cast time
		this.castTime = config.getInt(section + "." + spellName + ".cast-time", 0);
		this.interruptOnMove = config.getBoolean(section + "." + spellName + ".interrupt-on-move", true);
		this.interruptOnTeleport = config.getBoolean(section + "." + spellName + ".interrupt-on-teleport", true);
		this.interruptOnDamage = config.getBoolean(section + "." + spellName + ".interrupt-on-damage", false);
		this.interruptOnCast = config.getBoolean(section + "." + spellName + ".interrupt-on-cast", true);
		this.spellNameOnInterrupt = config.getString(section + "." + spellName + ".spell-on-interrupt", null);
		
		// targeting
		this.minRange = config.getInt(section + "." + spellName + ".min-range", 0);
		this.range = config.getInt(section + "." + spellName + ".range", 20);
		this.spellPowerAffectsRange = config.getBoolean(section + "." + spellName + ".spell-power-affects-range", true);
		this.obeyLos = config.getBoolean(section + "." + spellName + ".obey-los", true);
		if (config.contains(section + "." + spellName + ".can-target")) {
			if (config.isList(section + "." + spellName + ".can-target")) {
				validTargetList = new ValidTargetList(this, config.getStringList(section + "." + spellName + ".can-target", null));
			} else {
				validTargetList = new ValidTargetList(this, config.getString(section + "." + spellName + ".can-target", ""));
			}
		} else {
			boolean targetPlayers = config.getBoolean(section + "." + spellName + ".target-players", true);
			boolean targetNonPlayers = config.getBoolean(section + "." + spellName + ".target-non-players", true);
			validTargetList = new ValidTargetList(targetPlayers, targetNonPlayers);
		}
		this.beneficial = config.getBoolean(section + "." + spellName + ".beneficial", isBeneficialDefault());
		this.targetDamageCause = null;
		String causeStr = config.getString(section + "." + spellName + ".target-damage-cause", null);
		if (causeStr != null) {
			for (DamageCause cause : DamageCause.values()) {
				if (cause.name().equalsIgnoreCase(causeStr)) {
					this.targetDamageCause = cause;
					break;
				}
			}
		}
		this.targetDamageAmount = config.getDouble(section + "." + spellName + ".target-damage-amount", 0);
		this.losTransparentBlocks = MagicSpells.getTransparentBlocks();
		if (config.contains(section + "." + spellName + ".los-transparent-blocks")) {
			this.losTransparentBlocks = new HashSet<Byte>(config.getByteList(section + "." + spellName + ".los-transparent-blocks", null));
			this.losTransparentBlocks.add((byte)0);
		}
		
		// graphical effects
		if (config.contains(section + "." + spellName + ".effects")) {
			this.effects = new HashMap<EffectPosition, List<SpellEffect>>();
			if (config.isList(section + "." + spellName + ".effects")) {
				List<String> effectsList = config.getStringList(section + "." + spellName + ".effects", null);
				if (effectsList != null) {
					for (Object obj : effectsList) {
						if (obj instanceof String) {
							String eff = (String)obj;
							String[] data = eff.split(" ", 3);
							EffectPosition pos = getPositionFromString(data[0]);
							if (pos != null) {
								SpellEffect effect = SpellEffect.createNewEffectByName(data[1]);
								if (effect != null) {
									effect.loadFromString(data.length > 2 ? data[2] : null);
									List<SpellEffect> e = effects.get(pos);
									if (e == null) {
										e = new ArrayList<SpellEffect>();
										effects.put(pos, e);
									}
									e.add(effect);
								}
							}
						}
					}
				}
			} else if (config.isSection(section + "." + spellName + ".effects")) {
				for (String key : config.getKeys(section + "." + spellName + ".effects")) {
					ConfigurationSection effConf = config.getSection(section + "." + spellName + ".effects." + key);
					EffectPosition pos = getPositionFromString(effConf.getString("position", ""));
					if (pos != null) {
						SpellEffect effect = SpellEffect.createNewEffectByName(effConf.getString("effect", ""));
						if (effect != null) {
							effect.loadFromConfiguration(effConf);
							List<SpellEffect> e = effects.get(pos);
							if (e == null) {
								e = new ArrayList<SpellEffect>();
								effects.put(pos, e);
							}
							e.add(effect);
						}
					}
				}
			}
		}
		
		// cost
		reagents = getConfigReagents("cost");
		if (reagents == null) reagents = new SpellReagents();
		
		// cooldowns
		this.cooldown = (float)config.getDouble(section + "." + spellName + ".cooldown", 0);
		this.rawSharedCooldowns = config.getStringList(section + "." + spellName + ".shared-cooldowns", null);
		this.ignoreGlobalCooldown = config.getBoolean(section + "." + spellName + ".ignore-global-cooldown", false);
		this.charges = config.getInt(section + "." + spellName + ".charges", 0);
		this.rechargeSound = config.getString(section + "." + spellName + ".recharge-sound", "");
		this.nextCast = new HashMap<String, Long>();
		this.chargesConsumed = new IntMap<String>();

		// modifiers
		this.modifierStrings = config.getStringList(section + "." + spellName + ".modifiers", null);
		this.targetModifierStrings = config.getStringList(section + "." + spellName + ".target-modifiers", null);
		
		// hierarchy options
		this.prerequisites = config.getStringList(section + "." + spellName + ".prerequisites", null);
		this.replaces = config.getStringList(section + "." + spellName + ".replaces", null);
		this.precludes = config.getStringList(section + "." + spellName + ".precludes", null);
		this.worldRestrictions = config.getStringList(section + "." + spellName + ".restrict-to-worlds", null);
		List<String> sXpGranted = config.getStringList(section + "." + spellName + ".xp-granted", null);
		List<String> sXpRequired = config.getStringList(section + "." + spellName + ".xp-required", null);
		if (sXpGranted != null) {
			xpGranted = new LinkedHashMap<String, Integer>();
			for (String s : sXpGranted) {
				String[] split = s.split(" ");
				try {
					int amt = Integer.parseInt(split[1]);
					xpGranted.put(split[0], amt);
				} catch (NumberFormatException e) {
					MagicSpells.error("Error in xp-granted entry for spell '" + internalName + "': " + s);
				}
			}
		}
		if (sXpRequired != null) {
			xpRequired = new LinkedHashMap<String, Integer>();
			for (String s : sXpRequired) {
				String[] split = s.split(" ");
				try {
					int amt = Integer.parseInt(split[1]);
					xpRequired.put(split[0], amt);
				} catch (NumberFormatException e) {
					MagicSpells.error("Error in xp-required entry for spell '" + internalName + "': " + s);
				}
			}
		}
		
		// variable options
		List<String> varModsCast = config.getStringList(section + "." + spellName + ".variable-mods-cast", null);
		if (varModsCast != null && varModsCast.size() > 0) {
			variableModsCast = new HashMap<String, Double>();
			for (String s : varModsCast) {
				try {
					String[] data = s.split(" ");
					String var = data[0];
					double val = Double.parseDouble(data[1]);
					variableModsCast.put(var, val);
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-cast option for spell '" + spellName + "': " + s);
				}
			}
		}
		List<String> varModsCasted = config.getStringList(section + "." + spellName + ".variable-mods-casted", null);
		if (varModsCasted != null && varModsCasted.size() > 0) {
			variableModsCasted = new HashMap<String, Double>();
			for (String s : varModsCasted) {
				try {
					String[] data = s.split(" ");
					String var = data[0];
					double val = Double.parseDouble(data[1]);
					variableModsCasted.put(var, val);
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-casted option for spell '" + spellName + "': " + s);
				}
			}
		}
		List<String> varModsTarget = config.getStringList(section + "." + spellName + ".variable-mods-target", null);
		if (varModsTarget != null && varModsTarget.size() > 0) {
			variableModsTarget = new HashMap<String, Double>();
			for (String s : varModsTarget) {
				try {
					String[] data = s.split(" ");
					String var = data[0];
					double val = Double.parseDouble(data[1]);
					variableModsTarget.put(var, val);
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-target option for spell '" + spellName + "': " + s);
				}
			}
		}
		
		
		// strings
		this.strCost = config.getString(section + "." + spellName + ".str-cost", null);
		this.strCastSelf = config.getString(section + "." + spellName + ".str-cast-self", null);
		this.strCastOthers = config.getString(section + "." + spellName + ".str-cast-others", null);
		this.strOnCooldown = config.getString(section + "." + spellName + ".str-on-cooldown", MagicSpells.plugin.strOnCooldown);
		this.strMissingReagents = config.getString(section + "." + spellName + ".str-missing-reagents", MagicSpells.plugin.strMissingReagents);
		this.strCantCast = config.getString(section + "." + spellName + ".str-cant-cast", MagicSpells.plugin.strCantCast);
		this.strCantBind = config.getString(section + "." + spellName + ".str-cant-bind", null);
		this.strWrongWorld = config.getString(section + "." + spellName + ".str-wrong-world", MagicSpells.plugin.strWrongWorld);
		this.strWrongCastItem = config.getString(section + "." + spellName + ".str-wrong-cast-item", strCantCast);
		this.strCastStart = config.getString(section + "." + spellName + ".str-cast-start", null);
		this.strInterrupted = config.getString(section + "." + spellName + ".str-interrupted", null);
		this.strModifierFailed = config.getString(section + "." + spellName + ".str-modifier-failed", null);
		this.strXpAutoLearned = config.getString(section + "." + spellName + ".str-xp-auto-learned", MagicSpells.plugin.strXpAutoLearned);
		if (this.strXpAutoLearned != null) {
			strXpAutoLearned = strXpAutoLearned.replace("%s", this.name);
		}
		
	}
	
	private EffectPosition getPositionFromString(String spos) {
		if (spos.equalsIgnoreCase("start") || spos.equalsIgnoreCase("startcast")) {
			return EffectPosition.START_CAST;
		} else if (spos.equalsIgnoreCase("pos1") || spos.equalsIgnoreCase("position1") || spos.equalsIgnoreCase("caster") || spos.equalsIgnoreCase("actor")) {
			return EffectPosition.CASTER;
		} else if (spos.equalsIgnoreCase("pos2") || spos.equalsIgnoreCase("position2") || spos.equalsIgnoreCase("target")) {
			return EffectPosition.TARGET;
		} else if (spos.equalsIgnoreCase("line") || spos.equalsIgnoreCase("trail")) {
			return EffectPosition.TRAIL;
		} else if (spos.equalsIgnoreCase("disabled")) {
			return EffectPosition.DISABLED;
		} else if (spos.equalsIgnoreCase("delayed")) {
			return EffectPosition.DELAYED;
		} else if (spos.equalsIgnoreCase("special")) {
			return EffectPosition.SPECIAL;
		} else if (spos.equalsIgnoreCase("buff") || spos.equalsIgnoreCase("active")) {
			return EffectPosition.BUFF;
		} else if (spos.equalsIgnoreCase("orbit")) {
			return EffectPosition.ORBIT;
		}
		return null;
	}
	
	protected SpellReagents getConfigReagents(String option) {
		SpellReagents reagents = null;
		List<String> costList = config.getStringList("spells." + internalName + "." + option, null);
		if (costList != null && costList.size() > 0) {
			reagents = new SpellReagents();
			String[] data;
			for (int i = 0; i < costList.size(); i++) {
				String costVal = costList.get(i);
				
				try {
					// parse cost data
					data = costVal.split(" ");
					if (data[0].equalsIgnoreCase("health")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setHealth(amt);
					} else if (data[0].equalsIgnoreCase("mana")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setMana(amt);
					} else if (data[0].equalsIgnoreCase("hunger")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setHunger(amt);
					} else if (data[0].equalsIgnoreCase("experience")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setExperience(amt);
					} else if (data[0].equalsIgnoreCase("levels")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setLevels(amt);
					} else if (data[0].equalsIgnoreCase("durability")) {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						reagents.setDurability(amt);
					} else if (data[0].equalsIgnoreCase("money")) {
						float amt = 1;
						if (data.length > 1) amt = Float.parseFloat(data[1]);
						reagents.setMoney(amt);
					} else if (data[0].equalsIgnoreCase("variable")) {
						reagents.addVariable(data[1], Double.parseDouble(data[2]));
					} else {
						int amt = 1;
						if (data.length > 1) amt = Integer.parseInt(data[1]);
						ItemStack is = Util.getItemStackFromString(data[0]);
						if (is != null) {
							is.setAmount(amt);
							reagents.addItem(is);
						} else {
							MagicSpells.error("Failed to process cost value for " + internalName + " spell: " + costVal);
						}
					}
				} catch (Exception e) {
					MagicSpells.error("Failed to process cost value for " + internalName + " spell: " + costVal);
				}
			}
		}
		return reagents;
	}
	
	/**
	 * This method is called immediately after all spells have been loaded.
	 */
	protected void initialize() {
		// modifiers		
		if (modifierStrings != null && modifierStrings.size() > 0) {
			debug(2, "Adding modifiers to " + internalName + " spell");
			modifiers = new ModifierSet(modifierStrings);
			modifierStrings = null;
		}
		if (targetModifierStrings != null && targetModifierStrings.size() > 0) {
			debug(2, "Adding target modifiers to " + internalName + " spell");
			targetModifiers = new ModifierSet(targetModifierStrings);
			targetModifierStrings = null;
		}
		
		// process shared cooldowns
		if (rawSharedCooldowns != null) {
			this.sharedCooldowns = new HashMap<Spell,Float>();
			for (String s : rawSharedCooldowns) {
				String[] data = s.split(" ");
				Spell spell = MagicSpells.getSpellByInternalName(data[0]);
				float cd = Float.parseFloat(data[1]);
				if (spell != null) {
					this.sharedCooldowns.put(spell, cd);
				}
			}
			rawSharedCooldowns.clear();
			rawSharedCooldowns = null;
		}
		
		// register events
		registerEvents();
		
		// other processing
		if (spellNameOnInterrupt != null && !spellNameOnInterrupt.isEmpty()) {
			spellOnInterrupt = MagicSpells.getSpellByInternalName(spellNameOnInterrupt);
		}
	}
	
	protected boolean configKeyExists(String key) {
		return config.contains("spells." + internalName + "." + key);
	}
	
	/**
	 * Access an integer config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected int getConfigInt(String key, int defaultValue) {
		return config.getInt("spells." + internalName + "." + key, defaultValue);
	}
	
	/**
	 * Access a long config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected long getConfigLong(String key, long defaultValue) {
		return config.getLong("spells." + internalName + "." + key, defaultValue);
	}
	
	/**
	 * Access a boolean config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected boolean getConfigBoolean(String key, boolean defaultValue) {
		return config.getBoolean("spells." + internalName + "." + key, defaultValue);
	}
	
	/**
	 * Access a String config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected String getConfigString(String key, String defaultValue) {
		return config.getString("spells." + internalName + "." + key, defaultValue);
	}
	
	/**
	 * Access a float config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected float getConfigFloat(String key, float defaultValue) {
		return (float)config.getDouble("spells." + internalName + "." + key, defaultValue);
	}
	
	/**
	 * Access a double config value for this spell.
	 * 
	 * @param key The key of the config value
	 * @param defaultValue The value to return if it does not exist in the config
	 * 
	 * @return The config value, or defaultValue if it does not exist
	 */
	protected double getConfigDouble(String key, double defaultValue) {
		return config.getDouble("spells." + internalName + "." + key, defaultValue);
	}
	
	protected List<Integer> getConfigIntList(String key, List<Integer> defaultValue) {
		return config.getIntList("spells." + internalName + "." + key, defaultValue);
	}
	
	protected List<String> getConfigStringList(String key, List<String> defaultValue) {
		return config.getStringList("spells." + internalName + "." + key, defaultValue);
	}
	
	protected Set<String> getConfigKeys(String key) {
		return config.getKeys("spells." + internalName + "." + key);
	}
	
	protected ConfigurationSection getConfigSection(String key) {
		return config.getSection("spells." + internalName + "." + key);
	}
	
	protected boolean isConfigString(String key) {
		return config.isString("spells." + internalName + "." + key);
	}
	
	protected boolean isConfigSection(String key) {
		return config.isSection("spells." + internalName + "." + key);
	}

	public final SpellCastResult cast(Player player) {
		return cast(player, 1.0F, null);
	}

	public final SpellCastResult cast(Player player, String[] args) {
		return cast(player, 1.0F, args);
	}
	
	public final SpellCastResult cast(Player player, float power, String[] args) {
		SpellCastEvent spellCast = preCast(player, power, args);
		if (spellCast == null) {
			return new SpellCastResult(SpellCastState.CANT_CAST, PostCastAction.HANDLE_NORMALLY);
		}
		PostCastAction action;
		int castTime = spellCast.getCastTime();
		if (castTime <= 0 || spellCast.getSpellCastState() != SpellCastState.NORMAL) {
			action = handleCast(spellCast);
		} else if (!preCastTimeCheck(player, args)) {
			action = PostCastAction.ALREADY_HANDLED;
		} else {
			action = PostCastAction.DELAYED;
			sendMessage(player, strCastStart);
			playSpellEffects(EffectPosition.START_CAST, player);
			if (MagicSpells.plugin.useExpBarAsCastTimeBar) {
				new DelayedSpellCastWithBar(spellCast);
			} else {
				new DelayedSpellCast(spellCast);
			}
		}
		return new SpellCastResult(spellCast.getSpellCastState(), action);
	}
	
	protected SpellCastState getCastState(Player player) {
		if (!MagicSpells.getSpellbook(player).canCast(this)) {
			return SpellCastState.CANT_CAST;
		} else if (worldRestrictions != null && !worldRestrictions.contains(player.getWorld().getName())) {
			return SpellCastState.WRONG_WORLD;
		} else if (MagicSpells.plugin.noMagicZones != null && MagicSpells.plugin.noMagicZones.willFizzle(player, this)) {
			return SpellCastState.NO_MAGIC_ZONE;
		} else if (onCooldown(player)) {
			return SpellCastState.ON_COOLDOWN;
		} else if (!hasReagents(player)) {
			return SpellCastState.MISSING_REAGENTS;
		} else {
			return SpellCastState.NORMAL;
		}
	}
	
	protected SpellCastEvent preCast(Player player, float power, String[] args) {
		// get spell state
		SpellCastState state = getCastState(player);
		debug(2, "    Spell cast state: " + state);
		
		// call events
		SpellCastEvent event = new SpellCastEvent(this, player, state, power, args, cooldown, reagents.clone(), castTime);
		Bukkit.getServer().getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			debug(2, "    Spell canceled");
			return null;
		} else {
			if (event.haveReagentsChanged()) {
				boolean hasReagents = hasReagents(player, event.getReagents());
				if (!hasReagents && state != SpellCastState.MISSING_REAGENTS) {
					event.setSpellCastState(SpellCastState.MISSING_REAGENTS);
					debug(2, "    Spell cast state changed: " + state);
				} else if (hasReagents && state == SpellCastState.MISSING_REAGENTS) {
					event.setSpellCastState(state = SpellCastState.NORMAL);
					debug(2, "    Spell cast state changed: " + state);
				}
			}
			if (event.hasSpellCastStateChanged()) {
				debug(2, "    Spell cast state changed: " + state);
			}
		}
		if (player.hasPermission("magicspells.nocasttime")) {
			event.setCastTime(0);
		}
		
		return event;
	}
	
	private PostCastAction handleCast(SpellCastEvent spellCast) {
		long start = System.nanoTime();
		Player player = spellCast.getCaster();
		SpellCastState state = spellCast.getSpellCastState();
		String[] args = spellCast.getSpellArgs();
		float power = spellCast.getPower();
		debug(3, "    Power: " + power);
		debug(3, "    Cooldown: " + cooldown);
		if (MagicSpells.plugin.debug && args != null && args.length > 0) {
			debug(3, "    Args: {" + Util.arrayJoin(args, ',') + "}");
		}
		PostCastAction action = castSpell(player, state, power, args);
		if (MagicSpells.plugin.enableProfiling) {
        	Long total = MagicSpells.plugin.profilingTotalTime.get(profilingKey);
        	if (total == null) total = (long)0;
        	total += (System.nanoTime() - start);
        	MagicSpells.plugin.profilingTotalTime.put(profilingKey, total);
        	Integer runs = MagicSpells.plugin.profilingRuns.get(profilingKey);
        	if (runs == null) runs = 0;
        	runs += 1;
        	MagicSpells.plugin.profilingRuns.put(profilingKey, runs);
		}
		postCast(spellCast, action);
		return action;
	}
	
	protected void postCast(SpellCastEvent spellCast, PostCastAction action) {
		debug(3, "    Post-cast action: " + action);
		Player player = spellCast.getCaster();
		SpellCastState state = spellCast.getSpellCastState();
		if (action != null && action != PostCastAction.ALREADY_HANDLED) {
			if (state == SpellCastState.NORMAL) {
				if (action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.COOLDOWN_ONLY || action == PostCastAction.NO_MESSAGES || action == PostCastAction.NO_REAGENTS) {
					setCooldown(player, spellCast.getCooldown());
				}
				if (action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.REAGENTS_ONLY || action == PostCastAction.NO_MESSAGES || action == PostCastAction.NO_COOLDOWN) {
					removeReagents(player, spellCast.getReagents());
				}
				if (action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.MESSAGES_ONLY || action == PostCastAction.NO_COOLDOWN || action == PostCastAction.NO_REAGENTS) {
					sendMessages(player);
				}
				if (experience > 0) {
					player.giveExp(experience);
				}
			} else if (state == SpellCastState.ON_COOLDOWN) {
				MagicSpells.sendMessage(player, formatMessage(strOnCooldown, "%c", Math.round(getCooldown(player))+""));
			} else if (state == SpellCastState.MISSING_REAGENTS) {
				MagicSpells.sendMessage(player, strMissingReagents);
				if (MagicSpells.plugin.showStrCostOnMissingReagents && strCost != null && !strCost.isEmpty()) {
					MagicSpells.sendMessage(player, "    (" + strCost + ")");
				}
			} else if (state == SpellCastState.CANT_CAST) {
				MagicSpells.sendMessage(player, strCantCast);
			} else if (state == SpellCastState.NO_MAGIC_ZONE) {
				MagicSpells.plugin.noMagicZones.sendNoMagicMessage(player, this);
			} else if (state == SpellCastState.WRONG_WORLD) {
				MagicSpells.sendMessage(player, strWrongWorld);
			}
		}
		SpellCastedEvent event = new SpellCastedEvent(this, player, state, spellCast.getPower(), spellCast.getSpellArgs(), cooldown, reagents, action);
		Bukkit.getPluginManager().callEvent(event);
	}
	
	public void sendMessages(Player player) {
		sendMessage(player, strCastSelf, "%a", player.getDisplayName());
		sendMessageNear(player, formatMessage(strCastOthers, "%a", player.getDisplayName()));
	}

	protected boolean preCastTimeCheck(Player player, String[] args) {
		return true;
	}
	
	/**
	 * This method is called when a player casts a spell, either by command, with a wand item, or otherwise.
	 * @param player the player casting the spell
	 * @param state the state of the spell cast (normal, on cooldown, missing reagents, etc)
	 * @param power the power multiplier the spell should be cast with (1.0 is normal)
	 * @param args the spell arguments, if cast by command
	 * @return the action to take after the spell is processed
	 */
	public abstract PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args);
		
	public List<String> tabComplete(CommandSender sender, String partial) {
		return null;
	}
	
	protected List<String> tabCompletePlayerName(CommandSender sender, String partial) {
		ArrayList<String> matches = new ArrayList<String>();
		partial = partial.toLowerCase();
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getName().toLowerCase().startsWith(partial)) {
				if (sender.isOp() || !(sender instanceof Player) || ((Player)sender).canSee(p)) {
					matches.add(p.getName());
				}
			}
		}
		if (matches.size() > 0) {
			return matches;
		} else {
			return null;
		}
	}
	
	protected List<String> tabCompleteSpellName(CommandSender sender, String partial) {
		return Util.tabCompleteSpellName(sender, partial);
	}
	
	/**
	 * This method is called when the spell is cast from the console.
	 * @param sender the console sender.
	 * @param args the command arguments
	 * @return true if the spell was handled, false otherwise
	 */
	public boolean castFromConsole(CommandSender sender, String[] args) {
		return false;
	}
	
	public abstract boolean canCastWithItem();
	
	public abstract boolean canCastByCommand();
	
	public boolean canCastWithLeftClick() {
		return castWithLeftClick;
	}
	
	public boolean canCastWithRightClick() {
		return castWithRightClick;
	}
	
	public boolean isAlwaysGranted() {
		return alwaysGranted;
	}
	
	public boolean isValidItemForCastCommand(ItemStack item) {
		if (!requireCastItemOnCommand || castItems == null) {
			return true;
		} else if (item == null && castItems.length == 1 && castItems[0].getItemTypeId() == 0) {
			return true;
		} else {
			for (CastItem castItem : castItems) {
				if (castItem.equals(item)) {
					return true;
				}
			}
			return false;
		}
	}
	
	public boolean canBind(CastItem item) {
		if (!bindable) {
			return false;
		} else if (bindableItems == null)  {
			return true;
		} else {
			return bindableItems.contains(item);
		}
	}
	
	public ItemStack getSpellIcon() {
		return spellIcon;
	}
	
	public String getCostStr() {
		if (strCost == null || strCost.equals("")) {
			return null;
		} else {
			return strCost;
		}
	}
	
	/**
	 * Check whether this spell is currently on cooldown for the specified player
	 * @param player The player to check
	 * @return whether the spell is on cooldown
	 */
	public boolean onCooldown(Player player) {
		if (player.hasPermission("magicspells.nocooldown")) {
			return false;
		}
		
		if (charges > 0) {
			return chargesConsumed.get(player.getName()) >= charges;
		}
		
		Long next = nextCast.get(player.getName());
		if (next != null) {
			if (next > System.currentTimeMillis()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Get how many seconds remain on the cooldown of this spell for the specified player
	 * @param player The player to check
	 * @return The number of seconds remaining in the cooldown
	 */
	public float getCooldown(Player player) {
		if (charges > 0) return -1;
		Long next = nextCast.get(player.getName());
		if (next != null) {
			float c = (next - System.currentTimeMillis()) / 1000F;
			return c > 0 ? c : 0;
		} else {
			return 0;
		}
	}
	
	/**
	 * Begins the cooldown for the spell for the specified player
	 * @param player The player to set the cooldown for
	 */
	public void setCooldown(Player player, float cooldown) {
		setCooldown(player, cooldown, true);
	}
	
	/**
	 * Begins the cooldown for the spell for the specified player
	 * @param player The player to set the cooldown for
	 */
	public void setCooldown(final Player player, float cooldown, boolean activateSharedCooldowns) {
		if (cooldown > 0) {
			if (charges <= 0) {
				nextCast.put(player.getName(), System.currentTimeMillis() + (long)(cooldown * 1000L));
			} else {
				final String name = player.getName();
				chargesConsumed.increment(name);
				MagicSpells.scheduleDelayedTask(new Runnable() {
					public void run() {
						chargesConsumed.decrement(name);
						if (rechargeSound != null && !rechargeSound.isEmpty()) {
							MagicSpells.getVolatileCodeHandler().playSound(player, rechargeSound, 1.0F, 1.0F);
						}
					}
				}, Math.round(20F * cooldown));
			}
		} else {
			if (charges <= 0) {
				nextCast.remove(player.getName());
			} else {
				chargesConsumed.remove(player.getName());
			}
		}
		if (activateSharedCooldowns && sharedCooldowns != null) {
			for (Map.Entry<Spell, Float> scd : sharedCooldowns.entrySet()) {
				scd.getKey().setCooldown(player, scd.getValue(), false);
			}
		}
	}
	
	/**
	 * Checks if a player has the reagents required to cast this spell
	 * @param player the player to check
	 * @return true if the player has the reagents, false otherwise
	 */
	protected boolean hasReagents(Player player) {
		return hasReagents(player, reagents);
	}
	
	/**
	 * Checks if a player has the reagents required to cast this spell
	 * @param player the player to check
	 * @param reagents the reagents to check for
	 * @return true if the player has the reagents, false otherwise
	 */
	protected boolean hasReagents(Player player, SpellReagents reagents) {
		if (reagents == null) return true;
		return hasReagents(player, reagents.getItemsAsArray(), reagents.getHealth(), reagents.getMana(), reagents.getHunger(), reagents.getExperience(), reagents.getLevels(), reagents.getDurability(), reagents.getMoney(), reagents.getVariables());
	}
	
	/**
	 * Checks if a player has the specified reagents, including health and mana
	 * @param player the player to check
	 * @param reagents the inventory item reagents to look for
	 * @param healthCost the health cost, in half-hearts
	 * @param manaCost the mana cost
	 * @return true if the player has all the reagents, false otherwise
	 */
	private boolean hasReagents(Player player, ItemStack[] reagents, int healthCost, int manaCost, int hungerCost, int experienceCost, int levelsCost, int durabilityCost, float moneyCost, Map<String, Double> variables) {
		if (player.hasPermission("magicspells.noreagents")) {
			return true;
		}
		if (healthCost > 0 && player.getHealth() <= healthCost) {
			return false;
		}
		if (manaCost > 0 && (MagicSpells.plugin.mana == null || !MagicSpells.plugin.mana.hasMana(player, manaCost))) {
			return false;
		}
		if (hungerCost > 0 && player.getFoodLevel() < hungerCost) {
			return false;
		}
		if (experienceCost > 0 && !ExperienceUtils.hasExp(player, experienceCost)) {
			return false;
		}
		if (levelsCost > 0 && player.getLevel() < levelsCost) {
			return false;
		}
		if (durabilityCost > 0) {
			ItemStack inHand = player.getItemInHand();
			if (inHand == null || inHand.getDurability() >= inHand.getType().getMaxDurability()) {
				return false;
			}
		}
		if (moneyCost > 0) {
			MoneyHandler moneyHandler = MagicSpells.getMoneyHandler();
			if (moneyHandler == null || !moneyHandler.hasMoney(player, moneyCost)) {
				return false;
			}
		}
		if (reagents != null) {
			for (ItemStack item : reagents) {
				if (item != null && !inventoryContains(player.getInventory(), item)) {
					return false;
				}
			}
		}
		if (variables != null) {
			VariableManager varMan = MagicSpells.getVariableManager();
			if (varMan == null) return false;
			for (String var : variables.keySet()) {
				double val = variables.get(var);
				if (val > 0 && varMan.getValue(var, player) < val) {
					return false;
				}
			}
		}
		
		return true;		
	}
	
	/**
	 * Removes the reagent cost of this spell from the player's inventoryy.
	 * This does not check if the player has the reagents, use hasReagents() for that.
	 * @param player the player to remove reagents from
	 */
	protected void removeReagents(Player player) {
		removeReagents(player, reagents);
	}
	
	/**
	 * Removes the specified reagents from the player's inventoryy.
	 * This does not check if the player has the reagents, use hasReagents() for that.
	 * @param player the player to remove the reagents from
	 * @param reagents the inventory item reagents to remove
	 */
	protected void removeReagents(Player player, ItemStack[] reagents) {
		removeReagents(player, reagents, 0, 0, 0, 0, 0, 0, 0, null);
	}
	
	protected void removeReagents(Player player, SpellReagents reagents) {
		removeReagents(player, reagents.getItemsAsArray(), reagents.getHealth(), reagents.getMana(), reagents.getHunger(), reagents.getExperience(), reagents.getLevels(), reagents.getDurability(), reagents.getMoney(), reagents.getVariables());
	}
	
	/**
	 * Removes the specified reagents, including health and mana, from the player's inventory.
	 * This does not check if the player has the reagents, use hasReagents() for that.
	 * @param player the player to remove the reagents from
	 * @param reagents the inventory item reagents to remove
	 * @param healthCost the health to remove
	 * @param manaCost the mana to remove
	 */
	private void removeReagents(Player player, ItemStack[] reagents, int healthCost, int manaCost, int hungerCost, int experienceCost, int levelsCost, int durabilityCost, float moneyCost, Map<String, Double> variables) {
		if (player.hasPermission("magicspells.noreagents")) {
			return;
		}
		if (reagents != null) {
			for (ItemStack item : reagents) {
				if (item != null) {
					removeFromInventory(player.getInventory(), item);
				}
			}
		}
		if (healthCost != 0) {
			double h = player.getHealth() - healthCost;
			if (h < 0) h = 0;
			if (h > player.getMaxHealth()) h = player.getMaxHealth();
			player.setHealth(h);
		}
		if (manaCost != 0) {
			MagicSpells.plugin.mana.addMana(player, -manaCost, ManaChangeReason.SPELL_COST);
		}
		if (hungerCost != 0) {
			int f = player.getFoodLevel() - hungerCost;
			if (f < 0) f = 0;
			if (f > 20) f = 20;
			player.setFoodLevel(f);
		}
		if (experienceCost != 0) {
			ExperienceUtils.changeExp(player, -experienceCost);
		}
		if (durabilityCost != 0) {
			ItemStack inHand = player.getItemInHand();
			if (inHand != null && inHand.getType().getMaxDurability() > 0) {
				short newDura = (short) (inHand.getDurability() + durabilityCost);
				if (newDura < 0) newDura = 0;
				if (newDura >= inHand.getType().getMaxDurability()) {
					player.setItemInHand(null);
				} else {
					inHand.setDurability(newDura);
					player.setItemInHand(inHand);
				}
			}
		}
		if (moneyCost != 0) {
			MoneyHandler moneyHandler = MagicSpells.getMoneyHandler();
			if (moneyHandler != null) {
				if (moneyCost > 0) {
					moneyHandler.removeMoney(player, moneyCost);
				} else {
					moneyHandler.addMoney(player, moneyCost);
				}
			}
		}
		if (levelsCost != 0) {
			int lvl = player.getLevel() - levelsCost;
			if (lvl < 0) lvl = 0;
			player.setLevel(lvl);
		}
		if (variables != null) {
			VariableManager varMan = MagicSpells.getVariableManager();
			if (varMan != null) {
				for (String var : variables.keySet()) {
					varMan.modify(var, player, -variables.get(var));
				}
			}
		}
	}
	
	private boolean inventoryContains(Inventory inventory, ItemStack item) {
		int count = 0;
		ItemStack[] items = inventory.getContents();
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null && item.isSimilar(items[i])) {
				count += items[i].getAmount();
			}
			if (count >= item.getAmount()) {
				return true;
			}
		}
		return false;
	}
	
	private void removeFromInventory(Inventory inventory, ItemStack item) {
		int amt = item.getAmount();
		ItemStack[] items = inventory.getContents();
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null && item.isSimilar(items[i])) {
				if (items[i].getAmount() > amt) {
					items[i].setAmount(items[i].getAmount() - amt);
					break;
				} else if (items[i].getAmount() == amt) {
					items[i] = null;
					break;
				} else {
					amt -= items[i].getAmount();
					items[i] = null;
				}
			}
		}
		inventory.setContents(items);
	}
	
	protected int getRange(float power) {
		return spellPowerAffectsRange ? Math.round(range * power) : range;
	}
	
	/**
	 * Gets the player a player is currently looking at, ignoring other living entities
	 * @param player the player to get the target for
	 * @param range the maximum range to check
	 * @param checkLos whether to obey line-of-sight restrictions
	 * @return the targeted Player, or null if none was found
	 */
	protected TargetInfo<Player> getTargetedPlayer(Player player, float power) {
		TargetInfo<LivingEntity> target = getTargetedEntity(player, power, true, null);
		if (target != null && target.getTarget() instanceof Player) {
			return new TargetInfo<Player>((Player)target.getTarget(), target.getPower());
		} else {
			return null;
		}
	}
	
	protected TargetInfo<Player> getTargetPlayer(Player player, float power) {
		return getTargetedPlayer(player, power);
	}
	
	protected TargetInfo<LivingEntity> getTargetedEntity(Player player, float power) {
		return getTargetedEntity(player, power, false, null);
	}
	
	protected TargetInfo<LivingEntity> getTargetedEntity(Player player, float power, ValidTargetChecker checker) {
		return getTargetedEntity(player, power, false, checker);
	}
	
	protected TargetInfo<LivingEntity> getTargetedEntity(Player player, float power, boolean forceTargetPlayers, ValidTargetChecker checker) {
		// get nearby entities
		int range = getRange(power);
		List<Entity> ne = player.getNearbyEntities(range, range, range);
		
		// get valid targets
		List<LivingEntity> entities;
		if (MagicSpells.plugin.checkWorldPvpFlag && validTargetList.canTargetPlayers() && !isBeneficial() && !player.getWorld().getPVP()) {
			entities = validTargetList.filterTargetList(player, ne, false);
		} else if (forceTargetPlayers) {
			entities = validTargetList.filterTargetList(player, ne, true);
		} else {
			entities = validTargetList.filterTargetList(player, ne);
		}
		
		// find target
		LivingEntity target = null;
		BlockIterator bi;
		try {
			bi = new BlockIterator(player, range);
		} catch (IllegalStateException e) {
			return null;
		}
		Block b;
		Location l;
		int bx, by, bz;
		double ex, ey, ez;
		// do min range
		for (int i = 0; i < minRange && bi.hasNext(); i++) {
			bi.next();
		}
		// loop through player's line of sight
		while (bi.hasNext()) {
			b = bi.next();
			bx = b.getX();
			by = b.getY();
			bz = b.getZ();
			if (obeyLos && !BlockUtils.isTransparent(this, b)) {
				// line of sight is broken, stop without target
				break;
			} else {
				// check for entities near this block in the line of sight
				for (LivingEntity e : entities) {
					l = e.getLocation();
					ex = l.getX();
					ey = l.getY();
					ez = l.getZ();
					if ((bx-.75 <= ex && ex <= bx+1.75) && (bz-.75 <= ez && ez <= bz+1.75) && (by-1 <= ey && ey <= by+2.5)) {
						// entity is close enough, set target and stop
						target = e;
						
						// check for invalid target
						if (target != null && target instanceof Player && ((Player)target).getGameMode() == GameMode.CREATIVE) {
							target = null;
							continue;
						}
						
						// check for anti-magic-zone
						if (target != null && MagicSpells.getNoMagicZoneManager() != null && MagicSpells.getNoMagicZoneManager().willFizzle(target.getLocation(), this)) {
							target = null;
							continue;
						}
						
						// check for teams
						if (target != null && target instanceof Player && MagicSpells.plugin.checkScoreboardTeams) {
							Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
							Team playerTeam = scoreboard.getPlayerTeam(player);
							Team targetTeam = scoreboard.getPlayerTeam((Player)target);
							if (playerTeam != null && targetTeam != null) {
								if (playerTeam.equals(targetTeam)) {
									if (!playerTeam.allowFriendlyFire() && !this.isBeneficial()) {
										target = null;
										continue;
									}
								} else {
									if (this.isBeneficial()) {
										target = null;
										continue;
									}
								}
							}
						}
						
						// call event listeners
						if (target != null) {
							SpellTargetEvent event = new SpellTargetEvent(this, player, target, power);
							Bukkit.getServer().getPluginManager().callEvent(event);
							if (event.isCancelled()) {
								target = null;
								continue;
							} else {
								target = event.getTarget();
								power = event.getPower();
							}
						}
						
						// call damage event
						if (targetDamageCause != null) {
							EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, targetDamageCause, targetDamageAmount);
							Bukkit.getServer().getPluginManager().callEvent(event);
							if (event.isCancelled()) {
								target = null;
								continue;
							}
						}
						
						// run checker
						if (target != null && checker != null) {
							if (!checker.isValidTarget(target)) {
								target = null;
								continue;
							}
						}
						
						return new TargetInfo<LivingEntity>(target, power);
					}
				}
			}
		}
		
		return null;
	}
	
	protected Block getTargetedBlock(LivingEntity entity, float power) {
		return BlockUtils.getTargetBlock(this, entity, spellPowerAffectsRange ? Math.round(range * power) : range);
	}
	
	protected List<Block> getLastTwoTargetedBlocks(LivingEntity entity, float power) {
		return BlockUtils.getLastTwoTargetBlock(this, entity, spellPowerAffectsRange ? Math.round(range * power) : range);
	}
	
	public HashSet<Byte> getLosTransparentBlocks() {
		return losTransparentBlocks;
	}
	
	protected void playSpellEffects(Entity pos1, Entity pos2) {
		playSpellEffects(EffectPosition.CASTER, pos1);
		playSpellEffects(EffectPosition.TARGET, pos2);
		playSpellEffectsTrail(pos1.getLocation(), pos2.getLocation());
	}
	
	protected void playSpellEffects(Entity pos1, Location pos2) {
		playSpellEffects(EffectPosition.CASTER, pos1);
		playSpellEffects(EffectPosition.TARGET, pos2);
		playSpellEffectsTrail(pos1.getLocation(), pos2);
	}
	
	protected void playSpellEffects(Location pos1, Entity pos2) {
		playSpellEffects(EffectPosition.CASTER, pos1);
		playSpellEffects(EffectPosition.TARGET, pos2);
		playSpellEffectsTrail(pos1, pos2.getLocation());
	}
	
	protected void playSpellEffects(Location pos1, Location pos2) {
		playSpellEffects(EffectPosition.CASTER, pos1);
		playSpellEffects(EffectPosition.TARGET, pos2);
		playSpellEffectsTrail(pos1, pos2);
	}
	
	protected void playSpellEffects(EffectPosition pos, Entity entity) {
		if (effects != null) {
			List<SpellEffect> effectsList = effects.get(pos);
			if (effectsList != null) {
				for (SpellEffect effect : effectsList) {
					effect.playEffect(entity);
				}
			}
		}
	}
	
	protected void playSpellEffects(EffectPosition pos, Location location) {
		if (effects != null) {
			List<SpellEffect> effectsList = effects.get(pos);
			if (effectsList != null) {
				for (SpellEffect effect : effectsList) {
					effect.playEffect(location);
				}
			}
		}
	}
	
	protected void playSpellEffectsTrail(Location loc1, Location loc2) {
		if (effects != null) {
			List<SpellEffect> effectsList = effects.get(EffectPosition.TRAIL);
			if (effectsList != null) {
				for (SpellEffect effect : effectsList) {
					effect.playEffect(loc1, loc2);
				}
			}
		}
	}
	
	protected void playSpellEffectsBuff(Entity entity, SpellEffect.SpellEffectActiveChecker checker) {
		if (effects != null) {
			List<SpellEffect> effectsList = effects.get(EffectPosition.BUFF);
			if (effectsList != null) {
				for (SpellEffect effect : effectsList) {
					effect.playEffectWhileActiveOnEntity(entity, checker);
				}
			}
			effectsList = effects.get(EffectPosition.ORBIT);
			if (effectsList != null) {
				for (SpellEffect effect : effectsList) {
					effect.playEffectWhileActiveOrbit(entity, checker);
				}
			}
		}
	}
	
	protected void registerEvents() {
		registerEvents(this);
	}
	
	protected void registerEvents(Listener listener) {
		MagicSpells.registerEvents(listener);
	}
	
	protected void unregisterEvents() {
		unregisterEvents(this);
	}
	
	protected void unregisterEvents(Listener listener) {
		HandlerList.unregisterAll(listener);
	}
	
	protected int scheduleDelayedTask(Runnable task, int delay) {
		return MagicSpells.scheduleDelayedTask(task, delay);
	}
	
	protected int scheduleRepeatingTask(Runnable task, int delay, int interval) {
		return MagicSpells.scheduleRepeatingTask(task, delay, interval);
	}
	
	/**
	 * Formats a string by performing the specified replacements.
	 * @param message the string to format
	 * @param replacements the replacements to make, in pairs.
	 * @return the formatted string
	 */
	protected String formatMessage(String message, String... replacements) {
		return MagicSpells.formatMessage(message, replacements);
	}
	
	/**
	 * Sends a message to a player, first making the specified replacements. This method also does color replacement and has multi-line functionality.
	 * @param player the player to send the message to
	 * @param message the message to send
	 * @param replacements the replacements to be made, in pairs
	 */
	protected void sendMessage(Player player, String message, String... replacements) {
		sendMessage(player, formatMessage(message, replacements));
	}
	
	/**
	 * Sends a message to a player. This method also does color replacement and has multi-line functionality.
	 * @param player the player to send the message to
	 * @param message the message to send
	 */
	protected void sendMessage(Player player, String message) {
		MagicSpells.sendMessage(player, message);
	}
	
	/**
	 * Sends a message to all players near the specified player, within the configured broadcast range.
	 * @param player the "center" player used to find nearby players
	 * @param message the message to send
	 */
	protected void sendMessageNear(Player player, String message) {
		sendMessageNear(player, null, message, broadcastRange);
	}
	
	/**
	 * Sends a message to all players near the specified player, within the specified broadcast range.
	 * @param player the "center" player used to find nearby players
	 * @param message the message to send
	 * @param range the broadcast range
	 */
	protected void sendMessageNear(Player player, Player ignore, String message, int range) {
		if (message != null && !message.equals("") && !player.hasPermission("magicspells.silent")) {
			String [] msgs = message.replaceAll("&([0-9a-f])", "\u00A7$1").split("\n");
			List<Entity> entities = player.getNearbyEntities(range*2, range*2, range*2);
			for (Entity entity : entities) {
				if (entity instanceof Player && entity != player && entity != ignore) {
					for (String msg : msgs) {
						if (!msg.equals("")) {
							((Player)entity).sendMessage(MagicSpells.plugin.textColor + msg);
						}
					}
				}
			}
		}
	}
	
	public String getInternalName() {
		return this.internalName;
	}
	
	public String getName() {
		if (this.name != null && !this.name.isEmpty()) {
			return this.name;
		} else {
			return this.internalName;
		}
	}
	
	public String getPermissionName() {
		return permName;
	}
	
	public boolean isHelperSpell() {
		return helperSpell;
	}
	
	public String getCantBindError() {
		return strCantBind;
	}
	
	public String[] getAliases() {
		return this.aliases;
	}
	
	public List<String> getIncantations() {
		return this.incantations;
	}
	
	public CastItem getCastItem() {
		if (this.castItems.length == 1) {
			return this.castItems[0];
		} else {
			return null;
		}
	}
	
	public CastItem[] getCastItems() {
		return this.castItems;
	}
	
	public CastItem[] getRightClickCastItems() {
		return this.rightClickCastItems;
	}
	
	public CastItem[] getConsumeCastItems() {
		return this.consumeCastItems;
	}
	
	public String getDanceCastSequence() {
		return this.danceCastSequence;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public SpellReagents getReagents() {
		return this.reagents;
	}
	
	public String getConsoleName() {
		return MagicSpells.plugin.strConsoleName;
	}
	
	public String getStrWrongCastItem() {
		return strWrongCastItem;
	}
	
	public final boolean isBeneficial() {
		return beneficial;
	}
	
	public boolean isBeneficialDefault() {
		return false;
	}
	
	public ModifierSet getModifiers() {
		return modifiers;
	}
	
	public ModifierSet getTargetModifiers() {
		return targetModifiers;
	}
	
	public String getStrModifierFailed() {
		return strModifierFailed;
	}
	
	public Map<String, Integer> getXpGranted() {
		return xpGranted;
	}
	
	public Map<String, Integer> getXpRequired() {
		return xpRequired;
	}
	
	public String getStrXpLearned() {
		return strXpAutoLearned;
	}
	
	Map<String, Long> getCooldowns() {
		return nextCast;
	}
	
	public Map<String, Double> getVariableModsCast() {
		return variableModsCast;
	}
	
	public Map<String, Double> getVariableModsCasted() {
		return variableModsCasted;
	}
	
	public Map<String, Double> getVariableModsTarget() {
		return variableModsTarget;
	}	
	
	void setCooldownManually(String name, long nextCast) {
		this.nextCast.put(name, nextCast);
	}
	
	protected void debug(int level, String message) {
		if (debug) MagicSpells.debug(level, message);
	}
	
	/**
	 * This method is called when the plugin is being disabled, for any reason.
	 */
	protected void turnOff() {
	}
	
	@Override
	public int compareTo(Spell spell) {
		return this.name.compareTo(spell.name);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Spell && ((Spell)o).internalName.equals(this.internalName)) {
			return true;
		} else {
			return false;
		}		
	}
	
	@Override
	public int hashCode() {
		return internalName.hashCode();
	}
	
	public enum SpellCastState {
		NORMAL,
		ON_COOLDOWN,
		MISSING_REAGENTS,
		CANT_CAST,
		NO_MAGIC_ZONE,
		WRONG_WORLD
	}
	
	public enum PostCastAction {
		HANDLE_NORMALLY(true, true, true),
		ALREADY_HANDLED(false, false, false),
		NO_MESSAGES(true, true, false),
		NO_REAGENTS(true, false, true),
		NO_COOLDOWN(false, true, true),
		MESSAGES_ONLY(false, false, true),
		REAGENTS_ONLY(false, true, false),
		COOLDOWN_ONLY(true, false, false),
		DELAYED(false, false, false);
		
		private boolean cooldown;
		private boolean reagents;
		private boolean messages;
		private PostCastAction(boolean cooldown, boolean reagents, boolean messages) {
			this.cooldown = cooldown;
			this.reagents = reagents;
			this.messages = messages;
		}
		
		public boolean setCooldown() {
			return cooldown;
		}
		
		public boolean chargeReagents() {
			return reagents;
		}
		
		public boolean sendMessages() {
			return messages;
		}
	}
	
	public class SpellCastResult {
		public SpellCastState state;
		public PostCastAction action;
		public SpellCastResult(SpellCastState state, PostCastAction action) {
			this.state = state;
			this.action = action;
		}
	}
	
	public class DelayedSpellCast implements Runnable, Listener {
		private Player player;
		private Location prevLoc;
		private Spell spell;
		private SpellCastEvent spellCast;
		private int taskId;
		private boolean cancelled = false;
		
		public DelayedSpellCast(SpellCastEvent spellCast) {
			this.player = spellCast.getCaster();
			this.prevLoc = player.getLocation().clone();
			this.spell = spellCast.getSpell();
			this.spellCast = spellCast;
			
			taskId = scheduleDelayedTask(this, spellCast.getCastTime());
			registerEvents(this);
		}
		
		@Override
		public void run() {
			if (!cancelled && player.isOnline() && !player.isDead()) {
				Location currLoc = player.getLocation();
				if (!interruptOnMove || (Math.abs(currLoc.getX() - prevLoc.getX()) < .2 && Math.abs(currLoc.getY() - prevLoc.getY()) < .2 && Math.abs(currLoc.getZ() - prevLoc.getZ()) < .2)) {
					if (!spell.hasReagents(player, reagents)) {
						spellCast.setSpellCastState(SpellCastState.MISSING_REAGENTS);
					}
					spell.handleCast(spellCast);
				} else {
					interrupt();
				}
			}
			unregisterEvents(this);
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onDamage(EntityDamageEvent event) {
			if (interruptOnDamage && !cancelled && event.getEntity().equals(player)) {
				cancelled = true;
				Bukkit.getScheduler().cancelTask(taskId);
				interrupt();
			}
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onSpellCast(SpellCastEvent event) {
			if (interruptOnCast && !cancelled && !(event.getSpell() instanceof PassiveSpell) && event.getCaster().equals(player)) {
				cancelled = true;
				Bukkit.getScheduler().cancelTask(taskId);
				interrupt();
			}
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onTeleport(PlayerTeleportEvent event) {
			if (interruptOnTeleport && !cancelled && event.getPlayer().equals(player)) {
				cancelled = true;
				Bukkit.getScheduler().cancelTask(taskId);
				interrupt();
			}
		}
		
		private void interrupt() {
			sendMessage(player, strInterrupted);
			if (spellOnInterrupt != null) {
				spellOnInterrupt.castSpell(player, SpellCastState.NORMAL, spellCast.getPower(), null);
			}
		}
	}
	
	public class DelayedSpellCastWithBar implements Runnable, Listener {
		private Player player;
		private Location prevLoc;
		private Spell spell;
		private SpellCastEvent spellCast;
		private int castTime;
		private int taskId;
		private boolean cancelled = false;
		
		private int interval = 5;
		private int elapsed = 0;
		
		public DelayedSpellCastWithBar(SpellCastEvent spellCast) {
			this.player = spellCast.getCaster();
			this.prevLoc = player.getLocation().clone();
			this.spell = spellCast.getSpell();
			this.spellCast = spellCast;
			this.castTime = spellCast.getCastTime();
			
			MagicSpells.getExpBarManager().lock(player, this);
			
			taskId = scheduleRepeatingTask(this, interval, interval);
			registerEvents(this);
		}
		
		@Override
		public void run() {
			if (!cancelled && player.isOnline() && !player.isDead()) {
				elapsed += interval;
				Location currLoc = player.getLocation();
				if (!interruptOnMove || (Math.abs(currLoc.getX() - prevLoc.getX()) < .2 && Math.abs(currLoc.getY() - prevLoc.getY()) < .2 && Math.abs(currLoc.getZ() - prevLoc.getZ()) < .2)) {
					if (elapsed >= castTime) {
						if (!spell.hasReagents(player, reagents)) {
							spellCast.setSpellCastState(SpellCastState.MISSING_REAGENTS);
						}
						spell.handleCast(spellCast);
						cancelled = true;
					}
					MagicSpells.getExpBarManager().update(player, 0, ((float)elapsed / (float)castTime), this);
				} else {
					interrupt();
				}
			} else {
				end();
			}
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onDamage(EntityDamageEvent event) {
			if (interruptOnDamage && !cancelled && event.getEntity().equals(player)) {
				cancelled = true;
				interrupt();
			}
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onSpellCast(SpellCastEvent event) {
			if (interruptOnCast && !cancelled && !(event.getSpell() instanceof PassiveSpell) && event.getCaster() != null && event.getCaster().equals(player)) {
				cancelled = true;
				interrupt();
			}
		}
		
		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onTeleport(PlayerTeleportEvent event) {
			if (interruptOnTeleport && !cancelled && event.getPlayer().equals(player)) {
				cancelled = true;
				interrupt();
			}
		}
		
		private void interrupt() {
			sendMessage(player, strInterrupted);
			end();
			if (spellOnInterrupt != null) {
				spellOnInterrupt.castSpell(player, SpellCastState.NORMAL, spellCast.getPower(), null);
			}
		}
		
		private void end() {
			cancelled = true;
			Bukkit.getScheduler().cancelTask(taskId);
			unregisterEvents(this);
			MagicSpells.getExpBarManager().unlock(player, this);
			MagicSpells.getExpBarManager().update(player, player.getLevel(), player.getExp());
			ManaHandler mana = MagicSpells.getManaHandler();
			if (mana != null) {
				mana.showMana(player);
			}
		}
	}
	
	public ValidTargetChecker getValidTargetChecker() {
		return null;
	}
	
	public interface ValidTargetChecker {
		public boolean isValidTarget(LivingEntity entity);
	}

}
