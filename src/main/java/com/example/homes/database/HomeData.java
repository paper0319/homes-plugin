package com.example.homes.database;

/** DB に保存されたホーム1件分の位置情報。Bukkit の World 解決前の生データ。 */
public record HomeData(String worldName, double x, double y, double z, float yaw, float pitch) {
}
