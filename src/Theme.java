import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;

/** Production uses Gruvbox Dark Hard. Reference renderers retain their captured palette. */
public final class Theme {
    private static boolean active;
    public static final Color BG=new Color(0x1d2021),PANEL=new Color(0x282828),RAISED=new Color(0x3c3836),BORDER=new Color(0x504945),FG=new Color(0xebdbb2),MUTED=new Color(0xa89984),AQUA=new Color(0x8ec07c),YELLOW=new Color(0xfabd2f),RED=new Color(0xfb4934);
    public static void install(Font font){
        active=true;
        com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(java.util.Map.of("@background","#1d2021","@foreground","#ebdbb2","@accentColor","#8ec07c","@selectionBackground","#504945","@selectionForeground","#ebdbb2","@disabledForeground","#a89984"));
        FlatDarkLaf.setup();UIManager.put("defaultFont",font);
        for(String prefix:new String[]{"Label","Panel","Viewport","Tree","Table","TextField","TextArea","FormattedTextField","EditorPane","List","FileChooser","OptionPane","PopupMenu","Menu","MenuItem","RadioButtonMenuItem","CheckBoxMenuItem","CheckBox","RadioButton","ComboBox","ToolTip"}){
            UIManager.put(prefix+".background",BG);UIManager.put(prefix+".foreground",FG);
            UIManager.put(prefix+".selectionBackground",BORDER);UIManager.put(prefix+".selectionForeground",FG);
        }
        for(String prefix:new String[]{"Button","ToggleButton","TableHeader","ScrollBar","ToolBar","TabbedPane","TitlePane"}){UIManager.put(prefix+".background",PANEL);UIManager.put(prefix+".foreground",FG);}
        for(String prefix:new String[]{"Tree","Table","List"}){UIManager.put(prefix+".selectionInactiveBackground",BORDER);UIManager.put(prefix+".selectionInactiveForeground",FG);}
        UIManager.put("Button.default.background",AQUA);UIManager.put("Button.default.foreground",BG);UIManager.put("Button.default.hoverBackground",AQUA);UIManager.put("Button.default.pressedBackground",MUTED);UIManager.put("Button.default.borderColor",AQUA);UIManager.put("Button.default.focusedBorderColor",AQUA);UIManager.put("Button.default.hoverBorderColor",AQUA);
        for(String prefix:new String[]{"Component","TextField","ComboBox","Button","Spinner"})UIManager.put(prefix+".focusedBorderColor",AQUA);
        UIManager.put("Button.hoverBackground",RAISED);UIManager.put("Button.pressedBackground",BORDER);UIManager.put("Button.focusedBackground",RAISED);
        UIManager.put("ComboBox.buttonBackground",PANEL);UIManager.put("ComboBox.buttonArrowColor",FG);UIManager.put("ComboBox.buttonHoverArrowColor",AQUA);
        UIManager.put("Separator.foreground",BORDER);UIManager.put("ScrollBar.thumb",BORDER);UIManager.put("ScrollBar.hoverThumbColor",MUTED);UIManager.put("ScrollBar.track",BG);
        UIManager.put("OptionPane.icon.errorColor",RED);UIManager.put("OptionPane.icon.questionColor",AQUA);UIManager.put("OptionPane.icon.informationColor",AQUA);UIManager.put("OptionPane.icon.warningColor",YELLOW);
        UIManager.put("Component.borderColor",BORDER);UIManager.put("Component.focusColor",AQUA);UIManager.put("Component.accentColor",AQUA);
        UIManager.put("TextComponent.selectionBackground",BORDER);UIManager.put("TextComponent.selectionForeground",FG);UIManager.put("TextField.caretForeground",FG);
        UIManager.put("TitlePane.unifiedBackground",false);UIManager.put("TitlePane.inactiveBackground",PANEL);UIManager.put("TitlePane.inactiveForeground",MUTED);
        UIManager.put("Button.arc",6);UIManager.put("Component.arc",6);UIManager.put("TextComponent.arc",6);
    }
    public static Color foreground(Color reference){return active?FG:reference;}
    public static Color heading(){return active?AQUA:new Color(5,65,105);}
    public static Color placeholder(){return active?MUTED:new Color(204,204,204);}
    public static Color border(){return active?BORDER:Color.BLACK;}
    public static void frame(JFrame frame){if(active){frame.getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND,BG);frame.getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND,FG);}}
    public static Color color(Color source){
        if(!active || source.getAlpha()==0)return source;
        int rgb=source.getRGB()&0xffffff;Color target;
        target=switch(rgb){
            case 0x054169 -> BG;
            case 0x04304d -> PANEL;
            case 0xc3d0d9,0x333333,0x000000 -> FG;
            case 0xaec8d9,0xafc9d8,0x646464,0xc8ff00 -> BORDER;
            case 0x0000ff,0x008deb,0x6496be -> AQUA;
            case 0xed1c24 -> YELLOW;
            case 0x878787 -> MUTED;
            default -> null;
        };
        if(target==null && source.getRed()==source.getGreen() && source.getGreen()==source.getBlue()){
            int value=source.getRed();target=value>=250?BG:value>=240?PANEL:value>=200?RAISED:value>=160?BORDER:value>=100?MUTED:FG;
        }
        if(target==null)target=source;
        return new Color(target.getRed(),target.getGreen(),target.getBlue(),source.getAlpha());
    }
    public static String style(String original){
        if(!active)return original;
        var matcher=java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(original);StringBuffer result=new StringBuffer();
        while(matcher.find()){Color c=matcher.group().equalsIgnoreCase("#333333")?BORDER:color(Color.decode(matcher.group()));matcher.appendReplacement(result,String.format("#%06x",c.getRGB()&0xffffff));}matcher.appendTail(result);return result.toString();
    }
    public static ImageIcon icon(ImageIcon original){
        if(!active || original==null)return original;
        Image[] sizes=new Image[2];
        for(int scale=1;scale<=2;scale++){
            BufferedImage image=new BufferedImage(original.getIconWidth()*scale,original.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();g.scale(scale,scale);original.paintIcon(null,g,0,0);g.setComposite(AlphaComposite.SrcIn);g.setColor(FG);g.fillRect(0,0,original.getIconWidth(),original.getIconHeight());g.dispose();sizes[scale-1]=image;
        }
        return new ImageIcon(new BaseMultiResolutionImage(sizes));
    }
    public static Image appIcon(){
        BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(BG);g.fillRoundRect(0,0,64,64,12,12);g.setColor(AQUA);g.setStroke(new BasicStroke(3));
        g.drawPolyline(new int[]{6,17,21,25,29,32,35,38,43,47,51,58},new int[]{46,46,40,46,46,13,46,46,32,46,46,46},12);g.dispose();return image;
    }
    private Theme(){}
}
