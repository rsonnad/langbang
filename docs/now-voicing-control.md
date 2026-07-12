# Now Voicing LLM control

LangBang's Now Voicing control plane is deliberately separate from the durable
Agent API. It lets an LLM/browser show or play temporary practice content on an
active Android Now Voicing screen; it cannot edit phrases, words, accounts, or
global lessons.

## Learner flow

1. Sign in and open the **External / Now Voicing** tab.
2. Choose **Start LLM control**. The app receives an eight-character pairing
   code and connects to its room over WebSocket.
3. Give the LLM the displayed `https://langbang.org/api/XXXX-XXXX` link.
4. The LLM opens that page, claims the code once, then uses the accessible form
   or the JSON command endpoint.
5. Stop control in the app to revoke the room and close device sockets.

`GET /api` and `GET /api/:code` are read-only. This matters because link
previewers and crawlers can issue GET requests. The one-time claim is always a
POST.

## API

| Route | Caller | Purpose |
| --- | --- | --- |
| `POST /v1/me/now-voicing-control` | signed-in Android app | creates a pairing session |
| `POST /v1/now-voicing-control/claim` | LLM/browser | exchanges the code for a controller capability |
| `POST /v1/now-voicing-control/commands` | controller | sends `show`, `clear`, `play`, `pause`, `resume`, or `stop` |
| `GET /v1/now-voicing-control/ws` | Android app | ordered device delivery WebSocket |
| `DELETE /v1/me/now-voicing-control/:id` | session owner | revokes the room |

Use portable `source` and `target` fields externally, even though the Android
renderer retains legacy `en`/`pl` field names internally:

```json
{
  "id": "optional-client-id",
  "op": "show",
  "source": "Where is the station?",
  "target": "Gdzie jest dworzec?",
  "literal": "Where is station?",
  "speaker": "top",
  "play": "sequence"
}
```

The server never waits for translation or morphology generation on this path.
The app applies the display state first; audio synthesis can still take longer
on a cold cache.

## Security and delivery

- Pairing codes use non-ambiguous Base32, expire after 10 minutes, and are
  single-use. Only an HMAC digest is persisted.
- A claim yields a separate high-entropy controller capability scoped solely to
  Now Voicing. Browser claims also receive a secure HttpOnly cookie.
- The Android device receives a distinct short-lived capability; neither token
  is an `lba_` Agent API token.
- A per-session Durable Object assigns monotonically increasing `seq` values,
  retains a snapshot plus a short replay buffer, and accepts device `ack`s after
  an applied command. Reconnect with `lastSeq` recovers missed commands.
- Each request verifies the narrow capability against its revocable control-session
  row, then enters the room Durable Object. Durable phrase/content tables are
  never touched.

## Manual verification

1. `GET https://langbang.org/api` shows the Now Voicing control page.
2. `GET https://langbang.org/agent` still shows the durable Agent API docs.
3. Start control in a signed-in Android build and open its pairing URL in a
   browser. Claim once; a second claim must be rejected.
4. Submit a `show` command from the form or JSON endpoint. The active Android
   screen should update and acknowledge it.
5. Stop control on Android. Subsequent command calls and the socket should fail.
