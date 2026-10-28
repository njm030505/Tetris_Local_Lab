package phase3.core;
public record MatchSnapshot(long matchId, MatchConfig config, PlayerSnapshot first,
        PlayerSnapshot second, long remainingMillis, boolean paused, String result, long tick) {}
