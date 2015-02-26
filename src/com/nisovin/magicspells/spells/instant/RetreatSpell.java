package com.nisovin.magicspells.spells.instant;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.MagicLocation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RetreatSpell extends InstantSpell {

    private String hideoutSpellName;
    private String strNoHideout;
    private String strRetreatFailed;

    public RetreatSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        hideoutSpellName = getConfigString("hideout-spell", "hideout");
        strNoHideout = getConfigString("str-no-hideout", "No hideout has been set.");
        strRetreatFailed = getConfigString("str-retreat-failed", "You were unable to retreat to the hideout.");
    }

    @Override
    public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
        if(state==SpellCastState.NORMAL) {
            MagicLocation hideout = getHideout();
            if(hideout==null) {
                sendMessage(player, strNoHideout);
                return PostCastAction.ALREADY_HANDLED;
            }
            Location from = player.getLocation();
            Location destination = hideout.getLocation();
            boolean tele = player.teleport(destination);
            if(!tele) {
                sendMessage(player, strRetreatFailed);
            }
            else {
                playSpellEffects(EffectPosition.CASTER,from);
                playSpellEffects(EffectPosition.TARGET,destination);
            }
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

    private MagicLocation getHideout() {
        Spell spell = MagicSpells.getSpellByInternalName(hideoutSpellName);
        if(spell!=null&&spell instanceof HideoutSpell) {
            return ((HideoutSpell) spell).getHideout();
        }
        else return null;
    }
}
