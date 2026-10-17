import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public class NativeTextRasterReference {
    public static void main(String[] args) throws Exception {
        Assets.load();BufferedImage actual=new BufferedImage(1200,360,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=actual.createGraphics();g.scale(2,2);
        for(int i=0;i<WindowsTextReference.labels().length;i++) {
            g.setColor(WindowsTextReference.backgrounds[i]);g.fillRect(0,i*30,600,30);
            NativeText.draw(g,Assets.regular.deriveFont((float)WindowsTextReference.sizes[i]),WindowsTextReference.labels()[i],20,20+i*30,
                WindowsTextReference.foregrounds[i],WindowsTextReference.backgrounds[i]);
        }
        g.dispose();ImageIO.write(actual,"png",Path.of("artifacts/standalone-native-text.png").toFile());
        BufferedImage expected=ImageIO.read(Path.of("artifacts/windows-native-text-reference-v2.png").toFile());
        long differences=0,compared=0;
        for(int y=2;y<358;y++)for(int x=20;x<1198;x++) {compared++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))differences++;}
        if(differences!=0)throw new AssertionError("Native text differs on "+differences+" pixels");
        System.out.println("PASS "+compared+" Windows on-screen text pixels, printable ASCII, 12/14/16-point type and five color pairs");
    }
}
