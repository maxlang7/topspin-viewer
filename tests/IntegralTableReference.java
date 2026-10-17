import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public class IntegralTableReference {
    public static void main(String[] args) throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        Spectrum spectrum=new Spectrum(Path.of(args[0]));List<String> rows=Files.readAllLines(Path.of(args[1]));
        IntegralTable table=new IntegralTable();table.load(spectrum);
        String[] widths=rows.get(0).split("\t",-1);int comparisons=0;
        if(table.table.getRowCount()!=rows.size()-1)throw new AssertionError("Integral table row count");
        for(int column=0;column<5;column++) {
            int actual=table.table.getColumnModel().getColumn(column).getPreferredWidth();
            if(actual!=Integer.parseInt(widths[column]))throw new AssertionError("Integral column width "+column+": "+actual+" vs "+widths[column]);
        }
        for(int row=0;row<table.table.getRowCount();row++) {
            String[] cells=rows.get(row+1).split("\t",-1);
            for(int column=0;column<5;column++) {
                Object value=table.table.getValueAt(row,column);
                if(value instanceof Number) {
                    if(Double.doubleToRawLongBits(((Number)value).doubleValue())!=Double.doubleToRawLongBits(Double.parseDouble(cells[column])))throw new AssertionError("Integral table value");
                }else if(!value.toString().equals(cells[column]))throw new AssertionError("Integral table label");
                if(column>0 && !IntegralTable.display(value,column).equals(cells[column+5]))throw new AssertionError("Integral display format");comparisons++;
            }
        }
        System.out.println("PASS "+comparisons+" original integral-table cells, exact display text and five column widths");
    }
}
