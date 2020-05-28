## 1.1.1 - Unreleased
- Support seqs and byte arrays, to match ring protocol's support

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
