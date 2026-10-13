"""Read-only navigation to the integral table on an isolated displayed dataset."""
from java.awt import Window
from java.awt.event import MouseEvent
from java.lang import Runnable, System
from javax.swing import SwingUtilities

class Select(Runnable):
    def run(self):
        def walk(c):
            if c.isShowing() and hasattr(c,'getText') and str(c.getText())=='INTEGRALS':
                target=c
                while target is not None and len(target.getMouseListeners())==0:target=target.getParent()
                if target is None:raise RuntimeError('Integral tab has no mouse controller')
                now=System.currentTimeMillis()
                for kind in [MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED]:
                    target.dispatchEvent(MouseEvent(target,kind,now,0,12,12,1,False,MouseEvent.BUTTON1))
                return True
            if hasattr(c,'getComponents'):
                for child in c.getComponents():
                    if walk(child):return True
            return False
        for window in Window.getWindows():
            if window.isVisible() and walk(window):return
        raise RuntimeError('Integral tab was not found')
try:
    if SwingUtilities.isEventDispatchThread():Select().run()
    else:SwingUtilities.invokeAndWait(Select())
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integrals-tab-error.txt','w') as output:output.write(traceback.format_exc())
