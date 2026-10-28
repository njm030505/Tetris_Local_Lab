package phase3.net;

import difficulty.Difficulty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import phase3.core.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.io.DataOutputStream;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class PeerSessionTest {
    @BeforeAll static void requirePermittedLoopback() throws Exception {
        try(ServerSocket server=new ServerSocket(0); Socket socket=new Socket("127.0.0.1",server.getLocalPort())) {
            // An actual connection is required; successful bind alone does not establish sandbox permission.
        } catch(SocketException e) {
            if(!Boolean.getBoolean("tetris.requireNetwork")&&e.getMessage()!=null&&e.getMessage().contains("Permission denied"))
                Assumptions.assumeTrue(false,"Loopback sockets denied by the execution sandbox: "+e.getMessage());
            throw e;
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            while (!condition.getAsBoolean()) Thread.sleep(10);
        });
    }
    @Test void connectReadyInputPauseResultAndRematchUseSameConnection() throws Exception {
        try (PeerSession host=PeerSession.host(0); PeerSession client=PeerSession.connect("127.0.0.1",host.boundPort())) {
            await(() -> host.view().state()==PeerSession.State.LOBBY && client.view().state()==PeerSession.State.LOBBY);
            var config=new MatchConfig(MatchMode.TIMED,Difficulty.HARD,10000,42);
            host.setConfig(config);
            await(() -> client.view().config().equals(config));
            client.ready();
            await(() -> host.view().remoteReady());
            assertEquals(PeerSession.State.LOBBY,host.view().state());
            host.ready();
            await(() -> client.snapshot()!=null && client.view().state()==PeerSession.State.PLAYING);
            long id=client.snapshot().matchId();
            int before=client.snapshot().second().current().x();
            client.command(GameAction.LEFT);
            await(() -> client.snapshot().second().current().x()==before-1);
            client.togglePause();
            await(() -> client.snapshot().paused());
            long remaining=client.snapshot().remainingMillis();
            Thread.sleep(150);
            assertEquals(remaining,client.snapshot().remainingMillis());
            host.togglePause();
            await(() -> !client.snapshot().paused());
            // Host's authority is exercised through hard drops, not a test-only result setter.
            for(int i=0;i<50 && host.view().state()==PeerSession.State.PLAYING;i++) {
                host.command(GameAction.HARD_DROP); Thread.sleep(20);
            }
            await(() -> host.view().state()==PeerSession.State.RESULT && client.view().state()==PeerSession.State.RESULT);
            assertFalse(client.snapshot().result().isBlank());
            client.ready(); host.ready();
            await(() -> client.snapshot().matchId()!=id && client.view().state()==PeerSession.State.PLAYING);
            assertEquals(0,client.snapshot().first().score());
            assertFalse(client.snapshot().paused());
        }
    }
    @Test void configurationChangeInvalidatesBothReadyFlagsAndClientCannotConfigure() throws Exception {
        try(PeerSession host=PeerSession.host(0); PeerSession client=PeerSession.connect("127.0.0.1",host.boundPort())) {
            await(() -> host.view().state()==PeerSession.State.LOBBY && client.view().state()==PeerSession.State.LOBBY);
            client.ready(); await(() -> host.view().remoteReady());
            host.setConfig(new MatchConfig(MatchMode.ITEM,Difficulty.EASY,180000,5));
            await(() -> !client.view().localReady() && client.view().config().mode()==MatchMode.ITEM);
            assertFalse(host.view().remoteReady());
            assertThrows(IllegalStateException.class,() -> client.setConfig(host.view().config()));
        }
    }
    @Test void disconnectAndMalformedFrameAreReportedAndSocketsClose() throws Exception {
        try(PeerSession host=PeerSession.host(0)) {
            PeerSession client=PeerSession.connect("127.0.0.1",host.boundPort());
            await(() -> host.view().state()==PeerSession.State.LOBBY);
            client.close();
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);
            assertFalse(host.view().status().isBlank());
        }
        try(PeerSession host=PeerSession.host(0); Socket socket=new Socket("127.0.0.1",host.boundPort())) {
            new DataOutputStream(socket.getOutputStream()).writeInt(Integer.MAX_VALUE);
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);
        }
    }
    @Test void silentPeerWarnsThenTimesOut() throws Exception {
        try(PeerSession host=PeerSession.host(0);Socket socket=new Socket("127.0.0.1",host.boundPort())) {
            await(() -> host.view().lastReceiveAgeMillis()>=1500);
            assertTrue(host.view().status().contains("지연"));
            await(() -> host.view().state()==PeerSession.State.DISCONNECTED);
        }
    }
}
