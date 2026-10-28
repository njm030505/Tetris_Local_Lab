package phase3.net;

import difficulty.Difficulty;
import phase3.core.*;
import phase3.runtime.MatchRuntime;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Two-player host authority. Public calls only update state or enqueue work; sockets have dedicated threads. */
public final class PeerSession implements AutoCloseable {
    private static final ExecutorService RESOLVER=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),r -> daemon(r,"phase3-resolve"),new ThreadPoolExecutor.AbortPolicy());
    public enum Role { HOST,CLIENT }
    public enum State { LISTENING,CONNECTING,LOBBY,PLAYING,RESULT,DISCONNECTED,CLOSED }
    public record PeerView(Role role,State state,boolean localReady,boolean remoteReady,MatchConfig config,
                           String status,long lastReceiveAgeMillis,String remoteAddress) {}
    private record Outbound(int type,long matchId,byte[] payload) {}
    private final Role role;
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"phase3-heartbeat"));
    private final ArrayBlockingQueue<Outbound> controls=new ArrayBlockingQueue<>(128);
    private volatile Outbound newestSnapshot;
    private volatile MatchSnapshot snapshot;
    private volatile Socket socket;
    private ServerSocket server;
    private MatchRuntime runtime;
    private State state;
    private MatchConfig config=new MatchConfig(MatchMode.NORMAL,Difficulty.NORMAL,180000,1);
    private boolean localReady,remoteReady;
    private long matchId,revision=1,lastReceiveNanos=System.nanoTime(),lastSequence;
    private String status="",remoteAddress="";
    private PeerSession(Role role){this.role=role;state=role==Role.HOST?State.LISTENING:State.CONNECTING;}
    private static Thread daemon(Runnable run,String name){Thread t=new Thread(run,name);t.setDaemon(true);return t;}

    public static PeerSession host(int port)throws IOException {
        PeerSession peer=new PeerSession(Role.HOST);
        try {
            peer.server=new ServerSocket();peer.server.setReuseAddress(true);peer.server.bind(new InetSocketAddress(port));
            peer.status="연결 대기 중";daemon(peer::accept,"phase3-accept").start();return peer;
        }catch(IOException|RuntimeException e){peer.close();throw e;}
    }
    public static PeerSession connect(String host,int port)throws IOException {
        Objects.requireNonNull(host);PeerSession peer=new PeerSession(Role.CLIENT);Socket connected=new Socket();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        try {
            Future<InetAddress> resolution=RESOLVER.submit(() -> InetAddress.getByName(host));
            InetAddress address;
            try {address=resolution.get(3000,TimeUnit.MILLISECONDS);}
            catch(TimeoutException e){resolution.cancel(true);throw new SocketTimeoutException("Address lookup timed out");}
            catch(InterruptedException e){resolution.cancel(true);Thread.currentThread().interrupt();throw new IOException("Connection interrupted",e);}
            catch(ExecutionException e){throw new IOException("Address lookup failed",e.getCause());}
            int remaining=(int)Math.max(1,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            connected.connect(new InetSocketAddress(address,port),remaining);peer.attach(connected);return peer;
        }
        catch(IOException|RuntimeException e){try{connected.close();}catch(IOException ignored){}peer.close();throw e;}
    }
    private void accept() {
        try {Socket connected=server.accept();attach(connected);server.close();}
        catch(IOException e){fail("연결 오류: "+e.getMessage());}
    }
    private synchronized void attach(Socket connected)throws IOException {
        if(state==State.CLOSED){connected.close();return;}
        connected.setTcpNoDelay(true);connected.setKeepAlive(true);socket=connected;
        remoteAddress=connected.getRemoteSocketAddress().toString();lastReceiveNanos=System.nanoTime();
        if(role==Role.HOST){state=State.LOBBY;status="준비를 선택하세요";sendState();}
        daemon(this::writeLoop,"phase3-send").start();daemon(this::readLoop,"phase3-receive").start();
        monitor.scheduleAtFixedRate(this::heartbeat,0,500,TimeUnit.MILLISECONDS);
    }
    public synchronized int boundPort(){return server==null?0:server.getLocalPort();}
    public synchronized void setConfig(MatchConfig config) {
        if(role!=Role.HOST)throw new IllegalStateException("Only the host can configure");
        if(state!=State.LOBBY&&state!=State.LISTENING&&state!=State.RESULT)throw new IllegalStateException("Match already running");
        this.config=Objects.requireNonNull(config);revision++;localReady=false;remoteReady=false;
        if(socket!=null)sendState();
    }
    public synchronized void ready() {
        if(state!=State.LOBBY&&state!=State.RESULT)return;
        if(localReady)return;localReady=true;
        if(role==Role.HOST){startIfReady();sendState();}
        else enqueue(WireCodec.READY,matchId,out -> out.writeLong(revision));
    }
    private void startIfReady() {
        if(!localReady||!remoteReady)return;
        if(runtime!=null)runtime.close();matchId++;state=State.PLAYING;localReady=false;remoteReady=false;
        runtime=new MatchRuntime(new MatchSession(config,matchId,false),this::published);
        snapshot=runtime.snapshot();sendState();sendSnapshot(snapshot);runtime.start();
    }
    public synchronized void command(GameAction action) {
        Objects.requireNonNull(action);if(state!=State.PLAYING)return;
        if(role==Role.HOST)runtime.command(1,action);
        else enqueue(WireCodec.COMMAND,matchId,out -> out.writeByte(action.ordinal()));
    }
    public synchronized void togglePause() {
        if(state!=State.PLAYING)return;
        if(role==Role.HOST)runtime.togglePause();else enqueue(WireCodec.PAUSE,matchId,out -> {});
    }
    public MatchSnapshot snapshot(){return snapshot;}
    public synchronized PeerView view() {
        long age=socket==null?0:TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-lastReceiveNanos);
        String display=age>=1500&&state!=State.DISCONNECTED&&state!=State.CLOSED?"연결 지연: 상대 응답 대기 중":status;
        return new PeerView(role,state,localReady,remoteReady,config,display,age,remoteAddress);
    }
    private synchronized void published(MatchSnapshot next) {
        if(state!=State.PLAYING||next.matchId()!=matchId)return;
        snapshot=next;
        if(!next.result().isEmpty()){state=State.RESULT;localReady=false;remoteReady=false;status="경기 종료: 다시 준비하세요";sendState();}
        sendSnapshot(next);
    }
    private void sendSnapshot(MatchSnapshot next) {
        try{newestSnapshot=new Outbound(WireCodec.SNAPSHOT,next.matchId(),WireCodec.payload(out -> WireCodec.snapshot(out,next)));}
        catch(IOException e){fail("스냅샷 오류");}
    }
    private void sendState() {
        enqueue(WireCodec.STATE,matchId,out -> {out.writeLong(revision);out.writeByte(state.ordinal());out.writeBoolean(localReady);
            out.writeBoolean(remoteReady);WireCodec.config(out,config);});
    }
    private void enqueue(int type,long id,WireCodec.Encoder encoder) {
        if(state==State.CLOSED||state==State.DISCONNECTED)return;
        try{if(!controls.offer(new Outbound(type,id,WireCodec.payload(encoder))))fail("출력 대기열 초과");}
        catch(IOException e){fail("프로토콜 오류");}
    }
    private void writeLoop() {
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            long sequence=0;
            while(!socket.isClosed()) {
                Outbound message=controls.poll();
                if(message==null){message=takeSnapshot();if(message==null)message=controls.poll(20,TimeUnit.MILLISECONDS);}
                if(message!=null)WireCodec.write(out,new WireCodec.Message(message.type(),++sequence,message.matchId(),message.payload()));
            }
        }catch(IOException e){fail("연결 종료: "+e.getMessage());}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private synchronized Outbound takeSnapshot(){Outbound message=newestSnapshot;newestSnapshot=null;return message;}
    private void readLoop() {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            while(!socket.isClosed())receive(WireCodec.read(in));
        }catch(IOException|RuntimeException e){fail("연결 종료: "+e.getMessage());}
    }
    private synchronized void receive(WireCodec.Message message)throws IOException {
        if(state==State.CLOSED||state==State.DISCONNECTED)return;
        if(message.sequence()<=lastSequence)throw new IOException("Stale sequence");lastSequence=message.sequence();
        DataInputStream in=WireCodec.input(message.payload());
        switch(message.type()) {
            case WireCodec.HEARTBEAT -> { }
            case WireCodec.STATE -> {
                if(role!=Role.CLIENT)throw new IOException("Client cannot set state");
                long nextRevision=in.readLong();if(nextRevision<revision||message.matchId()<matchId)throw new IOException("Stale state");
                State next=State.values()[WireCodec.range(in.readUnsignedByte(),0,State.values().length-1)];
                if(next!=State.LOBBY&&next!=State.PLAYING&&next!=State.RESULT)throw new IOException("Invalid remote state");
                remoteReady=in.readBoolean();localReady=in.readBoolean();config=WireCodec.config(in);
                revision=nextRevision;matchId=message.matchId();
                // A result is visible together with its terminal snapshot, never before it.
                if(next!=State.RESULT||(snapshot!=null&&snapshot.matchId()==matchId&&!snapshot.result().isEmpty()))state=next;
                status=next==State.RESULT?"경기 종료: 다시 준비하세요":"연결됨";
            }
            case WireCodec.READY -> {
                if(role!=Role.HOST)throw new IOException("Host cannot send ready");
                long readyRevision=in.readLong();
                if(readyRevision==revision&&message.matchId()==matchId&&(state==State.LOBBY||state==State.RESULT)){
                    remoteReady=true;startIfReady();sendState();
                }
            }
            case WireCodec.COMMAND -> {
                if(role!=Role.HOST)throw new IOException("Host cannot send commands");
                GameAction action=GameAction.values()[WireCodec.range(in.readUnsignedByte(),0,GameAction.values().length-1)];
                if(message.matchId()==matchId&&state==State.PLAYING)runtime.command(2,action);
            }
            case WireCodec.PAUSE -> {
                if(role!=Role.HOST)throw new IOException("Host cannot send pause");
                if(message.matchId()==matchId&&state==State.PLAYING)runtime.togglePause();
            }
            case WireCodec.SNAPSHOT -> {
                if(role!=Role.CLIENT)throw new IOException("Client cannot send snapshot");
                MatchSnapshot next=WireCodec.snapshot(in);
                if(next.matchId()!=message.matchId())throw new IOException("Snapshot ID mismatch");
                if(next.matchId()==matchId&&(snapshot==null||snapshot.matchId()!=matchId||next.tick()>=snapshot.tick())){
                    snapshot=next;if(!next.result().isEmpty())state=State.RESULT;
                }
            }
            default -> throw new IOException("Unknown message");
        }
        WireCodec.end(in);lastReceiveNanos=System.nanoTime();
    }
    private synchronized void heartbeat() {
        if(state==State.CLOSED||state==State.DISCONNECTED)return;
        if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-lastReceiveNanos)>=5000){fail("연결 시간 초과");return;}
        // One heartbeat at most; snapshots never consume the reliable control capacity.
        if(controls.stream().noneMatch(m -> m.type()==WireCodec.HEARTBEAT))enqueue(WireCodec.HEARTBEAT,matchId,out -> {});
    }
    private synchronized void fail(String reason) {
        if(state==State.CLOSED||state==State.DISCONNECTED)return;
        state=State.DISCONNECTED;status=reason;stop();
    }
    private void stop() {
        if(runtime!=null)runtime.close();monitor.shutdownNow();controls.clear();newestSnapshot=null;
        // Socket close unblocks accept/read/write and does not perform protocol IO.
        try{if(socket!=null)socket.close();}catch(IOException ignored){}
        try{if(server!=null)server.close();}catch(IOException ignored){}
    }
    @Override public synchronized void close(){if(state==State.CLOSED)return;state=State.CLOSED;status="연결 닫힘";stop();}
}
