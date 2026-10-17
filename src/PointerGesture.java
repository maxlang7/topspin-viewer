import java.awt.Point;

/** Relative toolbar movement, including the target's anchor and wheel behavior. */
public final class PointerGesture {
    public record Update(Double amount,Point warpPoint) {}
    private Point anchor,lastPoint;
    private Double lastAmount;
    public void reset(){anchor=lastPoint=null;lastAmount=null;}
    public Point anchor(){return anchor==null?null:new Point(anchor);}
    public Update drag(int x,int y,boolean warpAvailable) {
        Point point=new Point(x,y);
        if(point.equals(anchor))return new Update(null,null);
        Double amount=null;
        if(lastPoint!=null){if(y!=lastPoint.y)amount=(double)(y-lastPoint.y);else amount=lastAmount;}
        lastAmount=amount;Point warp=null;
        if(anchor==null)anchor=new Point(point);
        else if(warpAvailable){lastPoint=new Point(anchor);warp=new Point(anchor);}
        else lastPoint=new Point(point);
        return new Update(amount,warp);
    }
    public double wheel(int rotation){lastAmount=4.0*rotation;return lastAmount;}
}
