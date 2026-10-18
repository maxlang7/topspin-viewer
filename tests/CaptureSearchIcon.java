import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import com.formdev.flatlaf.extras.FlatSVGIcon;
public class CaptureSearchIcon {
    public static void main(String[] args)throws Exception {
        var resource=ClassLoader.getSystemResource("de/bruker/nmr/prsc/prop/svg/Magnifier_Search_Find.svg");
        var icon=new FlatSVGIcon(resource);
        for(int scale:new int[]{1,2}) {
            Path path=Path.of("assets/icons/search-magnifier"+(scale==2?"@2x":"")+".png");
            BufferedImage image=new BufferedImage(24*scale,24*scale,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);g.dispose();
            try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",out);}
        }
    }
}
