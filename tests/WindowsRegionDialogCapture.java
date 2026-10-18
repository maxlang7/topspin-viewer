package oracle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.ifc.PhysicalRangeIfc;
import de.bruker.nmr.jview.module.GetPhysicalRangeDialog;
import com.formdev.flatlaf.FlatLightLaf;

/** A pure dialog reference JVM; does not load TopSpinMain or request a license. */
public class WindowsRegionDialogCapture {
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
    static void describe(Component component,String path,StringBuilder text){
        text.append(path).append(" ").append(component.getClass().getName()).append(" ").append(component.getBounds()).append(" minimum=").append(component.getMinimumSize()).append(" preferred=").append(component.getPreferredSize()).append(" maximum=").append(component.getMaximumSize()).append(" font=").append(component.getFont()).append(" bg=").append(component.getBackground()).append(" fg=").append(component.getForeground());
        if(component instanceof JLabel)text.append(" text=").append(((JLabel)component).getText());
        if(component instanceof AbstractButton)text.append(" text=").append(((AbstractButton)component).getText()).append(" icon=").append(((AbstractButton)component).getIcon());
        if(component instanceof javax.swing.text.JTextComponent)text.append(" text=").append(((javax.swing.text.JTextComponent)component).getText());
        text.append("\n");if(component instanceof Container){int i=0;for(Component child:((Container)component).getComponents())describe(child,path+"/"+(i++),text);}
    }
    static void unfocused(Component component){
        if(component instanceof JComponent)((JComponent)component).putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_FOCUS_OWNER,(java.util.function.Predicate<JComponent>)(c->false));
        if(component instanceof JTextField){JTextField field=(JTextField)component;field.setFocusable(false);field.setCaretPosition(0);field.getCaret().setSelectionVisible(false);field.getCaret().setVisible(false);}
        if(component instanceof Container)for(Component child:((Container)component).getComponents())unfocused(child);
    }
    static void writeBody(Container body,Path path)throws Exception{
        layout(body);BufferedImage image=new BufferedImage(body.getWidth()*2,body.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);body.paint(g);g.dispose();ImageIO.write(image,"png",path.toFile());
    }
    static void fields(Component component,java.util.List<JTextField> fields){if(component instanceof JTextField)fields.add((JTextField)component);if(component instanceof Container)for(Component child:((Container)component).getComponents())fields(child,fields);}
    public static void main(String[] args)throws Exception {
        Thread watchdogThread=new Thread(()->{try{Thread.sleep(20000);}catch(InterruptedException ignored){}System.exit(2);});watchdogThread.setDaemon(true);watchdogThread.start();
        System.out.println("Starting independent region dialog reference");
        Path output=Path.of(args[0]);Files.createDirectory(output);
        com.bruker.ioc.IOCBuilder.createInjector(new com.google.inject.AbstractModule(){
            @Override protected void configure(){bind(de.bruker.nmr.jutil.ui.IconProvider.class).toInstance(new de.bruker.nmr.jutil.ui.IconProvider());}
        });
        FlatLightLaf.registerCustomDefaultsSource("bruker.bio.topspin");FlatLightLaf.setup();
        System.out.println("Original look and feel ready");
        SwingUtilities.invokeAndWait(()->{
            Timer watchdog=new Timer(15000,e->System.exit(2));watchdog.setRepeats(false);watchdog.start();
            Timer timer=new Timer(800,null);timer.addActionListener(event->{
                for(Window window:Window.getWindows())if(window instanceof JDialog && window.isShowing()) {
                    timer.stop();try {
                        window.pack();window.validate();
                        BufferedImage image=new BufferedImage(window.getWidth()*2,window.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g=image.createGraphics();g.scale(2,2);window.paint(g);g.dispose();ImageIO.write(image,"png",output.resolve("paint.png").toFile());
                        Container body=((JDialog)window).getContentPane();Dimension preferredBody=body.getPreferredSize();body.setSize(preferredBody);layout(body);
                        unfocused(body);
                        int[][] sizes={{215,151},{320,220},{550,350},{215,190},{440,151}};
                        for(int index=0;index<sizes.length;index++){body.setSize(sizes[index][0],sizes[index][1]);writeBody(body,output.resolve("body-"+index+".png"));}
                        java.util.List<JTextField> textFields=new java.util.ArrayList<>();fields(body,textFields);StringBuilder layouts=new StringBuilder();
                        for(int index=0;index<4096;index++) {
                            int width=215+index%731,height=151+index%874;body.setSize(width,height);layout(body);
                            layouts.append(width).append("\t").append(height);
                            for(JTextField field:textFields){Point point=SwingUtilities.convertPoint(field,0,0,body);layouts.append("\t").append(point.x).append("\t").append(point.y).append("\t").append(field.getWidth()).append("\t").append(field.getHeight());}
                            layouts.append("\n");
                        }
                        Files.writeString(output.resolve("layouts.tsv"),layouts.toString());
                        body.setSize(preferredBody);writeBody(body,output.resolve("body.png"));
                        KeyboardFocusManager previousFocusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Window getActiveWindow(){return window;}});
                        StringBuilder metrics=new StringBuilder();
                        for(JTextField field:textFields){metrics.append("text=").append(field.getText()).append(" stringWidth=").append(field.getFontMetrics(field.getFont()).stringWidth(field.getText())).append("\n");for(int i=0;i<=field.getText().length();i++)metrics.append(i).append("	").append(field.modelToView2D(i)).append("\n");}
                        Files.writeString(output.resolve("field-metrics.txt"),metrics.toString());
                        for(int state=0;state<4;state++) {
                            unfocused(body);JTextField field=textFields.get(state==3?1:0);
                            field.putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_FOCUS_OWNER,(java.util.function.Predicate<JComponent>)(component->true));
                            field.setFocusable(true);field.select(state==2?2:0,state==0?0:field.getText().length());field.getCaret().setVisible(false);field.getCaret().setSelectionVisible(state!=0);
                            writeBody(body,output.resolve("focus-"+state+".png"));
                        }
                        KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocusManager);unfocused(body);
                        BufferedImage full=new BufferedImage(window.getWidth()*2,window.getHeight()*2,BufferedImage.TYPE_INT_ARGB);Graphics2D fullGraphics=full.createGraphics();fullGraphics.scale(2,2);window.paint(fullGraphics);fullGraphics.dispose();ImageIO.write(full,"png",output.resolve("window-unfocused.png").toFile());
                        StringBuilder tree=new StringBuilder();describe(body,"body",tree);Files.writeString(output.resolve("tree.txt"),tree.toString());
                        java.lang.reflect.Method font2d=Class.forName("sun.font.FontUtilities").getMethod("getFont2D",Font.class);
                        Files.writeString(output.resolve("fonts.txt"),"UI font: "+font2d.invoke(null,body.getFont())+"\nNew Dialog: "+font2d.invoke(null,new Font("Dialog",Font.PLAIN,12))+"\nOpacity: "+window.getOpacity()+"\n");
                        Files.writeString(output.resolve("insets.txt"),window.getInsets()+"\nbody="+preferredBody+"\n");
                        Files.writeString(output.resolve("window.txt"),((JDialog)window).getTitle()+"\n"+window.getPreferredSize().width+","+window.getPreferredSize().height+"\n"+window.getBounds()+"\n");
                        System.out.println("Reference dialog: "+((JDialog)window).getTitle()+" "+window.getBounds());
                        new Timer(8000,e->{window.dispose();System.exit(0);}).start();
                    }catch(Exception error){error.printStackTrace();System.exit(1);}
                }
            });timer.start();
            System.out.println("Constructing original region dialog");
            JFrame owner=new JFrame("TopSpin region dialog reference");owner.setSize(500,300);owner.setLocationRelativeTo(null);owner.setVisible(true);
            new GetPhysicalRangeDialog(owner,"add","Exact coordinates of integrated area",new PhysicalRangeIfc[]{new PhysicalRange(12.34567,-1.23456,"ppm")},new PhysicalRangeIfc[]{new PhysicalRange(15,-5,"ppm")});
        });
    }
}
