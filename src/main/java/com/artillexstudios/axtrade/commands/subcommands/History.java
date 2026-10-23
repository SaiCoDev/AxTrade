package com.artillexstudios.axtrade.commands.subcommands;

import com.artillexstudios.axtrade.history.HistoryPlayer;
import com.artillexstudios.axtrade.history.TradeHistoryDatabase;
import com.artillexstudios.axtrade.history.TradeRecord;
import com.artillexstudios.axtrade.history.gui.HistoryDetailGui;
import com.artillexstudios.axtrade.history.gui.HistoryListGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.artillexstudios.axtrade.AxTrade.MESSAGEUTILS;

public enum History {

    INSTANCE;

    public void execute(Player sender, String name, @Nullable Integer index) {
        if (!TradeHistoryDatabase.isEnabled()) {
            MESSAGEUTILS.sendLang(sender, "history.disabled");
            return;
        }

        Player online = Bukkit.getPlayerExact(name);
        TradeHistoryDatabase.submit(() -> {
            try {
                HistoryPlayer target = online != null ? new HistoryPlayer(online.getUniqueId(), online.getName()) : TradeHistoryDatabase.findPlayer(name);
                if (target == null) {
                    MESSAGEUTILS.sendLang(sender, "history.no-history", Map.of("%player%", name));
                    return;
                }

                if (index == null) {
                    openList(sender, target);
                    return;
                }

                TradeRecord record = TradeHistoryDatabase.get(target.uuid(), index);
                if (record == null) {
                    int amount = TradeHistoryDatabase.count(target.uuid());
                    if (amount == 0) MESSAGEUTILS.sendLang(sender, "history.no-history", Map.of("%player%", target.name()));
                    else MESSAGEUTILS.sendLang(sender, "history.invalid-index", Map.of("%player%", target.name(), "%amount%", "" + amount));
                    return;
                }
                new HistoryDetailGui(sender, target, index, record, () -> TradeHistoryDatabase.submit(() -> openList(sender, target))).open();
            } catch (Exception ex) {
                MESSAGEUTILS.sendLang(sender, "history.failed");
                throw new RuntimeException(ex);
            }
        });
    }

    private void openList(Player sender, HistoryPlayer target) {
        List<TradeRecord> records;
        try {
            records = TradeHistoryDatabase.list(target.uuid());
        } catch (Exception ex) {
            MESSAGEUTILS.sendLang(sender, "history.failed");
            throw new RuntimeException(ex);
        }
        if (records.isEmpty()) {
            MESSAGEUTILS.sendLang(sender, "history.no-history", Map.of("%player%", target.name()));
            return;
        }
        new HistoryListGui(sender, target, records).open();
    }

}