"""Select the actual FID tab in the isolated reference window, without processing data."""
from java.awt import Window
from java.awt.event import MouseEvent
from java.lang import Runnable, System
from javax.swing import SwingUtilities

class Select(Runnable):
    def run(self):
        def contains_reference_error(component):
            if hasattr(component,'getText') and 'show_raw_data' in str(component.getText()):
                return True
            if hasattr(component,'getComponents'):
                return any(contains_reference_error(child) for child in component.getComponents())
            return False
        # Dismiss only errors created by the earlier unsupported console alias probe.
        for window in Window.getWindows():
            if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle())=='Error' and contains_reference_error(window):
                window.dispose()
        def walk(component):
            if not component.isShowing():
                return False
            if hasattr(component,'getText') and str(component.getText())=='FID':
                target=component
                while target is not None and len(target.getMouseListeners())==0:
                    target=target.getParent()
                if target is None:
                    raise RuntimeError('FID tab has no mouse controller')
                now=System.currentTimeMillis()
                for kind in [MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED]:
                    target.dispatchEvent(MouseEvent(target,kind,now,0,12,12,1,False,MouseEvent.BUTTON1))
                return True
            if hasattr(component,'getComponents'):
                for child in component.getComponents():
                    if walk(child):
                        return True
            return False
        for window in Window.getWindows():
            if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle()).startswith('Bruker TopSpin') and walk(window):
                return
        raise RuntimeError('FID tab was not found')
try:
    if SwingUtilities.isEventDispatchThread():
        Select().run()
    else:
        SwingUtilities.invokeAndWait(Select())
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/fid-tab-error.txt','w') as output:
        output.write(traceback.format_exc())
