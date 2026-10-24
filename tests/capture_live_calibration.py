"""Run the original calibration controller on an isolated copied dataset, confirming its real dialog."""
try:
    import json,traceback,jarray
    from java.awt import Window
    from java.lang import Class,Double,Boolean,Object
    from javax.swing import SwingUtilities,Timer,JTextField,JCheckBox,AbstractButton
    from java.lang.reflect import Modifier
    project='Z:/home/maxlang/Projects/topspin-viewer/'
    with open(project+'artifacts/calibration-request-20261024.json') as stream:request=json.load(stream)
    def find(component):
        if not component.isShowing():return None
        if hasattr(component,'getCurrentModule'):
            module=component.getCurrentModule()
            if module is not None and 'de.bruker.nmr.pr.util.Calib' in str(module.__class__):return module
        if hasattr(component,'getComponents'):
            for child in component.getComponents():
                module=find(child)
                if module is not None:return module
        return None
    module=None
    for window in Window.getWindows():
        if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle()).startswith('Bruker TopSpin'):
            module=find(window)
            if module is not None:break
    if module is None:raise RuntimeError('Original calibration module is not active')
    data=module.getDatasetsVector().get(0)
    if 'calib-reference-20261024' not in str(data.getIdentifier()):raise RuntimeError('Calibration reference copy required')
    def values():
        result={}
        for name in ['SF','OFFSET','SR']:
            value=data.getProperty('1s '+name)
            result[name]=str(value)
        ranges=data.getFullPhysicalRange()
        result['range']=[Double.toHexString(ranges[0].getStart()),Double.toHexString(ranges[0].getEnd())]
        return result
    result={'before':values(),'clicked':request['clicked'],'target':request['target']}
    def descend(component):
        found=[component]
        if hasattr(component,'getComponents'):
            for child in component.getComponents():found.extend(descend(child))
        return found
    submitted=[False]
    def confirm(event):
        for window in Window.getWindows():
            if not window.isVisible() or not hasattr(window,'getTitle'):continue
            if 'calib' not in str(window.getTitle()).lower():continue
            children=descend(window)
            fields=[c for c in children if isinstance(c,JTextField) and c.isShowing() and c.isEditable()]
            buttons=[c for c in children if isinstance(c,AbstractButton) and str(c.getText()).strip().lower()=='ok']
            if len(fields)==1 and buttons:
                result['defaultField']=str(fields[0].getText())
                fields[0].setText(str(request['target']))
                for c in children:
                    if isinstance(c,JCheckBox):c.setSelected(False)
                result['dialogTitle']=str(window.getTitle())
                submitted[0]=True;timer.stop();buttons[0].doClick();return
    timer=Timer(150,confirm);timer.start()
    method=Class.forName('de.bruker.nmr.pr.util.Calib').getDeclaredMethod('calib1d',jarray.array([Double.TYPE,Boolean.TYPE],Class));method.setAccessible(True)
    def run():method.invoke(module,jarray.array([Double(request['clicked']),Boolean(False)],Object))
    try:
        if SwingUtilities.isEventDispatchThread():run()
        else:SwingUtilities.invokeAndWait(run)
    finally:timer.stop()
    if not submitted[0]:raise RuntimeError('Original calibration dialog was not confirmed')
    result['after']=values()
    with open(project+request['output'],'w') as stream:json.dump(result,stream,indent=2)
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/calibration-capture-error-20261024.txt','w') as stream:stream.write(traceback.format_exc())
