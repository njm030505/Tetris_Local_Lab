package phase3.core;

import blocks.core.Block;
import blocks.tetromino.*;
import difficulty.Difficulty;
import item.types.*;
import java.util.Objects;
import java.util.Random;

/** Supplies fresh pieces. The seeded source owns every random choice, including attached markers. */
@FunctionalInterface
public interface PieceSource {
    Block next(boolean item);
    static PieceSource seeded(Difficulty difficulty, long seed) {
        Objects.requireNonNull(difficulty,"difficulty");
        return new WeightedPieceSource(difficulty, seed);
    }
}

final class WeightedPieceSource implements PieceSource {
    private final Difficulty difficulty;
    private final Random random;
    WeightedPieceSource(Difficulty difficulty, long seed) {
        this.difficulty=difficulty;random=new Random(seed);
    }
    @Override public Block next(boolean item) {
        if (!item) return normal();
        int kind=random.nextInt(5);
        if (kind==1) return new WeightItem();
        if (kind==2) return new BombItem();
        Block source=normal();
        int occupied=0;
        for (int[] row:source.getShape()) for (int cell:row) if(cell!=0)occupied++;
        int selected=random.nextInt(occupied);
        for(int row=0;row<source.getHeight();row++)for(int col=0;col<source.getWidth();col++) {
            if(source.getShape()[row][col]!=0 && selected--==0) return switch(kind) {
                case 0 -> new LineClearItem(source,row,col);
                case 3 -> new BonusItem(source,row,col);
                default -> new SlowItem(source,row,col);
            };
        }
        throw new IllegalStateException("Empty source piece");
    }
    private Block normal() {
        // Same rejection sampling and weights as the upstream BlockFactory.
        int iWeight=difficulty.getIBlockWeight(), maxWeight=Math.max(iWeight,10);
        while(true) {
            int candidate=random.nextInt(7);
            if(random.nextDouble() >= (candidate==0?iWeight:10)/(double)maxWeight)continue;
            return switch(candidate) {
                case 0 -> new IBlock(); case 1 -> new OBlock(); case 2 -> new TBlock();
                case 3 -> new SBlock(); case 4 -> new ZBlock(); case 5 -> new JBlock();
                default -> new LBlock();
            };
        }
    }
}
