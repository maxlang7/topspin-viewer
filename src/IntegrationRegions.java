import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Manual integration regions, using TopSpin's inclusive bounds and file units. */
public final class IntegrationRegions {
    public record Region(double left,double right,double bias,double slope) {}
    private final List<Region> regions=new ArrayList<>();
    private final Deque<List<Region>> history=new ArrayDeque<>();
    public double normalizingFactor=1;
    public List<Region> regions() {return List.copyOf(regions);}
    private void checkpoint() {history.push(List.copyOf(regions));}
    public void add(double a,double b) {checkpoint();regions.add(new Region(Math.max(a,b),Math.min(a,b),0,0));sort();}
    public void remove(int index) {checkpoint();regions.remove(index);}
    public void replace(int index,Region region) {checkpoint();regions.set(index,region);sort();}
    public void undo() {if(!history.isEmpty()){regions.clear();regions.addAll(history.pop());}}
    private void sort() {regions.sort(Comparator.comparingDouble(Region::left).reversed());}
    public Numerics.Integral calculate(Region region,double[] spectrum,double fullLeft,double fullRight) {
        double a=Math.min(fullLeft,region.left()),b=Math.max(fullRight,region.right());
        if(a<b)throw new IllegalArgumentException("Integral region is outside the spectrum");
        int start=Math.max(0,Math.min(spectrum.length-1,Numerics.index(a,fullLeft,fullRight,spectrum.length)));
        int end=Math.max(start,Math.min(spectrum.length-1,Numerics.index(b,fullLeft,fullRight,spectrum.length)));
        return Numerics.integrate(Arrays.copyOfRange(spectrum,start,end+1),region.bias(),region.slope(),false,1);
    }
    public void calibrate(int index,double value,double[] spectrum,double left,double right) {
        double integral=calculate(regions.get(index),spectrum,left,right).value();
        if(!Double.isFinite(value) || integral==0)throw new IllegalArgumentException("Cannot calibrate a zero integral");
        normalizingFactor=value/integral;
    }
    public static IntegrationRegions read(Path file,int nc,double intScale) throws IOException {
        IntegrationRegions result=new IntegrationRegions();
        double scale=Math.pow(2.0,nc);result.normalizingFactor=intScale/scale;
        if(!Files.isRegularFile(file))return result;
        List<String> lines=Files.readAllLines(file);
        if(lines.isEmpty() || !lines.get(0).startsWith("A 1.0"))throw new IOException("Unsupported integration-region format");
        for(int i=1;i<lines.size();i++) {
            String line=lines.get(i).split("#",2)[0].trim();if(line.isEmpty())continue;
            String[] cells=line.split("\\s+");if(cells.length<4)throw new IOException("Invalid integration-region row");
            try {double a=Double.parseDouble(cells[0]),b=Double.parseDouble(cells[1]);
                double bias=-1.0*scale*Double.parseDouble(cells[2]);
                double slope=-1.0*scale*Double.parseDouble(cells[3]);
                if(!Double.isFinite(a+b+bias+slope))throw new NumberFormatException();
                result.regions.add(new Region(a,b,bias,slope));
            } catch(NumberFormatException e) {throw new IOException("Invalid integration-region number",e);}
        }
        result.sort();return result;
    }
    public String serialize(int nc) {
        double scale=Math.pow(2.0,nc);
        StringBuilder text=new StringBuilder("A 1.0 #regions in PPM\n# low field   high field  bias        slope\n");
        int index=0;
        for(Region r:regions)text.append("  ").append(r.left()).append("  ").append(r.right()).append("  ")
            .append(-1.0*r.bias()/scale).append("  ").append(-1.0*r.slope()/scale)
            .append("  # for region ").append(++index).append('\n');
        return text.toString();
    }
}
