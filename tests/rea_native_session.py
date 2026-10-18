"""Project-local REA stdio client; retains a single native analysis session."""
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import threading
import time

root = Path.cwd()
launchers = list((root / 'artifacts/rea-npm-cache/_npx').glob('*/node_modules/rea-agents/scripts/rea.mjs'))
if len(launchers) != 1:
    raise SystemExit(f'Expected one pinned REA launcher, got {len(launchers)}')
messages = queue.Queue()
log = (root / 'artifacts/rea-native-session-stderr.log').open('a')
process = subprocess.Popen(['node', str(launchers[0]), '--mcp'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log, text=True, bufsize=1)
def receive():
    for line in process.stdout:
        try:
            messages.put(json.loads(line))
        except json.JSONDecodeError:
            messages.put({'diagnostic': line.strip()})
    messages.put({'process_exited': process.poll()})
threading.Thread(target=receive, daemon=True).start()
serial = 0
def send(method, params, notification=False):
    global serial
    serial += 1
    message = {'jsonrpc':'2.0', 'method':method, 'params':params}
    if not notification:
        message['id'] = serial
    process.stdin.write(json.dumps(message)+'\n');process.stdin.flush()
    if notification:
        return
    started=time.monotonic()
    while True:
        try:
            response=messages.get(timeout=20)
        except queue.Empty:
            print(f'REA {method} running for {int(time.monotonic()-started)} seconds', flush=True)
            continue
        if response.get('id') == serial:
            if 'error' in response:
                raise RuntimeError(response['error'])
            return response['result']
        if 'process_exited' in response:
            raise RuntimeError(response)
def payload(result):
    if result.get('structuredContent') is not None:
        return result['structuredContent']
    for block in result.get('content',[]):
        if block.get('type')=='text':
            try:
                return json.loads(block['text'])
            except json.JSONDecodeError:
                return block['text']
    return result
try:
    send('initialize', {'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'topspin-local-native-reference','version':'1'}})
    send('notifications/initialized', {}, True)
    catalog=send('tools/list', {})
    (root/'artifacts/rea-native-tool-catalog.json').write_text(json.dumps(catalog,indent=2))
    print('REA ready; tools: '+', '.join(tool['name'] for tool in catalog['tools'] if any(word in tool['name'] for word in ['binary','search','function','decomp','xref','snapshot'])), flush=True)
    for line in sys.stdin:
        command=json.loads(line)
        if command.get('stop'):
            break
        result=send('tools/call', {'name':command['tool'],'arguments':command.get('arguments',{})})
        target=root/command['output']
        if target.exists():
            raise RuntimeError(f'Refusing to overwrite {target}')
        target.write_text(json.dumps(result,indent=2))
        value=payload(result)
        rendered=json.dumps(value.get("normalized_result",value) if isinstance(value,dict) else value)
        print(json.dumps({'saved':str(target),'isError':result.get('isError',False),'preview':rendered[:1800]}), flush=True)
finally:
    process.stdin.close()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.terminate();process.wait(timeout=10)
    log.close()
