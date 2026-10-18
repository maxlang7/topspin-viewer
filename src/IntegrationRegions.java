import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Manual integration regions, using TopSpin's inclusive bounds and file units. */
public final class IntegrationRegions {
    public record Region(double left,double right,double bias,double slope) {
        public Region {
            if(!Double.isFinite(left) || !Double.isFinite(right) || !Double.isFinite(bias) || !Double.isFinite(slope) || left<right)
                throw new IllegalArgumentException("Invalid integral region");
        }
    }
    private final List<Region> regions=new ArrayList<>();
    private List<Region> previous=List.of();
    private Region selectedObject;
    private final Set<Region> selection=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean selectAllNext=true;
    public record DisplayScale(double factor,double relative,boolean hideFactor) {}
    private static final DisplayScale DEFAULT_DISPLAY=new DisplayScale(.8,1,true);
    private final Map<Region,DisplayScale> displayScales=new IdentityHashMap<>();
    public DisplayScale displayScale(int index) {return displayScales.getOrDefault(regions.get(index),DEFAULT_DISPLAY);}
    public void scaleSelected(double amount) {
        List<Integer> selected=selectedIndices();boolean hide=selected.isEmpty() || selected.size()==regions.size();
        for(int index=0;index<regions.size();index++)if(selected.isEmpty() || isSelected(index)) {
            DisplayScale old=displayScale(index);
            displayScales.put(regions.get(index),new DisplayScale(Math.max(1e-7,amount*old.factor()),amount*old.relative(),hide));
        }
    }
    private int calibrationReference=0;
    private double calibrationValue=1;
    private double lastBias,lastSlope;
    public void resetLastCorrections(){lastBias=lastSlope=0;}
    public double normalizingFactor=1;
    public List<Region> regions() {return List.copyOf(regions);}
    public IntegrationRegions copy() {
        IntegrationRegions result=new IntegrationRegions();result.regions.addAll(regions);
        result.normalizingFactor=normalizingFactor;result.calibrationReference=calibrationReference;
        result.calibrationValue=calibrationValue;result.selection.addAll(selection);
        result.selectAllNext=selectAllNext;result.displayScales.putAll(displayScales);
        result.lastBias=lastBias;result.lastSlope=lastSlope;return result;
    }
    private void checkpoint() {previous=List.copyOf(regions);}
    public void select(int index) {selectedObject=index<0?null:regions.get(index);}
    public boolean isSelected(int index) {return selection.contains(regions.get(index));}
    public List<Integer> selectedIndices() {
        List<Integer> result=new ArrayList<>();
        for(int i=0;i<regions.size();i++)if(isSelected(i))result.add(i);
        return result;
    }
    public void toggleSelectAll() {
        selection.clear();if(selectAllNext)selection.addAll(regions);selectAllNext=!selectAllNext;
    }
    /** Selection flags and the last clicked object are independent in the original controller. */
    public int selectRegion(int hit,double position,double fullWidth,boolean shift,boolean control) {
        Region clicked=hit<0?null:regions.get(hit);selectedObject=clicked;
        if(!shift && !control)selection.removeIf(region->region!=clicked);
        if(clicked==null)return -1;
        Region chosen=clicked;double width=fullWidth;
        for(Region region:regions)if(position<=region.left() && position>=region.right() && region.left()-region.right()<width) {
            chosen=region;width=region.left()-region.right();
        }
        if(!selection.remove(chosen)) {
            selection.add(chosen);
            if(shift) {
                List<Region> ordered=sorted();int current=-1;
                for(int i=0;i<ordered.size();i++)if(ordered.get(i)==chosen){current=i;break;}
                int before=-1,after=-1;
                for(int i=0;i<current;i++)if(selection.contains(ordered.get(i)))before=i;
                for(int i=current+1;i<ordered.size();i++)if(selection.contains(ordered.get(i))){after=i;break;}
                if(before>=0)selection.addAll(ordered.subList(before,current));
                else if(after>=0)selection.addAll(ordered.subList(current,after));
            }
        }
        for(int i=0;i<regions.size();i++)if(regions.get(i)==chosen)return i;
        return -1;
    }
    public void selectContained(double a,double b) {
        double left=Math.max(a,b),right=Math.min(a,b);
        for(Region region:regions)if(left>=region.left() && right<=region.right())selection.add(region);
    }
    public void add(double a,double b) {checkpoint();regions.add(new Region(Math.max(a,b),Math.min(a,b),0,0));}
    public void addFromDialog(double a,double b,double[] spectrum,double fullLeft,double fullRight) {
        a=Math.min(fullLeft,Math.max(fullRight,a));b=Math.min(fullLeft,Math.max(fullRight,b));
        checkpoint();regions.add(new Region(Math.max(a,b),Math.min(a,b),lastBias,lastSlope));
        calibrationValue=1;normalize(spectrum,fullLeft,fullRight);
    }
    public void remove(int index) {
        checkpoint();if(regions.size()==1){calibrationReference=0;calibrationValue=1;}
        Region removed=regions.remove(index);selection.remove(removed);displayScales.remove(removed);
    }
    public boolean removeSelected() {
        if(selection.isEmpty())return false;
        checkpoint();if(selection.size()==regions.size()){calibrationReference=0;calibrationValue=1;}
        for(Region region:selection)displayScales.remove(region);
        regions.removeIf(selection::contains);selection.clear();return true;
    }
    public void replace(int index,Region region) {
        Region old=regions.set(index,region);if(selectedObject==old)selectedObject=region;
        if(selection.remove(old))selection.add(region);
        DisplayScale display=displayScales.remove(old);if(display!=null)displayScales.put(region,display);
    }
    public void adjustBias(int index,double amount,double sensitivity) {
        Region r=regions.get(index);double correction=amount*sensitivity*1000.0;
        replace(index,new Region(r.left(),r.right(),r.bias()-correction,r.slope()));lastBias=regions.get(index).bias();
    }
    public void adjustSlope(int index,double amount,double sensitivity,double[] spectrum,double left,double right) {
        Region r=regions.get(index);double correction=amount*sensitivity*1000.0;
        int points=calculate(r,spectrum,left,right).curve().length;
        replace(index,new Region(r.left(),r.right(),r.bias(),r.slope()-correction/points));lastSlope=regions.get(index).slope();
    }
    public void split(int index,double position,double fullLeft,double fullRight,int size) {
        Region r=regions.get(index);
        if(!Double.isFinite(position) || position<r.right() || position>r.left())return;
        double start=Math.min((r.left()-fullLeft)/(fullRight-fullLeft)*size,size-1.0);
        double cut=Math.min((position-fullLeft)/(fullRight-fullLeft)*size,size-1.0);
        checkpoint();selection.remove(regions.remove(index));displayScales.remove(r);
        regions.add(new Region(r.left(),position,r.bias(),r.slope()));
        regions.add(new Region(Math.min(fullLeft,position+1e-16),r.right(),r.bias()+(cut-start)*r.slope(),r.slope()));
    }
    public boolean undo() {
        if(previous.isEmpty())return false;
        List<Region> current=List.copyOf(regions);
        double bias=selectedObject==null?0:selectedObject.bias(),slope=selectedObject==null?0:selectedObject.slope();
        selection.clear();displayScales.clear();regions.clear();for(Region r:previous)regions.add(new Region(r.left(),r.right(),bias,slope));
        previous=current;
        return true;
    }
    public void normalize(double[] spectrum,double left,double right) {
        if(regions.isEmpty())return;
        double[] values=new double[regions.size()];
        for(int i=0;i<values.length;i++)values[i]=calculate(regions.get(i),spectrum,left,right).value();
        normalizingFactor=IntegralScale.factor(values,calibrationReference,calibrationValue,normalizingFactor,0);
    }
    public void preserveScale(){calibrationReference=-2;}
    private List<Region> sorted() {List<Region> sorted=new ArrayList<>(regions);sorted.sort(Comparator.comparingDouble(Region::left).reversed());return sorted;}
    public Numerics.Integral calculate(Region region,double[] spectrum,double fullLeft,double fullRight) {
        double a=Math.min(fullLeft,region.left()),b=Math.max(fullRight,region.right());
        if(a<b)throw new IllegalArgumentException("Integral region is outside the spectrum");
        int start=Math.max(0,Math.min(spectrum.length,Numerics.index(a,fullLeft,fullRight,spectrum.length)));
        int end=Math.max(start,Math.min(spectrum.length,Numerics.index(b,fullLeft,fullRight,spectrum.length)));
        return Numerics.integrate(Arrays.copyOfRange(spectrum,start,end+1),region.bias(),region.slope(),false,1);
    }
    public void calibrate(int index,double value,double[] spectrum,double left,double right) {
        double integral=calculate(regions.get(index),spectrum,left,right).value();
        if(!Double.isFinite(value))throw new IllegalArgumentException("Integral calibration must be finite");
        normalizingFactor=IntegralScale.factor(new double[]{integral},0,value,0,0);
        calibrationReference=-2;calibrationValue=value;
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
        if(!result.regions.isEmpty())result.preserveScale();
        return result;
    }
    public String serialize(int nc) {
        double scale=Math.pow(2.0,nc);
        StringBuilder text=new StringBuilder("A 1.0 #regions in PPM\n# low field   high field  bias        slope\n");
        int index=0;
        for(Region r:sorted())text.append("  ").append(r.left()).append("  ").append(r.right()).append("  ")
            .append(-1.0*r.bias()/scale).append("  ").append(-1.0*r.slope()/scale)
            .append("  # for region ").append(++index).append('\n');
        return text.toString();
    }
    public String table(String name,String expno,String procno,String directory,
            double[] spectrum,double left,double right) {
        StringBuilder text=new StringBuilder("   Current data set:\n   NAME =\t")
            .append(name).append("\tEXPNO =\t").append(expno).append("\tPROCNO = ").append(procno)
            .append("\n   DU   =\t").append(directory)
            .append("\n\n   Number   Integrated Region     Integral\n");
        java.text.DecimalFormat format=new java.text.DecimalFormat("0.000",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
        int index=0;
        for(Region r:sorted()) {
            String number=Integer.toString(++index);format.applyPattern("0.000");
            String a=padDecimal(format.format(r.left()),7),b=padDecimal(format.format(r.right()),7);
            double value=calculate(r,spectrum,left,right).normalized(normalizingFactor);
            format.applyPattern(value<1_000_000?"0.00000":"0.00000E00");
            String integral=padDecimal(format.format(value),10);
            text.append(" ".repeat(Math.max(0,6-number.length()))).append(number).append(a).append(b).append(integral).append('\n');
        }
        return text.toString();
    }
    private static String padDecimal(String value,int width) {
        int decimal=value.indexOf('.');return decimal>0?" ".repeat(Math.max(0,width-decimal))+value:value;
    }
}
