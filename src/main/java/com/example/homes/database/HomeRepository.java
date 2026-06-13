package com.example.homes.database;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ホームデータの永続化層。HomeManager はこのインターフェース経由でのみ DB に触れる。
 *
 * 読み取り系は失敗時に空のコレクションを返す (ログは実装側で出す)。
 * 書き込み系は失敗時に RuntimeException を投げる — 呼び出し側 (HomeManager) が
 * 捕捉してプレイヤーへ通知する。
 */
public interface HomeRepository {

    Map<String, HomeData> getHomesData(UUID uuid);

    Map<String, Boolean> getHomePublicStatus(UUID uuid);

    Map<String, Boolean> getHomeFavoriteStatus(UUID uuid);

    Map<String, String> getHomeMemos(UUID uuid);

    List<UUID> getPlayerUuidsWithPublicHomes();

    void setHome(UUID uuid, String name, String worldName, double x, double y, double z, float yaw, float pitch, boolean isPublic);

    void updatePublic(UUID uuid, String name, boolean isPublic);

    void updateFavorite(UUID uuid, String name, boolean isFavorite);

    void updateMemo(UUID uuid, String name, String memo);

    void renameHome(UUID uuid, String oldName, String newName);

    void deleteHome(UUID uuid, String name);

    void close();
}
