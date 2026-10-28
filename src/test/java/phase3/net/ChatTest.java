package phase3.net;

import org.junit.jupiter.api.Test;
import phase3.core.GameAction;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    private static void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
            while (!condition.getAsBoolean()) Thread.sleep(5);
        });
    }
    @Test void koreanMessagesTravelBothWaysAndRemainAcrossRematch() throws Exception {
        var sockets=PeerSessionMemoryTest.sockets();
        try(var host=PeerSessionMemoryTest.peer(PeerSession.Role.HOST,sockets[0]);
            var client=PeerSessionMemoryTest.peer(PeerSession.Role.CLIENT,sockets[1])) {
            await(() -> client.view().state()==PeerSession.State.LOBBY);
            host.sendChat("안녕하세요! 😀");
            await(() -> client.chatHistory().size()==1);
            assertEquals("안녕하세요! 😀",client.chatHistory().get(0).text());
            assertEquals(PeerSession.Role.HOST,client.chatHistory().get(0).sender());
            client.sendChat("준비됐어요");
            await(() -> host.chatHistory().size()==2);
            assertEquals("준비됐어요",host.chatHistory().get(1).text());
            assertThrows(UnsupportedOperationException.class,()->host.chatHistory().clear());
            host.ready();client.ready();await(() -> host.view().state()==PeerSession.State.PLAYING);
            assertThrows(IllegalStateException.class,()->host.sendChat("경기 중"));
            assertThrows(IllegalStateException.class,()->client.sendChat("경기 중"));
            for(int i=0;i<50&&host.view().state()==PeerSession.State.PLAYING;i++) {
                host.command(GameAction.HARD_DROP);Thread.sleep(20);
            }
            await(() -> client.view().state()==PeerSession.State.RESULT);
            client.sendChat("한 판 더!");await(() -> host.chatHistory().size()==3);
            assertEquals("한 판 더!",host.chatHistory().get(2).text());
            host.ready();client.ready();await(() -> client.view().state()==PeerSession.State.PLAYING);
            assertEquals(3,host.chatHistory().size());
        }
    }
    @Test void rejectsEmptyOversizedAndMultilineMessagesAndBoundsHistory() throws Exception {
        var sockets=PeerSessionMemoryTest.sockets();
        try(var host=PeerSessionMemoryTest.peer(PeerSession.Role.HOST,sockets[0]);
            var client=PeerSessionMemoryTest.peer(PeerSession.Role.CLIENT,sockets[1])) {
            await(() -> client.view().state()==PeerSession.State.LOBBY);
            for(String text:new String[]{"", "   ","가".repeat(501),"첫 줄\n둘째 줄"})
                assertThrows(IllegalArgumentException.class,()->host.sendChat(text));
            host.sendChat("가".repeat(500));await(() -> client.chatHistory().size()==1);
            assertEquals(500,client.chatHistory().get(0).text().length());
            for(int i=0;i<205;i++) {
                String text="message "+i;host.sendChat(text);
                await(() -> client.chatHistory().get(client.chatHistory().size()-1).text().equals(text));
            }
            assertEquals(200,host.chatHistory().size());assertEquals(200,client.chatHistory().size());
            assertEquals("message 204",client.chatHistory().get(199).text());
        }
    }
}
