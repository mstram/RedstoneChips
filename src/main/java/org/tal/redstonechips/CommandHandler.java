/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tal.redstonechips;

import java.util.ArrayList;
import org.tal.redstonechips.circuit.Circuit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tal.redstonechips.circuit.InputPin;
import org.tal.redstonechips.circuit.ReceivingCircuit;
import org.tal.redstonechips.circuit.TransmittingCircuit;
import org.tal.redstonechips.circuit.rcTypeReceiver;
import org.tal.redstonechips.util.BitSetUtils;

/**
 *
 * @author Tal Eisenberg
 */
public class CommandHandler {
    RedstoneChips rc;
    private final static int MaxLines = 15;

    public CommandHandler(RedstoneChips plugin) {
        rc = plugin; 
    }

    public void listActiveCircuits(CommandSender p, String[] args) {
        World world = null;

        TreeMap<Integer, Circuit> sorted = new TreeMap<Integer, Circuit>(rc.getCircuitManager().getCircuits());

        boolean oneWorld = true;
        if (args.length>0) {
            if (!args[0].equalsIgnoreCase("all")) {
                world = rc.getServer().getWorld(args[0]);
            } else oneWorld = false;
        }

        if (oneWorld && world==null && (p instanceof Player)) {
            // use player world as default.
            world = ((Player)p).getWorld();
        }

        if (sorted.isEmpty()) p.sendMessage(rc.getPrefsManager().getInfoColor() + "There are no active circuits.");
        else {
            String title = " active IC(s) in ";
            String commandName = "rc-list";
            List<String> lines = new ArrayList<String>();
            for (Integer id : sorted.keySet()) {
                Circuit c = sorted.get(id);
                if (world==null || c.world.getName().equals(world.getName())) {
                    StringBuilder builder = new StringBuilder();
                    for (String arg : c.args) {
                        builder.append(arg);
                        builder.append(" ");
                    }

                    String cargs = "";
                    if (builder.length()>0) cargs = builder.toString().substring(0, builder.length()-1);
                    
                    if(cargs.length() > 20) cargs = cargs.substring(0, 17) + "...";
                    cargs = "[ " + cargs + " ]";


                    String sworld = "";
                    if (world==null) sworld = c.world.getName() + " ";
                    lines.add(c.id + ": " + ChatColor.YELLOW + c.getClass().getSimpleName() + ChatColor.WHITE + " @ "
                            + c.activationBlock.getX() + "," + c.activationBlock.getY() + "," + c.activationBlock.getZ()
                            + " " + sworld + rc.getPrefsManager().getInfoColor() + cargs);
                } 
            }

            if (lines.isEmpty()) {
                p.sendMessage(rc.getPrefsManager().getInfoColor() + "There are no active circuits on world " + world.getName() + ".");
                return;
            }

            String page = null;
            if (args.length>1) page = args[args.length-1];
            else if (args.length>0) page = args[0];

            if (world==null) title += "all worlds";
            else title +="world " + world.getName();
            title = lines.size() + title;
            
            pageMaker(p, page, title, commandName, lines.toArray(new String[lines.size()]),
                    rc.getPrefsManager().getInfoColor(), rc.getPrefsManager().getErrorColor());
        }

    }

    public void listCircuitClasses(CommandSender p) {
        Map<String,Class> circuitClasses = rc.getCircuitLoader().getCircuitClasses();
        if (circuitClasses.isEmpty()) p.sendMessage(rc.getPrefsManager().getInfoColor() + "There are no circuit classes installed.");
        else {
            List<String> names = Arrays.asList(circuitClasses.keySet().toArray(new String[circuitClasses.size()]));
            Collections.sort(names);
            p.sendMessage("");
            p.sendMessage(rc.getPrefsManager().getInfoColor() + "Installed circuit classes:");
            p.sendMessage(rc.getPrefsManager().getInfoColor() + "-----------------------");
            String list = "";
            ChatColor color = ChatColor.WHITE;
            for (String name : names) {
                list += color + name + ", ";
                if (list.length()>50) {
                    p.sendMessage(list.substring(0, list.length()-2));
                    list = "";
                }
                if (color==ChatColor.WHITE)
                    color = ChatColor.YELLOW;
                else color = ChatColor.WHITE;
            }
            if (!list.isEmpty()) p.sendMessage(list.substring(0, list.length()-2));
            p.sendMessage(rc.getPrefsManager().getInfoColor() + "----------------------");
            p.sendMessage("");
        }
    }

