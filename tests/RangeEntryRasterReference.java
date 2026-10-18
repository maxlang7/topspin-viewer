import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Original Windows pixels and layouts, using the same 2x component metrics as the app. */
public class RangeEntryRasterReference {
    private static final Path REFERENCE=Path.of("artifacts/windows-region-dialog-reference-20261018-18");
    private static JFrame host;
    private static RangeEntryPane pane(int width,int height) {
        RangeEntryPane pane=new RangeEntryPane("12.3457","-1.2346",()->{},()->{});
        host.setContentPane(pane);host.pack();pane.setSize(width,height);pane.doLayout();return pane;
    }
    private static BufferedImage render(Component component) {
        BufferedImage image=new BufferedImage(component.getWidth()*2,component.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();graphics.scale(2,2);component.paint(graphics);graphics.dispose();return image;
    }
    private static long compare(BufferedImage actual,String name,int side,int bottom)throws Exception {
        BufferedImage expected=ImageIO.read(REFERENCE.resolve(name).toFile());
        if(actual.getWidth()!=expected.getWidth() || actual.getHeight()!=expected.getHeight())throw new AssertionError("Dialog dimensions differ: "+name);
        long different=0,pixels=0;
        for(int y=0;y<actual.getHeight()-bottom;y++)for(int x=side;x<actual.getWidth()-side;x++) {
            pixels++;if(actual.getRGB(x,y)!=expected.getRGB(x,y))different++;
        }
        if(different!=0) {
            ImageIO.write(actual,"png",Path.of("artifacts/region-dialog-mismatch-"+name).toFile());
            throw new AssertionError(different+" dialog pixels differ: "+name);
        }
        return pixels;
    }
    private static void verify()throws Exception {
        int[][] sizes={{215,151},{320,220},{550,350},{215,190},{440,151}};long pixels=0;
        for(int index=0;index<sizes.length;index++)pixels+=compare(render(pane(sizes[index][0],sizes[index][1])),"body-"+index+".png",0,0);
        for(int state=0;state<4;state++) {
            RangeEntryPane pane=pane(215,151);JTextField field=state==3?pane.to:pane.from;
            field.putClientProperty(com.formdev.flatlaf.FlatClientProperties.COMPONENT_FOCUS_OWNER,(java.util.function.Predicate<JComponent>)(component->true));
            field.select(state==2?2:0,state==0?0:field.getText().length());field.getCaret().setVisible(false);field.getCaret().setSelectionVisible(state!=0);
            pixels+=compare(render(pane),"focus-"+state+".png",0,0);
        }
        RangeEntryPane pane=pane(215,151);int layouts=0;
        for(String row:Files.readAllLines(REFERENCE.resolve("layouts.tsv"))) {
            int[] cells=Arrays.stream(row.split("\t")).mapToInt(Integer::parseInt).toArray();pane.setSize(cells[0],cells[1]);pane.doLayout();
            JTextField[] fields={pane.from,pane.to};
            for(int i=0;i<fields.length;i++) {
                Rectangle bounds=fields[i].getBounds();int a=2+4*i;
                if(bounds.x!=cells[a] || bounds.y!=cells[a+1] || bounds.width!=cells[a+2] || bounds.height!=cells[a+3])throw new AssertionError("Dialog field layout differs: "+row);
            }
            layouts++;
        }
        BufferedImage screen=ImageIO.read(REFERENCE.resolve("screen.png").toFile());
        BufferedImage body=ImageIO.read(REFERENCE.resolve("body.png").toFile());
        // The compositor's client crop starts 32 physical pixels above the content.
        for(int y=0;y<body.getHeight();y++)for(int x=0;x<body.getWidth();x++)if(screen.getRGB(x,y+32)!=body.getRGB(x,y))throw new AssertionError("On-screen reference differs from the isolated body at "+x+","+y);
        var constructor=RangeEntryDialog.class.getDeclaredConstructor(JFrame.class,double.class,double.class);constructor.setAccessible(true);
        RangeEntryDialog dialog=constructor.newInstance(host,12.34567,-1.23456);
        try {
            dialog.setModalityType(Dialog.ModalityType.MODELESS);dialog.setVisible(true);
            // Original Windows non-client side/bottom borders are not painted by Window.paint.
            pixels+=compare(render(dialog),"window-unfocused.png",4,4);
        }finally{dialog.dispose();}
        System.out.println("PASS 5 Windows dialog sizes, 4 focused/selected fields and caption: "+pixels+" exact pixels; "+layouts+" field layouts; "+body.getWidth()*body.getHeight()+" on-screen reference pixels");
    }
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{
            host=new JFrame();KeyboardFocusManager previous=KeyboardFocusManager.getCurrentKeyboardFocusManager();
            // Match the reference's controlled focus state without relying on desktop focus timing.
            KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Window getActiveWindow(){return host;}});
            try{verify();}catch(Exception error){throw new RuntimeException(error);}
            finally{KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);host.dispose();}
        });
    }
}
