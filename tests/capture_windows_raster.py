"""Invoke the read-only spectrum renderer probe in TopSpin's actual Windows JVM."""
try:
    from java.net import URLClassLoader, URL
    from java.lang import Class, ClassLoader, Object, String
    from java.io import File
    import jarray
    root='Z:/home/maxlang/Projects/topspin-viewer/'
    loader=URLClassLoader(jarray.array([File(root+'build/live').toURI().toURL()],URL),ClassLoader.getSystemClassLoader())
    helper=Class.forName('PlotRasterProbe',True,loader)
    strings=jarray.array([root+'artifacts/viewer-workflow-16708493597640484641/sample/10/pdata/2',root+'artifacts/original-windows-spectrum-raster.png'],String)
    capture=helper.getMethod('main',jarray.array([Class.forName('[Ljava.lang.String;')],Class))
    capture.invoke(None,jarray.array([strings],Object))
    with open(root+'artifacts/windows-raster-success.txt','w') as output:output.write('Completed in actual TopSpin JVM\n')
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/windows-raster-error.txt','w') as output:output.write(traceback.format_exc())
