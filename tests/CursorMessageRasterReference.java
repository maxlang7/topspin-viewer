import java.awt.*;

public final class CursorMessageRasterReference {
    static java.awt.image.BufferedImage canvas(int width,int height,int scale) {
        var image=new java.awt.image.BufferedImage((width+8)*scale,(height+8)*scale,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(245,245,245));g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();return image;
    }
    public static void main(String[] args)throws Exception {
        Assets.load();int cases=0;long pixels=0;
        for(int scale:new int[]{1,2})for(int offset:new int[]{0,22})for(Color color:new Color[]{Color.BLACK,new Color(51,51,51)})
        for(String[] labels:new String[][]{{"Mouse Sensitivity: 1.0",null,"Sum = 5.3045",null,null,null},{"a","","c"},{null,"value = 3.44"},{null,null}}) {
            var expected=javax.imageio.ImageIO.read(java.nio.file.Path.of("artifacts/windows-cursor-message-reference",cases+".png").toFile());
            var actual=canvas(430,150,scale);
            Graphics2D g;
            g=actual.createGraphics();g.scale(scale,scale);g.setColor(new Color(237,28,36));g.fillRect(120,0,3,150);g.setColor(Color.BLUE);g.drawLine(0,35,430,35);
            CursorMessagePainter.paint(g,labels,new Rectangle(2,2,400,140),offset,color);g.dispose();
            long different=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){pixels++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0)throw new AssertionError(different+" cursor message pixels differ at scale="+scale+" offset="+offset);cases++;
        }
        System.out.println("PASS "+cases+" actual Windows cursor message cases, "+pixels+" exact pixels");
    }
}
