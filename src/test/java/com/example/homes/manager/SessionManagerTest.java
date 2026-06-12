package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    private SessionManager sessionManager;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        uuid = UUID.randomUUID();
    }

    @Test
    void modesDefaultToOff() {
        assertFalse(sessionManager.isDeleteMode(uuid));
        assertFalse(sessionManager.isPublicMode(uuid));
        assertFalse(sessionManager.isRenameMode(uuid));
        assertFalse(sessionManager.isFavoriteMode(uuid));
        assertFalse(sessionManager.isMemoMode(uuid));
    }

    @Test
    void modeToggle() {
        sessionManager.setDeleteMode(uuid, true);
        assertTrue(sessionManager.isDeleteMode(uuid));
        sessionManager.setDeleteMode(uuid, false);
        assertFalse(sessionManager.isDeleteMode(uuid));
    }

    @Test
    void searchQueryIsTrimmedAndClearedWhenBlank() {
        sessionManager.setSearchQuery(uuid, "  base  ");
        assertEquals("base", sessionManager.getSearchQuery(uuid));
        sessionManager.setSearchQuery(uuid, "   ");
        assertNull(sessionManager.getSearchQuery(uuid));
    }

    @Test
    void waitingForInputCoversAllInputStates() {
        assertFalse(sessionManager.isWaitingForInput(uuid));

        sessionManager.setCreatingHome(uuid, true);
        assertTrue(sessionManager.isWaitingForInput(uuid));
        sessionManager.setCreatingHome(uuid, false);

        sessionManager.setSearchingHomes(uuid, true);
        assertTrue(sessionManager.isWaitingForInput(uuid));
        sessionManager.setSearchingHomes(uuid, false);

        sessionManager.setRenamingTarget(uuid, "base");
        assertTrue(sessionManager.isWaitingForInput(uuid));
        sessionManager.setRenamingTarget(uuid, null);

        sessionManager.setEditingMemoTarget(uuid, "base");
        assertTrue(sessionManager.isWaitingForInput(uuid));
        sessionManager.setEditingMemoTarget(uuid, null);

        assertFalse(sessionManager.isWaitingForInput(uuid));
    }

    @Test
    void pageDefaultsToZeroAndClampsNegative() {
        assertEquals(0, sessionManager.getPage(uuid));
        sessionManager.setPage(uuid, 3);
        assertEquals(3, sessionManager.getPage(uuid));
        sessionManager.setPage(uuid, -1);
        assertEquals(0, sessionManager.getPage(uuid));
    }

    @Test
    void cleanupClearsEverything() {
        sessionManager.setDeleteMode(uuid, true);
        sessionManager.setSearchQuery(uuid, "base");
        sessionManager.setPage(uuid, 2);
        sessionManager.setCreatingHome(uuid, true);
        sessionManager.setRenamingTarget(uuid, "base");

        sessionManager.cleanup(uuid);

        assertFalse(sessionManager.isDeleteMode(uuid));
        assertNull(sessionManager.getSearchQuery(uuid));
        assertEquals(0, sessionManager.getPage(uuid));
        assertFalse(sessionManager.isWaitingForInput(uuid));
    }
}
