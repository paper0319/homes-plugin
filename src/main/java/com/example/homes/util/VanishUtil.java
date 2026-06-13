package com.example.homes.util;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * vanish (透明化) プラグインとの連携ユーティリティ。
 *
 * <p>SuperVanish / PremiumVanish / EssentialsX / CMI などは、vanish 中のプレイヤーに
 * {@code "vanished"} メタデータ (boolean=true) を付与する共通仕様を持つ。これを参照することで、
 * 特定プラグインへハード依存せずに vanish 状態を判定できる
 * (SuperVanish API の "isInvisible" 相当)。
 */
public final class VanishUtil {

    /** vanish 透視権限。これを持つプレイヤー (既定で OP) には vanish 中の相手も見える。 */
    public static final String SEE_HIDDEN_PERMISSION = "homes.tpa.seehidden";

    private VanishUtil() {
    }

    /** プレイヤーが vanish 中か。 */
    public static boolean isVanished(Player player) {
        if (player == null) return false;
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code viewer} から見て {@code target} が「隠れている」(TPA 対象として扱うべきでない) か。
     * target が vanish 中で、かつ viewer が透視権限を持たない場合に true。
     */
    public static boolean isHiddenFrom(Player viewer, Player target) {
        if (!isVanished(target)) return false;
        return viewer == null || !viewer.hasPermission(SEE_HIDDEN_PERMISSION);
    }
}
