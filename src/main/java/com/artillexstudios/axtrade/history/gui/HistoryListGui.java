package com.artillexstudios.axtrade.history.gui;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axtrade.history.HistoryPlayer;
import com.artillexstudios.axtrade.history.TradeHistoryDatabase;
import com.artillexstudios.axtrade.history.TradeRecord;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

import static com.artillexstudios.axtrade.AxTrade.HISTORY_GUIS;
import static com.artillexstudios.axtrade.AxTrade.MESSAGEUTILS;

public class HistoryListGui {

    private final Player viewer;
    private final HistoryPlayer target;
    private final List<TradeRecord> records;
    private final List<Integer> tradeSlots;
    private final Gui gui;
    private int page = 0;

    public HistoryListGui(Player viewer, HistoryPlayer target, List<TradeRecord> records) {
        this.viewer = viewer;
        this.target = target;
        this.records = records;
        this.tradeSlots = HistoryItems.getSlots(HISTORY_GUIS, "list.trade-slots");
        this.gui = Gui.gui()
                .rows(HISTORY_GUIS.getInt("list.rows", 6))
                .title(StringUtils.format(getTitle()))
                .disableAllInteractions()
                .create();

        setStaticItem("list.filler", null);
        setStaticItem("list.previous-page", () -> changePage(-1));
        setStaticItem("list.next-page", () -> changePage(1));
        setStaticItem("list.close", () -> Scheduler.get().run(viewer, task -> viewer.closeInventory(), () -> {}));
    }

    public void open() {
        render();
        Scheduler.get().run(viewer, task -> gui.open(viewer), () -> {});
    }

    private int getPages() {
        if (tradeSlots.isEmpty()) return 1;
        return Math.max(1, (int) Math.ceil(records.size() / (double) tradeSlots.size()));
    }

    private String getTitle() {
        return HISTORY_GUIS.getString("list.title", "&0Trade history: %player%")
                .replace("%player%", target.name())
                .replace("%page%", "" + (page + 1))
                .replace("%pages%", "" + getPages())
                .replace("%amount%", "" + records.size());
    }

    private void changePage(int change) {
        int newPage = page + change;
        if (newPage < 0 || newPage >= getPages()) return;
        page = newPage;
        render();
        gui.updateTitle(StringUtils.format(getTitle()));
    }

    private void render() {
        int start = page * tradeSlots.size();
        for (int i = 0; i < tradeSlots.size(); i++) {
            int slot = tradeSlots.get(i);
            int position = start + i;
            if (position >= records.size()) {
                gui.removeItem(slot);
                continue;
            }

            TradeRecord record = records.get(position);
            int index = position + 1;
            Map<String, String> replacements = HistoryItems.getReplacements(record, target.uuid(), index);
            ItemStack itemStack = HistoryItems.build(HISTORY_GUIS, "list.trade", viewer, replacements, record, target.uuid(), record.getPartnerUuid(target.uuid()));
            if (itemStack == null) continue;
            gui.setItem(slot, new GuiItem(itemStack, event -> openDetail(index)));
        }
        gui.update();
    }

    private void openDetail(int index) {
        TradeHistoryDatabase.submit(() -> {
            TradeRecord record;
            try {
                record = TradeHistoryDatabase.get(target.uuid(), index);
            } catch (Exception ex) {
                MESSAGEUTILS.sendLang(viewer, "history.failed");
                throw new RuntimeException(ex);
            }
            if (record == null) {
                MESSAGEUTILS.sendLang(viewer, "history.no-history", Map.of("%player%", target.name()));
                return;
            }
            new HistoryDetailGui(viewer, target, index, record, this::open).open();
        });
    }

    private void setStaticItem(String route, Runnable action) {
        ItemStack itemStack = HistoryItems.build(HISTORY_GUIS, route, viewer, Map.of("%player%", target.name()), null, null, null);
        if (itemStack == null) return;
        GuiItem guiItem = new GuiItem(itemStack, event -> {
            if (action != null) action.run();
        });
        for (int slot : HistoryItems.getSlots(HISTORY_GUIS, route + ".slot")) {
            gui.setItem(slot, guiItem);
        }
    }

}