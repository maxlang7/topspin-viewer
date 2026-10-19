import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import com.formdev.flatlaf.extras.FlatSVGIcon;

/** Capture the source Data-tab glyph without initializing TopSpin. */
public class CaptureBrowserIcon {
    public static void main(String[] args)throws Exception {
        var resource=ClassLoader.getSystemResource("de/bruker/nmr/prsc/prop/svg/List.svg");
        if(resource==null)throw new IllegalStateException("Missing source browser icon");
        var icon=new FlatSVGIcon(resource);
        for(int scale:new int[]{1,2}) {
            var image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);g.dispose();
            try(var out=Files.newOutputStream(Path.of("assets/icons/browser-data"+(scale==2?"@2x":"")+".png"),StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",out);}
        }
    }
}
