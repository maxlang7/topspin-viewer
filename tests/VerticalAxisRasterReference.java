import java.awt.*;
import java.awt.image.BufferedImage;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.view.*;

public final class VerticalAxisRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();long compared=0;int cases=0;
        for(int height:new int[]{31,257,636})for(int scale:new int[]{1,2})for(int position:new int[]{1,2})for(int offset:new int[]{0,2})
        for(double[] range:new double[][]{{-1.27779894375E7,7.24086068125E7,2.2149919552415099E-7},{-23.001,102.345,.3},{.000001,.000007,1e8},{-8,-2,3}}) {
            int width=27;Rectangle viewport=new Rectangle(2,2,width,height);Font font=Assets.bold.deriveFont(14f);
            VerticalAxis axis=new VerticalAxis();axis.setViewport(viewport);axis.setPosition(position);axis.setOffset(offset);axis.setUnit("rel");
            axis.setProperty("AXIS_COLOR","128 128 128");axis.setProperty("AXIS_FONT","FN=Arial,FY=BOLD,FS=14");
            PhysicalRange physical=new PhysicalRange(range[0],range[1],"");physical.setReference(range[2]);axis.setRange(physical);
            MapperY mapper=new MapperY();mapper.setPhysicalMapping(physical);mapper.setScreenMapping(2,height+2);axis.setMapper(mapper);
            BufferedImage expected=AxisRasterReference.canvas(width,height,scale),actual=AxisRasterReference.canvas(width,height,scale);
            Graphics2D g=expected.createGraphics();g.scale(scale,scale);axis.paint(new AxisRasterReference.OriginalRenderer(g,font));g.dispose();
            g=actual.createGraphics();g.scale(scale,scale);AxisPainter.paintVertical(g,viewport,range[0],range[1],range[2],1,font,new Color(128,128,128),position,offset);g.dispose();
            long different=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){compared++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0)throw new AssertionError(different+" vertical pixels differ, height="+height+" position="+position+" offset="+offset+" start="+range[0]);cases++;
        }
        System.out.println("PASS "+cases+" original vertical-axis raster cases, "+compared+" exact pixels");
    }
}
