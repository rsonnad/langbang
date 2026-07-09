# LangBang Phrase Bridge Worker

Small compatibility Worker for clients that cannot safely call the authenticated
LangBang Agent API directly.

- Live URL: `https://langbang-phrase-bridge.langbangml.workers.dev/add-phrases`
- Cloudflare account: `langbangapp@gmail.com`
- Account ID: `df99afea5ab9636a19adbdead37fc133`
- Secret: `LANGBANG_AGENT_TOKEN`
- Upstream: `https://langbang.org/v1/agent/phrases`
- Approval page: `https://langbang.org/bridge/approve`

This Worker is an adapter around the LangBang Agent API. It accepts a JSON array
of phrases, then forwards each phrase to LangBang with `atomic:true`. It does not
replace `/v1/agent/phrases`.

Bridge writes are not permanently public. With `ALLOW_PUBLIC_WRITES=false`, a
signed-in approver must open the approval page and allow bridge writes for up to
one hour. If no approval is active, `/add-phrases` returns `approval_required`.

Approval endpoints:

- `GET /approval-status` reports whether a write window is active.
- `POST /approve` requires `Authorization: Bearer <LangBang web session token>`
  and opens a write window for at most 60 minutes.
- `POST /revoke` requires the same LangBang web session token and closes the
  current write window.

Only emails listed in `APPROVER_EMAILS` can approve or revoke. Keep
`ALLOW_PUBLIC_WRITES=false` for production unless the user explicitly requests a
temporary public test.

Do not deploy this Worker or any other LangBang-writing bridge in the
Wingsiebird Cloudflare account. Wingsiebird is only for the `langbang.org`
DNS/zone/site-edge shim.

## Example

```bash
curl -sS https://langbang-phrase-bridge.langbangml.workers.dev/add-phrases \
  -H "Content-Type: application/json" \
  -d '{
    "groupTitle": "More Than I Thought",
    "phrases": [
      "Your laugh and that look in those eyes,",
      "rail trips, and blue sunny skies."
    ]
  }'
```

If a lyric fragment is rejected by LangBang's phrase generation, provide both
`english` and `polish` fields for that item.
