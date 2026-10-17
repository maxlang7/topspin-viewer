import java.awt.*;
import java.util.function.DoubleToIntFunction;

/** Tick and label rendering for a horizontal linear axis, without cursor overlays. */
public final class AxisPainter {
    public static void paint(Graphics2D graphics,Rectangle viewport,VisualNumerics.Mapper mapper,
            AxisTicks.Layout layout,Font font,Color color,int position,int offset) {
        paintMapped(graphics,viewport,value->mapper.screen(value,1),layout,font,color,position,offset);
    }
    public static void paintVertical(Graphics2D graphics,Rectangle viewport,double start,double end,
            double reference,double gain,Font font,Color color,int position,int offset) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.translate(viewport.x,viewport.y+viewport.height);g.rotate(-Math.PI/2);
        var mapper=VisualNumerics.vertical(end,start,0,viewport.height);
        var layout=AxisTicks.calculate(start*reference,end*reference,"rel",null,true,true);
        paintMapped(g,new Rectangle(0,0,viewport.height,viewport.width),
            value->mapper.screen(value/reference,gain),layout,font,color,(position&1)!=0?1:2,offset);
        g.dispose();
    }
    private static void paintMapped(Graphics2D graphics,Rectangle viewport,DoubleToIntFunction map,
            AxisTicks.Layout layout,Font font,Color color,int position,int offset) {
        Graphics2D g=(Graphics2D)graphics.create();g.setClip(viewport);
        g.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);g.setColor(color);FontMetrics metrics=g.getFontMetrics();int height=metrics.getHeight();
        int shortTick=(int)(.3*height),longTick=(int)(.5*height),shift,labelShift;
        if(position==2){shortTick=-shortTick;longTick=-longTick;labelShift=(int)(1.2*longTick);shift=-offset+viewport.height;}
        else {labelShift=(int)(1.2*longTick);shift=offset;}
        int line=viewport.y+shift,labelY=(position&3)==2?line+labelShift-height:line+labelShift;
        g.drawLine(viewport.x,line,viewport.x+viewport.width,line);
        Rectangle unitBounds=new Rectangle();
        if(layout.unitLabel()!=null) {
            unitBounds=metrics.getStringBounds(layout.unitLabel(),g).getBounds();
            unitBounds.setLocation(viewport.x+viewport.width-2-unitBounds.width,labelY-unitBounds.y);
            g.drawString(layout.unitLabel(),unitBounds.x,unitBounds.y);
        }
        for(var tick:layout.ticks()) {
            int x=map.applyAsInt(tick.value());
            g.drawLine(x,line,x,line+(tick.large()?longTick:shortTick));
        }
        Rectangle previous=null;
        for(var tick:layout.ticks()) {
            if(!tick.large())continue;
            int x=map.applyAsInt(tick.value());Rectangle bounds=metrics.getStringBounds(tick.label(),g).getBounds();int half=bounds.width/2;
            if(x-half<=viewport.x || x+half>=viewport.x+viewport.width)continue;
            bounds.setLocation(x-half,labelY-bounds.y);
            if(bounds.intersects(unitBounds) || previous!=null && bounds.intersects(previous))continue;
            g.drawString(tick.label(),x-half,bounds.y);previous=bounds;
        }
        g.dispose();
    }
    private AxisPainter(){}
}
