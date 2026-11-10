package dev.che1s.phantomcreeper.stats;

import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StatsService implements AutoCloseable {
    private static final String UPSERT_SQL = """
            INSERT INTO phantomcreeper_stats
                (player_uuid, player_name, kills, deaths, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                player_name = excluded.player_name,
                kills = excluded.kills,
                deaths = excluded.deaths,
                updated_at = excluded.updated_at
            """;

    private final String jdbcUrl;
    private final Consumer<Throwable> errorReporter;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService writer =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "PhantomCreeper-SQLite");
                thread.setDaemon(true);
                return thread;
            });
    private Connection writerConnection;

    public StatsService(
            Path databaseFile,
            Consumer<Throwable> errorReporter) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        this.errorReporter = errorReporter;
        try {
            Class.forName("org.sqlite.JDBC");
            initialize();
            load();
            writer.scheduleWithFixedDelay(this::flushDirty, 5, 5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            writer.shutdownNow();
            throw new IllegalStateException(exception);
        }
    }

    public PlayerStats get(UUID playerId) {
        return cache.getOrDefault(
                playerId,
                new PlayerStats(playerId, "", 0L, 0L)
        );
    }

    public void incrementKills(Player player) {
        incrementKills(player.getUniqueId(), player.getName());
    }

    public void incrementDeaths(Player player) {
        incrementDeaths(player.getUniqueId(), player.getName());
    }

    public void incrementKills(UUID playerId, String playerName) {
        update(playerId, playerName, true);
    }

    public void incrementDeaths(UUID playerId, String playerName) {
        update(playerId, playerName, false);
    }

    private void update(UUID playerId, String playerName, boolean kill) {
        cache.compute(playerId, (uuid, previous) -> {
            PlayerStats base = previous == null
                    ? new PlayerStats(uuid, playerName, 0L, 0L)
                    : previous;
            return new PlayerStats(
                    uuid,
                    playerName,
                    base.kills() + (kill ? 1L : 0L),
                    base.deaths() + (kill ? 0L : 1L)
            );
        });
        dirtyPlayers.add(playerId);
    }

    private void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS phantomcreeper_stats (
                        player_uuid TEXT PRIMARY KEY NOT NULL,
                        player_name TEXT NOT NULL,
                        kills INTEGER NOT NULL DEFAULT 0,
                        deaths INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private void load() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT player_uuid, player_name, kills, deaths
                     FROM phantomcreeper_stats
                     """)) {
            while (result.next()) {
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                cache.put(playerId, new PlayerStats(
                        playerId,
                        result.getString("player_name"),
                        result.getLong("kills"),
                        result.getLong("deaths")
                ));
            }
        }
    }

    private void flushDirty() {
        Set<UUID> batch = Set.copyOf(dirtyPlayers);
        if (batch.isEmpty()) {
            return;
        }
        dirtyPlayers.removeAll(batch);
        Connection connection = null;
        try {
            connection = writerConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                long now = System.currentTimeMillis();
                for (UUID playerId : batch) {
                    PlayerStats stats = cache.get(playerId);
                    if (stats == null) {
                        continue;
                    }
                    statement.setString(1, stats.playerId().toString());
                    statement.setString(2, stats.playerName());
                    statement.setLong(3, stats.kills());
                    statement.setLong(4, stats.deaths());
                    statement.setLong(5, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            dirtyPlayers.addAll(batch);
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            errorReporter.accept(exception);
        }
    }

    private Connection writerConnection() throws SQLException {
        if (writerConnection == null || writerConnection.isClosed()) {
            writerConnection = connect();
            try (Statement statement = writerConnection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
            }
        }
        return writerConnection;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @Override
    public void close() {
        writer.execute(() -> {
            flushDirty();
            if (writerConnection != null) {
                try {
                    writerConnection.close();
                } catch (SQLException exception) {
                    errorReporter.accept(exception);
                } finally {
                    writerConnection = null;
                }
            }
        });
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    public record PlayerStats(UUID playerId, String playerName, long kills, long deaths) {
    }
}
