package com.denizenscript.denizen.utilities.command;

import com.denizenscript.denizen.objects.NPCTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.FormattedTextHelper;
import com.denizenscript.denizen.utilities.Settings;
import com.denizenscript.denizen.utilities.debugging.Debug;
import com.denizenscript.denizen.utilities.depends.Depends;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.scripts.ScriptBuilder;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.queues.core.InstantQueue;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ExCommandHandler implements CommandExecutor {

    public void enableFor(PluginCommand command) {
        command.setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String cmdName, String[] args) {

        // <--[language]
        // @name /ex command
        // @group Console Commands
        // @description
        // The '/ex' command is an easy way to run a single denizen script command in-game. Its syntax,
        // aside from '/ex' is exactly the same as any other command. When running a command, some context
        // is also supplied, such as '<player>' if being run by a player (versus the console), as well as
        // '<npc>' if a NPC is selected by using the '/npc sel' command.
        //
        // By default, ex command debug output is sent to the player that ran the ex command (if the command was ran by a player).
        // To avoid this, use '-q' at the start of the ex command.
        // Like: /ex -q narrate "wow no output"
        //
        // Examples:
        // /ex flag <player> test_flag:!
        // /ex run 's@npc walk script' as:<npc>
        //
        // Need to '/ex' a command as a different player or NPC? No problem. Just use the 'npc' and 'player'
        // value arguments, or utilize the object fetcher.
        //
        // Examples:
        // /ex narrate player:p@NLBlackEagle 'Your health is <player.health.formatted>.'
        // /ex walk npc:n@fred <player.location.cursor_on>

        // -->

        if (cmdName.equalsIgnoreCase("ex")) {
            List<Object> entries = new ArrayList<>();
            String entry = String.join(" ", args);
            boolean quiet = false;
            if (entry.length() > 3 && entry.startsWith("-q ")) {
                quiet = true;
                entry = entry.substring("-q ".length());
            }
            if (!Settings.showExDebug()) {
                quiet = !quiet;
            }

            if (entry.length() < 2) {
                sender.sendMessage("/ex (-q) <denizen script command> (arguments)");
                return true;
            }

            if (Settings.showExHelp()) {
                if (Debug.showDebug) {
                    sender.sendMessage(ChatColor.YELLOW + "Executing Denizen script command... check the console for full debug output!");
                }
                else {
                    sender.sendMessage(ChatColor.YELLOW + "Executing Denizen script command... to see debug, use /denizen debug");
                }
            }

            entries.add(entry);
            InstantQueue queue = new InstantQueue("EXCOMMAND");
            NPCTag npc = null;
            if (Depends.citizens != null && Depends.citizens.getNPCSelector().getSelected(sender) != null) {
                npc = new NPCTag(Depends.citizens.getNPCSelector().getSelected(sender));
            }
            List<ScriptEntry> scriptEntries = ScriptBuilder.buildScriptEntries(entries, null,
                    new BukkitScriptEntryData(sender instanceof Player ? new PlayerTag((Player) sender) : null, npc));

            queue.addEntries(scriptEntries);
            if (!quiet && sender instanceof Player) {
                final Player player = (Player) sender;
                queue.debugOutput = (s) -> {
                    player.spigot().sendMessage(FormattedTextHelper.parse(s));
                };
            }
            queue.start();
            return true;
        }
        return false;
    }
}
