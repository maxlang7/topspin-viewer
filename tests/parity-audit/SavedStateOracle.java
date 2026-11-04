import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.ifc.PhysicalRangeIfc;
import de.bruker.nmr.prsc.datacomps.integral.Integral;
public class SavedStateOracle {
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);List<String> rows=new ArrayList<>();int serial=0;
  for(int arg=1;arg<args.length;arg++){Path path=Path.of(args[arg]).toRealPath();var s=new BrukerDataset(path);var model=IntegrationRegions.read(path.resolve("intrng"),s.integer("NC_proc"),s.numberOr("INTSCL",1));
   for(int i=0;i<model.regions().size();i++){var r=model.regions().get(i);var data=new VendorDifferential.ReferenceData(s.real);data.setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(s.left,s.right)});var a=new Integral(data,new PhysicalRangeIfc[]{new PhysicalRange(r.left(),r.right())},r.bias(),r.slope());a.setBaselinCorr(false);a.calculateIntegral(r.bias(),r.slope());a.setNormalizingFactor(model.normalizingFactor);
    double[] curve=a.getDataDouble();ByteBuffer b=ByteBuffer.allocate((curve.length+6)*8).order(ByteOrder.LITTLE_ENDIAN);for(double v:new double[]{r.left(),r.right(),r.bias(),r.slope(),model.normalizingFactor,a.getNormalizedValue()})b.putDouble(v);for(double v:curve)b.putDouble(v);String name="saved-"+(serial++)+".f64";Files.write(out.resolve(name),b.array());rows.add(path+"\t"+i+"\t"+name);
   }
  }
  Files.write(out.resolve("saved.tsv"),rows);System.out.println("Saved-region cases="+rows.size());
 }
}
