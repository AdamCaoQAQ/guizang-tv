#!/usr/bin/env python3
"""Opt-in LAN companion. HTTPS + certificate pin + bearer, Codex over local stdio.
Python 3.11+, Codex CLI logged in, openssl for setup. No third-party dependencies.
"""
import argparse, base64, hashlib, hmac, http.server, ipaddress, json, os
from pathlib import Path
import queue, re, secrets, shutil, ssl, subprocess, threading, time, tomllib

MAX_BODY = 16384


def codex_command(binary):
    args = [binary, 'app-server', '--listen', 'stdio://']
    # Do not inherit personal integrations into a shared living-room chat.
    home = Path(os.environ.get('CODEX_HOME', Path.home() / '.codex'))
    config = tomllib.loads((home / 'config.toml').read_text()) if (home / 'config.toml').exists() else {}
    for feature in ('shell_tool', 'hooks', 'apps', 'plugins', 'remote_plugin', 'multi_agent',
                    'skill_search', 'image_generation', 'browser_use', 'computer_use'):
        args += ['-c', f'features.{feature}=false']
    for name in config.get('mcp_servers', {}):
        if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
            raise ValueError("Unsupported MCP configuration name; use a dedicated Codex profile")
        args += ['-c', f'mcp_servers.{name}.enabled=false']
    args += ['-c', 'web_search="disabled"']
    return args


