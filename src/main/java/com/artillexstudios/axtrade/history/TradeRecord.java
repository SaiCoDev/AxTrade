package com.artillexstudios.axtrade.history;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record TradeRecord(
        long id,
        long time,
        UUID player1Uuid,
        String player1Name,
        UUID player2Uuid,
        String player2Name,
        int player1ItemCount,
        int player2ItemCount,
        List<HistoryCurrency> player1Currencies,
        List<HistoryCurrency> player2Currencies,
        @Nullable List<ItemStack> player1Items,
        @Nullable List<ItemStack> player2Items
) {

    public boolean isPlayer1(UUID uuid) {
        return player1Uuid.equals(uuid);
    }

    public String getName(UUID viewer) {
        return isPlayer1(viewer) ? player1Name : player2Name;
    }

    public UUID getPartnerUuid(UUID viewer) {
        return isPlayer1(viewer) ? player2Uuid : player1Uuid;
    }

    public String getPartnerName(UUID viewer) {
        return isPlayer1(viewer) ? player2Name : player1Name;
    }

    public int getGivenItemCount(UUID viewer) {
        return isPlayer1(viewer) ? player1ItemCount : player2ItemCount;
    }

    public int getReceivedItemCount(UUID viewer) {
        return isPlayer1(viewer) ? player2ItemCount : player1ItemCount;
    }

    public List<HistoryCurrency> getGivenCurrencies(UUID viewer) {
        return isPlayer1(viewer) ? player1Currencies : player2Currencies;
    }

    public List<HistoryCurrency> getReceivedCurrencies(UUID viewer) {
        return isPlayer1(viewer) ? player2Currencies : player1Currencies;
    }

    @Nullable
    public List<ItemStack> getGivenItems(UUID viewer) {
        return isPlayer1(viewer) ? player1Items : player2Items;
    }

    @Nullable
    public List<ItemStack> getReceivedItems(UUID viewer) {
        return isPlayer1(viewer) ? player2Items : player1Items;
    }

}