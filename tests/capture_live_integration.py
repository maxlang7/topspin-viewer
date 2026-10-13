import sys
sys.path.append('Z:/home/maxlang/Projects/topspin-viewer/build/live')
from oracle import LiveIntegration
from java.awt import Window

def find(c):
    if hasattr(c,'getCurrentModule'):
        module=c.getCurrentModule()
        if module is not None and 'int1d.Module' in str(module.__class__):
            LiveIntegration.capture(module,'Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-integration-reference')
            return True
    if hasattr(c,'getComponents'):
        for child in c.getComponents():
            if find(child):return True
    return False

found=False
for window in Window.getWindows():
    if window.isVisible() and find(window):found=True;break
if not found:raise RuntimeError('Interactive integration module was not found')
