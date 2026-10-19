"""Jython entry point for a read-only capture of the currently selected copied FID."""
try:
    import json
    import jarray
    from java.awt import Window
    from java.io import File
    from java.lang import Class, ClassLoader, Object, String, System, Boolean
    from java.net import URLClassLoader, URL
    project = 'Z:/home/maxlang/Projects/topspin-viewer/'
    with open(project+'artifacts/live-fid-capture-request.json') as stream:
        request = json.load(stream)
    destination = request['destination']
    if not destination.startswith(project+'artifacts/live-fid-display-20261019/'):
        raise RuntimeError('Fresh FID capture destination required')
    loader = URLClassLoader(jarray.array([File(project+'build/live').toURI().toURL()],URL),ClassLoader.getSystemClassLoader())
    helper = Class.forName('oracle.LiveFidDisplay',True,loader)
    capture = helper.getMethod('capture',jarray.array([Object,String,Boolean.TYPE],Class))
    def find(component):
        if not component.isShowing():
            return False
        if hasattr(component,'getCurrentModule'):
            module = component.getCurrentModule()
            if module is not None:
                capture.invoke(None,jarray.array([module,destination,Boolean(request.get('raw',True))],Object))
                return True
        if hasattr(component,'getComponents'):
            for child in component.getComponents():
                if find(child):
                    return True
        return False
    found = False
    for window in Window.getWindows():
        if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle()).startswith('Bruker TopSpin') and find(window):
            found = True
            break
    if not found:
        raise RuntimeError('No active data module was found')
    with open(project+'artifacts/live-fid-display-success.txt','w') as output:
        output.write(destination+'\n')
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-fid-display-error.txt','w') as output:
        output.write(traceback.format_exc())
