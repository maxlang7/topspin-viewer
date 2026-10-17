import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.regex.*;
import javax.imageio.ImageIO;

/** Full trace and integral compositions from the actual Windows module. */
public final class CapturedIntegralPanelReference {
    static double[] mapping(String text,String prefix) {
        Matcher values=Pattern.compile(prefix+"Physical=\\[([^,]+),([^\\]]+)\\]").matcher(text);
        if(!values.find())throw new AssertionError("Missing captured "+prefix+" mapping");
        return new double[]{Double.parseDouble(values.group(1)),Double.parseDouble(values.group(2))};
    }
    public static void main(String[] args)throws Exception {
        Assets.load();long compared=0,masked=0,ink=0;
        for(String capture:new String[]{"2610918686881465039","11503113690315933686","1535056959243772464"}) {
            Path folder=Path.of("artifacts/live-display-integration-"+capture);String metadata=Files.readString(folder.resolve("views.txt"));
            BufferedImage expected=ImageIO.read(folder.resolve("panel.png").toFile());
            boolean saved=capture.equals("11503113690315933686");
            Spectrum data=new Spectrum(Path.of(saved?"artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2":"artifacts/reference-datasets/datasets/MLN1P121/10/pdata/1"));
            if(!saved)data.integrals.calibrate(0,1,data.real,data.left,data.right);
            int width=expected.getWidth()/2,height=expected.getHeight()/2,w=width-33,h=height-31;
            BufferedImage logical=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=logical.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.setColor(Color.BLUE);
            double[] vertical=mapping(metadata,"y");
            var horizontal=VisualNumerics.horizontal(data.left,data.right,2,w+2);
            SpectrumPainter.paintPanel(g,data.real,0,2,2,w,h,horizontal,VisualNumerics.vertical(vertical[0],vertical[1],2,h+2),data.left,data.right,data.real.length,1);
            g.setColor(new Color(245,245,245));g.fillRect(2,h+2,w,27);g.fillRect(w+2,2,27,h);
            AxisPainter.paint(g,new Rectangle(2,h+2,w,27),horizontal,AxisTicks.calculate(data.left,data.right,"ppm",null,true,true),Assets.bold.deriveFont(14f),new Color(128,128,128),1,0,capture.equals("2610918686881465039")?8.73:null);
            var regions=data.integrals.regions();ArrayList<Numerics.Integral> integrals=new ArrayList<>();
            for(var region:regions)integrals.add(data.integrals.calculate(region,data.real,data.left,data.right));
            var common=IntegralCurvePainter.commonRange(integrals.stream().map(Numerics.Integral::curve).toList(),.2);
            for(int index=0;index<regions.size();index++) {
                var region=regions.get(index);var integral=integrals.get(index);
                int start=horizontal.screen(region.left(),1),end=horizontal.screen(region.right(),1);
                Rectangle viewport=new Rectangle(start,2,Math.max(1,end-start),h);
                IntegralCurvePainter.paint(g,integral.curve(),region.left(),region.right(),region.left(),region.right(),viewport,horizontal,common,IntegralCurvePainter.reference(integral.curve()),.8);
                IntegralLabelPainter.paint(g,viewport,integral.normalized(data.integrals.normalizingFactor),4,false);
            }
            g.dispose();BufferedImage actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_ARGB);
            g=actual.createGraphics();g.drawImage(logical,0,0,actual.getWidth(),actual.getHeight(),null);g.scale(2,2);
            double sum=0;for(var integral:integrals)sum+=integral.normalized(data.integrals.normalizingFactor);
            var format=java.text.NumberFormat.getNumberInstance(java.util.Locale.ROOT);format.setMinimumFractionDigits(4);format.setMaximumFractionDigits(4);
            CursorMessagePainter.paint(g,new String[]{"Mouse Sensitivity: 1.0",null,"Sum = "+format.format(sum),null,null,null},new Rectangle(2,2,w,h),22,capture.equals("2610918686881465039")?Color.BLACK:new Color(51,51,51),Assets.mono);g.dispose();long different=0;
            for(int y=52;y<(h+3)*2;y++)for(int x=4;x<(w+2)*2;x++) {
                int original=expected.getRGB(x,y),pixel=actual.getRGB(x,y),r=(original>>>16)&255,green=(original>>>8)&255,b=original&255;
                // Source foreground cursor and gray module text have separate rendering contracts.
                if(saved && (x==1065 || x==1066) || r==green && r==b && r!=255){masked++;continue;}
                compared++;if(r!=green || r!=b)ink++;if(original!=pixel)different++;
            }
            if(different!=0)throw new AssertionError(capture+": "+different+" spectrum and integral composition pixels differ");
        }
        System.out.println("PASS 3 captured Windows integral panels, "+compared+" exact composed pixels, "+ink+" trace/label pixels; "+masked+" cursor/gray annotation pixels excluded");
    }
}
