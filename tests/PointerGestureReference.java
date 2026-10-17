import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.util.*;
import javax.swing.*;
import de.bruker.nmr.jview.listener.ButtonListener;
import de.bruker.nmr.jview.listener.VShiftButton;
import de.bruker.nmr.jview.listener.HShiftButton;
import de.bruker.nmr.jview.listener.VScaleButton;
import de.bruker.nmr.jview.listener.HScaleButton;
import java.util.function.Supplier;

/** Runs the original complete mouse listener. Its pointer output is stubbed. */
public class PointerGestureReference {
    public static class TestRobot extends Robot {
        int calls;Point target;
        public TestRobot() throws AWTException {super();}
        @Override public synchronized void mouseMove(int x,int y){calls++;target=new Point(x,y);}
    }
    private static Vector<Object> queue(VShiftButton listener) throws Exception {
        Class<?> type=Class.forName("de.bruker.nmr.jview.listener.ButtonListener$CommandThread");
        Constructor<?> constructor=type.getDeclaredConstructor(ButtonListener.class);constructor.setAccessible(true);
        Object queue=constructor.newInstance(listener);Field field=ButtonListener.class.getDeclaredField("cmdThread");field.setAccessible(true);field.set(listener,queue);
        @SuppressWarnings("unchecked") Vector<Object> result=(Vector<Object>)queue;return result;
    }
    private static void equal(Double actual,Vector<Object> queue) {
        if(actual==null){if(!queue.isEmpty())throw new AssertionError("Unexpected original movement");return;}
        if(queue.size()!=1 || Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits((Double)queue.get(0)))throw new AssertionError("Pointer movement differs: "+actual+" vs "+queue);
    }
    public static void main(String[] args) throws Exception {
        Field robotField=bruker.bio.layer1.swing.robot.BRobot.class.getDeclaredField("robot");robotField.setAccessible(true);
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        TestRobot robot=(TestRobot)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(TestRobot.class);
        JPanel panel=new JPanel();panel.setLocation(37,29);Random random=new Random(302165);int comparisons=0;
        java.util.List<Supplier<VShiftButton>> factories=java.util.List.of(VShiftButton::new,HShiftButton::new,VScaleButton::new,HScaleButton::new);
        for(var kind:PointerGesture.Kind.values())for(boolean warp:new boolean[]{false,true})for(int trial=0;trial<32;trial++) {
            robot.calls=0;robotField.set(null,warp?robot:null);
            VShiftButton original=factories.get(kind.ordinal()).get();Vector<Object> pending=queue(original);PointerGesture rebuilt=new PointerGesture(kind);
            for(int event=0;event<200;event++) {
                pending.clear();int before=robot.calls;
                if(event%13==7) {
                    int rotation=random.nextInt(9)-4;
                    original.mouseWheelMoved(new MouseWheelEvent(panel,MouseEvent.MOUSE_WHEEL,1,0,12,8,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,rotation));
                    equal(rebuilt.wheel(rotation,12,8),pending);
                }else {
                    Point anchor=rebuilt.anchor();int x=random.nextInt(80)-20,y=random.nextInt(100)-30;
                    if(event<3){x=12+event;y=12;}
                    if(event%11==4 && anchor!=null){x=anchor.x;y=anchor.y;}
                    if(event%5==3)y=12;
                    original.mouseDragged(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,1,InputEvent.BUTTON1_DOWN_MASK,x,y,0,false,MouseEvent.NOBUTTON));
                    var update=rebuilt.drag(x,y,warp);equal(update.amount(),pending);
                    if((robot.calls>before)!=(update.warpPoint()!=null))throw new AssertionError("Pointer anchoring differs");
                    if(update.warpPoint()!=null){Point expected=new Point(update.warpPoint());SwingUtilities.convertPointToScreen(expected,panel);if(!expected.equals(robot.target))throw new AssertionError("Pointer return position differs");}
                }
                comparisons++;
            }
        }
        robotField.set(null,null);System.out.println("PASS "+comparisons+" original pointer-listener events, exact movement, wheel and anchor comparisons");
    }
}
