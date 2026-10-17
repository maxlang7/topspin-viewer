import java.awt.Point;

/** Relative toolbar movement, including the target's anchor and wheel behavior. */
public final class PointerGesture {
    public enum Kind {VERTICAL_SHIFT,HORIZONTAL_SHIFT,VERTICAL_SCALE,HORIZONTAL_SCALE;
        public boolean horizontal(){return this==HORIZONTAL_SHIFT || this==HORIZONTAL_SCALE;}
        public boolean scale(){return this==VERTICAL_SCALE || this==HORIZONTAL_SCALE;}
    }
    public record Update(Double amount,Point warpPoint) {}
    private Point anchor,lastPoint;
    private Double lastAmount;
    private final Kind kind;
    public PointerGesture(){this(Kind.VERTICAL_SHIFT);}
    public PointerGesture(Kind kind){this.kind=kind;}
    public void reset(){anchor=lastPoint=null;lastAmount=null;}
    public Point anchor(){return anchor==null?null:new Point(anchor);}
    public Update drag(int x,int y,boolean warpAvailable) {
        Point point=new Point(x,y);
        if(point.equals(anchor))return new Update(null,null);
        Double amount=movement(point);
        lastAmount=amount;Point warp=null;
        if(anchor==null)anchor=new Point(point);
        else if(warpAvailable){lastPoint=new Point(anchor);warp=new Point(anchor);}
        else lastPoint=new Point(point);
        return new Update(amount,warp);
    }
    private Double movement(Point point) {
        if(lastPoint==null)return null;
        int current=kind.horizontal()?point.x:point.y,previous=kind.horizontal()?lastPoint.x:lastPoint.y;
        if(current==previous)return lastAmount;
        double delta=(double)(current-previous);
        return kind.scale()?Math.pow(1.05,-delta/5.0):delta;
    }
    public Double wheel(int rotation){return wheel(rotation,0,0);}
    public Double wheel(int rotation,int x,int y) {
        if(kind==Kind.VERTICAL_SCALE)return null;
        lastAmount=kind==Kind.VERTICAL_SHIFT?Double.valueOf(4.0*rotation):movement(new Point(x,y));return lastAmount;
    }
}
