"""Read the displayed calibration result after the original controller returns to the viewer."""
try:
    import json
    from java.awt import Window
    from javax.swing import SwingUtilities
    from java.lang import Double
    result={}
    def find(component):
        if not component.isShowing():return False
        if hasattr(component,'getCurrentModule'):
            module=component.getCurrentModule()
            if module is not None and module.getDatasetsVector().size()>0:
                data=module.getDatasetsVector().get(0)
                if 'calib-reference-20261024' in str(data.getIdentifier()):
                    result['module']=str(module.__class__);result['identifier']=str(data.getIdentifier())
                    physical=data.getFullPhysicalRange()[0]
                    result['dataRange']=[Double.toHexString(physical.getStart()),Double.toHexString(physical.getEnd())]
                    view=data.getLayout().getBaseView();physical=view.getCoordinateMapper()[0].getPhysicalMapping()
                    result['viewRange']=[Double.toHexString(physical.getStart()),Double.toHexString(physical.getEnd())]
                    return True
        if hasattr(component,'getComponents'):
            for child in component.getComponents():
                if find(child):return True
        return False
    def capture():
        for window in Window.getWindows():
            if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle()).startswith('Bruker TopSpin') and find(window):break
    if SwingUtilities.isEventDispatchThread():capture()
    else:SwingUtilities.invokeAndWait(capture)
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/calibration-request-20261024.json') as stream:request=json.load(stream)
    output=request['output'].replace('result.json','display-after-return.json')
    with open('Z:/home/maxlang/Projects/topspin-viewer/'+output,'w') as stream:json.dump(result,stream,indent=2)
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/calibration-display-after-return-error.txt','w') as stream:stream.write(traceback.format_exc())
