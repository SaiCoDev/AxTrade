package com.artillexstudios.axtrade.history.gui;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axtrade.history.HistoryPlayer;
import com.artillexstudios.axtrade.history.TradeRecord;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.artillexstudios.axtrade.AxTrade.HISTORY_GUIS;

public class HistoryDetailGui {

    private final Player viewer;
    private final HistoryPlayer target;
    private final TradeRecord record;
    private final Map<String, String> replacements;
    private final Runnable back;
    private final Gui gui;

    public HistoryDetailGui(Player viewer, HistoryPlayer target, int index, TradeRecord record, Runnable back) {
        this.viewer = viewer;
        this.target = target;
        this.record = record;
        this.back = back;
        this.replacements = HistoryItems.getReplacements(record, target.uuid(), index);
        this.gui = Gui.gui()
                .rows(HISTORY_GUIS.getInt("detail.rows", 6))
                .title(StringUtils.format(HistoryItems.replace(HISTORY_GUIS.getString("detail.title", "&0#%index%"), replacements)))
                .disableAllInteractions()
                .create();

        setItems("detail.given-slots", record.getGivenItems(target.uuid()));
        setItems("detail.received-slots", record.getReceivedItems(target.uuid()));

        setStaticItem("detail.filler", null);
        setStaticItem("detail.given", null);
        setStaticItem("detail.info", null);
        setStaticItem("detail.received", null);
        setStaticItem("detail.back", back);
    }

    public void open() {
        Scheduler.get().run(viewer, task -> gui.open(viewer), () -> {});
    }

    private void setItems(String route, @Nullable List<ItemStack> items) {
        if (items == null) return;
        List<Integer> slots = HistoryItems.getSlots(HISTORY_GUIS, route);
        for (int i = 0; i < slots.size() && i < items.size(); i++) {
            gui.setItem(slots.get(i), new GuiItem(items.get(i)));
        }
    }

    private void setStaticItem(String route, @Nullable Runnable action) {
        String head = HISTORY_GUIS.getString(route + ".head");
        UUID headOwner = null;
        if ("player".equalsIgnoreCase(head)) headOwner = target.uuid();
        else if ("partner".equalsIgnoreCase(head)) headOwner = record.getPartnerUuid(target.uuid());

        ItemStack itemStack = HistoryItems.build(HISTORY_GUIS, route, viewer, replacements, record, target.uuid(), headOwner);
        if (itemStack == null) return;
        GuiItem guiItem = new GuiItem(itemStack, event -> {
            if (action != null) action.run();
        });
        for (int slot : HistoryItems.getSlots(HISTORY_GUIS, route + ".slot")) {
            gui.setItem(slot, guiItem);
        }
    }

}