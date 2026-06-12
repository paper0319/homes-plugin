package com.example.homes.manager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    // --- GUI Mode States ---
    private final Set<UUID> deleteModePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> publicModePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> renameModePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> favoriteModePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> memoModePlayers = ConcurrentHashMap.newKeySet();
    
    // --- Search & Pagination States ---
    private final Map<UUID, String> searchQuery = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> page = new ConcurrentHashMap<>();
    
    // --- Input Waiting States ---
    private final Set<UUID> creatingHome = ConcurrentHashMap.newKeySet();
    private final Set<UUID> searchingHomes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> renamingHomeTarget = new ConcurrentHashMap<>();
    private final Map<UUID, String> editingMemoTarget = new ConcurrentHashMap<>();

    public void cleanup(UUID uuid) {
        deleteModePlayers.remove(uuid);
        publicModePlayers.remove(uuid);
        renameModePlayers.remove(uuid);
        favoriteModePlayers.remove(uuid);
        memoModePlayers.remove(uuid);
        searchQuery.remove(uuid);
        page.remove(uuid);

        creatingHome.remove(uuid);
        searchingHomes.remove(uuid);
        renamingHomeTarget.remove(uuid);
        editingMemoTarget.remove(uuid);
    }
    
    // Mode Getters/Setters
    public boolean isDeleteMode(UUID uuid) { return deleteModePlayers.contains(uuid); }
    public void setDeleteMode(UUID uuid, boolean state) { if(state) deleteModePlayers.add(uuid); else deleteModePlayers.remove(uuid); }

    public boolean isPublicMode(UUID uuid) { return publicModePlayers.contains(uuid); }
    public void setPublicMode(UUID uuid, boolean state) { if(state) publicModePlayers.add(uuid); else publicModePlayers.remove(uuid); }

    public boolean isRenameMode(UUID uuid) { return renameModePlayers.contains(uuid); }
    public void setRenameMode(UUID uuid, boolean state) { if(state) renameModePlayers.add(uuid); else renameModePlayers.remove(uuid); }

    public boolean isFavoriteMode(UUID uuid) { return favoriteModePlayers.contains(uuid); }
    public void setFavoriteMode(UUID uuid, boolean state) { if(state) favoriteModePlayers.add(uuid); else favoriteModePlayers.remove(uuid); }

    public boolean isMemoMode(UUID uuid) { return memoModePlayers.contains(uuid); }
    public void setMemoMode(UUID uuid, boolean state) { if(state) memoModePlayers.add(uuid); else memoModePlayers.remove(uuid); }

    // Search Query
    public String getSearchQuery(UUID uuid) { return searchQuery.get(uuid); }
    public void setSearchQuery(UUID uuid, String query) {
        if (query == null || query.trim().isEmpty()) {
            searchQuery.remove(uuid);
        } else {
            searchQuery.put(uuid, query.trim());
        }
    }

    // Pagination
    public int getPage(UUID uuid) { return page.getOrDefault(uuid, 0); }
    public void setPage(UUID uuid, int value) {
        if (value <= 0) page.remove(uuid);
        else page.put(uuid, value);
    }

    // Input States
    public boolean isCreatingHome(UUID uuid) { return creatingHome.contains(uuid); }
    public void setCreatingHome(UUID uuid, boolean state) { if(state) creatingHome.add(uuid); else creatingHome.remove(uuid); }

    public boolean isSearchingHomes(UUID uuid) { return searchingHomes.contains(uuid); }
    public void setSearchingHomes(UUID uuid, boolean state) { if(state) searchingHomes.add(uuid); else searchingHomes.remove(uuid); }

    public String getRenamingTarget(UUID uuid) { return renamingHomeTarget.get(uuid); }
    public void setRenamingTarget(UUID uuid, String target) { 
        if(target == null) renamingHomeTarget.remove(uuid); 
        else renamingHomeTarget.put(uuid, target); 
    }

    public String getEditingMemoTarget(UUID uuid) { return editingMemoTarget.get(uuid); }
    public void setEditingMemoTarget(UUID uuid, String target) {
        if(target == null) editingMemoTarget.remove(uuid);
        else editingMemoTarget.put(uuid, target);
    }

    public boolean isWaitingForInput(UUID uuid) {
        return creatingHome.contains(uuid)
                || searchingHomes.contains(uuid)
                || editingMemoTarget.containsKey(uuid)
                || renamingHomeTarget.containsKey(uuid);
    }
}
