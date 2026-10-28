package phase3.core;
@FunctionalInterface
public interface InputAgent { GameAction decide(PlayerSnapshot observation); }
