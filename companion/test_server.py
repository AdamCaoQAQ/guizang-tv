import http.client,json,threading,unittest
from unittest.mock import patch
import server

class FakeChat:
    def __init__(self): self.sent=[]
    def state(self): return {'messages':self.sent}
    def send(self,text): self.sent.append(text)
    def cancel(self): pass
    def reset(self): self.sent=[]
    def voice(self): pass

class HttpBoundary(unittest.TestCase):
    def setUp(self):
        self.s=server.http.server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        self.s.token='test-secret';self.s.chat=FakeChat()
        self.t=threading.Thread(target=self.s.serve_forever,daemon=True);self.t.start()
    def tearDown(self): self.s.shutdown();self.s.server_close();self.t.join()
    def request(self,path='/state',method='GET',body=None,headers=None):
        c=http.client.HTTPConnection(*self.s.server_address,timeout=2)
        c.request(method,path,body,headers or {});r=c.getresponse();data=json.loads(r.read());c.close();return r.status,data
    def test_missing_token_rejected(self):self.assertEqual(401,self.request()[0])
    def test_wrong_token_rejected(self):self.assertEqual(401,self.request(headers={'Authorization':'Bearer wrong'})[0])
    def test_browser_origin_rejected_even_with_token(self):
        self.assertEqual(401,self.request(headers={'Authorization':'Bearer test-secret','Origin':'https://example.com'})[0])
    def test_paired_state(self):self.assertEqual(200,self.request(headers={'Authorization':'Bearer test-secret'})[0])
    def test_message(self):
        self.assertEqual(200,self.request('/message','POST',json.dumps({'text':'你好'}),{'Authorization':'Bearer test-secret'})[0])
        self.assertEqual(['你好'],self.s.chat.sent)
    def test_large_body_rejected_without_dispatch(self):
        self.assertEqual(413,self.request('/message','POST','x'*17000,{'Authorization':'Bearer test-secret'})[0])
        self.assertEqual([],self.s.chat.sent)
    def test_non_text_payload_rejected(self):
        self.assertEqual(400,self.request('/message','POST','{"text":42}',{'Authorization':'Bearer test-secret'})[0])
    def test_unknown_method_not_proxied(self):
        self.assertEqual(404,self.request('/thread/read','POST','{}',{'Authorization':'Bearer test-secret'})[0])

class ConversationEvents(unittest.TestCase):
    def chat(self):
        with patch.object(server,'Codex'):
            c=server.Chat('codex','/tmp')
        c.thread='a';c.busy=True;return c
    def test_other_thread_does_not_leak(self):
        c=self.chat();c.event('item/agentMessage/delta',{'threadId':'other','delta':'secret'})
        self.assertEqual('',c.reply)
    def test_interrupt_preserves_partial_answer(self):
        c=self.chat();c.event('item/agentMessage/delta',{'threadId':'a','delta':'部分回答'})
        c.event('turn/completed',{'threadId':'a','turn':{'status':'interrupted'}})
        self.assertFalse(c.busy);self.assertEqual('部分回答',c.messages[0]['text'])
    def test_busy_rejects_second_request(self):
        with self.assertRaises(ValueError):self.chat().send('second')
    def test_stale_timeout_cannot_cancel_new_turn(self):
        c=self.chat();c.generation=3;c.timeout(2);c.codex.rpc.assert_not_called()
    def test_dead_server_fails_closed(self):
        c=self.chat();c.event('bridge/disconnected',{})
        self.assertFalse(c.alive)
        with self.assertRaises(ValueError):c.send('hello')

if __name__=='__main__':unittest.main()
