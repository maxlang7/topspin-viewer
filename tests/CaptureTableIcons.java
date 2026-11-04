import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
import de.bruker.nmr.mfw.table.IconConstants;

/** Test-only capture preserves SVG strokes that extend outside the icon box. */
public class CaptureTableIcons {
    public static void main(String[] args) throws Exception {
        FlatLightLaf.setup();JTable table=new JTable();table.setForeground(Color.BLACK);
        String[] names={"sort-up","sort-down"};Icon[] icons={IconConstants.COLUMN_SORT_UP,IconConstants.COLUMN_SORT_DOWN};
        Path output=Path.of("artifacts/integral-table-icons");Files.createDirectories(output);
        for(int i=0;i<icons.length;i++)for(int scale:new int[]{1,2}) {
            Icon icon=icons[i];BufferedImage image=new BufferedImage((icon.getIconWidth()+2)*scale,(icon.getIconHeight()+2)*scale,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.scale(scale,scale);icon.paintIcon(table,g,1,1);g.dispose();
            ImageIO.write(image,"png",output.resolve(names[i]+"-padded"+(scale==2?"@2x":"")+".png").toFile());
        }
    }
}
