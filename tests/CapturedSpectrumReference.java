import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Full-panel trace acceptance, with the captured labels and integral overlays masked. */
public final class CapturedSpectrumReference {
    public static void main(String[] args)throws Exception {
        long compared=0,trace=0,overlays=0;
        for(String capture:new String[]{"2610918686881465039","11503113690315933686","1535056959243772464"}) {
            Path folder=Path.of("artifacts/live-display-integration-"+capture);
            BufferedImage expected=ImageIO.read(folder.resolve("panel.png").toFile());
            Path dataPath=capture.equals("11503113690315933686")?Path.of("artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2"):Path.of("artifacts/reference-datasets/datasets/MLN1P121/10/pdata/1");
            BrukerDataset data=new BrukerDataset(dataPath);
            Matcher physical=Pattern.compile("yPhysical=\\[([^,]+),([^\\]]+)\\]").matcher(Files.readString(folder.resolve("views.txt")));
            if(!physical.find())throw new AssertionError("Missing intensity mapping");
            int width=expected.getWidth()/2,height=expected.getHeight()/2,w=width-33,h=height-31;
            BufferedImage logical=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=logical.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.setColor(Color.BLUE);
            SpectrumPainter.paintPanel(g,data.real,0,2,2,w,h,VisualNumerics.horizontal(data.left,data.right,2,w+1),
                VisualNumerics.vertical(Double.parseDouble(physical.group(1)),Double.parseDouble(physical.group(2)),2,h+2),data.left,data.right,data.real.length,1);g.dispose();
            BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);
            g=actual.createGraphics();g.drawImage(logical,0,0,actual.getWidth(),actual.getHeight(),null);g.dispose();
            long different=0;
            // Name and integration annotations occupy the top 26 logical pixels.
            for(int y=52;y<(h+2)*2;y++)for(int x=4;x<(w+2)*2;x++) {
                int original=expected.getRGB(x,y),pixel=actual.getRGB(x,y);
                if(original!=Color.WHITE.getRGB() && original!=Color.BLUE.getRGB()){overlays++;continue;}
                compared++;if(original==Color.BLUE.getRGB())trace++;if(pixel!=original)different++;
            }
            if(different!=0)throw new AssertionError(capture+": "+different+" buffered trace pixels differ");
        }
        System.out.println("PASS 3 captured Windows spectrum panels, "+compared+" exact pixels including "+trace+" trace pixels; "+overlays+" annotation pixels masked");
    }
}
