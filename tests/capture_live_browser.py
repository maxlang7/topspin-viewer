try:
    """Read the active reference browser without opening or modifying an acquisition."""
    import json
    from java.awt import Window
    from javax.swing import SwingUtilities
    project='Z:/home/maxlang/Projects/topspin-viewer/'
    rows=[]
    def inspect(component):
        if 'bruker.bio.topspin.databrowser2.gui.view.DatabrowserTree' in str(component.__class__):
            tree=component
            for index in range(tree.getRowCount()-1,-1,-1):
                path=tree.getPathForRow(index);label=str(path.getLastPathComponent())
                if ('live-fid-reference-20261019' in label and label.endswith('datasets')) or label.replace('\\','/').endswith('/panel-modes-fresh') or label.replace('\\','/').endswith('/10'):tree.expandPath(path)
            for index in range(tree.getRowCount()):
                path=tree.getPathForRow(index);node=path.getLastPathComponent()
                cell=tree.getCellRenderer().getTreeCellRendererComponent(tree,node,False,tree.isExpanded(path),tree.getModel().isLeaf(node),index,False)
                rows.append({'row':index,'node':str(node),'label':str(cell.getText()),'icon':str(cell.getIcon()),'foreground':cell.getForeground().getRGB(),'leaf':tree.getModel().isLeaf(node),'height':tree.getRowHeight(),'bounds':str(tree.getRowBounds(index))})
        if hasattr(component,'getComponents'):
            for child in component.getComponents():inspect(child)
    def capture():
        for window in Window.getWindows():
            if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle()).startswith('Bruker TopSpin'):inspect(window)
            if window.isVisible() and hasattr(window,'getTitle') and str(window.getTitle())=='Error':
                def texts(component):
                    result=str(component.getText()) if hasattr(component,'getText') else ''
                    if hasattr(component,'getComponents'):
                        for child in component.getComponents():result+=' '+texts(child)
                    return result
                if 'capture_live_browser.py' in texts(window):window.dispose()
    if SwingUtilities.isEventDispatchThread():capture()
    else:SwingUtilities.invokeAndWait(capture)
    with open(project+'artifacts/live-browser-rows-20261019.json','w') as output:json.dump(rows,output,indent=2)
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/live-browser-capture-error.txt','w') as output:output.write(traceback.format_exc())
