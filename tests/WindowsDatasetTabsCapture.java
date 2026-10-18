package oracle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import com.formdev.flatlaf.FlatLightLaf;
import bruker.bio.topspin.bfw.gui.flowgui.*;

/** Retained original dataset tabs, without TopSpinMain, data or licensing services. */
public class WindowsDatasetTabsCapture {
    static class ReferenceUI extends de.bruker.nmr.mfw.base.DatasetPaneUI {int hoverIndex=-1;void hover(int index){hoverIndex=index;}@Override protected int getRolloverTab(){return hoverIndex;}}
    static class Tabs extends JTabbedPane implements BTabLabelIfc {
        @Override public String getUIClassID(){return "DatasetPaneUI";}
        @Override public int getIndexOf(String name){return indexOfTab(name);}
    }
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectory(output);
        FlatLightLaf.registerCustomDefaultsSource("bruker.bio.topspin");FlatLightLaf.setup();
        SwingUtilities.invokeAndWait(()->{JFrame frame=new JFrame("TopSpin dataset tabs reference");try {
            Tabs tabs=new Tabs();ReferenceUI referenceUI=new ReferenceUI();tabs.setUI(referenceUI);tabs.setFocusable(false);tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            for(String text:new String[]{"SPECTRUM","INTEGRALS","FID"}){tabs.addTab(text,new JPanel());tabs.setTabComponentAt(tabs.getTabCount()-1,new BTabLabel(text,text,tabs));}
            frame.setUndecorated(true);frame.setContentPane(tabs);frame.setSize(500,160);frame.setVisible(true);frame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);tabs.setSize(500,160);layout(tabs);
            StringBuilder bounds=new StringBuilder();
            for(int index=0;index<3;index++)bounds.append(index).append("\t").append(tabs.getBoundsAt(index)).append("\t").append(tabs.getTabComponentAt(index).getBounds()).append("\n");
            Files.writeString(output.resolve("bounds.txt"),bounds.toString());
            for(int selected=0;selected<3;selected++)for(int hover=-1;hover<3;hover++) {
                tabs.setSelectedIndex(selected);referenceUI.hover(hover);layout(tabs);
                BufferedImage image=new BufferedImage(1000,320,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.scale(2,2);tabs.paint(g);g.dispose();ImageIO.write(image,"png",output.resolve("tabs-"+selected+"-"+hover+".png").toFile());
            }
            referenceUI.hover(-1);tabs.repaint();
            Files.writeString(output.resolve("window.txt"),frame.getTitle()+"\n");
            new Timer(10000,event->{frame.dispose();System.exit(0);}).start();
            System.out.println(bounds);System.out.println("Captured retained dataset tabs: "+output);
        }catch(Exception error){frame.dispose();throw new RuntimeException(error);}});
    }
}
