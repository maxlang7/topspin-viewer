import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;
import com.formdev.flatlaf.ui.FlatTextFieldUI;

/** Retains Swing editing while matching the range dialog's grayscale text and baseline. */
@SuppressWarnings("deprecation")
public final class DialogFieldUI extends FlatTextFieldUI {
    @Override public View create(Element element) {
        return new FieldView(element) {
            private float draw(Graphics graphics,float x,float y,int from,int to,boolean selected)throws BadLocationException {
                JTextField field=(JTextField)getContainer();String text=getDocument().getText(from,to-from);
                Graphics2D g=(Graphics2D)graphics.create();
                Color foreground=selected?field.getSelectedTextColor():field.getForeground();
                g.setFont(field.getFont());g.setColor(foreground);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.drawString(text,x,y-.5f);g.dispose();
                return x+graphics.getFontMetrics(field.getFont()).stringWidth(text);
            }
            @Override protected int drawUnselectedText(Graphics g,int x,int y,int a,int b)throws BadLocationException{return (int)draw(g,x,y,a,b,false);}
            @Override protected int drawSelectedText(Graphics g,int x,int y,int a,int b)throws BadLocationException{return (int)draw(g,x,y,a,b,true);}
            @Override protected float drawUnselectedText(Graphics2D g,float x,float y,int a,int b)throws BadLocationException{return draw(g,x,y,a,b,false);}
            @Override protected float drawSelectedText(Graphics2D g,float x,float y,int a,int b)throws BadLocationException{return draw(g,x,y,a,b,true);}
        };
    }
}
