import java.awt.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import com.formdev.flatlaf.icons.FlatAscendingSortIcon;
import com.formdev.flatlaf.icons.FlatDescendingSortIcon;

/** Manual integral table, with the measured target column formats and sizing. */
public final class IntegralTable extends JScrollPane {
    public static final String[] NAMES={"Object","Integral [abs]","Integral [rel]","Peaks","ν(F1) [ppm]"};
    private static final String[] FORMATS={null,"0.00","0.0000","0","0.0000"};
    private final Icon sortUp=sortIcon(true),sortDown=sortIcon(false);
    public final JTable table=new JTable();
    private Object[][] values=new Object[0][5];
    public IntegralTable() {
        setViewport(new JViewport(){
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);graphics.setColor(Theme.color(new Color(245,245,245)));
                int width=table.getColumnModel().getTotalColumnWidth();
                for(int row=1;row*table.getRowHeight()<getHeight();row+=2)
                    graphics.fillRect(0,row*table.getRowHeight(),width,table.getRowHeight());
            }
        });getViewport().setBackground(Theme.color(Color.WHITE));
        setViewportView(table);setBorder(BorderFactory.createLineBorder(Theme.color(new Color(179,179,179))));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(28);
        table.setShowHorizontalLines(false);table.setShowVerticalLines(true);table.setIntercellSpacing(new Dimension(1,1));
        table.setGridColor(Theme.color(new Color(204,204,204)));
        table.setFont(Assets.regular);table.setForeground(Theme.color(Color.BLACK));table.setBackground(Theme.color(Color.WHITE));
        table.setModel(new AbstractTableModel() {
            public int getRowCount(){return values.length;}
            public int getColumnCount(){return NAMES.length;}
            public String getColumnName(int column){return NAMES[column];}
            public Object getValueAt(int row,int column){return values[row][column];}
        });
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer() {
            private int column;
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(getBackground());graphics.fillRect(0,0,getWidth(),getHeight());
                Graphics2D text=(Graphics2D)graphics.create();text.translate(0,column==0?1.5:-.5);getUI().paint(text,this);text.dispose();
            }
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column) {
                this.column=column;
                super.getTableCellRendererComponent(t,value,selected,focus,row,column);
                setText(display(value,column));setFont(Assets.regular);
                setBorder(BorderFactory.createEmptyBorder(1,column==0?21:10,1,10));
                setHorizontalAlignment(column==0?SwingConstants.LEFT:SwingConstants.RIGHT);
                if(!selected)setBackground(row%2==0?Theme.color(Color.WHITE):Theme.color(new Color(245,245,245)));return this;
            }
        });
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);graphics.setColor(Theme.color(new Color(204,204,204)));
                for(int column=0;column<getColumnModel().getColumnCount();column++) {
                    Rectangle rect=getHeaderRect(column);graphics.fillRect(rect.x+rect.width-1,0,1,getHeight()-1);
                }
            }
        });
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer(){
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(getBackground());graphics.fillRect(0,0,getWidth(),getHeight());
                Graphics2D text=(Graphics2D)graphics.create();text.translate(0,-.5);getUI().paint(text,this);text.dispose();
            }
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column) {
                super.getTableCellRendererComponent(t,value,false,false,row,column);
                setFont(Assets.regular);setBackground(Theme.color(new Color(230,230,230)));setForeground(Theme.color(Color.BLACK));
                setBorder(BorderFactory.createEmptyBorder(2,3,2,3));setHorizontalAlignment(SwingConstants.CENTER);
                setIcon(column==0?sortUp:null);return this;
            }
        });
        table.getTableHeader().setFont(Assets.regular);table.getTableHeader().setPreferredSize(new Dimension(1,28));
        table.getTableHeader().setBackground(Theme.color(new Color(230,230,230)));table.getTableHeader().setForeground(Theme.color(Color.BLACK));
        setColumnHeaderView(table.getTableHeader());
    }
    public void load(Spectrum spectrum) {
        var regions=spectrum.integrals.regions();values=new Object[regions.size()][5];
        for(int i=0;i<regions.size();i++) {
            var region=regions.get(i);var integral=spectrum.integrals.calculate(region,spectrum.real,spectrum.left,spectrum.right);
            values[i]=new Object[]{"Integral "+(i+1),integral.value(),integral.normalized(spectrum.integrals.normalizingFactor),0,(region.left()+region.right())/2};
        }
        ((AbstractTableModel)table.getModel()).fireTableDataChanged();
        FontMetrics metrics=table.getFontMetrics(Assets.bold);
        for(int column=0;column<NAMES.length;column++) {
            int width=metrics.stringWidth(NAMES[column]);
            for(Object[] row:values)width=Math.max(width,metrics.stringWidth(display(row[column],column)));
            // Original sizing reserves ten pixels for sorting, twenty for
            // cell padding and one for intercell spacing.
            table.getColumnModel().getColumn(column).setPreferredWidth(width+31);
        }
    }
    public static String display(Object value,int column) {
        return value instanceof Number && FORMATS[column]!=null
            ?new DecimalFormat(FORMATS[column],DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value):String.valueOf(value);
    }
    private static Icon sortIcon(boolean ascending) {
        Icon icon=ascending?new FlatAscendingSortIcon(){
            {chevron=true;}
            @Override protected void paintIcon(Component component,Graphics2D graphics){graphics.setColor(Theme.color(Color.BLACK));paintArrow(component,graphics,true);}
        }:new FlatDescendingSortIcon(){
            {chevron=true;}
            @Override protected void paintIcon(Component component,Graphics2D graphics){graphics.setColor(Theme.color(Color.BLACK));paintArrow(component,graphics,true);}
        };
        return new Icon(){
            public int getIconWidth(){return icon.getIconWidth();}
            public int getIconHeight(){return icon.getIconHeight();}
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                Graphics2D drawing=(Graphics2D)graphics.create();drawing.translate(0,.5);icon.paintIcon(component,drawing,x,y);drawing.dispose();
            }
        };
    }
}
