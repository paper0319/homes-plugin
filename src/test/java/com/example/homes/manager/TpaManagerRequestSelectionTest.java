package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.homes.manager.TpaManager.RequestType;
import com.example.homes.manager.TpaManager.TpaRequest;

class TpaManagerRequestSelectionTest {

    @Test
    void returnsNullForEmptyMap() {
        assertNull(TpaManager.mostRecentRequest(new HashMap<>()));
    }

    @Test
    void picksTheMostRecentByTimestamp() throws InterruptedException {
        Map<UUID, TpaRequest> pending = new HashMap<>();

        TpaRequest first = new TpaRequest(UUID.randomUUID(), RequestType.TPA);
        pending.put(first.sender, first);
        Thread.sleep(2);
        TpaRequest second = new TpaRequest(UUID.randomUUID(), RequestType.TPA);
        pending.put(second.sender, second);

        assertSame(second, TpaManager.mostRecentRequest(pending));
    }

    @Test
    void singleEntryIsReturned() {
        Map<UUID, TpaRequest> pending = new HashMap<>();
        TpaRequest only = new TpaRequest(UUID.randomUUID(), RequestType.TPAHERE);
        pending.put(only.sender, only);

        assertEquals(only, TpaManager.mostRecentRequest(pending));
    }
}
