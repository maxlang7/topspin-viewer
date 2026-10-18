import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import com.formdev.flatlaf.FlatLightLaf;
public class CaptureDialogIcons {
    public static void main(String[] args)throws Exception {
        com.bruker.ioc.IOCBuilder.createInjector(new com.google.inject.AbstractModule(){protected void configure(){bind(de.bruker.nmr.jutil.ui.IconProvider.class).toInstance(new de.bruker.nmr.jutil.ui.IconProvider());}});
        FlatLightLaf.registerCustomDefaultsSource("bruker.bio.topspin");FlatLightLaf.setup();
        for(String command:new String[]{"OK","CANCEL"}) {
            var icon=de.bruker.nmr.jutil.ui.IconFactory.getIconForAction(command);
            for(int scale:new int[]{1,2}) {
                BufferedImage image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);g.dispose();
                Path target=Path.of("assets/icons/dialog-"+command.toLowerCase()+(scale==2?"@2x":"")+".png");
                try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",out);}
            }
        }
    }
}
