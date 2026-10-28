package phase3.ui;
import phase3.core.GameAction;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Standalone prototype data, never upstream user settings or scores. */
public final class LocalOptions {
 public static final List<GameAction> ACTIONS=List.of(GameAction.LEFT,GameAction.RIGHT,GameAction.SOFT_DROP,GameAction.ROTATE,GameAction.HARD_DROP);
 private final Path file;
 private final Map<Integer,EnumMap<GameAction,Integer>> bindings=new HashMap<>();
 private String recent="";
 public LocalOptions(Path file){this.file=file;defaults();load();}
 private void defaults(){int[][] keys={{KeyEvent.VK_A,KeyEvent.VK_D,KeyEvent.VK_S,KeyEvent.VK_W,KeyEvent.VK_F},{KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_DOWN,KeyEvent.VK_UP,KeyEvent.VK_SPACE}};for(int p=1;p<=2;p++){var m=new EnumMap<GameAction,Integer>(GameAction.class);for(int i=0;i<ACTIONS.size();i++)m.put(ACTIONS.get(i),keys[p-1][i]);bindings.put(p,m);}}
 public int key(int player,GameAction action){return bindings.get(player).get(action);}
 public void bind(int player,GameAction action,int key){if(!ACTIONS.contains(action)||!bindings.containsKey(player)||key<=0||key==KeyEvent.VK_P||key==KeyEvent.VK_ESCAPE||key==KeyEvent.VK_ENTER)throw new IllegalArgumentException("P / ESC / Enter는 공통 조작용입니다.");for(int p=1;p<=2;p++)for(GameAction a:ACTIONS)if((p!=player||a!=action)&&key(p,a)==key)throw new IllegalArgumentException("다른 조작과 중복된 키입니다.");bindings.get(player).put(action,key);}
 public String recentIp(){return recent;}
 public void recentIp(String value){recent=Objects.requireNonNull(value).trim();}
 public void save() throws IOException {Files.createDirectories(file.getParent());Properties p=new Properties();p.setProperty("recentIp",recent);for(int n=1;n<=2;n++)for(GameAction a:ACTIONS)p.setProperty("p"+n+"."+a.name(),""+key(n,a));try(var out=Files.newOutputStream(file)){p.store(out,"Standalone Phase3 controls");}}
 private void load(){Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);Map<Integer,EnumMap<GameAction,Integer>> proposed=new HashMap<>();Set<Integer> used=new HashSet<>();for(int n=1;n<=2;n++){var m=new EnumMap<GameAction,Integer>(GameAction.class);for(GameAction a:ACTIONS){int k=Integer.parseInt(p.getProperty("p"+n+"."+a.name(),""+key(n,a)));if(k<=0||k==KeyEvent.VK_P||k==KeyEvent.VK_ESCAPE||k==KeyEvent.VK_ENTER||!used.add(k))throw new IllegalArgumentException();m.put(a,k);}proposed.put(n,m);}bindings.putAll(proposed);recent=p.getProperty("recentIp","");}catch(IOException|IllegalArgumentException ignored){/* defaults remain on invalid file */}}
}
