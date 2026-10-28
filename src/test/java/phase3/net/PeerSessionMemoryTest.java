package phase3.net;

import difficulty.Difficulty;
import phase3.core.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real framing/read/write/state threads using Java pipes, without opening a network socket. */
class PeerSessionMemoryTest {
    static final class MemorySocket extends Socket {
        final PipedInputStream input=new PipedInputStream(65536);
        PipedOutputStream output;
        volatile boolean closed;
        @Override public InputStream getInputStream(){return input;}
        @Override public OutputStream getOutputStream(){return output;}
        @Override public void setTcpNoDelay(boolean value){}
        @Override public void setKeepAlive(boolean value){}
        @Override public SocketAddress getRemoteSocketAddress(){return new InetSocketAddress("127.0.0.1",12345);}
        @Override public boolean isClosed(){return closed;}
        @Override public synchronized void close()throws IOException {if(closed)return;closed=true;input.close();output.close();}
    }
    private static MemorySocket[] sockets()throws IOException {
        MemorySocket a=new MemorySocket(),b=new MemorySocket();a.output=new PipedOutputStream(b.input);b.output=new PipedOutputStream(a.input);
        return new MemorySocket[]{a,b};
    }
    private static PeerSession peer(PeerSession.Role role,MemorySocket socket)throws Exception {
        Constructor<PeerSession> constructor=PeerSession.class.getDeclaredConstructor(PeerSession.Role.class);constructor.setAccessible(true);
        PeerSession peer=constructor.newInstance(role);Method attach=PeerSession.class.getDeclaredMethod("attach",Socket.class);attach.setAccessible(true);
        attach.invoke(peer,socket);return peer;
    }
    private static void await(BooleanSupplier condition){assertTimeoutPreemptively(Duration.ofSeconds(4),() -> {while(!condition.getAsBoolean())Thread.sleep(5);});}
    @Test void authorityReadinessConfigMovementPauseHeartbeatResultAndRematch()throws Exception {
        MemorySocket[] sockets=sockets();
        try(PeerSession host=peer(PeerSession.Role.HOST,sockets[0]);PeerSession client=peer(PeerSession.Role.CLIENT,sockets[1])) {
            await(() -> client.view().state()==PeerSession.State.LOBBY);
            assertEquals(PeerSession.Role.CLIENT,client.view().role());
            assertThrows(IllegalStateException.class,() -> client.setConfig(host.view().config()));
            client.ready();await(() -> host.view().remoteReady());assertTrue(client.view().localReady());
            assertFalse(host.view().localReady());assertEquals(PeerSession.State.LOBBY,host.view().state());
            MatchConfig config=new MatchConfig(MatchMode.TIMED,Difficulty.HARD,10000,42);host.setConfig(config);
            await(() -> client.view().config().equals(config)&&!client.view().localReady());assertFalse(host.view().remoteReady());
            host.ready();await(() -> client.view().remoteReady());assertFalse(client.view().localReady());
            client.ready();await(() -> client.snapshot()!=null&&client.view().state()==PeerSession.State.PLAYING);
            assertFalse(host.view().localReady());assertFalse(client.view().remoteReady());
            assertThrows(IllegalStateException.class,() -> host.setConfig(config));
            long id=client.snapshot().matchId();int x=client.snapshot().second().current().x();
            client.command(GameAction.LEFT);await(() -> client.snapshot().second().current().x()==x-1);
            host.command(GameAction.RIGHT);await(() -> client.snapshot().first().current().x()==4);
            client.togglePause();await(() -> client.snapshot().paused());
            long remaining=client.snapshot().remainingMillis();Thread.sleep(1650);
            assertEquals(remaining,client.snapshot().remainingMillis());assertTrue(host.view().lastReceiveAgeMillis()<1500);
            assertTrue(client.view().lastReceiveAgeMillis()<1500);
            host.togglePause();await(() -> !client.snapshot().paused());
            for(int i=0;i<50&&host.view().state()==PeerSession.State.PLAYING;i++){host.command(GameAction.HARD_DROP);Thread.sleep(20);}
            await(() -> client.view().state()==PeerSession.State.RESULT);assertFalse(client.snapshot().result().isEmpty());
            client.ready();host.ready();await(() -> client.snapshot().matchId()>id&&client.view().state()==PeerSession.State.PLAYING);
            assertEquals(0,client.snapshot().first().score());assertFalse(client.snapshot().paused());
            client.close();await(() -> host.view().state()==PeerSession.State.DISCONNECTED);assertFalse(host.view().status().isBlank());
        }
    }
    @Test void staleMatchCommandsIgnoredAndReplayedSequenceDisconnects()throws Exception {
        MemorySocket[] sockets=sockets();
        try(PeerSession host=peer(PeerSession.Role.HOST,sockets[0]);MemorySocket raw=sockets[1]) {
            DataOutputStream out=new DataOutputStream(raw.output);
            host.ready();WireCodec.write(out,new WireCodec.Message(WireCodec.READY,1,0,WireCodec.payload(data -> data.writeLong(1))));
            await(() -> host.view().state()==PeerSession.State.PLAYING);
            int x=host.snapshot().second().current().x();
            WireCodec.write(out,new WireCodec.Message(WireCodec.COMMAND,2,0,new byte[]{(byte)GameAction.LEFT.ordinal()}));
            Thread.sleep(80);assertEquals(x,host.snapshot().second().current().x());
            WireCodec.write(out,new WireCodec.Message(WireCodec.COMMAND,3,1,new byte[]{(byte)GameAction.LEFT.ordinal()}));
            await(() -> host.snapshot().second().current().x()==x-1);
            WireCodec.write(out,new WireCodec.Message(WireCodec.HEARTBEAT,3,1,new byte[0]));
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);
        }
    }
    @Test void malformedInputDisconnectsAndCloseIsIdempotent()throws Exception {
        MemorySocket[] sockets=sockets();
        try(PeerSession host=peer(PeerSession.Role.HOST,sockets[0]);MemorySocket raw=sockets[1]) {
            DataOutputStream out=new DataOutputStream(raw.output);out.writeInt(Integer.MAX_VALUE);out.flush();
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);
            host.close();host.close();assertEquals(PeerSession.State.CLOSED,host.view().state());assertTrue(sockets[0].closed);
        }
    }
    @Test void warningAndTimeoutUseTrafficAge()throws Exception {
        MemorySocket[] sockets=sockets();
        try(PeerSession host=peer(PeerSession.Role.HOST,sockets[0]);MemorySocket raw=sockets[1]) {
            Field received=PeerSession.class.getDeclaredField("lastReceiveNanos");received.setAccessible(true);
            synchronized(host){received.setLong(host,System.nanoTime()-TimeUnit.MILLISECONDS.toNanos(1600));}
            assertTrue(host.view().status().contains("지연"));
            synchronized(host){received.setLong(host,System.nanoTime()-TimeUnit.MILLISECONDS.toNanos(5100));}
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);assertTrue(host.view().status().contains("시간 초과"));
        }
    }
}
