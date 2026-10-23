package com.artillexstudios.axtrade.commands;

import com.artillexstudios.axtrade.commands.subcommands.History;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.AutoComplete;
import revxrsal.commands.annotation.DefaultFor;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

@CommandPermission(value = "axtrade.history")
public class HistoryCommand implements OrphanCommand {

    @DefaultFor("~")
    @AutoComplete("@players *")
    public void history(Player sender, String player, @Optional Integer index) {
        History.INSTANCE.execute(sender, player, index);
    }

}