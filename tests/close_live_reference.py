"""Close only the normal exit confirmation of the reference application started for tests."""
import json
from java.awt import Window
from javax.swing import SwingUtilities, AbstractButton
project='Z:/home/maxlang/Projects/topspin-viewer/'
rows=[]
def texts(component):
    result=[]
    if hasattr(component,'getText'):result.append(str(component.getText()))
    if hasattr(component,'getComponents'):
        for child in component.getComponents():result.extend(texts(child))
    return result
def inspect():
    for window in Window.getWindows():
        if window.isVisible():
            content=texts(window)
            rows.append({'title':str(window.getTitle()) if hasattr(window,'getTitle') else '', 'texts':content})
            if any('Close Application.' in text and 'Exit anyway?' in text for text in content):
                def confirm(component):
                    if isinstance(component,AbstractButton) and str(component.getText())=='OK':component.doClick()
                    elif hasattr(component,'getComponents'):
                        for child in component.getComponents():confirm(child)
                confirm(window)
if SwingUtilities.isEventDispatchThread():inspect()
else:SwingUtilities.invokeAndWait(inspect)
with open(project+'artifacts/live-reference-exit-dialog.json','w') as output:json.dump(rows,output,indent=2)
