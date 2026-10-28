package phase3.ui;

import phase3.core.*;
import phase3.runtime.MatchRuntime;
import phase3.net.PeerSession;
import difficulty.Difficulty;
import menu.settings.*;
import menu.GameOverScreen;
import menu.ScoreboardScreen;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Standalone local launcher: UI receives snapshots and submits the same commands as future agents. */
public final class LocalMain extends JFrame {
 private final LocalOptions options;
 private final CardLayout cards=new CardLayout();
 private final JPanel content=new JPanel(cards);
 private final MatchPanel board=new MatchPanel();
 private final JLabel lobbyStatus=new JLabel("서버 만들기 또는 클라이언트 접속을 선택하세요.");
 private final JLabel gameStatus=new JLabel();
 private final JComboBox<MatchMode> mode=new JComboBox<>(MatchMode.values());
 private final JComboBox<Difficulty> difficulty=new JComboBox<>(Difficulty.values());
 private final JSpinner seconds=new JSpinner(new SpinnerNumberModel(180,10,3600,10));
 private final JTextField address=new JTextField(15);
 private final JSpinner port=new JSpinner(new SpinnerNumberModel(24816,1,65535,1));
 private final JButton ready=new JButton("게임 시작 / 준비");
 private final JButton configure=new JButton("서버 설정 적용");
 private final JButton rematch=new JButton("다시 시작");
 private final Map<Integer,Long> held=new HashMap<>();
 private final Set<Integer> pressed=new HashSet<>();
 private final javax.swing.Timer frameTimer;
 private MatchRuntime runtime;
 private PeerSession peer;
 private MatchSnapshot latest;
 private boolean solo,soloResultHandled;
 private boolean disposed;
 private long connectionGeneration;