class Codex:
    def __init__(self, binary, work, callback):
        self.callback, self.pending, self.serial = callback, {}, 0
        self.lock = threading.Lock()
        self.process = subprocess.Popen(codex_command(binary), cwd=work, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        threading.Thread(target=self.read, daemon=True).start()
        try:
            self.rpc('initialize', {'clientInfo': {'name': 'guizang_tv', 'title': 'GuiZang TV', 'version': '0.1.0'}})
            self.write({'method': 'initialized', 'params': {}})
        except Exception:
            self.close()
            raise

    def write(self, obj):
        with self.lock:
            self.process.stdin.write(json.dumps(obj) + '\n')
            self.process.stdin.flush()

    def rpc(self, method, params):
        with self.lock:
            self.serial += 1
            rid = self.serial
            q = self.pending[rid] = queue.Queue()
        self.write({'id': rid, 'method': method, 'params': params})
        try:
            result = q.get(timeout=25)
            if 'error' in result:
                raise RuntimeError('Codex 请求失败，请检查电脑端登录与连接')
            return result['result']
        finally:
            self.pending.pop(rid, None)

    def read(self):
        try:
            for line in self.process.stdout:
                obj = json.loads(line)
                if 'method' in obj and 'id' in obj:
                    # This trial never approves agent side effects or grants permissions.
                    self.write({'id': obj['id'], 'error': {'code': -32601, 'message': 'Unavailable in TV chat trial'}})
                elif 'id' in obj:
                    q = self.pending.get(obj['id'])
                    if q: q.put(obj)
                else:
                    self.callback(obj.get('method'), obj.get('params', {}))
        finally:
            for q in list(self.pending.values()): q.put({'error': 'disconnected'})
            self.callback('bridge/disconnected', {})

    def close(self):
        self.process.terminate()
        try: self.process.wait(timeout=5)
        except subprocess.TimeoutExpired: self.process.kill()


class Chat:
    def __init__(self, binary, work, voice=None):
        self.lock = threading.RLock()
        self.thread = self.turn = None
        self.messages, self.reply, self.error = [], '', ''
        self.busy, self.alive = False, True
        self.voice_command, self.listening, self.draft = voice, False, ''
        self.voice_generation = 0
        self.generation = 0
        self.codex = Codex(binary, work, self.event)
        self.work = str(work)

    def state(self):
        with self.lock:
            return {'messages': list(self.messages), 'reply': self.reply, 'busy': self.busy,
                    'error': self.error, 'alive': self.alive, 'voiceAvailable': bool(self.voice_command),
                    'listening': self.listening, 'draft': self.draft}

    def event(self, method, params):
        with self.lock:
            if method == 'bridge/disconnected':
                self.alive = self.busy = False
                self.error = 'Codex 已断开，请在电脑上重启连接服务'
                return
            if params.get('threadId') != self.thread: return
            if method == 'turn/started': self.turn = params['turn']['id']
            if method == 'item/agentMessage/delta': self.reply = (self.reply + params.get('delta', ''))[:24000]
            if method == 'turn/completed':
                status = params['turn']['status']
                if self.reply: self.messages.append({'role': 'assistant', 'text': self.reply})
                self.messages = self.messages[-12:]
                self.reply, self.busy, self.turn = '', False, None
                if status == 'failed': self.error = '本次回答失败，请检查电脑端 Codex 登录、额度或网络后重试'
                elif status == 'interrupted': self.error = '已停止回答'

    def send(self, text):
        text = text.strip()
        if not text or len(text) > 2000: raise ValueError('请输入 1–2000 字')
        with self.lock:
            if self.busy or self.listening: raise ValueError('请先结束当前回答或语音输入')
            if not self.alive: raise ValueError('电脑上的 Codex 已断开')
            self.busy, self.reply, self.error, self.draft = True, '', '', ''
            self.messages.append({'role': 'user', 'text': text})
            self.messages = self.messages[-12:]
            self.generation += 1
            generation = self.generation
        threading.Thread(target=self.run, args=(text, generation), daemon=True).start()

    def run(self, text, generation):
        try:
            if not self.thread:
                result = self.codex.rpc('thread/start', {'cwd': self.work, 'ephemeral': True,
                    'sandbox': 'read-only', 'approvalPolicy': 'never',
                    'developerInstructions': '你是归藏 TV 的 Codex 试用助手。用简洁中文回答，适合电视阅读。此版本仅文字对话，不使用工具、不访问文件、不控制设备。没有实时天气、日程、米家、聊天历史权限；不声称已执行操作。'})
                self.thread = result['thread']['id']
            result = self.codex.rpc('turn/start', {'threadId': self.thread,
                'input': [{'type': 'text', 'text': text}], 'effort': 'low'})
            with self.lock:
                if self.busy: self.turn = result['turn']['id']
            timer = threading.Timer(180, lambda: self.timeout(generation))
            timer.daemon = True; timer.start()
        except Exception:
            with self.lock:
                self.busy = False
                self.error = '连接 Codex 失败，请检查电脑端登录与网络后重试'

    def timeout(self, generation):
        with self.lock:
            if generation != self.generation or not self.busy: return
        self.cancel()

    def cancel(self):
        with self.lock: tid, turn = self.thread, self.turn
        if tid and turn:
            self.codex.rpc('turn/interrupt', {'threadId': tid, 'turnId': turn})
        elif self.busy:
            raise ValueError('正在建立连接，请稍后停止')

    def reset(self):
        with self.lock:
            if self.busy or self.listening: raise ValueError('请先结束当前回答或语音输入')
            self.thread = self.turn = None
            self.messages, self.reply, self.error, self.draft = [], '', '', ''
            self.generation += 1

    def voice(self):
        with self.lock:
            if not self.voice_command: raise ValueError('此电脑未配置遥控器语音实验')
            if self.busy or self.listening: raise ValueError('请先结束当前回答或语音输入')
            self.listening, self.draft, self.error = True, '', ''
            self.voice_generation += 1
            generation = self.voice_generation
        def listen():
            try:
                result = subprocess.run(self.voice_command, capture_output=True, text=True, timeout=35)
                text = result.stdout.strip()[:2000] if result.returncode == 0 else ''
                with self.lock:
                    if generation != self.voice_generation: return
                    self.draft = text
                    if not text: self.error = '未收到语音文字，请检查电视调试连接或使用键盘输入'
            except Exception:
                with self.lock: self.error = '语音实验连接失败，请使用键盘输入'
            finally:
                with self.lock: self.listening = False
        threading.Thread(target=listen, daemon=True).start()


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args): pass  # Do not log text, tokens or client addresses.
    def response(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers(); self.wfile.write(data)

    def authorized(self):
        supplied = self.headers.get('Authorization', '')
        return not self.headers.get('Origin') and hmac.compare_digest(supplied, 'Bearer ' + self.server.token)

    def do_GET(self):
        if not self.authorized(): return self.response(401, {'error': '未配对'})
        if self.path != '/state': return self.response(404, {'error': '未找到'})
        self.response(200, self.server.chat.state())

    def do_POST(self):
        if not self.authorized(): return self.response(401, {'error': '未配对'})
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= MAX_BODY: return self.response(413, {'error': '请求过大或为空'})
            self.connection.settimeout(10)
            body = json.loads(self.rfile.read(size))
            if not isinstance(body, dict): raise ValueError('无效请求')
            if self.path == '/message':
                if not isinstance(body.get('text'), str): raise ValueError('无效文字')
                self.server.chat.send(body['text'])
            elif self.path == '/cancel': self.server.chat.cancel()
            elif self.path == '/reset': self.server.chat.reset()
            elif self.path == '/voice': self.server.chat.voice()
            else: return self.response(404, {'error': '未找到'})
            self.response(200, self.server.chat.state())
        except ValueError as e: self.response(400, {'error': str(e)})
        except Exception: self.response(503, {'error': '连接暂时不可用'})


def setup(home, host, port):
    ipaddress.ip_address(host)  # Bind an explicit IP; do not resolve untrusted hostnames.
    home.mkdir(parents=True, exist_ok=True, mode=0o700)
    if (home / 'config.json').exists(): raise SystemExit('配置已存在；如需新配对，请使用新目录，旧文件保留。')
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '365',
                    '-keyout', str(home/'key.pem'), '-out', str(home/'cert.pem'), '-subj', '/CN=GuiZang TV',
                    '-addext', 'subjectAltName=IP:'+host], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    cert = ssl.PEM_cert_to_DER_cert((home/'cert.pem').read_text())
    conf = {'host': host, 'port': port, 'token': secrets.token_urlsafe(32), 'pin': hashlib.sha256(cert).hexdigest()}
    (home/'config.json').write_text(json.dumps(conf))
    pair = {'url': f'https://{host}:{port}', 'token': conf['token'], 'pin': conf['pin']}
    (home/'tv-pairing.txt').write_text('gtv1:'+base64.urlsafe_b64encode(json.dumps(pair).encode()).decode())
    for name in ('key.pem', 'config.json', 'tv-pairing.txt'): (home/name).chmod(0o600)
    print('配对文件已生成，请通过可信方式填写到电视。不要提交或分享配对文件。')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home', type=Path, required=True, help='Private state directory outside the source repository')
    p.add_argument('--init', action='store_true'); p.add_argument('--host', default='127.0.0.1')
    p.add_argument('--port', type=int, default=8765); p.add_argument('--codex', default=shutil.which('codex'))
    p.add_argument('--voice-command', nargs='+', help='Optional local experiment adapter; stdout must contain only recognized text')
    args = p.parse_args(); home = args.home.resolve()
    os.umask(0o077)
    if args.init: return setup(home, args.host, args.port)
    conf = json.loads((home/'config.json').read_text())
    work = home/'work'; work.mkdir(exist_ok=True)
    if not args.codex: raise SystemExit('请先安装 Codex CLI 并执行 codex login')
    server = http.server.ThreadingHTTPServer((conf['host'], conf['port']), Handler)
    try: chat = Chat(args.codex, work, args.voice_command)
    except Exception:
        server.server_close()
        raise
    server.daemon_threads = True; server.token, server.chat = conf['token'], chat
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(home/'cert.pem', home/'key.pem')
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    print('Codex 电视连接服务已启动；Ctrl+C 停止。', flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close(); chat.codex.close()

if __name__ == '__main__': main()
