import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Actual product FID states versus composed panels captured inside Windows TopSpin. */
public final class LiveFidPanelReference {
    private static Field field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private static void call(Object object,String name,Class<?>[] types,Object...args)throws Exception {Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(object,args);}
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        Path root=Path.of("artifacts/live-fid-reference-20261019"),captures=Path.of("artifacts/live-fid-display-20261019");
        Path work=Files.createTempDirectory(Path.of("artifacts"),"live-fid-panel-acceptance-");
        SwingUtilities.invokeAndWait(()->{Viewer viewer=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),root.resolve("datasets").toAbsolutePath()));try {
            long traces=0,axes=0;int cases=0;
            for(String line:Files.readAllLines(captures.resolve("fresh-panels.tsv"))) {
                String[] row=line.split("\t");Path folder=captures.resolve(row[3]);
                viewer.open(root.resolve(row[1]));Spectrum spectrum=(Spectrum)field(viewer,"spectrum").get(viewer);
                spectrum.raw=new RawDataset(spectrum.experiment,spectrum.path.resolve("proc"));
                Component plot=(Component)field(viewer,"plot").get(viewer);
                BufferedImage expected=ImageIO.read(folder.resolve("panel.png").toFile());
                int width=expected.getWidth()/2,height=expected.getHeight()/2,w=width-33,h=height-31;plot.setSize(width,height);
                call(plot,"showRaw",new Class<?>[]{boolean.class},true);
                if(!row[2].equals("default"))call(plot,"zoom",new Class<?>[]{double.class},.5);
                if(row[2].equals("scaled"))call(plot,"scale",new Class<?>[]{double.class},2.0);
                String metadata=Files.readString(folder.resolve("views.txt"));
                Matcher x=Pattern.compile("xPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(metadata),y=Pattern.compile("yPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(metadata);
                if(!x.find() || !y.find())throw new AssertionError("Missing live view ranges");
                ViewRanges.Range vertical=(ViewRanges.Range)field(plot,"verticalRange").get(plot);
                double[] actualRange={(double)field(plot,"viewLeft").get(plot),(double)field(plot,"viewRight").get(plot),vertical.start(),vertical.end()};
                double[] expectedRange={Double.parseDouble(x.group(1)),Double.parseDouble(x.group(2)),Double.parseDouble(y.group(1)),Double.parseDouble(y.group(2))};
                for(int i=0;i<4;i++)if(Double.doubleToRawLongBits(expectedRange[i])!=Double.doubleToRawLongBits(actualRange[i]))throw new AssertionError(row[3]+": native view range "+i+" differs: "+Double.toHexString(expectedRange[i])+" != "+Double.toHexString(actualRange[i]));
                BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);plot.paint(g);g.dispose();
                long traceDiff=0,axisDiff=0;
                for(int py=52;py<(h+2)*2;py++)for(int px=4;px<(w+2)*2;px++) {
                    int pixel=expected.getRGB(px,py);if(pixel!=Color.WHITE.getRGB() && pixel!=Color.BLUE.getRGB())continue;
                    traces++;if(pixel!=actual.getRGB(px,py))traceDiff++;
                }
                for(Rectangle area:new Rectangle[]{new Rectangle(4,(h+2)*2,w*2,54),new Rectangle((w+2)*2,4,54,h*2)})
                    for(int py=area.y;py<area.y+area.height;py++)for(int px=area.x;px<area.x+area.width;px++){axes++;if(expected.getRGB(px,py)!=actual.getRGB(px,py))axisDiff++;}
                if(traceDiff!=0 || axisDiff!=0) {
                    ImageIO.write(actual,"png",work.resolve(row[3]+"-actual.png").toFile());
                    throw new AssertionError(row[3]+": live panel trace="+traceDiff+", axes="+axisDiff+" differing pixels; "+work);
                }
                cases++;
            }
            if(cases!=9)throw new AssertionError("Incomplete live panel coverage");
            System.out.println("PASS "+cases+" actual Windows FID panels, integer/float/zero data at default/zoom/scaled states; "+traces+" exact trace pixels, "+axes+" exact axis pixels and 36 bitwise view endpoints; annotations excluded");
        }catch(Exception error){throw new RuntimeException(error);}finally{viewer.dispose();}});
    }
}
