"""Offline SDK example provider. Binds only loopback; never forwards a model request."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from socketserver import TCPServer

class LoopbackServer(ThreadingHTTPServer):
    def server_bind(self):
        # HTTPServer's default bind performs reverse DNS, even for a numeric loopback IP.
        TCPServer.server_bind(self)
        self.server_name = 'localhost'
        self.server_port = self.server_address[1]

class Provider(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(2)

    def do_POST(self):
        try:
            self._post()
        except (ValueError, KeyError, TypeError, UnicodeError):
            self.send_error(400)
        except (TimeoutError, ConnectionError, OSError):
            self.close_connection = True

    def _post(self):
        try:
            size = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            self.send_error(400)
            return
        if not 0 < size <= 1048576:
            self.send_error(413)
            return
        raw = self.rfile.read(size)
        if len(raw) != size:
            self.send_error(400)
            return
        body = json.loads(raw.decode("utf-8"))
        if not isinstance(body, dict) or not isinstance(body.get("messages"), list) or any(not isinstance(m, dict) or not isinstance(m.get("role"), str) for m in body["messages"]):
            self.send_error(400)
            return
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
    with LoopbackServer(('127.0.0.1',8089), Provider) as server:
        print('Offline SDK provider on loopback port 8089', flush=True)
        try: server.serve_forever()
        except KeyboardInterrupt: pass
