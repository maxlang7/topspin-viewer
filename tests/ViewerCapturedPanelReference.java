import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Exercise the product's actual plot against Windows panel captures, rather than composing helpers. */
public final class ViewerCapturedPanelReference {
    private static Field field(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field;
    }
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        Path work=Files.createTempDirectory(Path.of("artifacts"),"viewer-captured-panels-");
        SwingUtilities.invokeAndWait(()->{Viewer viewer=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),work));try {
            long tracePixels=0,axisPixels=0;
            for(String capture:new String[]{"2610918686881465039","11503113690315933686","1535056959243772464"}) {
                Path folder=Path.of("artifacts/live-display-integration-"+capture);
                BufferedImage expected=ImageIO.read(folder.resolve("panel.png").toFile());
                Path data=capture.equals("11503113690315933686")?Path.of("artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2"):Path.of("artifacts/reference-datasets/datasets/MLN1P121/10/pdata/1");
                viewer.open(data);Spectrum spectrum=(Spectrum)field(viewer,"spectrum").get(viewer);spectrum.integrals=new IntegrationRegions();
                Component plot=(Component)field(viewer,"plot").get(viewer);
                int width=expected.getWidth()/2,height=expected.getHeight()/2,w=width-33,h=height-31;plot.setSize(width,height);
                Matcher mapping=Pattern.compile("yPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(Files.readString(folder.resolve("views.txt")));
                if(!mapping.find())throw new AssertionError("Missing reference range");
                field(plot,"verticalRange").set(plot,new ViewRanges.Range(Double.parseDouble(mapping.group(1)),Double.parseDouble(mapping.group(2))));
                field(plot,"cursorValue").set(plot,capture.equals("2610918686881465039")?8.73:null);
                BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=actual.createGraphics();g.scale(2,2);plot.paint(g);g.dispose();long traceDiff=0,axisDiff=0;
                for(int y=52;y<(h+2)*2;y++)for(int x=4;x<(w+2)*2;x++) {
                    int pixel=expected.getRGB(x,y);if(pixel!=Color.WHITE.getRGB() && pixel!=Color.BLUE.getRGB())continue;
                    tracePixels++;if(actual.getRGB(x,y)!=pixel)traceDiff++;
                }
                for(Rectangle area:new Rectangle[]{new Rectangle(4,(h+2)*2,w*2,54),new Rectangle((w+2)*2,4,54,h*2)})
                    for(int y=area.y;y<area.y+area.height;y++)for(int x=area.x;x<area.x+area.width;x++) {
                        int pixel=expected.getRGB(x,y),r=(pixel>>>16)&255,green=(pixel>>>8)&255,b=pixel&255;
                        if(r>green && r>b)continue;axisPixels++;if(actual.getRGB(x,y)!=pixel)axisDiff++;
                    }
                if(traceDiff!=0 || axisDiff!=0) {
                    ImageIO.write(actual,"png",work.resolve("actual-"+capture+".png").toFile());
                    throw new AssertionError(capture+": actual viewer trace="+traceDiff+", axes="+axisDiff+" differing pixels; "+work);
                }
            }
            System.out.println("PASS actual Viewer plot against three captured Windows panels: "+tracePixels+" trace pixels and "+axisPixels+" axis pixels; source annotations masked");
        }catch(Exception error){throw new RuntimeException(error);}finally{viewer.dispose();}});
    }
}
