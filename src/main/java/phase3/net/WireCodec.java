package phase3.net;

import difficulty.Difficulty;
import phase3.core.*;
import java.io.*;
import java.util.*;

/** Versioned, length-delimited binary data only: never Java serialization. */
final class WireCodec {
    static final int VERSION=1,MAX_FRAME=32768;
    static final int STATE=1,READY=2,COMMAND=3,PAUSE=4,SNAPSHOT=5,HEARTBEAT=6;
    record Message(int type,long sequence,long matchId,byte[] payload) {}
    interface Encoder {void write(DataOutputStream out) throws IOException;}
    static byte[] payload(Encoder encoder) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){encoder.write(out);}return bytes.toByteArray();
    }
    static void write(DataOutputStream out,Message message)throws IOException {
        byte[] body=payload(data -> {data.writeByte(VERSION);data.writeByte(message.type());
            data.writeLong(message.sequence());data.writeLong(message.matchId());data.write(message.payload());});
        if(body.length>MAX_FRAME)throw new IOException("Frame too large");out.writeInt(body.length);out.write(body);out.flush();
    }
    static Message read(DataInputStream in)throws IOException {
        int length=in.readInt();if(length<18||length>MAX_FRAME)throw new IOException("Invalid frame length");
        byte[] body=new byte[length];in.readFully(body);DataInputStream data=input(body);
        if(data.readUnsignedByte()!=VERSION)throw new IOException("Unsupported protocol version");
        int type=data.readUnsignedByte();if(type<STATE||type>HEARTBEAT)throw new IOException("Invalid message type");
        long sequence=data.readLong(),id=data.readLong();
        if(sequence<1||id<0)throw new IOException("Invalid sequence or match ID");
        return new Message(type,sequence,id,data.readAllBytes());
    }
    static DataInputStream input(byte[] body){return new DataInputStream(new ByteArrayInputStream(body));}
    static void end(DataInputStream in)throws IOException {if(in.available()!=0)throw new IOException("Trailing data");}
    static int range(int value,int min,int max)throws IOException {
        if(value<min||value>max)throw new IOException("Value out of range");return value;
    }
    static void config(DataOutputStream out,MatchConfig c)throws IOException {
        out.writeByte(c.mode().ordinal());out.writeByte(c.difficulty().ordinal());out.writeLong(c.timeLimitMillis());out.writeLong(c.seed());
    }
    static MatchConfig config(DataInputStream in)throws IOException {
        MatchMode mode=MatchMode.values()[range(in.readUnsignedByte(),0,MatchMode.values().length-1)];
        Difficulty difficulty=Difficulty.values()[range(in.readUnsignedByte(),0,Difficulty.values().length-1)];
        try{return new MatchConfig(mode,difficulty,in.readLong(),in.readLong());}
        catch(IllegalArgumentException e){throw new IOException("Invalid configuration",e);}
    }
    static void snapshot(DataOutputStream out,MatchSnapshot s)throws IOException {
        out.writeLong(s.matchId());config(out,s.config());player(out,s.first());player(out,s.second());
        out.writeLong(s.remainingMillis());out.writeBoolean(s.paused());out.writeUTF(s.result());out.writeLong(s.tick());
    }
    static MatchSnapshot snapshot(DataInputStream in)throws IOException {
        long id=in.readLong();if(id<1)throw new IOException("Invalid match ID");
        MatchConfig c=config(in);PlayerSnapshot a=player(in),b=player(in);
        long remaining=in.readLong();if(remaining<0||remaining>c.timeLimitMillis())throw new IOException("Invalid time");
        boolean paused=in.readBoolean();String result=in.readUTF();
        if(!List.of("","P1","P2","DRAW").contains(result))throw new IOException("Invalid result");
        long tick=in.readLong();if(tick<0)throw new IOException("Invalid tick");
        return new MatchSnapshot(id,c,a,b,remaining,paused,result,tick);
    }
    private static void player(DataOutputStream out,PlayerSnapshot p)throws IOException {
        matrix(out,p.board());piece(out,p.current());piece(out,p.next());out.writeInt(p.score());out.writeInt(p.level());
        out.writeInt(p.lines());out.writeInt(p.dropMillis());out.writeBoolean(p.gameOver());
        out.writeByte(p.clearingRows().size());for(int row:p.clearingRows())out.writeByte(row);
        out.writeByte(p.pendingRows().size());for(int[] row:p.pendingRows())for(int cell:row)out.writeInt(cell);
    }
    private static PlayerSnapshot player(DataInputStream in)throws IOException {
        int[][] board=matrix(in,23,23,10,10);PieceSnapshot current=piece(in),next=piece(in);
        if(next==null)throw new IOException("Missing next piece");
        int score=range(in.readInt(),0,Integer.MAX_VALUE),level=range(in.readInt(),1,1000000);
        int lines=range(in.readInt(),0,Integer.MAX_VALUE),drop=range(in.readInt(),1,1000000);boolean over=in.readBoolean();
        int count=range(in.readUnsignedByte(),0,23);List<Integer> clearing=new ArrayList<>();
        for(int i=0;i<count;i++){int row=range(in.readUnsignedByte(),0,22);if(clearing.contains(row))throw new IOException("Duplicate row");clearing.add(row);}
        int pending=range(in.readUnsignedByte(),0,10);List<int[]> rows=new ArrayList<>();
        for(int i=0;i<pending;i++){int[] row=new int[10];for(int j=0;j<10;j++){row[j]=in.readInt();if(row[j]!=0&&row[j]!=11)throw new IOException("Invalid pending cell");}rows.add(row);}
        return new PlayerSnapshot(board,current,next,score,level,lines,drop,over,clearing,rows);
    }
    private static void piece(DataOutputStream out,PieceSnapshot p)throws IOException {
        out.writeBoolean(p!=null);if(p!=null){matrix(out,p.cells());out.writeInt(p.x());out.writeInt(p.y());}
    }
    private static PieceSnapshot piece(DataInputStream in)throws IOException {
        if(!in.readBoolean())return null;int[][] cells=matrix(in,1,8,1,8);
        return new PieceSnapshot(cells,range(in.readInt(),-8,10),range(in.readInt(),-8,23));
    }
    private static void matrix(DataOutputStream out,int[][] cells)throws IOException {
        out.writeByte(cells.length);out.writeByte(cells[0].length);for(int[] row:cells)for(int cell:row)out.writeInt(cell);
    }
    private static int[][] matrix(DataInputStream in,int minRows,int maxRows,int minCols,int maxCols)throws IOException {
        int rows=range(in.readUnsignedByte(),minRows,maxRows),cols=range(in.readUnsignedByte(),minCols,maxCols);
        int[][] result=new int[rows][cols];
        for(int[] row:result)for(int j=0;j<cols;j++){int cell=in.readInt();if(!validCell(cell))throw new IOException("Invalid cell");row[j]=cell;}return result;
    }
    private static boolean validCell(int cell) {
        if(cell>=0&&cell<=11)return true;if(cell==-1)return true;
        long packed=-(long)cell;int symbol=(int)((packed>>>8)&0xffff),type=(int)(packed&255);
        return (packed&0xff000000L)==0x1000000L&&type>=1&&type<=10&&"LWBSP".indexOf(symbol)>=0;
    }
}
