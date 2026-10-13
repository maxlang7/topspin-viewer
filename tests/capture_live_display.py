try:
    import sys
    sys.path.append('Z:/home/maxlang/Projects/topspin-viewer/build/live')
    from java.net import URLClassLoader, URL
    from java.lang import Class, ClassLoader, Object, String
    from java.io import File
    import jarray
    loader=URLClassLoader(jarray.array([File('Z:/home/maxlang/Projects/topspin-viewer/build/live').toURI().toURL()],URL),ClassLoader.getSystemClassLoader())
    helper=Class.forName('oracle.LiveDisplay',True,loader)
    capture=helper.getMethod('capture',jarray.array([Object,String],Class))
    from java.awt import Window
    
    def find(c):
        if hasattr(c,'getCurrentModule'):
            module=c.getCurrentModule()
            if module is not None and 'int1d.Module' in str(module.__class__):
                capture.invoke(None,jarray.array([module,'Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-display-integration'],Object))
                return True
        if hasattr(c,'getComponents'):
            for child in c.getComponents():
                if find(child):return True
        return False
    
    found=False
    for window in Window.getWindows():
        if window.isVisible() and find(window):found=True;break
    if not found:raise RuntimeError('Display module was not found')
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-display-capture-error.txt','w') as output:output.write(traceback.format_exc())
