# Rent Reclaimer

**Reclaim SOL rent trapped in empty token accounts — in one tap.**

Built for the [Solana Mobile CLOCK IN Hackathon](https://solanamobile.radiant.nexus/) (Sept 8 – Oct 8, 2026).

Every SPL Token account on Solana locks ~0.002 SOL in rent. Wallets accumulate empty
token accounts over time — from airdrops, DEX trades, NFT mints — and most users have no
idea that real SOL is sitting there, retrievable. Rent Reclaimer scans your wallet for
empty token accounts and closes them in a single Mobile Wallet Adapter transaction,
returning the rent to you.

## How it works

1. **Connect** — Mobile Wallet Adapter `authorize` (works with any MWA wallet: Phantom, Solflare, …)
2. **Scan** — `getTokenAccountsByOwner` RPC, filters zero-balance SPL Token accounts
3. **Reclaim** — builds SPL Token `CloseAccount` instructions (one per account), sends via
   MWA `signAndSendTransactions`. Rent flows back to your wallet.

## Solana Mobile Stack integration

| Integration | Detail |
|---|---|
| Mobile Wallet Adapter | `authorize` for session + `signAndSendTransactions` for the reclaim tx (clientlib-ktx 2.2.0) |
| Transaction building | Hand-rolled legacy Solana message serializer (no heavy web3 dep) |
| Mobile-first | Native Kotlin + Jetpack Compose, minSdk 26, dark mobile-native UI |
| Seed Vault ready | Key custody stays in the user's wallet app; the dApp never touches keys |

## Project structure

```
app/src/main/java/app/rentreclaimer/
├── MainActivity.kt            # entry point
├── MainViewModel.kt           # MWA session + scan/reclaim orchestration
├── solana/
│   ├── Base58.kt              # base58 codec (no extra dependency)
│   ├── TransactionBuilder.kt  # unsigned CloseAccount tx builder (verified)
│   └── SolanaRpc.kt           # minimal JSON-RPC client (OkHttp)
└── ui/
    ├── Theme.kt               # Solana-brand dark theme
    └── Screens.kt             # Compose screens
app/src/test/.../TxVerify.kt   # 24-assertion verification of tx serialization
```

## Build

Requirements: JDK 17, Android SDK (platform 34, build-tools 34).

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

A release keystore (`rent-reclaimer-release.keystore`) is included for the hackathon build.

## Demo mode

No wallet handy? Tap **"Try demo mode"** on the connect screen to walk through the full
flow with simulated data (clearly badged `DEMO MODE` in the UI).

## Roadmap

- Token-2022 empty-account scanning
- Batch close across >20 accounts (address lookup tables / multiple txs)
- Rent analytics: historical rent paid vs. reclaimed
- dApp Store listing

## License

MIT — see [LICENSE](LICENSE).
