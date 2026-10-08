# Architecture

## Overview

Rent Reclaimer is a native Android app (Kotlin + Jetpack Compose) with a clean
separation between the Solana protocol layer, the wallet-adapter layer, and the UI.

```
┌─────────────────────────────────────────┐
│ UI (Compose)                            │
│ Screens.kt — state-driven screens        │
│ Theme.kt — Solana dark theme            │
└───────────────┬─────────────────────────┘
                │ UiState (sealed interface)
┌───────────────▼─────────────────────────┐
│ MainViewModel                           │
│ - MWA session lifecycle                 │
│ - scan / reclaim orchestration          │
│ - demo mode                             │
└───┬─────────────────────────┬───────────┘
    │                         │
┌───▼───────────┐   ┌─────────▼──────────────────┐
│ SolanaRpc     │   │ MobileWalletAdapter (2.2.0) │
│ - getToken-   │   │ - transact { authorize }   │
│   AccountsBy- │   │ - signAndSendTransactions  │
│   Owner       │   └────────────────────────────┘
│ - getLatest-  │
│   Blockhash   │
└───┬───────────┘
    │
┌───▼───────────────┐
│ TransactionBuilder│  Base58
│ - CloseAccount ix │  (codec)
│ - legacy message  │
│   serializer      │
└───────────────────┘
```

## Transaction construction

The app builds an **unsigned legacy transaction** locally:

1. `getLatestBlockhash` → 32-byte recent blockhash
2. Account keys: `[owner(signer,writable), tokenAccount₁…ₙ(writable), TokenProgram(readonly)]`
3. One SPL Token `CloseAccount` instruction (discriminator `9`) per account:
   `accounts = [tokenAccount, destination=owner, authority=owner]`
4. Prepend `[sigCount=1][64 zero bytes]` placeholder signature

The unsigned bytes go to the wallet via MWA `signAndSendTransactions`, which signs
with the Seed Vault / wallet key and submits. The dApp never sees private keys.

Serialization is covered by 24 assertions in
`app/src/test/java/app/rentreclaimer/solana/TxVerify.kt` (header bytes, account
ordering, instruction layout, multi-account batching).

## Why no web3 SDK?

The Solana Kotlin ecosystem moves fast and several libs are dormant. The surface we
need (3 RPC methods + legacy tx serialization) is small, fully specified, and
unit-testable — so we implemented it directly on OkHttp + org.json. Fewer
dependencies, deterministic builds, no version-churn risk during the hackathon.

## Cluster support

Mainnet-beta (default) and Devnet, switchable on the connect screen. MWA
`blockchain` is set to match before `transact`, so re-authorization targets the
right cluster.
