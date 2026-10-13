import json
from java.awt import Window
from javax.swing import JTable
rows=[]
try:
    def walk(c):
        if c.isShowing() and isinstance(c,JTable) and 'BTreeTable' in str(c.__class__):
            cols=c.getColumnModel()
            rows.append({'class':str(c.__class__),'rowHeight':c.getRowHeight(),'rows':c.getRowCount(),
                'columns':[{'name':unicode(c.getColumnName(i)),'width':cols.getColumn(i).getWidth(),'preferredWidth':cols.getColumn(i).getPreferredWidth(),'class':str(c.getColumnClass(i))} for i in range(c.getColumnCount())],
                'display':[[unicode(c.prepareRenderer(c.getCellRenderer(r,i),r,i).getText()) if hasattr(c.prepareRenderer(c.getCellRenderer(r,i),r,i),'getText') else '' for i in range(c.getColumnCount())] for r in range(c.getRowCount())],
                'values':[[unicode(c.getValueAt(r,i)) for i in range(c.getColumnCount())] for r in range(c.getRowCount())],
                'grid':[c.getShowHorizontalLines(),c.getShowVerticalLines()],'autoResizeMode':c.getAutoResizeMode(),'intercellSpacing':[c.getIntercellSpacing().width,c.getIntercellSpacing().height],
                'renderer':[str(c.getCellRenderer(0,i).__class__) for i in range(c.getColumnCount())] if c.getRowCount() else []})
        if hasattr(c,'getComponents'):
            for child in c.getComponents():walk(child)
    for window in Window.getWindows():
        if window.isVisible():walk(window)
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integrals-table.json','w') as output:json.dump(rows,output,indent=2)
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integrals-table-error.txt','w') as output:output.write(traceback.format_exc())
