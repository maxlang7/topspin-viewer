import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Product defaults and processed-spectrum edges against composed Windows module panels. */
public final class LiveModePanelReference {
    private static Field field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        Path root=Path.of("artifacts/live-fid-reference-20261019"),captures=Path.of("artifacts/live-fid-display-20261019");
        Path work=Files.createTempDirectory(Path.of("artifacts"),"live-mode-panel-acceptance-");
        java.util.List<String> rows=new ArrayList<>(Files.readAllLines(captures.resolve("modes.tsv")));rows.addAll(Files.readAllLines(captures.resolve("spectrum-edges.tsv")));
        SwingUtilities.invokeAndWait(()->{Viewer viewer=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),root.resolve("datasets").toAbsolutePath()));try {
            long traces=0,axes=0;int cases=0;
            for(String line:rows) {
                String[] row=line.split("\t");Path folder=captures.resolve(row[2]);viewer.open(root.resolve(row[1]));
                Viewer.Mode mode=row[0].equals("phase")?Viewer.Mode.PHASE:row[0].equals("integration")?Viewer.Mode.INTEGRATE:Viewer.Mode.VIEW;
                if(mode!=Viewer.Mode.VIEW){Method setMode=Viewer.class.getDeclaredMethod("setMode",Viewer.Mode.class);setMode.setAccessible(true);setMode.invoke(viewer,mode);}
                Component plot=(Component)field(viewer,"plot").get(viewer);BufferedImage expected=ImageIO.read(folder.resolve("panel.png").toFile());
                int width=expected.getWidth()/2,height=expected.getHeight()/2;plot.setSize(width,height);
                String metadata=Files.readString(folder.resolve("views.txt"));
                Matcher viewport=Pattern.compile("viewport=java.awt.Rectangle\\[x=(\\d+),y=(\\d+),width=(\\d+),height=(\\d+)\\]").matcher(metadata);
                Matcher x=Pattern.compile("xPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(metadata),y=Pattern.compile("yPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(metadata);
                if(!viewport.find() || !x.find() || !y.find())throw new AssertionError("Missing source module ranges");
                int left=Integer.parseInt(viewport.group(1)),top=Integer.parseInt(viewport.group(2)),w=Integer.parseInt(viewport.group(3)),h=Integer.parseInt(viewport.group(4));
                var vertical=(ViewRanges.Range)field(plot,"verticalRange").get(plot);
                double[] actualRange={(double)field(plot,"viewLeft").get(plot),(double)field(plot,"viewRight").get(plot),vertical.start(),vertical.end()};
                double[] expectedRange={Double.parseDouble(x.group(1)),Double.parseDouble(x.group(2)),Double.parseDouble(y.group(1)),Double.parseDouble(y.group(2))};
                for(int i=0;i<4;i++)if(Double.doubleToRawLongBits(expectedRange[i])!=Double.doubleToRawLongBits(actualRange[i]))throw new AssertionError(row[0]+": native view range "+i+" differs: "+Double.toHexString(expectedRange[i])+" != "+Double.toHexString(actualRange[i]));
                BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);plot.paint(g);g.dispose();long traceDiff=0,axisDiff=0;
                for(int py=(top+26)*2;py<(top+h)*2;py++)for(int px=left*2;px<(left+w)*2;px++) {
                    int pixel=expected.getRGB(px,py);if(pixel!=Color.WHITE.getRGB() && pixel!=Color.BLUE.getRGB())continue;
                    traces++;if(pixel!=actual.getRGB(px,py))traceDiff++;
                }
                for(Rectangle area:new Rectangle[]{new Rectangle(left*2,(top+h)*2,w*2,54),new Rectangle((left+w)*2,top*2,54,h*2)})
                    for(int py=area.y;py<area.y+area.height;py++)for(int px=area.x;px<area.x+area.width;px++){axes++;if(expected.getRGB(px,py)!=actual.getRGB(px,py)){if(axisDiff<8)System.out.println(row[0]+" axis "+px+","+py+" expected="+Integer.toHexString(expected.getRGB(px,py))+" actual="+Integer.toHexString(actual.getRGB(px,py)));axisDiff++;}}
                if(traceDiff!=0 || axisDiff!=0) {
                    ImageIO.write(actual,"png",work.resolve(row[0]+"-actual.png").toFile());
                    throw new AssertionError(row[0]+": live module trace="+traceDiff+", axes="+axisDiff+" differing pixels; "+work);
                }
                cases++;
            }
            System.out.println("PASS "+cases+" actual Windows spectrum/phase/integration and positive/negative/zero spectrum panels; "+traces+" exact trace pixels, "+axes+" exact axis pixels and 24 bitwise view endpoints; annotations excluded");
        }catch(Exception error){throw new RuntimeException(error);}finally{viewer.dispose();}});
    }
}
