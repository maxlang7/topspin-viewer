import json
from java.awt import Window

rows=[]
def walk(c):
    if hasattr(c,'getToolTipText'):
        tip=str(c.getToolTipText())
        if tip in ['0 order correction','1st order correction']:
            row={'tooltip':tip,'class':str(c.__class__),'mouseListeners':[str(x.__class__) for x in c.getMouseListeners()],'motionListeners':[str(x.__class__) for x in c.getMouseMotionListeners()]}
            if hasattr(c,'getActionCommand'):row['actionCommand']=str(c.getActionCommand())
            rows.append(row)
    if hasattr(c,'getComponents'):
        for child in c.getComponents():walk(child)
for w in Window.getWindows():
    if w.isVisible():walk(w)
with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/phase-controls.json','w') as output:json.dump(rows,output,indent=2)
