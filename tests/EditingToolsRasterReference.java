import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public final class EditingToolsRasterReference {
    static BufferedImage raster(JComponent component) {
        component.setSize(component.getPreferredSize());if(component instanceof Container container)container.doLayout();
        BufferedImage image=new BufferedImage(component.getWidth()*2,component.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);component.paint(g);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{try {
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-phase.png").toFile());long compared=0;
            String[] controls={"0 order correction","1st order correction","Reset phase angles","Add 90 degrees","Subtract 90 degrees","Add 180 degrees","Increase mouse sensitivity","Decrease mouse sensitivity","Reset mouse sensitivity","Save for nD spectrum","Return & save phased spectrum","Return, do NOT save"};
            for(boolean retainNd:new boolean[]{true,false}) {
                EditingTools panel=new EditingTools();
                for(int i=0;i<controls.length;i++){if(i!=9 || retainNd)panel.addTool("phase",controls[i],()->{});if(i==2 || i==5 || i==8)panel.separator();}
                BufferedImage actual=raster(panel);
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++) {
                    int originalX=!retainNd && x>=562?x+60:x;compared++;
                    if(actual.getRGB(x,y)!=source.getRGB(1118+originalX,338+y))throw new AssertionError("Phase toolbar pixel "+x+","+y+" differs");
                }
            }
            String[] integralControls={"Define new region using cursor (toggle)","Define new region using cursor, use automated baseline correction (toggle)","Define new region via dialog","Cut integral in two parts (toggle)","Select/Deselect all regions (toggle)","Delete selected regions","Undo last region operation","Interactive bias correction","Interactive slope correction","Set slope and bias to zero","Width [ppm] for bias determination","Increase mouse sensitivity","Decrease mouse sensitivity","Reset mouse sensitivity","Integrals larger by factor 2","Integrals smaller by factor 2","Integrals smaller/larger","Define scaling factor via dialog","Autoscale all integrals to same height (toggle)","Show bottom limit","Show top limit","Shift integrals up/down","Read / Import integration regions","Save / Export integration regions","Return, save regions","Return, do NOT save regions"};
            java.util.Set<Integer> separators=java.util.Set.of(3,6,10,13,17,18,21,23);
            java.util.Set<Integer> omitted=java.util.Set.of(1,10,16,17,18,19,20,22,23);
            int[] originalStarts=new int[integralControls.length];int originalPosition=2;
            for(int i=0;i<integralControls.length;i++){originalStarts[i]=originalPosition;originalPosition+=30;if(separators.contains(i))originalPosition+=3;}
            source=ImageIO.read(Path.of("evidence/ui/topspin-integration.png").toFile());
            for(boolean full:new boolean[]{true,false}) {
                EditingTools panel=new EditingTools();java.util.List<Integer> columns=new java.util.ArrayList<>();columns.add(0);columns.add(1);
                for(int i=0;i<integralControls.length;i++) {
                    if(!full && omitted.contains(i))continue;
                    panel.addTool("integration",integralControls[i],()->{});
                    for(int x=0;x<30;x++)columns.add(originalStarts[i]+x);
                    if(full?separators.contains(i):java.util.Set.of(3,6,9,13,15,21).contains(i)) {
                        panel.separator();for(int x=0;x<3;x++)columns.add(122+x);
                    }
                }
                columns.add(originalPosition);columns.add(originalPosition+1);
                BufferedImage actual=raster(panel);
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++) {
                    int originalX=full?x:columns.get(x/2)*2+x%2;compared++;
                    if(actual.getRGB(x,y)!=source.getRGB(938+originalX,338+y))throw new AssertionError("Integration toolbar full="+full+" pixel "+x+","+y+" differs: "+Integer.toHexString(actual.getRGB(x,y))+" / "+Integer.toHexString(source.getRGB(938+originalX,338+y)));
                }
            }
            for(String mode:new String[]{"phase","integration"}) {
                String text=mode.equals("phase")?"Interactive phase correction":"Integration";
                ModeLabel label=new ModeLabel(text,Assets.icon(mode,"text."+text));BufferedImage actual=raster(label);
                source=ImageIO.read(Path.of("evidence/ui/topspin-"+mode+".png").toFile());
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){compared++;if(actual.getRGB(x,y)!=source.getRGB(704+x,338+y))throw new AssertionError(mode+" mode label pixel "+x+","+y+" differs");}
            }
            System.out.println("PASS phase/integration toolbars, retained controls and both mode labels: "+compared+" exact captured Windows pixels");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
