import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.pr.baseline.Baseline;
import de.bruker.nmr.jview.data.IndexRange;
import de.bruker.nmr.jview.ifc.IndexRangeIfc;
public class NativeOracle {
 static Path root; static List<String> rows=new ArrayList<>();static int serial;
 static String write(double[] values)throws Exception{String name="buffer-"+(serial++)+".f64";ByteBuffer b=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double v:values)b.putDouble(v);Files.write(root.resolve(name),b.array());return name;}
 public static void main(String[] args)throws Exception{
  root=Path.of(args[0]);Files.createDirectories(root);Random rng=new Random(803859);
  var method=Baseline.class.getDeclaredMethod("getDataDouble",IndexRangeIfc[].class,boolean.class);method.setAccessible(true);
  for(int n:new int[]{1,2,3,4,17,256,4096,65536}){
   double[] re=new double[n],im=new double[n];for(int i=0;i<n;i++){re[i]=rng.nextGaussian()*1e6;im[i]=rng.nextGaussian()*1e6;}
   String source=write(re),imag=write(im);
   for(boolean correct:new boolean[]{false,true})for(int points:new int[]{1,Math.min(4,n)})for(double bias:new double[]{0.,1234.567,-112345.6})for(double slope:new double[]{0.,12.3,-1.234}){
    var a=new VendorDifferential.ReferenceIntegral(re,correct,points);a.calculateIntegral(bias,slope);double[] curve=a.getDataDouble();double[] expected=Arrays.copyOf(curve,curve.length+4);expected[curve.length]=a.getBias();expected[curve.length+1]=a.getSlope();expected[curve.length+2]=a.getIntegralValue();a.setNormalizingFactor(.123456);expected[curve.length+3]=a.getNormalizedValue();
    rows.add(String.join("\t","I",source,write(expected),""+bias,""+slope,""+correct,""+points));
   }
   for(double p0:new double[]{0.,90.,-41.5206061238053,180.})for(double p1:new double[]{0.,-11.9209746984819,720.}){
    double[] expected=new VendorDifferential.ReferencePhase(re,im,n,0).run(0,n,p0,p1);rows.add(String.join("\t","P",source,write(expected),imag,""+p0,""+p1,""+n));
   }
   for(double[] c:new double[][]{{0,0,0,0,0},{12.3,0,0,0,0},{1,-2,0.001,-1e-8,1e-13}}){
    var data=new VendorDifferential.ReferenceData(re);var base=new Baseline(data,0,c);base.setDiffMode(true);double[] corrected=(double[])method.invoke(base,new IndexRangeIfc[]{new IndexRange(0,n)},false);
    var integral=new VendorDifferential.ReferenceIntegral(corrected,true,Math.min(4,n));integral.calculateIntegral(12.3,-1.234);double[] curve=integral.getDataDouble();double[] expected=Arrays.copyOf(curve,curve.length+4);expected[curve.length]=integral.getBias();expected[curve.length+1]=integral.getSlope();expected[curve.length+2]=integral.getIntegralValue();integral.setNormalizingFactor(.123456);expected[curve.length+3]=integral.getNormalizedValue();
    rows.add(String.join("\t","C",source,write(expected),""+c[0],""+c[1],""+c[2],""+c[3],""+c[4],""+Math.min(4,n)));
   }
   for(int start:new int[]{0,n/3})for(double[] c:new double[][]{{0,0,0,0,0},{12.3,0,0,0,0},{-100,0.123,0,0,0},{1,-2,0.001,-1e-8,1e-13}}){
    var data=new VendorDifferential.ReferenceData(re);var base=new Baseline(data,0,c);base.setDiffMode(true);
    var range=new IndexRangeIfc[]{new IndexRange(start,n-start)};double[] expected=(double[])method.invoke(base,range,false);
    rows.add(String.join("\t","B",source,write(expected),""+start,""+(n-start),""+c[0],""+c[1],""+c[2],""+c[3],""+c[4]));
   }
  }
  for(int reference:new int[]{-2,-1,0,1,2})for(int nc:new int[]{-20,-2,0,8,20})for(double stored:new double[]{0,.123456,100}){
   double[] vals={-3,4,9};double expected=IntegralScale.factor(vals,reference,2.5,stored,nc);rows.add(String.join("\t","N",write(vals),write(new double[]{expected}),""+reference,"2.5",""+stored,""+nc));
  }
  Files.write(root.resolve("cases.tsv"),rows);System.out.println("Generated "+rows.size()+" original-TopSpin/native normalization cases");
 }
}
