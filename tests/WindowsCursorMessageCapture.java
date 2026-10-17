package oracle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.listener.AbstractListener;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;
import de.bruker.nmr.jview.view.SpecView1D;

/** Unmodified cursor-message renderer in the bundled Windows JVM, without a TopSpin desktop. */
public class WindowsCursorMessageCapture {
    static class Original extends AbstractListener {
        Original(ViewIfc view){super(view);}
        @Override protected void drawCursor(JRendererIfc r,Point p,Rectangle v){}
        @Override protected String[] createLabels(ViewIfc view,Point point){return null;}
        @Override public void recalculateCoordinates(Point p){}
        void render(JRendererIfc r,String[] labels){drawCursorMessage(r,labels,new Rectangle(2,2,400,140));}
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectory(root);int index=0;
        for(int scale:new int[]{1,2})for(int offset:new int[]{0,22})for(Color color:new Color[]{Color.BLACK,new Color(51,51,51)})
        for(String[] labels:new String[][]{{"Mouse Sensitivity: 1.0",null,"Sum = 5.3045",null,null,null},{"a","","c"},{null,"value = 3.44"},{null,null}}) {
            BufferedImage image=new BufferedImage(438*scale,158*scale,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.setColor(new Color(245,245,245));g.fillRect(0,0,image.getWidth(),image.getHeight());g.scale(scale,scale);
            g.setColor(new Color(237,28,36));g.fillRect(120,0,3,150);g.setColor(Color.BLUE);g.drawLine(0,35,430,35);
            SpecView1D view=new SpecView1D();view.setProperty("CURSOR_FONT","FN=Monospaced,FY=PLAIN,FS=12");view.setProperty("TEXT_OFFSET",Integer.toString(offset));view.setProperty("BACKGROUND_COLOR","255 255 255");
            ForegroundRenderer renderer=new ForegroundRenderer(g,new java.util.Properties(),new javax.swing.JPanel());renderer.setColor(color);new Original(view).render(renderer,labels);g.dispose();
            try(var output=Files.newOutputStream(root.resolve(index+++".png"),StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",output);}
        }
        System.out.println("Captured "+index+" actual Windows cursor-message cases");
    }
}
