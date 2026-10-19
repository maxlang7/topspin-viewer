import java.nio.file.Path;
import java.util.*;
import bruker.bio.topspin.databrowser2.util.DatabrowserPathComparator;
import bruker.bio.topspin.databrowser2.gui.treemodel.DatabrowserProcnoNode;

/** Compare the original path ordering and processing-number labels without starting TopSpin. */
public class BrowserBehaviorReference {
    public static void main(String[] args) {
        var source=new DatabrowserPathComparator();Random random=new Random(719203);
        List<String> names=new ArrayList<>(List.of("1","2","10","100","01","+2","-1","0","2147483647","2147483648","sample","Sample","sample10","sample2","éthanol","Ethanol","[13C]","a b"));
        for(int i=0;i<100;i++)names.add(Integer.toString(random.nextInt(100000)));
        int cases=0;
        for(int i=0;i<20000;i++) {
            Path a=path(random,names),b=path(random,names);
            int expected=source.compare(a,b),actual=DatasetBrowserModel.comparePaths(a,b);
            if(expected!=actual)throw new AssertionError(a+" vs "+b+": "+expected+" != "+actual);cases++;
        }
        for(int n:new int[]{0,1,2,10,100,Integer.MAX_VALUE}) {
            var node=new DatabrowserProcnoNode(null,Path.of("/data"),Path.of("sample/10/pdata/"+n),"title",n,1);
            String label=DatasetBrowserModel.entry(Path.of("/data/sample/10/pdata/"+n),false).label();
            if(!node.getDisplayName(null).equals(label))throw new AssertionError("Processing number label changed");
        }
        System.out.println("PASS original browser path comparator: "+cases+" exact results and six processing-number labels");
    }
    private static Path path(Random random,List<String> names) {
        Path path=Path.of("/data");for(int i=0,n=random.nextInt(5);i<n;i++)path=path.resolve(names.get(random.nextInt(names.size())));return path;
    }
}
