## 1.2.6 - July 21, 2022

- Bump to undertow-core 2.2.18.Final

## 1.2.5

## 1.2.4 - December 7, 2021

- Bump to undertow-core 2.2.14.Final
- Update license to MIT

## 1.2.3 - August 19, 2021

- Fix project.clj config to prevent [packaging issue](https://github.com/luminus-framework/ring-undertow-adapter/issues/19)
- Bump to ring 1.9.4
- Bump to undertow-core 2.2.10.Final

## 1.2.2 - July 22, 2021

- Fix reflection warning [PR 17](https://github.com/luminus-framework/ring-undertow-adapter/pull/17)

## 1.2.1 - April 18, 2021

- Add `custom-manager` configuration option to allow custom SessionManager interface to be passed in and used instead of the default InMemorySessionManager.
- Bump to ring 1.9.2
- Bump to undertow-core 2.2.7.Final

## 1.2.0 - February 20, 2021

- Add InMemorySessionManager from Undertow by default
- Add configuration options: `session-manager?` (default: true), `max-sessions` (default: -1), `server-name` (default: "ring-undertow")
- Bump to ring 1.9.1
- Bump to undertow-core 2.2.4.Final

## 1.1.5 - February 20, 2021
- Remove delay from session middleware. This is because using a different form of request mapping, i.e. non-lazy-map.

## 1.1.4 - January 25, 2021
- Add: `:max-entity-size`

## 1.1.3 - October 20, 2020
- Added entire web exchange in response

## 1.1.2 - August 12, 2020
- support for custom headers in Websocket upgrade response [PR 9](https://github.com/luminus-framework/ring-undertow-adapter/pull/9)

## 1.1.1 - July 9, 2020
- Support seqs and byte arrays, to match ring protocol's support
- Add `:websocket?` configuration key to turn on/off websocket handling
- Add `:ring-async?` configuration key to support ring async handlers

## 1.1.0 - May 18, 2020
- **Breaking**: Removed ISeq body response support
- Added: Support for ByteBuffer responses
- Added: `websocket?` key to exchange request map
- Added: Exception on unsupported response body class
- Improved: InputStream response handling based on IO blocking
- Misc: Removed use of deprecated `extractTokenFromHeader` 

## 1.0.6 - May 15, 2020
- Bumped to `io.undertow/undertow-core "2.1.1.Final"`
- Bumped to `ring/ring-core "1.8.1"`

## 1.0.5 - May 15, 2020
- Added: `:handler-proxy` key to provide custom user `handler-proxy`. Must implement `HttpHandler`
- Fixed: javac target incorrectly set to 1.10. Reverted to 1.8

## 1.0.4 - April 8, 2020
- Added: immutant-style ring session middleware in `ring.adapter.undertow.middleware.session`

## 1.0.3 - April 5, 2020
- Added: websocket support
