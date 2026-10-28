package phase3.core;

import blocks.core.Block;
import blocks.tetromino.*;
import board.Board;
import difficulty.Difficulty;
import item.types.*;
import org.junit.jupiter.api.Test;
import ui.ItemAppearanceResolver;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    final MatchConfig normal = new MatchConfig(MatchMode.NORMAL, Difficulty.NORMAL, 180_000, 42);
    PieceSource onlyO = item -> new OBlock();
    PlayerEngine engine(Board board, Block first) {
        ArrayDeque<Block> pieces = new ArrayDeque<>(); pieces.add(first);
        return new PlayerEngine(normal, item -> pieces.isEmpty() ? new OBlock() : pieces.removeFirst(), board);
    }
    void fillExcept(Board b, int row, int... holes) {
        Arrays.fill(b.getBoard()[row], 1);
        for (int h:holes) b.getBoard()[row][h]=0;
    }
    @Test void immutableNestedSnapshotsAndEncodedMarkers() {
        int[][] input={{1,2}}; PieceSnapshot p=new PieceSnapshot(input,3,4); input[0][0]=9;
        assertEquals(1,p.cells()[0][0]); p.cells()[0][0]=8; assertEquals(1,p.cells()[0][0]);
        int[] row={1,0}; PlayerSnapshot s=new PlayerSnapshot(input,p,p,0,1,0,1000,false,List.of(2),List.of(row));
        row[0]=7; s.pendingRows().get(0)[0]=4; s.board()[0][0]=5;
        assertEquals(1,s.pendingRows().get(0)[0]); assertEquals(9,s.board()[0][0]);
        assertThrows(UnsupportedOperationException.class,()->s.clearingRows().add(1));
        PlayerEngine e=engine(new Board(),new BonusItem(new OBlock(),0,0));
        assertEquals('P',ItemAppearanceResolver.fromBoardCell(e.snapshot().current().cells()[0][0]).symbol());
    }
    @Test void configValidatesAndHasNormalDurationDefault() {
        assertThrows(IllegalArgumentException.class,()->new MatchConfig(MatchMode.TIMED,Difficulty.NORMAL,9999,1));
        assertThrows(IllegalArgumentException.class,()->new MatchConfig(MatchMode.TIMED,Difficulty.NORMAL,3600001,1));
        assertThrows(NullPointerException.class,()->new MatchConfig(null,Difficulty.NORMAL,10000,1));
        assertEquals(180000,new MatchConfig(MatchMode.TIMED,Difficulty.NORMAL,1).timeLimitMillis());
    }
    @Test void exactAttackRemovesLockedPieceCoordinatesAndWaitsForAnimation() {
        Board b=new Board(); fillExcept(b,21,3,4); fillExcept(b,22,3,4);
        PlayerEngine e=engine(b,new OBlock()); e.apply(GameAction.HARD_DROP);
        assertEquals(List.of(21,22), e.snapshot().clearingRows());
        assertTrue(e.drainAttacks().isEmpty()); e.advance(299); assertTrue(e.drainAttacks().isEmpty());
        e.advance(1); List<int[]> attacks=e.drainAttacks(); assertEquals(2,attacks.size());
        assertArrayEquals(new int[]{11,11,11,0,0,11,11,11,11,11},attacks.get(0));
        assertEquals(2,e.snapshot().lines()); assertTrue(e.atTurnBoundary());
        e.completeTurn(); assertFalse(e.atTurnBoundary());
    }
    @Test void queueKeepsOldTopTenAndAppliesOnlyBeforeSpawn() {
        PlayerEngine e=engine(new Board(),new OBlock()); List<int[]> incoming=new ArrayList<>();
        for(int i=0;i<12;i++){int[] r=new int[10];r[i%10]=1;incoming.add(r);}
        e.enqueue(incoming); incoming.get(0)[0]=0;
        assertEquals(10,e.snapshot().pendingRows().size()); assertEquals(0,e.snapshot().board()[22][0]);
        e.apply(GameAction.HARD_DROP); assertEquals(10,e.snapshot().pendingRows().size());
        e.completeTurn(); assertTrue(e.snapshot().pendingRows().isEmpty());
        assertEquals(Board.GARBAGE,e.snapshot().board()[13][0]);
        assertEquals(Board.GARBAGE,e.snapshot().board()[22][9]);
    }
    @Test void shiftedOutAndHiddenRowsTopOutAndSpawnCollision() {
        Board b=new Board(); b.getBoard()[0][0]=1; PlayerEngine e=engine(b,new OBlock());
        e.enqueue(List.of(new int[10])); e.apply(GameAction.HARD_DROP);e.completeTurn();assertTrue(e.snapshot().gameOver());
        Board hidden=new Board();hidden.getBoard()[3][0]=1;PlayerEngine h=engine(hidden,new OBlock());
        h.enqueue(List.of(new int[10]));h.apply(GameAction.HARD_DROP);h.completeTurn();assertTrue(h.snapshot().gameOver());
        Board collision=new Board();collision.getBoard()[0][3]=1;assertTrue(engine(collision,new OBlock()).snapshot().gameOver());
    }
    @Test void lineItemForcedHolesAttackSortedAndCountsOnce() {
        Board b=new Board(); fillExcept(b,22,3,4); b.getBoard()[21][0]=1;
        PlayerEngine e=engine(b,new LineClearItem(new OBlock(),0,0));
        e.apply(GameAction.HARD_DROP);e.advance(300);List<int[]> a=e.drainAttacks();
        assertEquals(2,a.size()); assertArrayEquals(new int[]{11,0,0,0,0,0,0,0,0,0},a.get(0));
        assertEquals(2,e.snapshot().lines());
    }
    @Test void bonusAndSlowMarkersActivateOnlyOnRemovedRowsAndGarbageHasNoMarker() {
        Board b=new Board();fillExcept(b,22,3,4);
        b.getBoard()[22][0]=ItemAppearanceResolver.toBoardCell(new BonusItem(new OBlock(),0,0),0,0);
        b.getBoard()[22][1]=ItemAppearanceResolver.toBoardCell(new SlowItem(new OBlock(),0,0),0,0);
        b.getBoard()[22][2]=Board.GARBAGE;
        PlayerEngine e=engine(b,new OBlock());e.apply(GameAction.HARD_DROP);e.advance(300);
        assertEquals(1,e.snapshot().lines());assertTrue(e.snapshot().score()>=1100);assertEquals(1000,e.snapshot().dropMillis());
        assertTrue(e.drainAttacks().isEmpty());
    }
    @Test void bombAndWeightAreaEffectsDoNotScoreLinesOrAttack() {
        Board b=new Board();b.getBoard()[22][3]=1;PlayerEngine bomb=engine(b,new BombItem());
        bomb.apply(GameAction.HARD_DROP);bomb.advance(300);
        assertEquals(0,bomb.snapshot().lines());assertTrue(bomb.drainAttacks().isEmpty());assertTrue(b.isPerfectClear());
        Board w=new Board();for(int r=18;r<23;r++)for(int c=3;c<7;c++)w.getBoard()[r][c]=1;
        PlayerEngine weight=engine(w,new WeightItem()); weight.apply(GameAction.HARD_DROP);
        assertEquals(0,weight.snapshot().lines());assertTrue(weight.drainAttacks().isEmpty());
        assertTrue(weight.atTurnBoundary());assertEquals('W',ItemAppearanceResolver.fromBoardCell(weight.snapshot().board()[22][3]).symbol());
    }
    @Test void deterministicSourcesIncludeItemAttachmentAndDifficultyWeights() {
        PieceSource a=PieceSource.seeded(Difficulty.HARD,93), b=PieceSource.seeded(Difficulty.HARD,93);
        Set<Class<?>> items=new HashSet<>();int iCount=0;
        for(int i=0;i<20000;i++) {
            boolean item=i%2==0; Block x=a.next(item), y=b.next(item);
            assertEquals(x.getClass(),y.getClass());assertTrue(Arrays.deepEquals(x.getShape(),y.getShape()));
            if(item)items.add(x.getClass());else if(x instanceof IBlock)iCount++;
        }
        assertEquals(5,items.size());assertTrue(iCount>1000&&iCount<1400,"hard I weight8 versus others10");
    }
    @Test void perfectClearChecksFloatingCells() {
        Board b=new Board();b.getBoard()[10][1]=1;assertFalse(b.isPerfectClear());
    }
    @Test void timedPauseFreezesTimerAnimationAndResultIsTerminal() {
        MatchConfig timed=new MatchConfig(MatchMode.TIMED,Difficulty.NORMAL,10000,1);
        MatchSession s=new MatchSession(timed,5,false); int y=s.snapshot().first().current().y();
        s.togglePause();s.advance(1000);s.apply(1,GameAction.HARD_DROP);
        assertEquals(10000,s.snapshot().remainingMillis());assertEquals(y,s.snapshot().first().current().y());
        s.togglePause();s.advance(10000);assertEquals("DRAW",s.snapshot().result());
        long tick=s.snapshot().tick();s.apply(1,GameAction.HARD_DROP);s.advance(100);s.togglePause();
        assertEquals("DRAW",s.snapshot().result());assertEquals(tick,s.snapshot().tick());
    }
    @Test void sameTickLossIsDrawAndSoloHasNoSecondEngine() {
        Board a=new Board(),b=new Board();a.getBoard()[0][0]=1;b.getBoard()[0][0]=1;
        PlayerEngine p=engine(a,new OBlock()), q=engine(b,new OBlock());
        MatchSession s=new MatchSession(normal,7,false,p,q);s.advance(22000);
        assertEquals("DRAW",s.snapshot().result());
        MatchSession solo=new MatchSession(normal,9,true);assertNull(solo.snapshot().second());
        assertThrows(IllegalArgumentException.class,()->solo.apply(2,GameAction.LEFT));
        assertThrows(IllegalArgumentException.class,()->solo.advance(-1));
    }
    @Test void noTimerThreadsAndSyntheticClockHandlesMultipleDrops() {
        MatchSession s=new MatchSession(normal,8,false);s.advance(2500);
        assertEquals(2,s.snapshot().first().current().y());assertEquals(2,s.snapshot().first().score());
        assertEquals("",s.snapshot().result());
    }
}

