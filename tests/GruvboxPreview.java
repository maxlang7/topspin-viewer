import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.*;
import javax.swing.*;
import javax.imageio.ImageIO;
public class GruvboxPreview {
 public static void main(String[] args)throws Exception {
  Assets.load();Theme.install(Assets.regular);JFrame.setDefaultLookAndFeelDecorated(true);
  Path work=Files.createTempDirectory(Path.of("artifacts"),"gruvbox-preview-");
  SwingUtilities.invokeAndWait(()->{
   Viewer viewer=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),Path.of("artifacts/reference-datasets/datasets").toAbsolutePath()));
   try{
    viewer.setSize(1260,800);viewer.setVisible(true);viewer.open(Path.of("artifacts/reference-datasets/datasets/MLN1P121/10"));
    Method mode=Viewer.class.getDeclaredMethod("setMode",Viewer.Mode.class);mode.setAccessible(true);
    for(Viewer.Mode next:Viewer.Mode.values()){
     mode.invoke(viewer,next);viewer.validate();BufferedImage image=new BufferedImage(viewer.getWidth()*2,viewer.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
     Graphics2D g=image.createGraphics();g.scale(2,2);viewer.paint(g);g.dispose();ImageIO.write(image,"png",work.resolve(next.name()+".png").toFile());
    }
    System.out.println(work);
   }catch(Exception error){throw new RuntimeException(error);}finally{viewer.dispose();}
  });
 }
}
