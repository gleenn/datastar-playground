# datastar-playground

The smallest honest Datastar + Ring app: one `deps.edn`, one Clojure file, no JS build step.

```sh
clojure -M:run    # http://localhost:8080
```

You get a server-driven clock (streamed over SSE) and a counter whose state the
server owns. `src/app.clj` is the whole app.

## The one idea

Datastar sends the page's signals with every request, and your response says
what to change. There is nothing else to learn.

## Datastar answers a request in one of three ways

Pick by `Content-Type` — the first two need no SSE at all:

| Response `Content-Type` | Datastar does                            | Body is          |
|-------------------------|------------------------------------------|------------------|
| `text/html`             | patches elements, matched by `id`        | an HTML fragment |
| `application/json`      | patches signals                          | a JSON object    |
| `text/event-stream`     | either of the above, repeatedly, live    | SSE events       |

`/inc` uses the JSON form (one line). `/clock` uses SSE to push forever.

## The SSE wire format

Each event names the patch twice — once as the event, once as the first word of
the data line:

```
event: datastar-patch-elements
data: elements <b id=clock>12:34:56</b>

```

Swap both words for `signals` and send `{"count":1}` to patch signals instead.
That's the entire protocol, which is why `sse` in `app.clj` is a few lines of
`format`.

## Signals come back on every request

- `GET` → JSON in the `datastar` query param: `/clock?datastar={"count":0}`
- everything else → JSON request body

`signals` in `app.clj` reads both.

## Attribute syntax (v1 gotcha)

A plugin's key is separated by a **colon**, not a hyphen:

```html
<body data-signals:count="0">                     <!-- not data-signals-count -->
<button data-on:click="@post('/inc')">            <!-- not data-on-click      -->
<span data-text="$count"></span>                  <!-- no key, no colon       -->
```

`data-on-click` parses as a plugin named `on-click`, which doesn't exist, so
Datastar **silently ignores it** — no console error. If an attribute seems dead,
check the colon first. (Keyless plugins like `data-text`, `data-init` and
`data-json-signals` keep their hyphens.)

Drop `<pre data-json-signals></pre>` on the page to watch the live signal store
while you experiment.

## Things to try

- Add `data-on-interval="@post('/inc')"` to the button to count up on its own
  (keyless plugin, so it keeps its hyphen).
- Make `/inc` return `text/html` with `<button id=...>` and patch the element
  instead of the signal — same app, the other Datastar half.
- `curl -N http://localhost:8080/clock` to read the raw stream.

Pinned to [Datastar v1.0.4](https://data-star.dev) via CDN.
