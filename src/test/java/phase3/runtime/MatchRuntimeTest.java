package phase3.runtime;

import difficulty.Difficulty;
import phase3.core.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MatchRuntimeTest {
    private void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(3),() -> {while(!condition.getAsBoolean())Thread.sleep(5);});
    }
    @Test void queuedCommandsOnlyMutateOnRuntimeThreadAndPauseFreezesTime()throws Exception {
        AtomicReference<Thread> callbackThread=new AtomicReference<>();
        MatchSession session=new MatchSession(new MatchConfig(MatchMode.TIMED,Difficulty.NORMAL,10000,42),1,false);
        try(MatchRuntime runtime=new MatchRuntime(session,s -> callbackThread.set(Thread.currentThread()))) {
            int x=runtime.snapshot().first().current().x();runtime.command(1,GameAction.LEFT);
            assertEquals(x,session.snapshot().first().current().x());
            runtime.start();runtime.start();await(() -> runtime.snapshot().first().current().x()==x-1);
            assertNotEquals(Thread.currentThread(),callbackThread.get());
            runtime.togglePause();await(() -> runtime.snapshot().paused());
            long remaining=runtime.snapshot().remainingMillis();Thread.sleep(90);
            assertEquals(remaining,runtime.snapshot().remainingMillis());
            runtime.command(1,GameAction.RIGHT);Thread.sleep(40);assertEquals(x-1,runtime.snapshot().first().current().x());
            runtime.togglePause();await(() -> !runtime.snapshot().paused());await(() -> runtime.snapshot().remainingMillis()<remaining);
            assertThrows(IllegalArgumentException.class,() -> runtime.command(0,GameAction.LEFT));
            assertThrows(NullPointerException.class,() -> runtime.command(1,null));
        }
    }
    @Test void boundedBurstAndIdempotentCloseLeaveSnapshotStable()throws Exception {
        MatchRuntime runtime=new MatchRuntime(new MatchSession(new MatchConfig(MatchMode.NORMAL,Difficulty.NORMAL,4),4,true));
        assertThrows(IllegalArgumentException.class,() -> runtime.command(2,GameAction.LEFT));
        for(int i=0;i<10000;i++)runtime.command(1,GameAction.WAIT);
        runtime.start();await(() -> runtime.snapshot().tick()>0);runtime.close();runtime.close();
        Thread.sleep(40);long tick=runtime.snapshot().tick();runtime.command(1,GameAction.HARD_DROP);runtime.togglePause();runtime.start();
        Thread.sleep(40);assertEquals(tick,runtime.snapshot().tick());
    }
}
