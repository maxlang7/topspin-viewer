import java.awt.*;
import java.util.function.DoubleToIntFunction;
import java.awt.geom.*;
import java.text.*;
import java.util.Locale;

/** Tick and label rendering for a horizontal linear axis, without cursor overlays. */
public final class AxisPainter {
    public static void paint(Graphics2D graphics,Rectangle viewport,VisualNumerics.Mapper mapper,
            AxisTicks.Layout layout,Font font,Color color,int position,int offset) {
        paint(graphics,viewport,mapper,layout,font,color,position,offset,null);
    }
    public static void paint(Graphics2D graphics,Rectangle viewport,VisualNumerics.Mapper mapper,
            AxisTicks.Layout layout,Font font,Color color,int position,int offset,Double cursor) {
        paintMapped(graphics,viewport,value->mapper.screen(value,1),layout,font,color,position,offset,cursor);
    }
    public static void paintVertical(Graphics2D graphics,Rectangle viewport,double start,double end,
            double reference,double gain,Font font,Color color,int position,int offset) {
        if(start==end)return;
        Graphics2D g=(Graphics2D)graphics.create();
        g.translate(viewport.x,viewport.y+viewport.height);g.rotate(-Math.PI/2);
        var mapper=VisualNumerics.vertical(end,start,0,viewport.height);
        var layout=AxisTicks.calculate(start*reference,end*reference,"rel",null,true,true);
        paintMapped(g,new Rectangle(0,0,viewport.height,viewport.width),
            value->mapper.screen(value/reference,gain),layout,font,color,(position&1)!=0?1:2,offset,null);
        g.dispose();
    }
    private static void paintMapped(Graphics2D graphics,Rectangle viewport,DoubleToIntFunction map,
            AxisTicks.Layout layout,Font font,Color color,int position,int offset,Double cursor) {
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
        Rectangle cursorBounds=null;
        if(cursor!=null) {
            int at=map.applyAsInt(cursor);double factor=Math.abs(shortTick/.3)/20.0;
            Path2D shape=new Path2D.Double();
            if(position==1){shape.moveTo(0,0);shape.lineTo(4,12);shape.lineTo(-4,12);}
            else {shape.moveTo(0,12);shape.lineTo(4,0);shape.lineTo(-4,0);}
            shape.closePath();shape.transform(AffineTransform.getScaleInstance(factor,factor));
            shape.transform(AffineTransform.getTranslateInstance(at,position==2?viewport.y+viewport.height-shape.getBounds().height:viewport.y));
            if(viewport.contains(shape.getBounds())) {
                g.setColor(Theme.color(new Color(0,141,235)));g.fill(shape);
                int precision=new DecimalFormat(layout.pattern(),DecimalFormatSymbols.getInstance(Locale.ROOT)).getMaximumFractionDigits()+2;
                DecimalFormat format=new DecimalFormat("0.00",DecimalFormatSymbols.getInstance(Locale.ROOT));format.setMaximumFractionDigits(precision);format.setMinimumFractionDigits(precision);
                String label=format.format(cursor/layout.multiplier());
                Rectangle bounds=metrics.getStringBounds(label,g).getBounds();bounds.grow(Math.abs(longTick),0);
                bounds.setLocation(at-bounds.width/2,labelY-bounds.y);
                int right=unitBounds.x>viewport.x?unitBounds.x:viewport.x+viewport.width;
                Rectangle allowed=new Rectangle(viewport.x-1,bounds.y,1+right-viewport.x,bounds.height);
                if(!allowed.contains(bounds))bounds.x=bounds.x-bounds.width/2<=viewport.x?viewport.x:right-bounds.width;
                if(allowed.contains(bounds)) {
                    int textWidth=metrics.getStringBounds(label,g).getBounds().width;
                    g.drawString(label,bounds.x+bounds.width/2-textWidth/2,bounds.y);cursorBounds=bounds;
                }else cursorBounds=new Rectangle();
                g.setColor(color);
            }
        }
        Rectangle previous=null;
        for(var tick:layout.ticks()) {
            if(!tick.large())continue;
            int x=map.applyAsInt(tick.value());Rectangle bounds=metrics.getStringBounds(tick.label(),g).getBounds();int half=bounds.width/2;
            if(x-half<=viewport.x || x+half>=viewport.x+viewport.width)continue;
            bounds.setLocation(x-half,labelY-bounds.y);
            if(bounds.intersects(unitBounds) || previous!=null && bounds.intersects(previous) || cursorBounds!=null && bounds.intersects(cursorBounds))continue;
            g.drawString(tick.label(),x-half,bounds.y);previous=bounds;
        }
        g.dispose();
    }
    private AxisPainter(){}
}
