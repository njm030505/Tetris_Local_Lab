package phase3.ui;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.ArrayList;
import menu.*;
import menu.settings.*;
import blocks.style.ColorMode;
import phase3.core.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real Swing construction/rendering and input routing on the EDT, without opening windows. */
class WindowSmokeTest {
 static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void paint(Component c){c.setSize(1100,800);if(c instanceof Container p)p.doLayout();BufferedImage image=new BufferedImage(1100,800,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();c.printAll(g);g.dispose();}
 @Test void bothPlayerBindingsRouteToIndependentEnginesAndToolbarCannotStealSpace()throws Exception{
  final LocalMain[] window={null};
  SwingUtilities.invokeAndWait(()->{window[0]=new LocalMain();window[0].startLocal(MatchMode.NORMAL,false);});
  try{Thread.sleep(80);SwingUtilities.invokeAndWait(()->{try{
   LocalMain w=window[0];call(w,"refresh",new Class<?>[0]);MatchSnapshot before=(MatchSnapshot)field(w,"latest");
   var root=w.getRootPane();Action p1=root.getActionMap().get("phase3.press."+KeyEvent.VK_A),p2=root.getActionMap().get("phase3.press."+KeyEvent.VK_LEFT);
   p1.actionPerformed(new ActionEvent(root,0,""));p2.actionPerformed(new ActionEvent(root,0,""));
   root.getActionMap().get("phase3.release."+KeyEvent.VK_A).actionPerformed(new ActionEvent(root,0,""));root.getActionMap().get("phase3.release."+KeyEvent.VK_LEFT).actionPerformed(new ActionEvent(root,0,""));
   assertNotNull(before.first());assertNotNull(before.second());assertTrue(((MatchPanel)field(w,"board")).isFocusable());
   assertFalse(((JButton)field(w,"rematch")).isFocusable());paint(w);
  }catch(Exception e){throw new RuntimeException(e);}});
  Thread.sleep(80);SwingUtilities.invokeAndWait(()->{try{LocalMain w=window[0];call(w,"refresh",new Class<?>[0]);MatchSnapshot s=(MatchSnapshot)field(w,"latest");assertEquals(2,s.first().current().x());assertEquals(2,s.second().current().x());call(w,"pause",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});
  Thread.sleep(40);SwingUtilities.invokeAndWait(()->{try{call(window[0],"refresh",new Class<?>[0]);assertTrue(((MatchSnapshot)field(window[0],"latest")).paused());paint(window[0]);}catch(Exception e){throw new RuntimeException(e);}});
  }finally{SwingUtilities.invokeAndWait(()->window[0].dispose());}
 }
 @Test void snapshotRendererHandlesEveryColorSizeAttackAndTerminalResult()throws Exception{
  SwingUtilities.invokeAndWait(()->{var settings=AppSettings.getInstance();try{for(ColorMode color:ColorMode.values())for(ScreenSize size:ScreenSize.values()){
   settings.setColorMode(color);settings.setScreenSize(size);MatchPanel panel=new MatchPanel();MatchSession session=new MatchSession(new MatchConfig(MatchMode.TIMED,difficulty.Difficulty.NORMAL,10000,44),4,false);
   session.apply(1,GameAction.HARD_DROP);panel.snapshot(session.snapshot());paint(panel);session.togglePause();panel.snapshot(session.snapshot());paint(panel);session.togglePause();session.advance(10000);panel.snapshot(session.snapshot());paint(panel);
  }}finally{settings.resetToDefaults();}});
 }
 @Test void legacySettingsAndScoreScreensStillConstructAndNavigate()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   AppSettings settings=AppSettings.getInstance();settings.resetToDefaults();
   StartMenu menu=new StartMenu();paint(menu);
   for(KeyListener l:menu.getKeyListeners()){l.keyPressed(new KeyEvent(menu,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_DOWN,' '));l.keyPressed(new KeyEvent(menu,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_UP,' '));}menu.dispose();
   SettingsScreen screen=new SettingsScreen(null,()->{},()->{});
   for(int i=0;i<5;i++){call(screen,"changeValue",new Class<?>[]{int.class},1);paint(screen);}
   call(screen,"moveDown",new Class<?>[0]);call(screen,"moveUp",new Class<?>[0]);
   screen.dispose();
   ArrayList<ScoreRecord> records=new ArrayList<>();for(int i=0;i<10;i++)records.add(new ScoreRecord("Test"+i,10000-i,"NORMAL","NORMAL"));new storage.ScoreStorage().save(records);
   ScoreboardScreen scores=new ScoreboardScreen();paint(scores);for(KeyListener l:scores.getKeyListeners()){l.keyPressed(new KeyEvent(scores,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_RIGHT,' '));l.keyPressed(new KeyEvent(scores,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_TAB,' '));}scores.dispose();
   GameOverScreen over=new GameOverScreen(0,"NORMAL","NORMAL");paint(over);for(KeyListener l:over.getKeyListeners()){l.keyPressed(new KeyEvent(over,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_RIGHT,' '));l.keyPressed(new KeyEvent(over,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_TAB,' '));}over.dispose();
  }catch(Exception e){throw new RuntimeException(e);}finally{AppSettings.getInstance().resetToDefaults();}});
 }
}
