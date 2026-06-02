Project: luminus/ring-undertow-adapter — Ring adapter for Undertow. Clojure/Leiningen project targeting Java 17.

Build/Test:
- `lein test ring.adapter.test.undertow` — runs all tests
- Deps: undertow-core 2.4.0.Final, ring-core 1.15.3

Source layout:
- `src/ring/adapter/undertow.clj` — main adapter, handler routing
- `src/ring/adapter/undertow/websocket.clj` — custom `:undertow/websocket` WS API
- `src/ring/adapter/undertow/request.clj` — request map building
- `src/ring/adapter/undertow/response.clj` — response writing via RespondBody protocol
- `src/ring/adapter/undertow/headers.clj` — header get/set
- `src/ring/adapter/undertow/ssl.clj` — SSL context
- `src/ring/adapter/undertow/middleware/gzip.clj`, `session.clj` — middleware
- Tests: `test/ring/adapter/test/undertow.clj` — uses clj-http + gniazdo (WS client)
