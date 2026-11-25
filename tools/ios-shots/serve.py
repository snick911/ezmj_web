"""iPhone 模擬器截圖用的網站：照原樣送出資料夾裡的檔案，另外收 harness.js 的報告。

    python3 serve.py <網站資料夾> <報告檔> <截好圖的記號資料夾>

POST /report 把內容加一行到報告檔；GET /ack/<步驟> 在 run.sh 截好那一步的圖（記號資料夾有同名的檔案）時回 200，
不然回 404。只聽 127.0.0.1:8080。
"""
import functools
import http.server
import os
import re
import sys

ROOT, REPORTS, ACKS = sys.argv[1], sys.argv[2], sys.argv[3]


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        '.js': 'text/javascript',
        '.webmanifest': 'application/manifest+json',
        '.woff2': 'font/woff2',
    }

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def do_POST(self):
        if self.path != '/report':
            self.send_error(404)
            return
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        with open(REPORTS, 'ab') as out:
            out.write(body.replace(b'\n', b' ') + b'\n')
        print('report', body.decode('utf-8', 'replace'), flush=True)
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        match = re.fullmatch(r'/ack/([a-z-]+)', self.path)
        if match:
            self.send_response(200 if os.path.exists(os.path.join(ACKS, match.group(1))) else 404)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        super().do_GET()

    def log_message(self, format, *args):
        pass


if __name__ == '__main__':
    handler = functools.partial(Handler, directory=ROOT)
    http.server.ThreadingHTTPServer(('127.0.0.1', 8080), handler).serve_forever()
