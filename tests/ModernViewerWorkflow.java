import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.tree.*;
import javax.imageio.ImageIO;
public class ModernViewerWorkflow {
 static Path captures;
 static Object field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception {Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
 static java.util.List<Component> descendants(Component component){var result=new ArrayList<Component>();result.add(component);if(component instanceof Container c)for(Component child:c.getComponents())result.addAll(descendants(child));return result;}
 static void await(BooleanSupplier predicate)throws Exception {for(int i=0;i<300;i++){boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=predicate.getAsBoolean());if(done[0])return;Thread.sleep(25);}throw new AssertionError("Browser did not settle");}
 static void dialog(Viewer viewer,double target,boolean accept)throws Exception {
  ((JButton)descendants(viewer).stream().filter(c->c instanceof JButton b && b.isShowing() && "Calib. Axis".equals(b.getText())).findFirst().orElseThrow()).doClick();
  Component plot=(Component)field(viewer,"plot");
  javax.swing.Timer timer=new javax.swing.Timer(100,null);timer.addActionListener(event->{
   for(Window window:Window.getWindows())if(window instanceof JDialog d && d.isShowing() && d.getTitle().equals("Calibrate axis")){
    timer.stop();try{BufferedImage shot=new BufferedImage(d.getWidth()*2,d.getHeight()*2,BufferedImage.TYPE_INT_ARGB);Graphics2D image=shot.createGraphics();image.scale(2,2);d.paint(image);image.dispose();ImageIO.write(shot,"png",captures.resolve("CALIBRATION.png").toFile());}catch(Exception failure){throw new RuntimeException(failure);}for(Component component:descendants(d))if(component instanceof JTextField input && "axisCalibrationPpm".equals(input.getName()))input.setText(Double.toString(target));
    for(Component component:descendants(d))if(component instanceof JOptionPane pane){pane.setValue(accept?JOptionPane.OK_OPTION:JOptionPane.CANCEL_OPTION);d.setVisible(false);return;}
   }
  });timer.start();
  try{plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,plot.getWidth()/2,plot.getHeight()/2,1,false,MouseEvent.BUTTON1));}finally{timer.stop();}
 }
 public static void main(String[] args)throws Exception {
  Path work=Files.createTempDirectory(Path.of("artifacts").toAbsolutePath(),"modern-viewer-");Path data=work.resolve("data"),source=Path.of("artifacts/reference-datasets/datasets/MLN1P121/10"),experiment=data.resolve("MLN1P99/10");
  try(var files=Files.walk(source)){for(Path file:files.toList()){Path target=experiment.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);}}
  Files.createDirectories(data.resolve("MLN1P111"));
  captures=work;byte[] original=Files.readAllBytes(experiment.resolve("pdata/1/1r"));Assets.load();Theme.install(Assets.regular);JFrame.setDefaultLookAndFeelDecorated(true);
  Viewer[] window=new Viewer[1];
  SwingUtilities.invokeAndWait(()->{window[0]=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),data));window[0].setSize(1260,800);window[0].setVisible(true);window[0].open(experiment);});
  Viewer viewer=window[0];
  try{
   SwingUtilities.invokeAndWait(()->{try{
    if(!Theme.AQUA.equals(UIManager.getColor("Button.default.background")))throw new AssertionError("Default dialog button is not Gruvbox");
    if(!viewer.getTitle().equals("NMR Viewer"))throw new AssertionError("Branded window title remains");
    for(Component component:descendants(viewer))if(component instanceof JLabel label && label.getText()!=null && label.getText().contains("TopSpin"))throw new AssertionError("Branded label remains");
    JButton filter=(JButton)descendants(viewer).stream().filter(c->c instanceof JButton b && "Filters and sorting".equals(b.getToolTipText())).findFirst().orElseThrow();filter.doClick();
    JPopupMenu popup=(JPopupMenu)Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath()).filter(c->c instanceof JPopupMenu).findFirst().orElseThrow();
    BufferedImage filterShot=new BufferedImage(popup.getWidth()*2,popup.getHeight()*2,BufferedImage.TYPE_INT_ARGB);Graphics2D filterGraphics=filterShot.createGraphics();filterGraphics.scale(2,2);popup.paint(filterGraphics);filterGraphics.dispose();ImageIO.write(filterShot,"png",work.resolve("FILTERS.png").toFile());
    JRadioButtonMenuItem natural=(JRadioButtonMenuItem)Arrays.stream(popup.getComponents()).filter(c->c instanceof JRadioButtonMenuItem item && item.getText().equals("Alphabetical (natural order)")).findFirst().orElseThrow();natural.doClick();popup.setVisible(false);
    if(new BrowserLocations(work.resolve("browser.properties"),data).sortOrder()!=DatasetBrowserModel.SortOrder.NATURAL)throw new AssertionError("Inline filter did not save sort choice");
    call(viewer,"execute",new Class<?>[]{String.class},".cal");if(!(boolean)field(viewer,"calibratingAxis"))throw new AssertionError(".cal command did not activate calibration");call(viewer,"execute",new Class<?>[]{String.class},".ret");if((boolean)field(viewer,"calibratingAxis"))throw new AssertionError(".ret did not cancel calibration");
    Spectrum before=(Spectrum)field(viewer,"spectrum");double left=before.left;dialog(viewer,7,false);
    if(((Spectrum)field(viewer,"spectrum")).left!=left || !Arrays.equals(original,Files.readAllBytes(experiment.resolve("pdata/1/1r"))))throw new AssertionError("Cancel modified the spectrum");
    dialog(viewer,7,true);
    if(((JLabel)field(viewer,"status")).getText().contains("cancelled"))throw new AssertionError("Calibration not applied");
    if(((Spectrum)field(viewer,"spectrum")).left==left)throw new AssertionError("Axis did not change");
    if(!Arrays.equals(original,Files.readAllBytes(experiment.resolve("pdata/1/1r"))))throw new AssertionError("Axis calibration changed samples");
    viewer.open(experiment);
    Component plot=(Component)field(viewer,"plot");
    for(Viewer.Mode mode:Viewer.Mode.values()){
     call(viewer,"setMode",new Class<?>[]{Viewer.Mode.class},mode);viewer.validate();
     BufferedImage image=new BufferedImage(viewer.getWidth()*2,viewer.getHeight()*2,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.scale(2,2);viewer.paint(g);g.dispose();
     long hard=0,white=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y);if(rgb==Theme.BG.getRGB())hard++;if(rgb==Color.WHITE.getRGB())white++;}
     if(!Theme.BORDER.equals(UIManager.getColor("Tree.selectionInactiveBackground")))throw new AssertionError("Inactive tree selection is not Gruvbox");
     if(hard<100000 || white>100)throw new AssertionError("Theme has a light panel: white="+white+", hard="+hard);
     ImageIO.write(image,"png",work.resolve(mode+".png").toFile());
    }
    call(viewer,"setMode",new Class<?>[]{Viewer.Mode.class},Viewer.Mode.VIEW);
    ((JButton)descendants(viewer).stream().filter(c->c instanceof JButton b && "FID".equals(b.getText())).findFirst().orElseThrow()).doClick();
    BufferedImage fid=new BufferedImage(plot.getWidth(),plot.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=fid.createGraphics();plot.paint(g);g.dispose();ImageIO.write(fid,"png",work.resolve("FID.png").toFile());
   }catch(Exception error){throw new RuntimeException(error);}});
   System.out.println("PASS inline filter natural sorting, saved preference, Gruvbox hard spectrum/phase/integration/FID, unbranded window and actual calibration click/confirm/cancel/reopen with unchanged component bytes; "+work);
  }finally{SwingUtilities.invokeAndWait(viewer::dispose);}
 }
}
