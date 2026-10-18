"""Offline SDK example provider. Binds only loopback; never forwards a model request."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Provider(BaseHTTPRequestHandler):
    def do_POST(self):
        size = int(self.headers.get('Content-Length', '0'))
        if not 0 < size <= 1048576:
            self.send_error(413)
            return
        body = json.loads(self.rfile.read(size))
        called = any(message.get('role') == 'tool' for message in body['messages'])
        message = {'role': 'assistant', 'content': 'README inspected using the local SDK fixture.'}
        if not called:
            message = {'role': 'assistant', 'content': None, 'tool_calls': [{'id':'demo-read','type':'function','function':{'name':'read_file','arguments':json.dumps({'path':'README.md'})}}]}
        result = {'choices':[{'index':0,'finish_reason':'stop' if called else 'tool_calls','message':message}], 'usage':{'prompt_tokens':20,'completion_tokens':8,'total_tokens':28}}
        content = json.dumps(result).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)
    def log_message(self, *_):
        pass

if __name__ == '__main__':
    with ThreadingHTTPServer(('127.0.0.1',8089), Provider) as server:
        print('Offline SDK provider on loopback port 8089', flush=True)
        try: server.serve_forever()
        except KeyboardInterrupt: pass
