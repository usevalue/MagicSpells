package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.TargetInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;

public class TeleportSpell extends TargetedSpell implements TargetedEntitySpell {

	private boolean requireExactName;
	private boolean requireAcceptance;
	private int maxAcceptDelay;
	private String acceptCommand;
	private String strUsage;
	private String strTeleportPending;
	private String strTeleportAccepted;
	private String strTeleportExpired;

	private HashMap<Player,Location> pendingTeleports;
	private HashMap<Player,Long> pendingTimes;
	
	public TeleportSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		requireExactName = getConfigBoolean("require-exact-name", false);
		requireAcceptance = getConfigBoolean("require-acceptance", true);
		maxAcceptDelay = getConfigInt("max-accept-delay", 90);
		acceptCommand = getConfigString("accept-command", "allow");
		strUsage = getConfigString("str-usage", "Usage: /cast teleport <playername>, or /cast teleport \nwhile looking at a sign with a player name on the first line.");
		strTeleportPending = getConfigString("str-teleport-pending", "Someone wants to teleport to you! Type /allow to accept it.");
		strTeleportAccepted = getConfigString("str-teleport-accepted", "%c teleports to you.");
		strTeleportExpired = getConfigString("str-teleport-expired", "The teleportation has expired.");

		if (requireAcceptance) {
			pendingTeleports = new HashMap<Player,Location>();
			pendingTimes = new HashMap<Player,Long>();
		}
		
	}

	@Override
	public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(player, power);
			if (target == null) {
				return noTarget(player);
			}

			Location landLoc = target.getTarget().getLocation();

			if (requireAcceptance) {
				pendingTeleports.put(player, landLoc);
				pendingTimes.put(player, System.currentTimeMillis());
				sendMessage((Player)target.getTarget(), strTeleportPending, "%a", player.getDisplayName());
			} else {

			boolean ok = teleport(player, target.getTarget());
				if (!ok) {
					return noTarget(player);
				}
			}
			sendMessages(player, target.getTarget());
			return PostCastAction.NO_MESSAGES;
		}
		return PostCastAction.HANDLE_NORMALLY;
	}
	
	boolean teleport(Player caster, LivingEntity target) {
		Location casterLoc = caster.getLocation();
		boolean ok = caster.teleport(target);
		if (ok) {
			playSpellEffects(EffectPosition.CASTER, casterLoc);
			playSpellEffects(EffectPosition.TARGET, target.getLocation());
			playSpellEffectsTrail(casterLoc, target.getLocation());
		}
		return ok;
	}

	@Override
	public boolean castAtEntity(Player caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target)) return false;
		return teleport(caster, target);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return false;
	}

	
	// Required for acceptance

	@EventHandler(priority=EventPriority.LOW)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		if (requireAcceptance && event.getMessage().equalsIgnoreCase("/" + acceptCommand) && pendingTeleports.containsKey(event.getPlayer())) {
			Player player = event.getPlayer();
			if (maxAcceptDelay > 0 && pendingTimes.get(player) + maxAcceptDelay*1000 < System.currentTimeMillis()) {
				// waited too long
				sendMessage(player, strTeleportExpired);
			} else {
				// all ok, teleport
				player.teleport(pendingTeleports.get(player));
				sendMessage(player, strTeleportAccepted);
			}
			pendingTeleports.remove(player);
			pendingTimes.remove(player);
			event.setCancelled(true);
		}
	}
}