 public static void main(String[] args){
  if(System.getProperty("tetris.dataDir")==null){try{Path location=Paths.get(LocalMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());Path root=Files.isDirectory(location)?Paths.get("").toAbsolutePath():location.getParent();System.setProperty("tetris.dataDir",root.resolve("data").toString());}catch(Exception e){System.setProperty("tetris.dataDir",Paths.get("data").toAbsolutePath().toString());}}
  SwingUtilities.invokeLater(()->new LocalMain().setVisible(true));
 }
 public LocalMain(){
  super("TFSE · Phase 3 Local");ui.AppIcon.apply(this);
  options=new LocalOptions(Paths.get(System.getProperty("tetris.dataDir","data"),"phase3.properties"));
  address.setText(options.recentIp());setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
  content.add(menu(),"menu");content.add(lobby(),"lobby");content.add(game(),"game");add(content);
  addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){dispose();}});
  addWindowFocusListener(new WindowAdapter(){@Override public void windowLostFocus(WindowEvent e){held.clear();pressed.clear();}});
  installInputs();setSize(1120,850);setMinimumSize(new Dimension(760,660));setLocationRelativeTo(null);
  frameTimer=new javax.swing.Timer(16,e->refresh());frameTimer.start();
 }
 private JPanel menu(){JPanel p=new JPanel(new GridBagLayout());p.setBackground(new Color(15,20,32));JPanel list=new JPanel(new GridLayout(0,1,8,10));list.setOpaque(false);JLabel title=new JLabel("TFSE · TETRIS",SwingConstants.CENTER);title.setForeground(Color.WHITE);title.setFont(new Font("Dialog",Font.BOLD,30));list.add(title);button(list,"일반 싱글",()->startLocal(MatchMode.NORMAL,true));button(list,"아이템 싱글",()->startLocal(MatchMode.ITEM,true));button(list,"로컬 2인 대전",()->{if(selectMatch())startLocal((MatchMode)mode.getSelectedItem(),false);});button(list,"P2P 대전",()->{stopGame();cards.show(content,"lobby");});button(list,"설정 · 화면 / 색각 / 난이도",()->new SettingsScreen(this,()->{},this::dispose).setVisible(true));button(list,"플레이어별 조작 키",this::controls);button(list,"스코어보드",()->new ScoreboardScreen().setVisible(true));button(list,"종료",this::dispose);p.add(list);return p;}
 private JPanel lobby(){JPanel p=new JPanel(new BorderLayout(12,12));p.setBorder(BorderFactory.createEmptyBorder(30,30,30,30));JPanel controls=new JPanel(new GridLayout(0,2,12,12));controls.add(new JLabel("서버 IP"));controls.add(address);controls.add(new JLabel("포트"));controls.add(port);button(controls,"서버 만들기",this::host);button(controls,"클라이언트 접속",this::connect);controls.add(new JLabel("모드"));controls.add(mode);controls.add(new JLabel("난이도"));controls.add(difficulty);controls.add(new JLabel("시간제한 (초)"));controls.add(seconds);configure.addActionListener(e->safe(()->peer.setConfig(config())));controls.add(configure);ready.addActionListener(e->safe(()->peer.ready()));controls.add(ready);p.add(controls,BorderLayout.NORTH);JTextArea help=new JTextArea("연결 후 서버가 규칙을 선택하고 '서버 설정 적용'을 누르세요.\n양쪽이 '게임 시작 / 준비'를 눌러야 경기가 시작됩니다.\n게임 종료 후 같은 연결로 이 화면에서 재경기할 수 있습니다.\n방화벽이 막으면 Windows에서 Java/게임의 개인 네트워크 접근을 허용하세요.\n같은 로컬 네트워크에서 서버 화면에 표시되는 IPv4와 포트를 입력하세요.\n\nP: 경기 전체 일시정지 / 재개 · ESC: 경기 나가기\nP2P에서는 각 PC의 PLAYER 1 키 설정으로 자기 플레이어를 조작합니다.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);p.add(help,BorderLayout.CENTER);JPanel bottom=new JPanel(new BorderLayout());bottom.add(lobbyStatus,BorderLayout.CENTER);button(bottom,"메뉴로",this::toMenu);p.add(bottom,BorderLayout.SOUTH);return p;}
 private JPanel game(){JPanel p=new JPanel(new BorderLayout());p.add(board,BorderLayout.CENTER);JPanel bottom=new JPanel();bottom.add(gameStatus);button(bottom,"일시정지 P",this::pause);rematch.addActionListener(e->{if(peer!=null){cards.show(content,"lobby");}else if(latest!=null&&!latest.result().isEmpty())startLocal(latest.config().mode(),solo);});bottom.add(rematch);button(bottom,"메뉴 / 나가기 ESC",this::leave);for(Component c:bottom.getComponents())if(c instanceof JButton)c.setFocusable(false);p.add(bottom,BorderLayout.SOUTH);return p;}
 private void button(JPanel panel,String label,Runnable action){JButton b=new JButton(label);b.addActionListener(e->action.run());panel.add(b);}
 private boolean selectMatch(){JPanel p=new JPanel(new GridLayout(0,2,8,8));JComboBox<MatchMode> m=new JComboBox<>(MatchMode.values());JComboBox<Difficulty> d=new JComboBox<>(Difficulty.values());d.setSelectedItem(AppSettings.getInstance().getDifficulty());JSpinner t=new JSpinner(new SpinnerNumberModel(180,10,3600,10));p.add(new JLabel("모드"));p.add(m);p.add(new JLabel("난이도"));p.add(d);p.add(new JLabel("시간제한 초"));p.add(t);if(JOptionPane.showConfirmDialog(this,p,"대전 설정",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return false;mode.setSelectedItem(m.getSelectedItem());difficulty.setSelectedItem(d.getSelectedItem());seconds.setValue(t.getValue());return true;}
 private MatchConfig config(){return new MatchConfig((MatchMode)mode.getSelectedItem(),(Difficulty)difficulty.getSelectedItem(),((Number)seconds.getValue()).longValue()*1000,System.nanoTime());}
 public void startLocal(MatchMode selected,boolean single){stopGame();solo=single;soloResultHandled=false;MatchConfig c=single?new MatchConfig(selected,AppSettings.getInstance().getDifficulty(),180000,System.nanoTime()):config();runtime=new MatchRuntime(new MatchSession(c,System.nanoTime(),single));runtime.start();cards.show(content,"game");held.clear();board.requestFocusInWindow();}
 private void host(){disconnect();long generation=connectionGeneration;lobbyStatus.setText("서버 준비 중...");int chosen=((Number)port.getValue()).intValue();new SwingWorker<PeerSession,Void>(){protected PeerSession doInBackground()throws Exception{return PeerSession.host(chosen);}protected void done(){try{PeerSession connected=get();if(disposed||generation!=connectionGeneration){connected.close();return;}peer=connected;peer.setConfig(config());lobbyStatus.setText("서버 대기 · "+localAddresses()+" : "+peer.boundPort());}catch(Exception e){error(e);}}}.execute();}
 private void connect(){disconnect();long generation=connectionGeneration;String ip=address.getText().trim();int chosen=((Number)port.getValue()).intValue();lobbyStatus.setText("접속 중: "+ip);new SwingWorker<PeerSession,Void>(){protected PeerSession doInBackground()throws Exception{return PeerSession.connect(ip,chosen);}protected void done(){try{PeerSession connected=get();if(disposed||generation!=connectionGeneration){connected.close();return;}peer=connected;options.recentIp(ip);options.save();}catch(Exception e){error(e);}}}.execute();}
 private String localAddresses(){try{List<String> ips=new ArrayList<>();for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))if(n.isUp()&&!n.isLoopback())for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address)ips.add(a.getHostAddress());return ips.isEmpty()?"127.0.0.1 (동일 PC 테스트)":String.join(" / ",ips);}catch(Exception e){return "IP 확인 불가";}}
 private void refresh(){if(disposed)return;if(peer!=null){var v=peer.view();boolean host=v.role()==PeerSession.Role.HOST;configure.setEnabled(host&&(v.state()==PeerSession.State.LOBBY||v.state()==PeerSession.State.LISTENING||v.state()==PeerSession.State.RESULT));ready.setEnabled(v.state()==PeerSession.State.LOBBY||v.state()==PeerSession.State.RESULT);String extra=v.state()==PeerSession.State.LISTENING?" · "+localAddresses()+" : "+peer.boundPort():"";lobbyStatus.setText(v.status()+extra+" · 나 "+(v.localReady()?"준비":"대기")+" / 상대 "+(v.remoteReady()?"준비":"대기"));if(v.state()==PeerSession.State.DISCONNECTED){held.clear();cards.show(content,"lobby");disconnect();lobbyStatus.setText(v.status()+" · 서버/클라이언트를 다시 선택하세요.");return;}MatchSnapshot s=peer.snapshot();if(s!=null){latest=s;if(v.state()==PeerSession.State.PLAYING)cards.show(content,"game");}}else if(runtime!=null)latest=runtime.snapshot();if(latest!=null){board.snapshot(latest);rematch.setEnabled(!latest.result().isEmpty());gameStatus.setText(peer!=null?"내 키: PLAYER 1 · P 정지 · ESC 나가기":solo?"PLAYER 1 키 · P 정지 · ESC 메뉴":"P1 A D S W F  /  P2 방향키 Space · P 정지");if(solo&&peer==null&&!latest.result().isEmpty()&&!soloResultHandled){soloResultHandled=true;MatchSnapshot completed=latest;stopGame();dispose();new GameOverScreen(completed.first().score(),completed.config().difficulty().name(),completed.config().mode()==MatchMode.ITEM?"ITEM":"NORMAL").setVisible(true);return;}}long now=System.nanoTime();for(var entry:new ArrayList<>(held.entrySet()))if(now-entry.getValue()>120_000_000L){dispatch(entry.getKey());held.put(entry.getKey(),now);}}
 private void installInputs(){
  JComponent root=getRootPane();pressed.clear();held.clear();root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).clear();root.getActionMap().clear();
  Set<Integer> keys=new HashSet<>(List.of(KeyEvent.VK_P,KeyEvent.VK_ESCAPE));
  for(int p=1;p<=2;p++)for(GameAction a:LocalOptions.ACTIONS)keys.add(options.key(p,a));
  for(int key:keys){final int code=key;String press="phase3.press."+key,release="phase3.release."+key;
   root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,0,false),press);
   root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,0,true),release);
   root.getActionMap().put(press,new AbstractAction(){public void actionPerformed(ActionEvent e){
    if(!pressed.add(code))return;
    if(code==KeyEvent.VK_P){pause();return;}if(code==KeyEvent.VK_ESCAPE){leave();return;}
    if(runtime==null&&(peer==null||peer.view().state()!=PeerSession.State.PLAYING))return;
    dispatch(code);if(isRepeating(code))held.put(code,System.nanoTime()+100_000_000L);
   }});
   root.getActionMap().put(release,new AbstractAction(){public void actionPerformed(ActionEvent e){held.remove(code);pressed.remove(code);}});
  }
 }
 private boolean isRepeating(int key){for(int p=1;p<=2;p++)for(GameAction a:List.of(GameAction.LEFT,GameAction.RIGHT,GameAction.SOFT_DROP))if(options.key(p,a)==key)return true;return false;}
 private void dispatch(int key){if(latest==null||latest.paused()||!latest.result().isEmpty())return;for(int p=1;p<=(peer!=null||solo?1:2);p++)for(GameAction a:LocalOptions.ACTIONS)if(options.key(p,a)==key){if(peer!=null)peer.command(a);else if(runtime!=null)runtime.command(p,a);}}
 private void pause(){held.clear();if(peer!=null&&peer.view().state()==PeerSession.State.PLAYING)peer.togglePause();else if(runtime!=null)runtime.togglePause();}
 private void leave(){if(runtime==null&&peer==null)return;boolean wasPaused=latest!=null&&latest.paused();if(!wasPaused)pause();if(JOptionPane.showConfirmDialog(this,"현재 경기를 나갈까요?","경기 나가기",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)toMenu();else if(!wasPaused)pause();}
 private void controls(){held.clear();JPanel p=new JPanel(new GridLayout(0,3,6,6));p.add(new JLabel("조작"));p.add(new JLabel("PLAYER 1"));p.add(new JLabel("PLAYER 2"));for(GameAction a:LocalOptions.ACTIONS){p.add(new JLabel(a.name()));for(int n=1;n<=2;n++){final int player=n;JButton key=new JButton(KeyEvent.getKeyText(options.key(n,a)));key.addActionListener(e->{key.setText("새 키를 누르세요");key.addKeyListener(new KeyAdapter(){@Override public void keyPressed(KeyEvent event){try{options.bind(player,a,event.getKeyCode());options.save();}catch(Exception ex){error(ex);}key.setText(KeyEvent.getKeyText(options.key(player,a)));key.removeKeyListener(this);event.consume();}});});p.add(key);}}JOptionPane.showMessageDialog(this,p,"조작 키 · P/ESC/Enter 예약 · 중복 금지",JOptionPane.PLAIN_MESSAGE);installInputs();}
 private void safe(Runnable action){try{if(peer!=null)action.run();}catch(Exception e){error(e);}}
 private void error(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();lobbyStatus.setText("오류: "+cause.getMessage());JOptionPane.showMessageDialog(this,cause.getMessage(),"TFSE",JOptionPane.ERROR_MESSAGE);}
 private void disconnect(){connectionGeneration++;if(peer!=null){peer.close();peer=null;}}
 private void stopGame(){held.clear();if(runtime!=null){runtime.close();runtime=null;}latest=null;}
 private void toMenu(){stopGame();disconnect();solo=false;cards.show(content,"menu");}
 @Override public void dispose(){if(disposed)return;disposed=true;if(frameTimer!=null)frameTimer.stop();stopGame();disconnect();super.dispose();}
}
