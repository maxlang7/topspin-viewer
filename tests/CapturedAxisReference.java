import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Acceptance against full Windows panel captures, excluding the colored cursor overlay. */
public final class CapturedAxisReference {
    public static void main(String[] args)throws Exception {
        Assets.load();long compared=0,overlays=0;
        for(String capture:new String[]{"2610918686881465039","11503113690315933686","1535056959243772464"}) {
            BufferedImage expected=ImageIO.read(Path.of("artifacts/live-display-integration-"+capture,"panel.png").toFile());
            int width=expected.getWidth()/2,height=expected.getHeight()/2,plotWidth=width-33,plotHeight=height-31;
            BufferedImage logical=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=logical.createGraphics();g.setColor(new Color(245,245,245));g.fillRect(0,0,width,height);
            double left=14.9974006758278,right=-4.9974006758207725;
            AxisPainter.paint(g,new Rectangle(2,plotHeight+2,plotWidth,27),VisualNumerics.horizontal(left,right,2,plotWidth+2),AxisTicks.calculate(left,right,"ppm",null,true,true),Assets.bold.deriveFont(14f),new Color(128,128,128),1,0,capture.equals("2610918686881465039")?8.73:null);
            String metadata=Files.readString(Path.of("artifacts/live-display-integration-"+capture,"views.txt"));
            var values=java.util.regex.Pattern.compile("yPhysical=\\[([^,]+),([^\\]]+)\\]  ref = ([^\\r\\n]+)").matcher(metadata);
            if(!values.find())throw new AssertionError("Missing captured vertical mapping");
            AxisPainter.paintVertical(g,new Rectangle(plotWidth+2,2,27,plotHeight),Double.parseDouble(values.group(1)),Double.parseDouble(values.group(2)),Double.parseDouble(values.group(3)),1,Assets.bold.deriveFont(14f),new Color(128,128,128),1,0);g.dispose();
            BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);
            g=actual.createGraphics();g.drawImage(logical,0,0,actual.getWidth(),actual.getHeight(),null);g.dispose();
            long differences=0;
            for(Rectangle area:new Rectangle[]{new Rectangle(4,(plotHeight+2)*2,plotWidth*2,54),new Rectangle((plotWidth+2)*2,4,54,plotHeight*2)})
            for(int y=area.y;y<area.y+area.height;y++)for(int x=area.x;x<area.x+area.width;x++) {
                int pixel=expected.getRGB(x,y),r=(pixel>>>16)&255,b=pixel&255,green=(pixel>>>8)&255;
                if(r>green && r>b){overlays++;continue;}
                compared++;if(pixel!=actual.getRGB(x,y))differences++;
            }
            if(differences!=0)throw new AssertionError(capture+": "+differences+" static axis pixels differ");
        }
        System.out.println("PASS 3 captured Windows panel axes, "+compared+" exact axis pixels including cursor, "+overlays+" integral-bracket endpoint pixels excluded");
    }
}
