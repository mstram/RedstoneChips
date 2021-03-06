package org.tal.redstonechips.command;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.tal.redstonechips.PrefsManager;
import org.tal.redstonechips.user.ChipProbe;
import org.tal.redstonechips.user.Tool;
import org.tal.redstonechips.user.UserSession;

/**
 *
 * @author Tal Eisenberg
 */
public class RCtool extends RCCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.checkIsPlayer(rc, sender);
        if (player==null) return true;
        
        if (!CommandUtils.checkPermission(rc, sender, command.getName(), false, true)) return true;

        if (args.length>0) {
            processArg(player, args[0]);
        } else setToItemInHand(rc, player);
        return true;
    }    
    
    private void processArg(Player player, String arg) {
        try {
            Material m = PrefsManager.findMaterial(arg).getItemType();
            setToType(rc, player, m);
        } catch (IllegalArgumentException e) {
            if ("clear".startsWith(arg)) clearTools(rc, player);
            else player.sendMessage(rc.getPrefs().getErrorColor() + e.getMessage());
        }
    }
    
    public static void setToItemInHand(org.tal.redstonechips.RedstoneChips rc, Player player) {
        ItemStack item = player.getItemInHand();
        Material type = item.getType();        
        
        setToType(rc, player, type);
    }
    
    public static void setToType(org.tal.redstonechips.RedstoneChips rc, Player player, Material type) {
        try {
            UserSession session = rc.getUserSession(player, true);
            Tool t = new ChipProbe();
            t.setItem(type);
            session.addTool(t);
        } catch (IllegalArgumentException ie) {
            player.sendMessage(rc.getPrefs().getErrorColor() + ie.getMessage());
            return;
        }
        
        player.sendMessage(rc.getPrefs().getInfoColor() + "Chip probe set to " + ChatColor.YELLOW + type.name().toLowerCase() + ". " 
                + rc.getPrefs().getInfoColor() + "Right-click a chip block to for info.");                
    }
    
    public static void clearTools(org.tal.redstonechips.RedstoneChips rc, Player player) {
        UserSession session = rc.getUserSession(player, true);
        session.getTools().clear();
        
        player.sendMessage(rc.getPrefs().getInfoColor() + "Tools cleared.");
    }
}
