package phase3.core;
import difficulty.Difficulty;
import java.util.Objects;
public record MatchConfig(MatchMode mode, Difficulty difficulty, long timeLimitMillis, long seed) {
    public static final long DEFAULT_TIME_LIMIT_MILLIS = 180_000;
    public MatchConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if (timeLimitMillis < 10_000 || timeLimitMillis > 3_600_000)
            throw new IllegalArgumentException("Duration must be 10000..3600000 milliseconds");
    }
    public MatchConfig(MatchMode mode, Difficulty difficulty, long seed) {
        this(mode, difficulty, DEFAULT_TIME_LIMIT_MILLIS, seed);
    }
}
