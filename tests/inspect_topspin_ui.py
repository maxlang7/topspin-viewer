import json
import hashlib
from java.awt.image import BufferedImage
from javax.imageio import ImageIO
from java.io import File
from java.awt import Window
from java.lang import Runnable, System
from javax.swing import UIManager, SwingUtilities

class Capture(Runnable):
    def run(self):
        def color(c):
            if c is None: return None
            return [c.getRed(),c.getGreen(),c.getBlue(),c.getAlpha()]
        def walk(c,path):
            b=c.getBounds(); f=c.getFont()
            d={'path':path,'class':str(c.__class__),'bounds':[b.x,b.y,b.width,b.height],'visible':c.isVisible(),'enabled':c.isEnabled(),'background':color(c.getBackground()),'foreground':color(c.getForeground())}
            if f is not None:d['font']={'name':str(f.getName()),'family':str(f.getFamily()),'size':f.getSize(),'style':f.getStyle()}
            if hasattr(c,'getText') and 'Password' not in str(str(c.__class__)):
                try:d['text']=str(c.getText())
                except:pass
            if hasattr(c,'getToolTipText'):
                try:d['tooltip']=str(c.getToolTipText())
                except:pass
            if hasattr(c,'getIcon'):
                try:
                    icon=c.getIcon()
                    if icon is not None:
                        d['icon']={'class':str(icon.__class__),'description':str(icon),'width':icon.getIconWidth(),'height':icon.getIconHeight()}
                        try: d['icon']['resource']=str(icon.getName())
                        except: pass
                        if icon.getIconWidth()>0 and icon.getIconHeight()>0:
                            key=hashlib.sha256(path.encode('utf-8')).hexdigest()[:16]
                            image=BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB)
                            graphics=image.createGraphics()
                            icon.paintIcon(c,graphics,0,0)
                            graphics.dispose()
                            pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),None,0,image.getWidth())
                            key=hashlib.sha256(','.join(str(x) for x in pixels).encode('ascii')).hexdigest()[:16]
                            directory=File('Z:/home/maxlang/Projects/topspin-viewer/evidence/ui/icons');directory.mkdirs()
                            ImageIO.write(image,'png',File(directory,key+'.png'))
                            d['icon']['painted']=key+'.png'
                            high=BufferedImage(icon.getIconWidth()*2,icon.getIconHeight()*2,BufferedImage.TYPE_INT_ARGB)
                            graphics=high.createGraphics();graphics.scale(2.0,2.0)
                            icon.paintIcon(c,graphics,0,0);graphics.dispose()
                            ImageIO.write(high,'png',File(directory,key+'@2x.png'))
                except:pass
            if hasattr(c,'getComponents'):d['children']=[walk(x,path+'/'+str(i)) for i,x in enumerate(c.getComponents())]
            return d
        windows=[];errors=[]
        for w in Window.getWindows():
            if hasattr(w,'getTitle') and 'Bruker TopSpin' in str(w.getTitle()): windows.append(walk(w,'topspin'))
            if hasattr(w,'getTitle') and w.isVisible() and str(w.getTitle())=='Error':errors.append(walk(w,'error'))
        info={'laf':str(UIManager.getLookAndFeel().__class__),'javaVersion':System.getProperty('java.version'),'windows':windows,'errors':errors}
        with open('Z:/home/maxlang/Projects/topspin-viewer/evidence/ui/component-tree.json','w') as f:json.dump(info,f,indent=2)

try:
    Capture().run()
except:
    import traceback
    with open('Z:/home/maxlang/Projects/topspin-viewer/evidence/ui/capture-error.txt','w') as f: f.write(traceback.format_exc())
