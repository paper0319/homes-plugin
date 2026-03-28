package com.example.homes.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;

import com.example.homes.HomesPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {

    private final HomesPlugin plugin;
    private HikariDataSource dataSource;
    private HikariConfig config;

    private interface MemoColumnDialect {
        String alterMemoColumnSql(int length);
    }

    public DatabaseManager(HomesPlugin plugin) {
        this.plugin = plugin;
        setupDataSource();
        createTable();
    }

    private void setupDataSource() {
        config = new HikariConfig();
        String type = plugin.getConfig().getString("database.type", "h2").toLowerCase();

        if (type.equals("mariadb") || type.equals("mysql")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            String port = plugin.getConfig().getString("database.port", "3306");
            String db = plugin.getConfig().getString("database.name", "minecraft");
            String user = plugin.getConfig().getString("database.user", "root");
            String pass = plugin.getConfig().getString("database.password", "");
            
            config.setJdbcUrl("jdbc:" + type + "://" + host + ":" + port + "/" + db);
            config.setUsername(user);
            config.setPassword(pass);
            
            // Optimization for MySQL/MariaDB
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
        } else {
            // H2 (Default)
            String path = plugin.getDataFolder().getAbsolutePath() + "/homes";
            config.setJdbcUrl("jdbc:h2:" + path + ";MODE=MySQL");
            config.setDriverClassName("org.h2.Driver");
        }

        // Connection Pool Tuning
        config.setPoolName("HomesPlugin-Pool");
        config.setMaximumPoolSize(10); // Adjust based on server load, 10 is usually enough for this plugin
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000); // 30 seconds
        config.setMaxLifetime(1800000); // 30 minutes
        config.setConnectionTimeout(10000); // 10 seconds

        dataSource = new HikariDataSource(config);
    }

    public List<String> getPlayersWithPublicHomes() {
        List<String> players = new ArrayList<>();
        String sql = "SELECT DISTINCT player_uuid FROM player_homes WHERE is_public = true";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String uuidStr = rs.getString("player_uuid");
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    if (op.getName() != null) {
                        players.add(op.getName());
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore invalid UUIDs
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_homes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "home_name VARCHAR(64) NOT NULL," +
                "world_name VARCHAR(64) NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "yaw FLOAT NOT NULL," +
                "pitch FLOAT NOT NULL," +
                "is_public BOOLEAN NOT NULL DEFAULT FALSE," +
                "UNIQUE (player_uuid, home_name)" +
                ");";
        
        // Use createIndex for faster lookups
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_homes(player_uuid);";

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(indexSql)) {
                    stmt.executeUpdate();
                }

                addColumnIfNotExists(conn, "is_favorite", "BOOLEAN NOT NULL DEFAULT FALSE");
                int desiredMemoLen = plugin.getConfig().getInt("settings.max-home-memo-length", 15);
                if (desiredMemoLen < 1) desiredMemoLen = 1;
                if (desiredMemoLen > 128) desiredMemoLen = 128;
                addColumnIfNotExists(conn, "memo", "VARCHAR(" + desiredMemoLen + ")");
                ensureMemoColumnLength(conn, desiredMemoLen);

                conn.commit();
            } catch (SQLException e) {
                SQLException txEx = new SQLException("Transaction failed", e);
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().warning("Rollback failed: " + rollbackEx.getMessage());
                    txEx.addSuppressed(rollbackEx);
                }
                throw txEx;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database initialization failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void ensureMemoColumnLength(Connection conn, int desiredMemoLen) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "PLAYER_HOMES", "memo")) {
            Integer size = null;
            if (rs.next()) {
                size = rs.getInt("COLUMN_SIZE");
            }
            if (size == null || size <= 0) {
                try (ResultSet rs2 = conn.getMetaData().getColumns(null, null, "player_homes", "memo")) {
                    if (rs2.next()) {
                        size = rs2.getInt("COLUMN_SIZE");
                    }
                }
            }
            if (size == null || size <= 0 || size == desiredMemoLen) {
                return;
            }

            String type = plugin.getConfig().getString("database.type", "h2").toLowerCase();
            MemoColumnDialect dialect = memoColumnDialect(type);
            if (dialect == null) {
                plugin.getLogger().warning("Skipping memo column length adjust for database type: " + type);
                return;
            }
            String alterSql = dialect.alterMemoColumnSql(desiredMemoLen);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(alterSql);
            }
        }
    }

    private MemoColumnDialect memoColumnDialect(String type) {
        if (type == null) return null;
        switch (type) {
            case "mysql":
            case "mariadb":
                return length -> "ALTER TABLE player_homes MODIFY memo VARCHAR(" + length + ")";
            case "h2":
                return length -> "ALTER TABLE player_homes ALTER COLUMN memo VARCHAR(" + length + ")";
            default:
                return null;
        }
    }

    private void addColumnIfNotExists(Connection conn, String columnName, String columnDef) throws SQLException {
        boolean columnExists = false;
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "PLAYER_HOMES", null)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    columnExists = true;
                    break;
                }
            }
        }

        if (!columnExists) {
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "player_homes", null)) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        columnExists = true;
                        break;
                    }
                }
            }
        }

        if (!columnExists) {
            String sql = "ALTER TABLE player_homes ADD COLUMN " + columnName + " " + columnDef;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void setHome(UUID uuid, String name, Location loc, boolean isPublic) {
        String sql = "INSERT INTO player_homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch, is_public) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE world_name=?, x=?, y=?, z=?, yaw=?, pitch=?, is_public=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, loc.getWorld().getName());
            stmt.setDouble(4, loc.getX());
            stmt.setDouble(5, loc.getY());
            stmt.setDouble(6, loc.getZ());
            stmt.setFloat(7, loc.getYaw());
            stmt.setFloat(8, loc.getPitch());
            stmt.setBoolean(9, isPublic);
            
            stmt.setString(10, loc.getWorld().getName());
            stmt.setDouble(11, loc.getX());
            stmt.setDouble(12, loc.getY());
            stmt.setDouble(13, loc.getZ());
            stmt.setFloat(14, loc.getYaw());
            stmt.setFloat(15, loc.getPitch());
            stmt.setBoolean(16, isPublic);

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setHome(UUID uuid, String name, Location loc) {
        setHome(uuid, name, loc, false); // Default not public
    }
    
    public void updatePublic(UUID uuid, String name, boolean isPublic) {
        String sql = "UPDATE player_homes SET is_public = ? WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isPublic);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateFavorite(UUID uuid, String name, boolean isFavorite) {
        String sql = "UPDATE player_homes SET is_favorite = ? WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isFavorite);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update favorite: " + e.getMessage());
        }
    }

    public void updateMemo(UUID uuid, String name, String memo) {
        String sql = "UPDATE player_homes SET memo = ? WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memo);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update memo: " + e.getMessage());
        }
    }

    public void renameHome(UUID uuid, String oldName, String newName) {
        String sql = "UPDATE player_homes SET home_name = ? WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, oldName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public boolean isPublic(UUID uuid, String name) {
        String sql = "SELECT is_public FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_public");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void deleteHome(UUID uuid, String name) {
        String sql = "DELETE FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Location getHome(UUID uuid, String name) {
        String sql = "SELECT * FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Location(
                            plugin.getServer().getWorld(rs.getString("world_name")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Boolean> getHomePublicStatus(UUID uuid) {
        Map<String, Boolean> status = new HashMap<>();
        String sql = "SELECT home_name, is_public FROM player_homes WHERE player_uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    status.put(rs.getString("home_name"), rs.getBoolean("is_public"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return status;
    }

    public Map<String, Boolean> getHomeFavoriteStatus(UUID uuid) {
        Map<String, Boolean> status = new HashMap<>();
        String sql = "SELECT home_name, is_favorite FROM player_homes WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    status.put(rs.getString("home_name"), rs.getBoolean("is_favorite"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return status;
    }

    public Map<String, String> getHomeMemos(UUID uuid) {
        Map<String, String> memos = new HashMap<>();
        String sql = "SELECT home_name, memo FROM player_homes WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String memo = rs.getString("memo");
                    if (memo != null && !memo.isEmpty()) {
                        memos.put(rs.getString("home_name"), memo);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return memos;
    }

    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> homes = new HashMap<>();
        String sql = "SELECT * FROM player_homes WHERE player_uuid = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Location loc = new Location(
                            plugin.getServer().getWorld(rs.getString("world_name")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                    homes.put(rs.getString("home_name"), loc);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return homes;
    }
}
