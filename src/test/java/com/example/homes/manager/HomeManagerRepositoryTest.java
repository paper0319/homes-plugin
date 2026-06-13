package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;
import com.example.homes.database.DataAccessException;
import com.example.homes.database.HomeData;
import com.example.homes.database.HomeRepository;

class HomeManagerRepositoryTest {

    /** メモリ上の Map だけで動くテスト用リポジトリ。failWrites で書き込み失敗を再現する。 */
    private static final class FakeRepository implements HomeRepository {
        final Map<UUID, Map<String, HomeData>> homes = new HashMap<>();
        boolean failWrites;

        @Override
        public Map<String, HomeData> getHomesData(UUID uuid) {
            return new HashMap<>(homes.getOrDefault(uuid, Map.of()));
        }

        @Override
        public Map<String, Boolean> getHomePublicStatus(UUID uuid) {
            return new HashMap<>();
        }

        @Override
        public Map<String, Boolean> getHomeFavoriteStatus(UUID uuid) {
            return new HashMap<>();
        }

        @Override
        public Map<String, String> getHomeMemos(UUID uuid) {
            return new HashMap<>();
        }

        @Override
        public List<UUID> getPlayerUuidsWithPublicHomes() {
            return List.of();
        }

        @Override
        public void setHome(UUID uuid, String name, String worldName, double x, double y, double z, float yaw, float pitch, boolean isPublic) {
            if (failWrites) throw new DataAccessException("write failed", null);
            homes.computeIfAbsent(uuid, k -> new HashMap<>())
                    .put(name, new HomeData(worldName, x, y, z, yaw, pitch));
        }

        @Override
        public void updatePublic(UUID uuid, String name, boolean isPublic) {
            if (failWrites) throw new DataAccessException("write failed", null);
        }

        @Override
        public void updateFavorite(UUID uuid, String name, boolean isFavorite) {
            if (failWrites) throw new DataAccessException("write failed", null);
        }

        @Override
        public void updateMemo(UUID uuid, String name, String memo) {
            if (failWrites) throw new DataAccessException("write failed", null);
        }

        @Override
        public void renameHome(UUID uuid, String oldName, String newName) {
            if (failWrites) throw new DataAccessException("write failed", null);
        }

        @Override
        public void deleteHome(UUID uuid, String name) {
            if (failWrites) throw new DataAccessException("write failed", null);
        }

        @Override
        public void close() {
        }
    }

    private ServerMock server;
    private HomesPlugin plugin;
    private FakeRepository repository;
    private HomeManager homeManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HomesPlugin.class);
        repository = new FakeRepository();
        homeManager = new HomeManager(plugin, repository);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * 条件が満たされるまで、非同期タスクと同期タスクを交互に処理しながら待つ。
     *
     * MockBukkit の非同期プールは、タスクがワーカーに拾われる前の一瞬は
     * getActiveCount()==0 になり waitAsyncTasksFinished() が素通りしうる。
     * さらにこのプラグインの書き込み失敗通知は「非同期書き込み → 同期で通知」と
     * 2段になるため、単発の flush では取りこぼす。スケジューラ内部の空き具合では
     * なく観測可能な結果をポーリングすることで、この競合に左右されないようにする。
     */
    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        do {
            if (condition.getAsBoolean()) {
                return;
            }
            server.getScheduler().waitAsyncTasksFinished();
            server.getScheduler().performTicks(1);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        } while (System.currentTimeMillis() < deadline);
    }

    @Test
    void loadsHomesFromRepository() {
        server.addSimpleWorld("world");
        UUID uuid = UUID.randomUUID();
        repository.homes.put(uuid, new HashMap<>(Map.of(
                "base", new HomeData("world", 1, 64, 1, 0f, 0f))));

        homeManager.ensureLoaded(uuid);
        awaitUntil(() -> homeManager.isLoaded(uuid));

        assertTrue(homeManager.isLoaded(uuid));
        assertNotNull(homeManager.getHome(uuid, "base"));
        assertEquals(1, homeManager.getHomes(uuid).size());
    }

    @Test
    void writeFailureNotifiesPlayer() {
        server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();

        repository.failWrites = true;
        homeManager.setHomeDirectly(player.getUniqueId(), "base", player.getLocation());

        List<String> messages = new ArrayList<>();
        awaitUntil(() -> {
            String message;
            while ((message = player.nextMessage()) != null) {
                messages.add(message);
            }
            return messages.stream().anyMatch(HomeManagerRepositoryTest::isSaveFailedMessage);
        });

        assertTrue(messages.stream().anyMatch(HomeManagerRepositoryTest::isSaveFailedMessage),
                "書き込み失敗時にプレイヤーへ通知されるべき");
    }

    private static boolean isSaveFailedMessage(String message) {
        return message.contains("保存に失敗") || message.toLowerCase().contains("failed to save");
    }

    @Test
    void successfulWriteReachesRepository() {
        server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();

        homeManager.setHomeDirectly(player.getUniqueId(), "base", player.getLocation());
        awaitUntil(() -> repository.homes.getOrDefault(player.getUniqueId(), Map.of()).containsKey("base"));

        assertTrue(repository.homes.getOrDefault(player.getUniqueId(), Map.of()).containsKey("base"));
    }
}
