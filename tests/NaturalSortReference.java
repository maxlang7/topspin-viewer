import java.nio.file.*;
import java.util.*;
public class NaturalSortReference {
 public static void main(String[] args)throws Exception {
  var order=DatasetBrowserModel.SortOrder.NATURAL;
  List<String> actual=new ArrayList<>(List.of("MLN1P111","MLN1P99","MLN1P9","MLN2P1","MLN1P10"));actual.sort(DatasetBrowserModel::naturalNames);
  if(!actual.equals(List.of("MLN1P9","MLN1P10","MLN1P99","MLN1P111","MLN2P1")))throw new AssertionError(actual);
  Random random=new Random(10242026);int cases=0;
  for(int i=0;i<10000;i++){
   long a=Math.abs(random.nextLong()),b=Math.abs(random.nextLong());
   if(Integer.signum(DatasetBrowserModel.naturalNames("MLN1P"+a,"MLN1P"+b))!=Long.compare(a,b))throw new AssertionError("Numeric run comparison differs");cases++;
  }
  if(DatasetBrowserModel.naturalNames("P"+"9".repeat(100),"P1"+"0".repeat(100))>=0)throw new AssertionError("Large numeric run overflow");
  Path root=Files.createTempDirectory(Path.of("artifacts"),"natural-sort-");for(String name:List.of("MLN1P111","MLN1P99","MLN1P9"))Files.createDirectory(root.resolve(name));
  var sorted=DatasetBrowserModel.children(DatasetBrowserModel.entry(root,true),order);
  if(!sorted.stream().map(DatasetBrowserModel.Entry::label).toList().equals(List.of("MLN1P9","MLN1P99","MLN1P111")))throw new AssertionError("Folder order differs");
  var locations=new BrowserLocations(root.resolve("config/browser.properties"),root);locations.sortOrder(order);
  if(new BrowserLocations(root.resolve("config/browser.properties"),root).sortOrder()!=order)throw new AssertionError("Natural order not persisted");
  System.out.println("PASS natural alphabetical names, "+cases+" integer-run comparisons, unbounded digit lengths, real folder order and preference persistence");
 }
}
