import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;

/** Reconstructs the target's native glyph masks and LCD color blending without Wine. */
public final class NativeText {
    static {System.loadLibrary("topspin_font");}
    private static final Map<String,int[]> masks=new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] coverage={0,0,104,124,140,154,167,178,189,199,208,217,225,233,240,248,255};
    private static final int[] gamma=new int[256],inverse=new int[256];
    static {for(int i=0;i<256;i++){gamma[i]=(int)(255*Math.pow(i/255.0,1.0/1.4));inverse[i]=(int)(255*Math.pow(i/255.0,1.4));}}
    private static native int[] glyph(String filename,int height,int index);
    private static int[] mask(int size,int index) {
        return masks.computeIfAbsent(size+":"+index,key->glyph("assets/fonts/arial.ttf",size,index));
    }
    private static int blend(int background,int foreground,int level) {
        if(level<=1)return background;if(level>=16)return foreground;
        int alpha=coverage[level],result=0xff000000;
        for(int shift:new int[]{16,8,0}) {
            int a=inverse[(foreground>>>shift)&255],b=inverse[(background>>>shift)&255];
            int mixed=(a*alpha+127)/255+(b*(255-alpha)+127)/255;
            result|=gamma[mixed]<<shift;
        }
        return result;
    }
    public static void draw(Graphics2D g,Font font,String text,double x,double baseline,Color foreground,Color background) {
        if(text.isEmpty())return;
        double sx=Math.abs(g.getTransform().getScaleX()),sy=Math.abs(g.getTransform().getScaleY());
        FontRenderContext context=new FontRenderContext(AffineTransform.getScaleInstance(sx,sy),RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        GlyphVector vector=font.createGlyphVector(context,text);
        int count=vector.getNumGlyphs(),height=(int)Math.round(font.getSize2D()*sy);
        int[][] glyphs=new int[count][];int[] positions=new int[count];int left=0,top=0,right=0,bottom=0;
        for(int i=0;i<count;i++) {
            int[] mask=mask(height,vector.getGlyphCode(i));glyphs[i]=mask;
            positions[i]=(int)Math.round(vector.getGlyphPosition(i).getX()*sx);
            left=Math.min(left,positions[i]+mask[0]);right=Math.max(right,positions[i]+mask[0]+mask[2]);
            top=Math.min(top,-mask[1]);bottom=Math.max(bottom,-mask[1]+mask[3]);
        }
        if(right<=left || bottom<=top)return;
        BufferedImage image=new BufferedImage(right-left,bottom-top,BufferedImage.TYPE_INT_RGB);
        int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();Arrays.fill(pixels,background.getRGB());
        for(int i=0;i<count;i++) {
            int[] mask=glyphs[i];
            for(int row=0;row<mask[3];row++)for(int col=0;col<mask[2];col++) {
                int pixel=(row-mask[1]-top)*image.getWidth()+positions[i]+mask[0]+col-left;
                pixels[pixel]=blend(pixels[pixel],foreground.getRGB(),mask[4+row*mask[2]+col]);
            }
        }
        AffineTransform transform=AffineTransform.getTranslateInstance(x+left/sx,baseline+top/sy);transform.scale(1/sx,1/sy);
        g.drawImage(image,transform,null);
    }
    private NativeText(){}
}
