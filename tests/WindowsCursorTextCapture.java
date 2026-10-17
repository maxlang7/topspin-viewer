import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Plain Windows JVM gray-antialiased text at the foreground panel's device transform. */
public class WindowsCursorTextCapture {
    public static void main(String[] args)throws Exception {
        BufferedImage image=new BufferedImage(1000,180,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);g.setColor(Color.WHITE);g.fillRect(0,0,500,90);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Monospaced",Font.PLAIN,12));g.setColor(new Color(51,51,51));
        g.drawString("Mouse Sensitivity: 1.0",5,38);g.drawString("Sum = 5.3045",5,66);
        StringBuilder ascii=new StringBuilder();for(int i=32;i<127;i++)ascii.append((char)i);
        g.drawString(ascii.substring(0,48),5,14);g.drawString(ascii.substring(48),5,85);
        System.out.println("font="+g.getFont()+" height="+g.getFontMetrics().getHeight()+" ascent="+g.getFontMetrics().getAscent()+" descent="+g.getFontMetrics().getDescent()+" leading="+g.getFontMetrics().getLeading()+" bounds="+g.getFontMetrics().getStringBounds("Mouse Sensitivity: 1.0",g));g.scale(.5,.5);System.out.println("scale1 height="+g.getFontMetrics().getHeight()+" bounds="+g.getFontMetrics().getStringBounds("Mouse Sensitivity: 1.0",g));g.dispose();
        try(var output=Files.newOutputStream(Path.of(args[0]),StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",output);}
    }
}
