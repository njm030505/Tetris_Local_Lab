package phase3.core;

import blocks.core.Block;
import board.Board;
import game.GameStateManager;
import item.types.*;
import score.DropType;
import score.ScoreManager;
import ui.ItemAppearanceResolver;
import java.util.*;

/** Thread-free, clock-free per-player rules. MatchSession serializes ownership and turn boundaries. */
final class PlayerEngine {
    static final long CLEAR_ANIMATION_MILLIS=300;
    static final int MAX_PENDING_ROWS=10;
    private final MatchConfig config;
    private final PieceSource source;
    private final Board board;
    private final GameStateManager state=new GameStateManager();
    private final ScoreManager score;
    private final List<int[]> pending=new ArrayList<>();
    private final List<int[]> attacks=new ArrayList<>();
    private Block current, next;
    private List<Integer> clearingRows=List.of();
    private List<int[]> capturedRows=List.of();
    private long gravityElapsed, clearRemaining;
    private boolean bombPending, turnBoundary, nextIsItem;

    PlayerEngine(MatchConfig config, PieceSource source, Board board) {
        this.config=Objects.requireNonNull(config,"config");
        this.source=Objects.requireNonNull(source,"source");
        this.board=Objects.requireNonNull(board,"board");
        score=new ScoreManager(config.difficulty());state.setDifficulty(config.difficulty());
        current=Objects.requireNonNull(source.next(false));next=Objects.requireNonNull(source.next(false));
        positionCurrent();
    }
    private void positionCurrent() {
        current.setX(3);current.setY(0);gravityElapsed=0;
        if(!board.isValidPosition(current.getShape(),current.getX(),current.getY()))state.setGameOver(true);
    }
    void apply(GameAction action) {
        Objects.requireNonNull(action,"action");
        if(state.isGameOver()||turnBoundary||clearRemaining>0)return;
        switch(action) {
            case LEFT -> moveHorizontal(-1);
            case RIGHT -> moveHorizontal(1);
            case ROTATE -> rotate();
            case SOFT_DROP -> {if(stepDown())score.addDropScore(1,DropType.SOFT,state.getCurrentLevel());}
            case HARD_DROP -> {
                int distance=0;
                while(!turnBoundary && clearRemaining==0 && !state.isGameOver()) {
                    if(stepDown())distance++;
                }
                score.addDropScore(distance,DropType.HARD,state.getCurrentLevel());
            }
            case WAIT -> { }
        }
    }
    private void moveHorizontal(int direction) {
        if(board.isValidPosition(current.getShape(),current.getX()+direction,current.getY())) {
            if(direction<0)current.moveLeft();else current.moveRight();
        }
    }
    private void rotate() {
        int[][] rotated=current.getRotate();
        for(int kick:new int[]{0,-1,1,-2,2}) {
            if(board.isValidPosition(rotated,current.getX()+kick,current.getY())) {
                current.rotate();current.setX(current.getX()+kick);return;
            }
        }
    }
    long millisUntilEvent() {
        if(state.isGameOver()||turnBoundary)return Long.MAX_VALUE;
        return clearRemaining>0?clearRemaining:Math.max(1,state.getDropSpeed()-gravityElapsed);
    }
    void advance(long millis) {
        if(millis<0)throw new IllegalArgumentException("Negative elapsed time");
        while(millis>0&&!state.isGameOver()&&!turnBoundary) {
            long step=Math.min(millis,millisUntilEvent());millis-=step;
            if(clearRemaining>0) {
                clearRemaining-=step;
                if(clearRemaining==0)completeClear();
            } else {
                gravityElapsed+=step;
                if(gravityElapsed>=state.getDropSpeed()) {
                    gravityElapsed-=state.getDropSpeed();
                    if(stepDown())score.addDropScore(1,DropType.AUTO,state.getCurrentLevel());
                }
            }
        }
    }
    private boolean stepDown() {
        int nextY=current.getY()+1;
        boolean canMove=board.isValidPosition(current.getShape(),current.getX(),nextY);
        if(!canMove&&current instanceof WeightItem weight
                &&board.hasBlockCollision(current.getShape(),current.getX(),nextY)) {
            weight.markLanded();weight.clearBlocksBelow(board);
            canMove=board.isValidPosition(current.getShape(),current.getX(),nextY);
        }
        if(canMove){current.moveDown();return true;}
        if(current instanceof BombItem) {
            bombPending=true;clearRemaining=CLEAR_ANIMATION_MILLIS;return false;
        }
        board.addBlock(current.getX(),current.getY(),encoded(current));
        if(current instanceof WeightItem weight)weight.markLanded();
        TreeSet<Integer> rows=new TreeSet<>(board.findFullRows());
        if(current instanceof LineClearItem line)rows.add(line.getLRow());
        if(rows.isEmpty())finishLock(0);
        else {
            clearingRows=List.copyOf(rows);
            capturedRows=captureAttackRows(clearingRows,true);
            clearRemaining=CLEAR_ANIMATION_MILLIS;
        }
        return false;
    }
    private void completeClear() {
        if(bombPending) {
            bombPending=false;
            ((BombItem)current).explode(board);
            // Area destruction is not a row clear. Only naturally full rows remaining afterward count.
            clearingRows=List.copyOf(board.findFullRows());
            capturedRows=captureAttackRows(clearingRows,false);
        }
        int removed=board.clearRows(clearingRows);
        if(removed>=2)attacks.addAll(capturedRows);
        clearingRows=List.of();capturedRows=List.of();finishLock(removed);
    }
    private List<int[]> captureAttackRows(List<Integer> rows, boolean removeLockedPiece) {
        if(rows.size()<2)return List.of();
        List<int[]> result=new ArrayList<>();
        int[][] cells=board.getBoard(),shape=current.getShape();
        for(int row:rows) {
            int[] attack=new int[board.getWidth()];
            for(int col=0;col<attack.length;col++) {
                int localRow=row-current.getY(),localCol=col-current.getX();
                boolean locked=removeLockedPiece&&localRow>=0&&localRow<shape.length
                        &&localCol>=0&&localCol<shape[localRow].length&&shape[localRow][localCol]!=0;
                attack[col]=cells[row][col]!=0&&!locked?Board.GARBAGE:0;
            }
            result.add(attack);
        }
        return result;
    }
    private void finishLock(int removed) {
        int bonus=removed>0?board.getLastClearedBonusLines():0;
        int slow=removed>0?board.getLastClearedSlowLines():0;
        int previous=state.getTotalLinesCleared();
        state.updateLevelUp(removed);
        nextIsItem=config.mode()==MatchMode.ITEM&&state.shouldSpawnItem(previous);
        // A legal piece clears at most four rows. Large legacy/imported boards remain score-safe.
        int left=removed;
        if(left==0)score.addLineClearScore(0,state.getCurrentLevel(),board.isPerfectClear());
        while(left>0){int batch=Math.min(4,left);score.addLineClearScore(batch,state.getCurrentLevel(),board.isPerfectClear());left-=batch;}
        score.addBonusScore(bonus);state.applySlowItem(slow);
        turnBoundary=true;
    }
    List<int[]> drainAttacks() {
        List<int[]> result=SnapshotCopies.rows(attacks);attacks.clear();return result;
    }
    void enqueue(List<int[]> rows) {
        Objects.requireNonNull(rows,"rows");
        for(int[] row:rows) {
            if(row.length!=board.getWidth())throw new IllegalArgumentException("Garbage row width must be10");
            if(pending.size()==MAX_PENDING_ROWS)break;
            int[] garbage=row.clone();for(int i=0;i<garbage.length;i++)if(garbage[i]!=0)garbage[i]=Board.GARBAGE;
            pending.add(garbage);
        }
    }
    boolean atTurnBoundary(){return turnBoundary;}
    void completeTurn() {
        if(!turnBoundary||state.isGameOver())return;
        turnBoundary=false;
        applyPending();
        if(board.hasBlocksInHiddenRows())state.setGameOver(true);
        if(state.isGameOver())return;
        current=next;next=Objects.requireNonNull(source.next(nextIsItem));positionCurrent();
    }
    private void applyPending() {
        if(pending.isEmpty())return;
        int[][] cells=board.getBoard();int count=pending.size();
        for(int row=0;row<count;row++)for(int value:cells[row])if(value!=0)state.setGameOver(true);
        for(int row=0;row<cells.length-count;row++)cells[row]=cells[row+count].clone();
        for(int i=0;i<count;i++)cells[cells.length-count+i]=pending.get(i).clone();
        pending.clear();
    }
    private static int[][] encoded(Block block) {
        int[][] result=new int[block.getHeight()][block.getWidth()];
        for(int row=0;row<result.length;row++)for(int col=0;col<result[row].length;col++)
            result[row][col]=ItemAppearanceResolver.toBoardCell(block,row,col);
        return result;
    }
    PlayerSnapshot snapshot() {
        PieceSnapshot active=turnBoundary?null:new PieceSnapshot(encoded(current),current.getX(),current.getY());
        return new PlayerSnapshot(board.getBoard(),active,new PieceSnapshot(encoded(next),3,0),score.getScore(),
                state.getCurrentLevel(),state.getTotalLinesCleared(),state.getDropSpeed(),state.isGameOver(),clearingRows,pending);
    }
}
