import java.awt.*;
import java.awt.event.*;
import java.util.function.DoubleConsumer;
import javax.swing.*;

/** Toolbar capture uses logical coordinates and returns the pointer to its anchor. */
public final class RelativeDragControl extends MouseAdapter {
    private final JButton button;
    private final DoubleConsumer consumer;
    private final Runnable onArm;
    private final boolean warpEnabled;
    private final PointerGesture gesture;
    private final int cursor;
    private Robot robot;
    private Point screenAnchor;
    private boolean armed;
    public RelativeDragControl(JButton button,Runnable onArm,DoubleConsumer consumer,boolean warpEnabled) {
        this(button,onArm,consumer,warpEnabled,PointerGesture.Kind.VERTICAL_SHIFT);
    }
    public RelativeDragControl(JButton button,Runnable onArm,DoubleConsumer consumer,boolean warpEnabled,PointerGesture.Kind kind) {
        this.button=button;this.onArm=onArm;this.consumer=consumer;this.warpEnabled=warpEnabled;
        gesture=new PointerGesture(kind);cursor=kind.horizontal()?Cursor.W_RESIZE_CURSOR:Cursor.N_RESIZE_CURSOR;
        button.addMouseListener(this);button.addMouseMotionListener(this);button.addMouseWheelListener(this);
    }
    @Override public void mousePressed(MouseEvent event) {
        if(!SwingUtilities.isLeftMouseButton(event))return;
        gesture.reset();screenAnchor=null;armed=true;onArm.run();button.setSelected(true);
        button.setCursor(Cursor.getPredefinedCursor(cursor));
        Window window=SwingUtilities.getWindowAncestor(button);if(window!=null)window.setCursor(Cursor.getPredefinedCursor(cursor));
        if(warpEnabled && robot==null)try{robot=new Robot();}catch(AWTException | SecurityException ignored){}
    }
    @Override public void mouseDragged(MouseEvent event) {
        if(!armed)return;
        var update=gesture.drag(event.getX(),event.getY(),robot!=null);
        if(screenAnchor==null && gesture.anchor()!=null){screenAnchor=gesture.anchor();SwingUtilities.convertPointToScreen(screenAnchor,button);}
        if(update.amount()!=null)consumer.accept(update.amount());
        if(update.warpPoint()!=null && robot!=null)robot.mouseMove(screenAnchor.x,screenAnchor.y);
    }
    @Override public void mouseWheelMoved(MouseWheelEvent event){if(armed){Double amount=gesture.wheel(event.getWheelRotation(),event.getX(),event.getY());if(amount!=null)consumer.accept(amount);}}
    @Override public void mouseExited(MouseEvent event) {
        if((event.getModifiersEx()&InputEvent.BUTTON1_DOWN_MASK)!=0)return;
        armed=false;gesture.reset();screenAnchor=null;button.setSelected(false);button.setCursor(Cursor.getDefaultCursor());
        Window window=SwingUtilities.getWindowAncestor(button);if(window!=null)window.setCursor(Cursor.getDefaultCursor());
    }
}
