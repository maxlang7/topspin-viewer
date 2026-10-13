from bruker.api.topspin import Topspin
top=Topspin()
identifier=top.getDataProvider().getCurrentDatasetIdentifier()
if 'topspin-viewer' not in identifier or 'artifacts' not in identifier:raise RuntimeError('UI probe requires isolated project dataset')
top.executeCommand('.ret',wait=True)
top.executeCommand('xpy Z:/home/maxlang/Projects/topspin-viewer/tests/open_integrals_tab.py',wait=True)
top.executeCommand('xpy Z:/home/maxlang/Projects/topspin-viewer/tests/inspect_topspin_ui.py',wait=True)
top.executeCommand('xpy Z:/home/maxlang/Projects/topspin-viewer/tests/inspect_integrals_table.py',wait=True)
