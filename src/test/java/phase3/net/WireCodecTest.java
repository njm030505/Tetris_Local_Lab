package phase3.net;

import difficulty.Difficulty;
import phase3.core.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WireCodecTest {
    private MatchSnapshot sample() {
        int[][] board=new int[23][10];board[22][0]=11;board[21][1]=-(0x1000000|('P'<<8)|3);
        PieceSnapshot current=new PieceSnapshot(new int[][]{{1,1,1,1}},3,2);
        PieceSnapshot next=new PieceSnapshot(new int[][]{{-(0x1000000|('W'<<8)|9)}},3,0);
        int[] row={11,0,11,0,11,0,11,0,11,0};
        PlayerSnapshot p=new PlayerSnapshot(board,current,next,220,2,10,450,false,List.of(21,22),List.of(row));
        return new MatchSnapshot(8,new MatchConfig(MatchMode.ITEM,Difficulty.HARD,180000,998),p,p,0,true,"",73);
    }
    @Test void boundedFrameAndDeepSnapshotRoundTrip()throws Exception {
        MatchSnapshot original=sample();
        byte[] payload=WireCodec.payload(out -> WireCodec.snapshot(out,original));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        WireCodec.write(new DataOutputStream(bytes),new WireCodec.Message(WireCodec.SNAPSHOT,3,8,payload));
        WireCodec.Message frame=WireCodec.read(WireCodec.input(bytes.toByteArray()));
        assertEquals(3,frame.sequence());assertEquals(8,frame.matchId());
        DataInputStream data=WireCodec.input(frame.payload());MatchSnapshot copy=WireCodec.snapshot(data);WireCodec.end(data);
        assertEquals(original.config(),copy.config());assertEquals(73,copy.tick());assertTrue(copy.paused());
        assertArrayEquals(original.first().board(),copy.first().board());
        assertArrayEquals(original.first().current().cells(),copy.first().current().cells());
        assertArrayEquals(original.first().next().cells(),copy.first().next().cells());
        assertArrayEquals(original.first().pendingRows().get(0),copy.first().pendingRows().get(0));
        int[][] cells=copy.first().board();cells[22][0]=0;assertEquals(11,copy.first().board()[22][0]);
        int[] row=copy.first().pendingRows().get(0);row[0]=0;assertEquals(11,copy.first().pendingRows().get(0)[0]);
    }
    @Test void rejectsMalformedFramesVersionsSequencesAndTruncation()throws Exception {
        for(int length:new int[]{-1,0,17,WireCodec.MAX_FRAME+1,Integer.MAX_VALUE}) {
            byte[] bytes=WireCodec.payload(out -> out.writeInt(length));
            assertThrows(IOException.class,() -> WireCodec.read(WireCodec.input(bytes)));
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        WireCodec.write(new DataOutputStream(bytes),new WireCodec.Message(WireCodec.HEARTBEAT,1,0,new byte[0]));
        byte[] good=bytes.toByteArray();byte[] version=good.clone();version[4]=99;
        assertThrows(IOException.class,() -> WireCodec.read(WireCodec.input(version)));
        byte[] sequence=good.clone();Arrays.fill(sequence,6,14,(byte)0);
        assertThrows(IOException.class,() -> WireCodec.read(WireCodec.input(sequence)));
        byte[] type=good.clone();type[5]=99;assertThrows(IOException.class,() -> WireCodec.read(WireCodec.input(type)));
        assertThrows(IOException.class,() -> WireCodec.read(WireCodec.input(Arrays.copyOf(good,good.length-1))));
        assertThrows(IOException.class,() -> WireCodec.end(WireCodec.input(new byte[]{0})));
    }
    @Test void rejectsWrongBoardDimensionsCellsAndInvalidConfiguration()throws Exception {
        byte[] good=WireCodec.payload(out -> WireCodec.snapshot(out,sample()));
        // Snapshot ID (8 bytes), config (18 bytes), then board dimension bytes.
        byte[] dims=good.clone();dims[26]=22;
        assertThrows(IOException.class,() -> WireCodec.snapshot(WireCodec.input(dims)));
        byte[] cells=good.clone();cells[28]=0x7f;
        assertThrows(IOException.class,() -> WireCodec.snapshot(WireCodec.input(cells)));
        byte[] mode=good.clone();mode[8]=99;
        assertThrows(IOException.class,() -> WireCodec.snapshot(WireCodec.input(mode)));
        byte[] duration=WireCodec.payload(out -> {out.writeByte(0);out.writeByte(0);out.writeLong(1);out.writeLong(1);});
        assertThrows(IOException.class,() -> WireCodec.config(WireCodec.input(duration)));
    }
}
