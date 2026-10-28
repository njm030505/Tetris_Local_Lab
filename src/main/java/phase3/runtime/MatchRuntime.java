package phase3.runtime;

import phase3.core.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Single mutation owner. Callers enqueue bounded commands; snapshots never wait on simulation. */
public final class MatchRuntime implements AutoCloseable {
    private final MatchSession session;
    private final Consumer<MatchSnapshot> listener;
    private final Object mutationLock=new Object();
    private final ArrayBlockingQueue<Runnable> commands=new ArrayBlockingQueue<>(256);
    private final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"phase3-match");t.setDaemon(true);return t;
    });
    private volatile MatchSnapshot snapshot;
    private volatile boolean closed;
    private boolean started;
    private long lastNanos;

    public MatchRuntime(MatchSession session) { this(session,null); }
    public MatchRuntime(MatchSession session,Consumer<MatchSnapshot> listener) {
        this.session=Objects.requireNonNull(session);this.listener=listener;this.snapshot=session.snapshot();
    }
    public MatchRuntime(MatchConfig config,long id,boolean solo,Consumer<MatchSnapshot> listener) {
        this(new MatchSession(config,id,solo),listener);
    }
    public synchronized void start() {
        if(closed||started)return;started=true;lastNanos=System.nanoTime();
        loop.scheduleAtFixedRate(this::step,0,16,TimeUnit.MILLISECONDS);
    }
    private void step() {
        synchronized(mutationLock) {
            if(closed)return;
            long now=System.nanoTime();long elapsed=TimeUnit.NANOSECONDS.toMillis(now-lastNanos);
            lastNanos+=TimeUnit.MILLISECONDS.toNanos(elapsed);
            // Advance elapsed time under the previous pause state before handling new input.
            session.advance(elapsed);
            for(int i=0;i<256;i++){Runnable command=commands.poll();if(command==null)break;command.run();}
            snapshot=session.snapshot();
        }
        // Callbacks must not hold the mutation lock: owners may close while processing a callback.
        if(listener!=null&&!closed)listener.accept(snapshot);
    }
    public void command(int player,GameAction action) {
        if(player!=1&&(player!=2||snapshot.second()==null))throw new IllegalArgumentException("Player must be present in match");
        Objects.requireNonNull(action);enqueue(() -> session.apply(player,action));
    }
    public void togglePause(){enqueue(session::togglePause);}
    private void enqueue(Runnable command){if(!closed)commands.offer(command);}
    public MatchSnapshot snapshot(){return snapshot;}
    @Override public synchronized void close(){
        synchronized(mutationLock){if(closed)return;closed=true;commands.clear();loop.shutdownNow();}
    }
}
