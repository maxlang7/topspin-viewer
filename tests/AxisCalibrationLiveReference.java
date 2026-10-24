import java.nio.file.*;
import java.util.*;
public class AxisCalibrationLiveReference {
 static void equal(double a,double b,String name){if(Double.doubleToRawLongBits(a)!=Double.doubleToRawLongBits(b))throw new AssertionError(name+": "+Double.toHexString(a)+" != "+Double.toHexString(b));}
 public static void main(String[] args)throws Exception {
  Path root=Path.of("artifacts/calib-reference-20261024"),source=Path.of("artifacts/full-lab-references/datasets/dataset-000-2-picolylamine/10");
  Path work=Files.createTempDirectory(Path.of("artifacts"),"calibration-live-acceptance-");int count=0;long bytes=0;
  for(String line:Files.readAllLines(root.resolve("cases.tsv"))){
   String[] row=line.split("\t",-1);source=Path.of(row[13]);Path experiment=work.resolve(row[0]+"/10");
   try(var files=Files.walk(source)){for(Path file:files.toList()){Path target=experiment.resolve(source.relativize(file));if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);}}
   Spectrum spectrum=new Spectrum(experiment);double clicked=Double.parseDouble(row[1]),target=Double.parseDouble(row[2]);
   equal(spectrum.left,Double.parseDouble(row[5]),"initial axis left");equal(spectrum.right,Double.parseDouble(row[6]),"initial axis right");
   Map<String,String> before=BrukerDataset.readParameters(spectrum.path.resolve("procs"));
   double shift=0;if(AxisCalibration.changed(clicked,target)){shift=spectrum.calibrateAxis(clicked,target).shift();BrukerWriter.saveCalibration(spectrum);}
   equal(Double.parseDouble(spectrum.parameters.get("SF")),Double.parseDouble(row[7]),"live SF");equal(Double.parseDouble(spectrum.parameters.get("OFFSET")),Double.parseDouble(row[8]),"live OFFSET");
   equal(Double.parseDouble(row[5])+shift,Double.parseDouble(row[9]),"original controller translated left");equal(Double.parseDouble(row[6])+shift,Double.parseDouble(row[10]),"original controller translated right");
   equal(spectrum.left,Double.parseDouble(row[11]),"final data left");equal(spectrum.right,Double.parseDouble(row[12]),"final data right");
   if(!row[14].isEmpty()){var visible=ViewRanges.intersect(new ViewRanges.Range(Double.parseDouble(row[5])+shift,Double.parseDouble(row[6])+shift),new ViewRanges.Range(spectrum.left,spectrum.right));equal(visible.start(),Double.parseDouble(row[14]),"native post-return visible left");equal(visible.end(),Double.parseDouble(row[15]),"native post-return visible right");}
   Spectrum reopened=new Spectrum(experiment);equal(reopened.left,Double.parseDouble(row[11]),"native API reopened left");equal(reopened.right,Double.parseDouble(row[12]),"native API reopened right");
   Map<String,String> after=BrukerDataset.readParameters(spectrum.path.resolve("procs"));
   for(var entry:before.entrySet())if(!Set.of("SF","OFFSET").contains(entry.getKey()) && !entry.getValue().equals(after.get(entry.getKey())))throw new AssertionError("Calibration changed "+entry.getKey());
   for(String name:List.of("1r","1i","intrng","integrals.txt","title"))if(Files.isRegularFile(source.resolve("pdata/1").resolve(name))){byte[] original=Files.readAllBytes(source.resolve("pdata/1").resolve(name)),saved=Files.readAllBytes(spectrum.path.resolve(name));if(!Arrays.equals(original,saved))throw new AssertionError("Calibration rewrote "+name);byte[] nativeFile=Files.readAllBytes(root.resolve(row[0]+"/10/pdata/1/"+name));if(!Arrays.equals(nativeFile,saved))throw new AssertionError("Native calibration file differs: "+name);bytes+=original.length;}
   count++;
  }
  if(count!=16)throw new AssertionError("Incomplete capture coverage");
  System.out.println("PASS "+count+" actual Windows calibration dialogs, bitwise SF/OFFSET/display/reopened API endpoints, post-return clipping, unchanged non-axis parameters and "+bytes+" identical component/region/title bytes; "+work);
 }
}
