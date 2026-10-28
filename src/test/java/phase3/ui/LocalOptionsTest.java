package phase3.ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.awt.event.KeyEvent;
import phase3.core.GameAction;
import static org.junit.jupiter.api.Assertions.*;
class LocalOptionsTest {
 @TempDir Path dir;
 @Test void bindingsValidateAcrossPlayersAndPersistWithRecentIp() throws Exception {
  LocalOptions o=new LocalOptions(dir.resolve("local.properties"));
  assertEquals(KeyEvent.VK_A,o.key(1,GameAction.LEFT));
  assertThrows(IllegalArgumentException.class,()->o.bind(1,GameAction.LEFT,KeyEvent.VK_P));
  assertThrows(IllegalArgumentException.class,()->o.bind(1,GameAction.LEFT,KeyEvent.VK_LEFT));
  o.bind(1,GameAction.LEFT,KeyEvent.VK_J);o.recentIp("127.0.0.1");o.save();
  LocalOptions loaded=new LocalOptions(dir.resolve("local.properties"));
  assertEquals(KeyEvent.VK_J,loaded.key(1,GameAction.LEFT));assertEquals("127.0.0.1",loaded.recentIp());
 }
 @Test void damagedSettingsFallBackWithoutBreakingAllBindings() throws Exception {
  java.nio.file.Files.writeString(dir.resolve("local.properties"),"p1.LEFT=80\np2.RIGHT=bad\n");
  LocalOptions o=new LocalOptions(dir.resolve("local.properties"));assertEquals(KeyEvent.VK_A,o.key(1,GameAction.LEFT));
 }
}
