try:
    from java.net import URLClassLoader, URL
    from java.lang import Class, ClassLoader, Object, String
    from java.io import File
    from java.awt import Window
    import jarray
    loader=URLClassLoader(jarray.array([File('Z:/home/maxlang/Projects/topspin-viewer/build/live').toURI().toURL()],URL),ClassLoader.getSystemClassLoader())
    helper=Class.forName('oracle.LiveIntegrationEdits',True,loader)
    capture=helper.getMethod('capture',jarray.array([Object,String],Class))
    def find(c):
        if hasattr(c,'getCurrentModule'):
            module=c.getCurrentModule()
            if module is not None and 'int1d.Module' in str(module.__class__):
                capture.invoke(None,jarray.array([module,'Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-integration-edits'],Object))
                return True
        if hasattr(c,'getComponents'):
            for child in c.getComponents():
                if find(child):return True
        return False
    found=False
    for window in Window.getWindows():
        if window.isVisible() and find(window):found=True;break
    if not found:raise RuntimeError('Integration module was not found')
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-integration-edits-error.txt','w') as output:output.write(traceback.format_exc())
