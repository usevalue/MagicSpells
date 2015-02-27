package com.nisovin.magicspells.spells.instant;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.MagicLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.*;

public class HideoutSpell extends InstantSpell {

    private MagicLocation hideout;

    public HideoutSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        loadHideout();
    }

    @Override
    public PostCastAction castSpell(Player player, SpellCastState state, float power, String[] args) {
        if (state == SpellCastState.NORMAL) {
            Location loc = player.getLocation();
            loc.setPitch(0);
            hideout = new MagicLocation(loc);
            saveHideout();
            playSpellEffects(EffectPosition.CASTER,player);
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

    public MagicLocation getHideout() {
        return hideout;
    }

    private void loadHideout() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(MagicSpells.plugin.getDataFolder(), "hideouts/"+ internalName +".txt")));
            try {
                String line = reader.readLine();
                if(line!="") {
                    String[] coords = line.split(",");
                    World world = MagicSpells.plugin.getServer().getWorld(coords[0]);
                    Double x = Double.parseDouble(coords[1]);
                    Double y = Double.parseDouble(coords[2]);
                    Double z = Double.parseDouble(coords[3]);
                    Float yaw = Float.parseFloat(coords[4]);
                    Location loc = new Location(world,x,y,z);
                    loc.setYaw(yaw);
                    loc.setPitch(0);
                    hideout = new MagicLocation(loc);
                }
                else saveHideout();
            } catch (Exception e) {
                MagicSpells.error("Failed to load hideout for "+internalName+" spell.");
            }
        }
        catch (Exception e) {
        }
    }

    private void saveHideout() {
        try {
            File hideoutDirectory = new File(MagicSpells.plugin.getDataFolder(),"hideouts");
            if(!hideoutDirectory.exists()) hideoutDirectory.mkdir();
            File file = new File(hideoutDirectory,internalName+".txt");
            file.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file,false));
            writer.write(hideout.getWorld() + "," + hideout.getX() + "," + hideout.getY() + "," + hideout.getZ() + "," + hideout.getYaw());
            writer.close();
        }
        catch (Exception e) {
            MagicSpells.error("Unable to save hideout for "+internalName+" spell.");
        }
    }

}
