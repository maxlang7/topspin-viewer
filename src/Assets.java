import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.awt.image.BaseMultiResolutionImage;
import javax.imageio.ImageIO;
import java.util.*;
import javax.swing.*;

/** Pinned fonts and captured control graphics from the target installation. */
public final class Assets {
    private static final Map<String,ImageIcon> icons=new LinkedHashMap<>();
    public static Font regular=new Font("Arial",Font.PLAIN,12),bold=new Font("Arial",Font.BOLD,12);
    public static Font mono=new Font(Font.MONOSPACED,Font.PLAIN,12);
    public static void load() throws Exception {
        regular=Font.createFont(Font.TRUETYPE_FONT,Path.of("assets/fonts/arial.ttf").toFile()).deriveFont(12f);
        bold=Font.createFont(Font.TRUETYPE_FONT,Path.of("assets/fonts/arialbd.ttf").toFile()).deriveFont(12f);
        mono=Font.createFont(Font.TRUETYPE_FONT,Path.of("assets/fonts/cour.ttf").toFile()).deriveFont(12f);
        for(String line:Files.readAllLines(Path.of("assets/icon-catalog.tsv"))) {
            String[] pair=line.split("\t",2);
            if(pair.length==2) {
                Path base=Path.of("assets/icons",pair[1]);Path high=base.resolveSibling(pair[1].replace(".png","@2x.png"));
                icons.put(pair[0],Files.isRegularFile(high)?new ImageIcon(new BaseMultiResolutionImage(ImageIO.read(base.toFile()),ImageIO.read(high.toFile()))):new ImageIcon(base.toString()));
            }
        }
    }
    public static ImageIcon icon(String mode,String tooltipPrefix) {
        for(var item:icons.entrySet())if(item.getKey().startsWith(mode+"."+tooltipPrefix))return item.getValue();
        return null;
    }
    public static ImageIcon image(String name) {
        Path base=Path.of("assets/icons",name),high=base.resolveSibling(name.replace(".png","@2x.png"));
        try {return Files.isRegularFile(high)?new ImageIcon(new BaseMultiResolutionImage(ImageIO.read(base.toFile()),ImageIO.read(high.toFile()))):new ImageIcon(base.toString());}
        catch(IOException e){throw new IllegalStateException("Missing interface asset "+name,e);}
    }
    public static JButton button(String mode,String tooltip,Runnable action,int width,int height) {
        JButton button=new JButton(icon(mode,tooltip));button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(width,height));button.setMinimumSize(new Dimension(width,height));
        button.setMargin(new Insets(0,0,0,0));button.setFocusable(false);button.setBorderPainted(false);
        button.setBackground(new Color(245,245,245));button.addActionListener(e->action.run());
        return button;
    }
    public static Icon colored(Icon icon,Color color) {
        if(icon==null)return null;
        java.awt.image.BufferedImage[] images=new java.awt.image.BufferedImage[2];
        for(int scale=1;scale<=2;scale++) {
            var image=new java.awt.image.BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);
            g.setComposite(AlphaComposite.SrcIn);g.setColor(color);g.fillRect(0,0,icon.getIconWidth(),icon.getIconHeight());g.dispose();images[scale-1]=image;
        }
        return new ImageIcon(new BaseMultiResolutionImage(images));
    }
    private Assets() {}
}
