import java.awt.Point;
import java.util.*;
import de.bruker.nmr.jview.ifc.SpecViewIfc;
import de.bruker.nmr.jview.listener.ObjectSelector;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Exercises the installed, unmodified controller with independent selection state. */
public class IntegrationSelectionReference {
    public static void main(String[] args) throws Exception {
        IntegralScaleReference.initializeServices();Random random=new Random(912785);int transitions=0,flags=0,edits=0;
        for(int run=0;run<100;run++) {
            IntegrationRegions model=new IntegrationRegions();
            for(int i=0;i<8;i++)model.add(11-i*1.5,10-i*1.5);
            if(run%2==0){model.add(10.5,3);model.add(7.9,7.3);model.add(7.9,7.3);}
            var original=IntegrationUndoReference.fixture(new double[128],model,List.of(),0);
            List<Integral> areas=new ArrayList<>();
            for(var data:original.getDatasetsVector())if(data instanceof Integral area) {
                area.setSelected(false);areas.add(area);
                var mapper=((SpecViewIfc)area.getLayout().getBaseView()).getCoordinateMapper()[0];
                mapper.setPhysicalMapping(12,-2);mapper.setScreenMapping(2,1002);
            }
            IntegralScaleReference.field(original,"selectionFlag",true);
            IntegralScaleReference.field(original,"mouseSens",1.0);
            IntegralScaleReference.field(original,"biasAndSlopeChanged",new IdentityHashMap<>());
            var spectrumMapper=((SpecViewIfc)original.getDatasetsVector().get(0).getLayout().getBaseView()).getCoordinateMapper()[0];
            spectrumMapper.setPhysicalMapping(12,-2);spectrumMapper.setScreenMapping(2,1002);
            for(int step=0;step<120;step++) {
                if(step%17==0) {original.CMD_int1d_selall(null,null);model.toggleSelectAll();}
                else {
                    int hit=random.nextInt(areas.size()+1)-1;
                    int pixel=hit<0?50:2+(int)((12-(model.regions().get(hit).left()+model.regions().get(hit).right())/2)/14*1000);
                    double position=VisualNumerics.horizontal(12,-2,2,1002).physical(pixel);
                    ObjectSelector selector=new ObjectSelector();selector.setObject(hit<0?null:areas.get(hit));selector.setLastMousePosition(new Point(pixel,600));
                    boolean shift=random.nextBoolean(),control=random.nextBoolean();
                    IntegrationUndoReference.call(original,"selectCurrentIntegral",new Class<?>[]{boolean.class,boolean.class},shift,control);
                    model.selectRegion(hit,position,14,shift,control);
                }
                for(int i=0;i<areas.size();i++) {
                    if(areas.get(i).isSelected()!=model.isSelected(i))throw new AssertionError("Selection run="+run+" step="+step+" region="+i+" model="+model.selectedIndices());
                    flags++;
                }
                if(step%11==0 && !model.selectedIndices().isEmpty()) {
                    double amount=random.nextDouble()-.5;
                    original.CMD__bias(amount,null);
                    for(int index:model.selectedIndices())model.adjustBias(index,amount,1);
                    original.CMD__slope(amount,null);
                    for(int index:model.selectedIndices())model.adjustSlope(index,amount,1,new double[128],12,-2);
                    IntegrationUndoReference.check(original,model,new double[128]);edits++;
                    if(step%22==0) {
                        original.CMD_reset_slope_bias(null,null);
                        for(int index:model.selectedIndices()){var r=model.regions().get(index);model.replace(index,new IntegrationRegions.Region(r.left(),r.right(),0,0));}
                        IntegrationUndoReference.check(original,model,new double[128]);edits++;
                    }
                }
                transitions++;
            }
            double[] samples=((IntegrationUndoReference.Data)original.getDatasetsVector().get(0)).samples;
            if(model.selectedIndices().isEmpty()){original.CMD_int1d_selall(null,null);model.toggleSelectAll();}
            if(!model.selectedIndices().isEmpty()) {
                original.CMD_int1d_del(null,null);model.removeSelected();model.normalize(samples,12,-2);
                IntegrationUndoReference.check(original,model,samples);
                for(int undo=0;undo<3;undo++) {
                    original.CMD_int1d_undo(null,null);if(model.undo())model.normalize(samples,12,-2);
                    IntegrationUndoReference.check(original,model,samples);
                }
            }
            new ObjectSelector().setObject(null);
        }
        System.out.println("PASS "+transitions+" original integration selection transitions, "+flags+" exact selection flags, "+edits+" selected bias/slope/reset operations and bulk deletion/undo");
    }
}