    public void prefsCommand(String[] args, CommandSender sender) {
            if (args.length==0) { // list preferences
                rc.getPrefsManager().printYaml(sender, rc.getPrefsManager().getPrefs());
                sender.sendMessage(rc.getPrefsManager().getInfoColor() + "Type /rc-prefs <name> <value> to make changes.");
            } else if (args.length==1) { // show one key value pair
                Object o = rc.getPrefsManager().getPrefs().get(args[0]);
                if (o==null) sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Unknown preferences key: " + args[0]);
                else {
                    Map<String,Object> map = new HashMap<String,Object>();
                    map.put(args[0], o);

                    rc.getPrefsManager().printYaml(sender, map);
                }
            } else if (args.length>=2) { // set value
                if (!sender.isOp()) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Only admins are authorized to change preferences values.");
                    return;
                }

                String val = "";
                for (int i=1; i<args.length; i++)
                    val += args[i] + " ";

                try {
                    Map<String, Object> map = rc.getPrefsManager().setYaml(args[0] + ": " + val);
                    rc.getPrefsManager().printYaml(sender, map);
                } catch (IllegalArgumentException ie) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + ie.getMessage());
                }
                sender.sendMessage(rc.getPrefsManager().getInfoColor() + "Saving changes...");
                rc.getPrefsManager().savePrefs();
            } else {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad rc-prefs syntax.");
            }

    }

    public void debugCommand(CommandSender sender, String[] args) {
        int id = -1;
        boolean add = true;
        boolean alloff = false;

        if (args.length==1) {
            // on, off or id (then on)
            if (args[0].equalsIgnoreCase("on"))
                add = true;
            else if (args[0].equalsIgnoreCase("off"))
                add = false;
            else if (args[0].equals("alloff"))
                alloff = true;
            else {
                try {
                    id = Integer.decode(args[0]);
                    add = true;
                } catch (NumberFormatException ne) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad argument: " + args[0] + ". Expecting on, off or a chip id.");
                }
            }
        } else if (args.length==2) {
            try {
                id = Integer.decode(args[0]);
            } catch (NumberFormatException ne) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad argument: " + args[0] + ". Expecting a chip id number.");
                return;
            }

            if (args[1].equalsIgnoreCase("on"))
                add = true;
            else if (args[1].equalsIgnoreCase("off"))
                add = false;
            else {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad argument: " + args[1] + ". Expecting on or off.");
                return;
            }
        }

        if (alloff) {
            for (Circuit c : rc.getCircuitManager().getCircuits().values())
                if (c.getDebuggers().contains(sender)) c.removeDebugger(sender);
            sender.sendMessage(rc.getPrefsManager().getInfoColor() + "You will not receive debug messages from any chip.");
        } else {
            Circuit c;
            if (id!=-1) {
                if (rc.getCircuitManager().getCircuits().size()<=id || id<0) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad chip id " + id + ". Could only find " + rc.getCircuitManager().getCircuits().size() + " active chips.");
                    return;
                }
                c = rc.getCircuitManager().getCircuits().get(id);
            } else {
                Player player = checkIsPlayer(sender);
                if (player==null) return;
                Block target = targetBlock(player);
                c = rc.getCircuitManager().getCircuitByStructureBlock(target);
                if (c==null) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at a block of the circuit you wish to debug.");
                    return;
                }
            }

            if (add) {
                try {
                    if (id!=-1 && !sender.isOp()) {
                        sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You must have admin priviliges to debug a chip by id.");
                        return;
                    } else
                        c.addDebugger(sender);
                } catch (IllegalArgumentException ie) {
                    try {
                        c.removeDebugger(sender);
                    } catch (IllegalArgumentException me) {
                        sender.sendMessage(rc.getPrefsManager().getInfoColor() + me.getMessage());
                        return;
                    }
                    sender.sendMessage(rc.getPrefsManager().getInfoColor() + "You will not receive any more debug messages from the " + c.getClass().getSimpleName() + " circuit.");

                    return;
                }
                sender.sendMessage(rc.getPrefsManager().getDebugColor() + "You are now a debugger of the " + c.getClass().getSimpleName() + " circuit.");
            } else {
                try {
                    c.removeDebugger(sender);
                } catch (IllegalArgumentException ie) {
                    sender.sendMessage(rc.getPrefsManager().getInfoColor() + ie.getMessage());
                    return;
                }
                sender.sendMessage(rc.getPrefsManager().getInfoColor() + "You will not receive any more debug messages from the " + c.getClass().getSimpleName() + " circuit.");
            }
        }
    }

    public void destroyCommand(CommandSender sender) {
        Player player = checkIsPlayer(sender);
        if (player==null) return;

        Block target = targetBlock(player);
        Circuit c = rc.getCircuitManager().getCircuitByStructureBlock(target);
        if (c==null) {
            player.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at a block of the circuit you wish to destroy.");
        } else {
            for (Location l : c.structure)
                c.world.getBlockAt(l).setType(Material.AIR);

            rc.getCircuitManager().destroyCircuit(c, sender);
            player.sendMessage(rc.getPrefsManager().getInfoColor() + "The " + c.getCircuitClass() + " chip is destroyed.");
        }
    }

    public void deactivateCommand(CommandSender sender, String[] args) {
        int idx = -1;
        if (args.length>0) {
            try {
                idx = Integer.decode(args[0]);
            } catch (NumberFormatException ne) {
                sender.sendMessage("Bad circuit id number: " + args[0]);
            }
        }
        

        Circuit c = null;
        if (idx==-1) {
            Player player = checkIsPlayer(sender);
            if (player==null) return;

            if (!sender.isOp()) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You must be an admin to remotely deactivate a circuit.");
                return;
            }

            Block target = targetBlock(player);
            c = rc.getCircuitManager().getCircuitByStructureBlock(target);

            if (c==null) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at a block of the circuit you wish to deactivate.");
                return;
            }
        } else {
            if (idx<rc.getCircuitManager().getCircuits().size()) {
                c = rc.getCircuitManager().getCircuits().get(idx);
            } else {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "There's no activated circuit with id " + idx);
                return;
            }
        }

        rc.getCircuitManager().destroyCircuit(c, sender);
        sender.sendMessage(rc.getPrefsManager().getInfoColor() + "The " + ChatColor.YELLOW + c.getCircuitClass() + " (" + c.id + ")" + rc.getPrefsManager().getInfoColor() + " circuit is now deactivated.");
    }

    public void handleRcType(CommandSender sender, String[] args) {
        Player player = checkIsPlayer(sender);

        
        Block block = targetBlock(player);

        Location l = block.getLocation();
        rcTypeReceiver t = rc.rcTypeReceivers.get(l);
        if (t==null) {
            player.sendMessage(rc.getPrefsManager().getErrorColor() + "You must point towards a typing block (a terminal circuit's interface block for example) to type.");
        } else {
            player.sendMessage(rc.getPrefsManager().getInfoColor() + "Input sent.");
            t.type(args, player);
        }
    }

    public void pinCommand(CommandSender sender) {
        Player player = checkIsPlayer(sender);

        Block target = targetBlock(player);
        sendPinInfo(target, player);

    }

    private void sendPinInfo(Block target, CommandSender sender) {

        List<InputPin> inputList = rc.getCircuitManager().lookupInputBlock(target);
        if (inputList==null) {
            Object[] oo = rc.getCircuitManager().lookupOutputBlock(target);
            if (oo==null) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at an output lever or input block.");
            } else { // output pin
                Circuit c = (Circuit)oo[0];
                int i = (Integer)oo[1];
                sender.sendMessage(rc.getPrefsManager().getInfoColor() + c.getClass().getSimpleName() + ": " + ChatColor.YELLOW + "output pin "
                        + i + " - " + (c.getOutputBits().get(i)?ChatColor.RED+"on":ChatColor.WHITE+"off"));
            }
        } else { // input pin
            for (InputPin io : inputList) {
                Circuit c = io.getCircuit();
                int i = io.getIndex();
                sender.sendMessage(rc.getPrefsManager().getInfoColor() + c.getClass().getSimpleName() + ": " + ChatColor.WHITE + "input pin "
                        + i + " - " + (c.getInputBits().get(i)?ChatColor.RED+"on":ChatColor.WHITE+"off"));
            }
        }
    }


    public void printCircuitInfo(CommandSender sender, String[] args) {
        HashMap<Integer, Circuit> circuits = rc.getCircuitManager().getCircuits();

        Circuit c;
        if (args.length==0) {
            Player p = checkIsPlayer(sender);
            if (p==null) return;
            Block target = targetBlock(p);
            c = rc.getCircuitManager().getCircuitByStructureBlock(target);
            if (c==null) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at a block of the circuit you wish to get info about.");
                return;
            }
        } else {
            try {
                int i = Integer.decode(args[0]);
                if (i>=circuits.size()) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad id. There are only " + circuits.size() + " active circuits.");
                    return;
                }

                c = rc.getCircuitManager().getCircuits().get(i);
            } catch (NumberFormatException ie) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad circuit id argument: " + args[0]);
                return;
            }
        }

        /*
         * 50: print circuit  (inputs disabled)
         * --------------
         * 4 input pins, 2 output pins and 6 interface blocks.
         * id: 50, location: x,y,z, world: world
         * input states: 010010
         * output states: 11011
         * sign args: binary scroll
         * internal state:
         *    text: 'some text blah blah'
         */

        ChatColor infoColor = rc.getPrefsManager().getInfoColor();
        ChatColor errorColor = rc.getPrefsManager().getErrorColor();
        ChatColor extraColor = ChatColor.YELLOW;

        String disabled;
        if (c.isDisabled()) disabled = errorColor + " (inputs disabled)";
        else disabled = "";

        String loc = c.activationBlock.getBlockX() + ", " + c.activationBlock.getBlockY() + ", " + c.activationBlock.getBlockZ();
        sender.sendMessage("");
        sender.sendMessage(extraColor + Integer.toString(c.id) + ": " + infoColor + c.getCircuitClass() + " circuit" + disabled);
        sender.sendMessage(extraColor + "----------------------");

        sender.sendMessage(infoColor + "" + c.inputs.length + " input(s), " + c.outputs.length + " output(s) and " + c.interfaceBlocks.length + " interface blocks.");
        sender.sendMessage(infoColor +
                "location: " + extraColor + loc + infoColor + " world: " + extraColor + c.world.getName());

        sender.sendMessage(infoColor + "input states: " + extraColor + BitSetUtils.bitSetToBinaryString(c.getInputBits(), 0, c.inputs.length));
        sender.sendMessage(infoColor + "output states: " + extraColor + BitSetUtils.bitSetToBinaryString(c.getOutputBits(), 0, c.outputs.length));

        String signargs = "";
        for (String arg : c.args)
            signargs += arg + " ";

        sender.sendMessage(infoColor + "sign args: " + extraColor + signargs);
        
        Map<String,String> internalState = c.saveState();
        if (!internalState.isEmpty()) {
            sender.sendMessage(infoColor + "internal state:");
            for (String key : internalState.keySet())
                sender.sendMessage(infoColor + "   " + key + ": " + extraColor + internalState.get(key));
        }
    }

    private Player checkIsPlayer(CommandSender sender) {
        if (sender instanceof Player) return (Player)sender;
        else {
            sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Only players are allowed to use this command.");
            return null;
        }
    }

    public void resetCircuit(CommandSender sender, String[] args) {
        Circuit c;

        if (args.length>0) {
            try {
                int id = Integer.decode(args[0]);
                c = rc.getCircuitManager().getCircuits().get(id);
                if (c==null) {
                    sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Invalid circuit id: " + id + ". Found " + rc.getCircuitManager().getCircuits().size() + " circuits.");
                    return;
                }
            } catch (NumberFormatException ne) {
                sender.sendMessage(rc.getPrefsManager().getErrorColor() + "Bad argument: " + args[0] + ". Expecting a number.");
                return;
            }
        } else {
            Player player = checkIsPlayer(sender);
            if (player==null) return;

            Block target = targetBlock(player);
            c = rc.getCircuitManager().getCircuitByStructureBlock(target);
            if (c==null) {
                player.sendMessage(rc.getPrefsManager().getErrorColor() + "You need to point at a block of the circuit you wish to reset.");
                return;
            }
        }

        Block activationBlock = c.world.getBlockAt(c.activationBlock.getBlockX(), c.activationBlock.getBlockY(), c.activationBlock.getBlockZ());
        List<CommandSender> debuggers = c.getDebuggers();
        int id = c.id;

        rc.getCircuitManager().destroyCircuit(c, sender);
        Block a = c.world.getBlockAt(c.activationBlock.getBlockX(), c.activationBlock.getBlockY(), c.activationBlock.getBlockZ());
        rc.getCircuitManager().checkForCircuit(a, sender);
        Circuit newCircuit = rc.getCircuitManager().getCircuitByActivationBlock(activationBlock);
        newCircuit.id = id;
        if (newCircuit!=null) {
            for (CommandSender d : debuggers) newCircuit.addDebugger(d);
            sender.sendMessage(rc.getPrefsManager().getInfoColor() + "The " + ChatColor.YELLOW + newCircuit.getCircuitClass() + " (" + + newCircuit.id + ")" + rc.getPrefsManager().getInfoColor() + " circuit is reactivated.");
        }
    }

    public static void pageMaker(CommandSender s, String spage, String title, String commandName, String[] lines, ChatColor infoColor, ChatColor errorColor) {
            int page = 1;
            if (spage!=null) {
                try {
                    page = Integer.decode(spage);
                } catch (NumberFormatException ne) {
                    //s.sendMessage(errorColor + "Invalid page number: " + args[args.length-1]);
                }
            }

            int pageCount = (int)(Math.ceil(lines.length/(float)MaxLines));
            if (page<1 || page>pageCount) s.sendMessage(errorColor + "Invalid page number: " + page + ". Expecting 1-" + pageCount);
            else {
                s.sendMessage("");
                s.sendMessage(infoColor + title + ": " + (pageCount>1?"( page " + page + " / " + pageCount  + " )":""));
                s.sendMessage(infoColor + "----------------------");
                for (int i=(page-1)*MaxLines; i<Math.min(lines.length, page*MaxLines); i++) {
                    s.sendMessage(lines[i]);
                }
                s.sendMessage(infoColor + "----------------------");
                if (pageCount>1) s.sendMessage("Use /" + commandName + " <page number> to see other pages.");
                s.sendMessage("");
            }

    }

    public void listBroadcastChannels(CommandSender sender, String[] args) {
        SortedMap<String, Integer[]> channels = new TreeMap<String, Integer[]>();

        for (TransmittingCircuit t : rc.transmitters) {
            if (channels.containsKey(t.getChannel()))
                channels.get(t.getChannel())[0] += 1;
            else
                channels.put(t.getChannel(), new Integer[] {1,0});
        }

        for (ReceivingCircuit r : rc.receivers) {
            if (channels.containsKey(r.getChannel()))
                channels.get(r.getChannel())[1] += 1;
            else
                channels.put(r.getChannel(), new Integer[] {0,1});
        }

        if (channels.isEmpty()) {
            sender.sendMessage(rc.getPrefsManager().getInfoColor() + "There are no active broadcast channels.");
        } else {
            String[] lines = new String[channels.size()];
            int idx = 0;
            for (String channel : channels.keySet()) {
                Integer[] counts = channels.get(channel);
                lines[idx] = ChatColor.YELLOW + channel + ChatColor.WHITE + " - " + counts[0] + " transmitters, " + counts[1] + " receivers.";
                idx++;

            }
            pageMaker(sender, (args.length>0?args[0]:null), "Active wireless broadcast channels", "rc-channels", lines, rc.getPrefsManager().getInfoColor(), rc.getPrefsManager().getErrorColor());
        }
    }

    public void commandHelp(CommandSender sender, String[] args) {
        Map commands = (Map)rc.getDescription().getCommands();
        ChatColor infoColor = rc.getPrefsManager().getInfoColor();
        ChatColor errorColor = rc.getPrefsManager().getErrorColor();

        if (args.length==0) {
            sender.sendMessage("");
            sender.sendMessage(infoColor + "RedstoneChips commands" + ":");
            sender.sendMessage(infoColor + "----------------------");

            for (Object command : commands.keySet()) {
                sender.sendMessage(ChatColor.YELLOW + command.toString());
            }

            sender.sendMessage(infoColor + "----------------------");
            sender.sendMessage("Use /rc-help <command name> for help on a specific command (omit the / sign).");
            sender.sendMessage("");
        } else {
            Map commandMap = (Map)commands.get(args[0]);
            if (commandMap==null) {
                sender.sendMessage(errorColor + "Unknown rc command: " + args[0]);
                return;
            }

            sender.sendMessage("");
            sender.sendMessage(infoColor + "/" + args[0] + ":");
            sender.sendMessage(infoColor + "----------------------");

            sender.sendMessage(ChatColor.YELLOW+commandMap.get("description").toString());

            sender.sendMessage(infoColor + "----------------------");
            sender.sendMessage("");

        }
    }

    static final HashSet<Byte> transparentMaterials = new HashSet<Byte>();
    static {
        transparentMaterials.add((byte)Material.AIR.getId());
        transparentMaterials.add((byte)Material.WATER.getId());
        transparentMaterials.add((byte)Material.STATIONARY_WATER.getId());
    }

    private Block targetBlock(Player player) {
        return player.getTargetBlock(transparentMaterials, 100);
    }
}