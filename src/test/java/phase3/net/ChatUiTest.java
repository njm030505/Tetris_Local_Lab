package phase3.net;

import org.junit.jupiter.api.Test;
import phase3.ui.LocalMain;
import javax.swing.*;
import java.lang.reflect.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ChatUiTest {
    private static Object field(Object target,String name)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private static void refresh(LocalMain window)throws Exception {
        Method m=LocalMain.class.getDeclaredMethod("refresh");m.setAccessible(true);m.invoke(window);
    }
    @Test void lobbyEnterSendsAndDisplaysKoreanChatAndDisablesDuringPlay()throws Exception {
        var sockets=PeerSessionMemoryTest.sockets();
        try(var host=PeerSessionMemoryTest.peer(PeerSession.Role.HOST,sockets[0]);
            var client=PeerSessionMemoryTest.peer(PeerSession.Role.CLIENT,sockets[1])) {
            assertTimeoutPreemptively(Duration.ofSeconds(4),()->{while(client.view().state()!=PeerSession.State.LOBBY)Thread.sleep(5);});
            LocalMain[] window={null};
            try {
                SwingUtilities.invokeAndWait(()->{try{
                    window[0]=new LocalMain();Field f=LocalMain.class.getDeclaredField("peer");f.setAccessible(true);f.set(window[0],host);
                    refresh(window[0]);JTextField input=(JTextField)field(window[0],"chatInput");
                    assertTrue(input.isEnabled());input.setText("한글 입력 테스트");input.postActionEvent();assertEquals("",input.getText());
                    assertTrue(((JTextArea)field(window[0],"chatLog")).getText().contains("서버 (나): 한글 입력 테스트"));
                }catch(Exception e){throw new RuntimeException(e);}});
                assertTimeoutPreemptively(Duration.ofSeconds(4),()->{while(client.chatHistory().isEmpty())Thread.sleep(5);});
                assertEquals("한글 입력 테스트",client.chatHistory().get(0).text());
                client.sendChat("상대 메시지");
                assertTimeoutPreemptively(Duration.ofSeconds(4),()->{while(host.chatHistory().size()<2)Thread.sleep(5);});
                SwingUtilities.invokeAndWait(()->{try{refresh(window[0]);assertTrue(((JTextArea)field(window[0],"chatLog")).getText().contains("클라이언트: 상대 메시지"));}catch(Exception e){throw new RuntimeException(e);}});
                host.ready();client.ready();
                assertTimeoutPreemptively(Duration.ofSeconds(4),()->{while(host.view().state()!=PeerSession.State.PLAYING)Thread.sleep(5);});
                SwingUtilities.invokeAndWait(()->{try{refresh(window[0]);assertFalse(((JTextField)field(window[0],"chatInput")).isEnabled());assertFalse(((JButton)field(window[0],"chatSend")).isEnabled());}catch(Exception e){throw new RuntimeException(e);}});
            } finally {if(window[0]!=null)SwingUtilities.invokeAndWait(()->window[0].dispose());}
        }
    }
}
