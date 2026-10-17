import json
from java.awt.image import BufferedImage
from javax.imageio import ImageIO
from java.io import File
from java.awt import Window
from javax.swing import JTable
from de.bruker.nmr.mfw.table import IconConstants
rows=[]
try:
    def walk(c):
        if c.isShowing() and isinstance(c,JTable) and 'BTreeTable' in str(c.__class__):
            cols=c.getColumnModel()
            tree=c.getCellRenderer(0,0);node=tree.getPathForRow(0).getLastPathComponent()
            treeCell=tree.getCellRenderer().getTreeCellRendererComponent(tree,node,False,False,True,0,False)
            iconDirectory=File('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integral-table-icons');iconDirectory.mkdirs()
            for name,icon in [('sort-up',IconConstants.COLUMN_SORT_UP),('sort-down',IconConstants.COLUMN_SORT_DOWN)]:
                for factor in [1,2]:
                    iconImage=BufferedImage(icon.getIconWidth()*factor,icon.getIconHeight()*factor,BufferedImage.TYPE_INT_ARGB)
                    iconGraphics=iconImage.createGraphics();iconGraphics.scale(float(factor),float(factor));icon.paintIcon(c,iconGraphics,0,0);iconGraphics.dispose()
                    ImageIO.write(iconImage,'png',File(iconDirectory,name+('' if factor==1 else '@2x')+'.png'))
            scroll=c.getParent().getParent();image=BufferedImage(scroll.getWidth()*2,scroll.getHeight()*2,BufferedImage.TYPE_INT_ARGB)
            graphics=image.createGraphics();graphics.scale(2.0,2.0);scroll.printAll(graphics);graphics.dispose()
            ImageIO.write(image,'png',File('Z:/home/maxlang/Projects/topspin-viewer/artifacts/original-integral-table.png'))
            rows.append({'class':str(c.__class__),'rendererFont':[unicode(c.getRendererFont().getName()),c.getRendererFont().getSize(),c.getRendererFont().getStyle()],'sortIconWidth':max(IconConstants.COLUMN_SORT_UP.getIconWidth(),IconConstants.COLUMN_SORT_DOWN.getIconWidth()),'modelClass':str(c.getModel().__class__),'addRowWidth':c.getAddRowWidth(),'addRowHeight':c.getAddRowHeight(),'rowHeight':c.getRowHeight(),'rows':c.getRowCount(),
                'columns':[{'name':unicode(c.getColumnName(i)),'width':cols.getColumn(i).getWidth(),'preferredWidth':cols.getColumn(i).getPreferredWidth(),'format':unicode(c.getModel().getColumnNumberFormatString(c.convertColumnIndexToModel(i))),'class':str(c.getColumnClass(i))} for i in range(c.getColumnCount())],
                'display':[[unicode(c.prepareRenderer(c.getCellRenderer(r,i),r,i).getText()) if hasattr(c.prepareRenderer(c.getCellRenderer(r,i),r,i),'getText') else '' for i in range(c.getColumnCount())] for r in range(c.getRowCount())],
                'values':[[unicode(c.getValueAt(r,i)) for i in range(c.getColumnCount())] for r in range(c.getRowCount())],
                'treeCell':{'font':str(treeCell.getFont()),'bounds':str(tree.getRowBounds(0)),'insets':str(treeCell.getInsets())},
                'headerCells':[{'font':str(c.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(c,c.getColumnName(i),False,False,-1,i).getFont()),'insets':str(c.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(c,c.getColumnName(i),False,False,-1,i).getInsets()),'alignment':c.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(c,c.getColumnName(i),False,False,-1,i).getHorizontalAlignment()} for i in range(c.getColumnCount())],
                'cells':[{'alignment':c.prepareRenderer(c.getCellRenderer(0,i),0,i).getHorizontalAlignment(),'fontSize':c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont().getSize(),'fontStyle':c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont().getStyle(),'insets':str(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getInsets()),'textWidth':c.getFontMetrics(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont()).stringWidth(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getText()),'ascent':c.getFontMetrics(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont()).getAscent(),'descent':c.getFontMetrics(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont()).getDescent(),'height':c.getFontMetrics(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont()).getHeight(),'fontName':unicode(c.prepareRenderer(c.getCellRenderer(0,i),0,i).getFont().getFontName())} if hasattr(c.prepareRenderer(c.getCellRenderer(0,i),0,i),'getHorizontalAlignment') else {} for i in range(c.getColumnCount())],
                'grid':[c.getShowHorizontalLines(),c.getShowVerticalLines()],'autoResizeMode':c.getAutoResizeMode(),'intercellSpacing':[c.getIntercellSpacing().width,c.getIntercellSpacing().height],
                'renderer':[str(c.getCellRenderer(0,i).__class__) for i in range(c.getColumnCount())] if c.getRowCount() else []})
        if hasattr(c,'getComponents'):
            for child in c.getComponents():walk(child)
    for window in Window.getWindows():
        if window.isVisible():walk(window)
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integrals-table.json','w') as output:json.dump(rows,output,indent=2)
    if rows:
        table=rows[0];lines=['\t'.join(str(c['width']) for c in table['columns'])]
        for values,display in zip(table['values'],table['display']):lines.append('\t'.join(values+display))
        with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integral-table-reference.tsv','w') as output:output.write(('\n'.join(lines)+'\n').encode('utf-8'))
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/artifacts/integrals-table-error.txt','w') as output:output.write(traceback.format_exc())
