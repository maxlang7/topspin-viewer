import java.nio.file.*;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.ifc.PhysicalRangeIfc;
import de.bruker.nmr.prsc.datacomps.integral.Integral;
/** Uses the original TopSpin Integral implementation, not the reconstructed Java numerics. */
public class CurrentRustParity {
 public static void main(String[] args)throws Exception{
  int compared=0,failed=0;
  System.err.println("REFERENCE_JAR="+Integral.class.getProtectionDomain().getCodeSource().getLocation());
  System.out.println("path\tregion\trust_value\ttopspin_value\trust_bits\ttopspin_bits\tmatch");
  for(String line:Files.readAllLines(Path.of(args[0]))){
   String[] c=line.split("\t");var s=new BrukerDataset(Path.of(c[0]));
   var data=new VendorDifferential.ReferenceData(s.real);
   data.setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(s.left,s.right)});
   var integral=new Integral(data,new PhysicalRangeIfc[]{new PhysicalRange(Double.parseDouble(c[2]),Double.parseDouble(c[3]))},0,0);
   integral.setBaselinCorr(false);integral.calculateIntegral(0,0);
   double expected=integral.getIntegralValue();
   String bits=String.format("%016x",Double.doubleToRawLongBits(expected));boolean match=bits.equals(c[5]);
   compared++;if(!match)failed++;
   System.out.printf("%s\t%s\t%s\t%.17g\t%s\t%s\t%s%n",c[0],c[1],c[4],expected,c[5],bits,match);
  }
  System.err.println("CURRENT_RUST cases="+compared+" exact_matches="+(compared-failed)+" mismatches="+failed);
  if(failed>0)System.exit(1);
 }
}
