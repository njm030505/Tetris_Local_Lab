package phase3.core;

import board.Board;
import java.util.List;
import java.util.Objects;

/** An authoritative match advanced only by supplied simulation time. All access is serialized. */
public final class MatchSession {
    private final MatchConfig config;
    private final long matchId;
    private final PlayerEngine first, second;
    private long remainingMillis, tick;
    private boolean paused;
    private String result="";

    public MatchSession(MatchConfig config,long matchId,boolean solo) {
        this(config,matchId,solo,
                new PlayerEngine(config,PieceSource.seeded(config.difficulty(),config.seed()),new Board()),
                solo?null:new PlayerEngine(config,PieceSource.seeded(config.difficulty(),config.seed()),new Board()));
    }
    MatchSession(MatchConfig config,long matchId,boolean solo,PlayerEngine first,PlayerEngine second) {
        this.config=Objects.requireNonNull(config,"config");this.matchId=matchId;
        this.first=Objects.requireNonNull(first,"first");this.second=solo?null:Objects.requireNonNull(second,"second");
        remainingMillis=config.mode()==MatchMode.TIMED?config.timeLimitMillis():0;
        settleTurns();
    }
    public synchronized void apply(int player,GameAction action) {
        PlayerEngine target=player(player);Objects.requireNonNull(action,"action");
        if(paused||!result.isEmpty())return;
        target.apply(action);settleTurns();tick++;
    }
    public synchronized void advance(long elapsedMillis) {
        if(elapsedMillis<0)throw new IllegalArgumentException("Negative elapsed time");
        if(paused||!result.isEmpty()||elapsedMillis==0)return;
        long left=config.mode()==MatchMode.TIMED?Math.min(elapsedMillis,remainingMillis):elapsedMillis;
        while(left>0&&result.isEmpty()) {
            long event=first.millisUntilEvent();
            if(second!=null)event=Math.min(event,second.millisUntilEvent());
            long step=Math.min(left,event);
            first.advance(step);if(second!=null)second.advance(step);
            left-=step;if(config.mode()==MatchMode.TIMED)remainingMillis-=step;
            // Both players finish this event before attacks are delivered and either new piece spawns.
            settleTurns();
            if(config.mode()==MatchMode.TIMED&&remainingMillis==0&&result.isEmpty())finishByScore();
        }
        tick++;
    }
    private void settleTurns() {
        List<int[]> fromFirst=first.drainAttacks();
        List<int[]> fromSecond=second==null?List.of():second.drainAttacks();
        if(second!=null){second.enqueue(fromFirst);first.enqueue(fromSecond);}
        first.completeTurn();if(second!=null)second.completeTurn();
        boolean a=first.snapshot().gameOver(),b=second!=null&&second.snapshot().gameOver();
        if(a||b)result=second==null?"P2":a&&b?"DRAW":a?"P2":"P1";
    }
    private void finishByScore() {
        int a=first.snapshot().score(),b=second==null?0:second.snapshot().score();
        result=a==b?"DRAW":a>b?"P1":"P2";
    }
    private PlayerEngine player(int number) {
        if(number==1)return first;
        if(number==2&&second!=null)return second;
        throw new IllegalArgumentException("Player must be1 or2 present in this match");
    }
    public synchronized void togglePause() {
        if(!result.isEmpty())return;
        paused=!paused;tick++;
    }
    public synchronized MatchSnapshot snapshot() {
        return new MatchSnapshot(matchId,config,first.snapshot(),second==null?null:second.snapshot(),remainingMillis,paused,result,tick);
    }
}
