import java.awt.Rectangle;
import java.text.*;
import java.util.Locale;

/** Coordinate, sample-index and amplitude text for the retained 1D cursor. */
public final class CursorReadout {
    public static String[] labels(VisualNumerics.Mapper mapper,Rectangle viewport,double coordinate,double frequency,
            int start,int count,double amplitude,double relativeReference) {
        double perPoint=Math.abs(mapper.physical(viewport.x)-mapper.physical(viewport.x+viewport.width))/viewport.width;
        int precision=0;for(;perPoint<1 && precision<8;perPoint*=10,precision++){}
        if(precision==0)precision=1;
        DecimalFormat format=new DecimalFormat("##.##",DecimalFormatSymbols.getInstance(Locale.ROOT));format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(precision);
        String coordinates=format.format(coordinate)+" ppm / "+format.format(coordinate*frequency)+" Hz";
        String index="Index = "+(start+1);if(count>1)index+=" - "+(start+1+count);
        return new String[]{coordinates,index,String.format(Locale.ROOT,"Value = %-10.4g %s",amplitude*relativeReference,"rel")};
    }
    private CursorReadout(){}
}
