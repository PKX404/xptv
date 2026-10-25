# Xparty

[Open Xparty 0.10.0](https://pkxparty.onrender.com)

Watch-party application source is maintained here, separately from PKX404's personal website. The release runs from `release/0.10.0` on Render in Singapore. Render reported the deployment live on 2026-10-01; startup logs confirmed Xparty 0.10 listening on port 10000.

The original Second Hero source is preserved in `preserved-second-hero-20261001`.

## Run and verify

Inside `xparty-service`: `npm ci`, `npm test`, `npm run check`, then `npm start`.

15 server tests pass. Local Chromium interface and simulated-YouTube checks pass at 320/390/844/1366 pixels. The browser test is `tests/release-browser.cjs`; provide Playwright through `CODEX_PRIMARY_RUNTIME_NODE_MODULES` and Chromium through `CHROMIUM_PATH`. Set `XPARTY_REQUIRE_MEDIA=1` to require actual media connectivity. Local test media transport was blocked by zero gathered ICE candidates, even though offer/answer negotiation completed.

This is the core room-improvement release, not completion of every requested feature. Google/OTP and cross-room account messaging require provider setup; TURN credentials and a virtual-browser worker service are not configured. Netflix/Prime/virtual-browser support is not implemented. Actual two-device/different-network media tests and real YouTube streaming validation remain outstanding.

See [release notes](xparty-service/RELEASE-0.10.0.md) and the [complete improvement map](xparty-service/docs/IMPROVEMENT-MAP-20261001.md).
