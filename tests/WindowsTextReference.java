import java.awt.*;
import javax.swing.*;

/** A test window in the bundled Windows JVM. No TopSpin classes or services are loaded. */
public class WindowsTextReference {
    public static String[] labels() {
        StringBuilder ascii=new StringBuilder();for(int i=32;i<127;i++)ascii.append((char)i);
        return new String[]{ascii.substring(0,48),ascii.substring(48),"Manual phase correction 123.456 +0.50 ppm","Open data: MLN1P121 / pdata / 1","Integrate; selected regions [1, 2, 3]","Zoom: 1.025, bias = -1234.56789"};
    }
    static int[] sizes={14,14,14,12,14,16};
    static Color[] backgrounds={new Color(4,48,77),new Color(4,48,77),new Color(4,48,77),Color.WHITE,new Color(207,207,207),Color.WHITE};
    static Color[] foregrounds={new Color(195,208,217),new Color(195,208,217),Color.WHITE,Color.BLACK,new Color(5,65,105),Color.BLACK};
    public static void main(String[] args) throws Exception {
        JFrame[] frame=new JFrame[1];
        SwingUtilities.invokeAndWait(()->{
            frame[0]=new JFrame("TopSpin replacement text reference");
            frame[0].setContentPane(new JPanel(){@Override protected void paintComponent(Graphics graphics){
                super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics;
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                for(int i=0;i<labels().length;i++) {
                    g.setColor(backgrounds[i]);g.fillRect(0,i*30,getWidth(),30);
                    g.setColor(foregrounds[i]);g.setFont(new Font("Dialog",Font.PLAIN,sizes[i]));g.drawString(labels()[i],20,20+i*30);
                }
            }});
            frame[0].setSize(650,220);frame[0].setVisible(true);
        });
        Thread.sleep(8000);SwingUtilities.invokeAndWait(frame[0]::dispose);
    }
}
