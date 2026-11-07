# InstaOS

A web-session Instagram client for Android — a test-account client built
on the [InstaGRUM](https://github.com/zyrexdz/InstaGRUM) UI base (MIT).
It signs in inside Instagram's own page, keeps the session encrypted on
the device, and reads the feed, stories, profile and messages through
that session. It is **not** Instagram, and it is not affiliated with
Meta. Nothing here is simulated: there are no fake followers, no fake
likes, no fake viewers. Where something is not available yet, the app
says so instead of inventing it.

## What is real

- **Sign-in** — WebView login on instagram.com; the `sessionid` cookie
  is harvested from the WebView's cookie jar and stored encrypted
  (`EncryptedSharedPreferences`, never in a backup, never on a server).
- **Home feed** — posts from the logged-in home page (GraphQL /
  homepage-HTML fallback) with pull-to-refresh and pagination; like,
  save and comment use the web endpoints.
- **Stories tray + viewer** — tap a ring to load that person's stories
  (`feed/reels_media`); the viewer shows the real author and the real
  view count. The viewer list is not loaded in this version — the
  insights panel says so plainly.
- **Reels** — full-screen vertical feed of the feed's videos plus your
  own local videos; double-tap to like, right rail for like / comments /
  save / share.
- **Profile** — your real posts / followers / following from the web
  session, 3:4 grid, Saved tab, highlights. Local posts you create in
  the app live alongside them and are labelled as on-device.
- **DMs** — inbox and threads from the `direct_v2` web endpoints,
  polled while the screen is open (no push, no realtime). Own messages
  right-aligned in purple→blue bubbles, received left in grey; date
  separators; "Seen" receipts where the API returns them; send works
  (optimistic, with tap-to-retry on failure). End-to-end encrypted
  threads are listed but cannot be read or sent in this version — the
  app says so instead of faking them.
- **Search** — people from `web/search/topsearch`; Explore is your real
  feed as a 3:4 grid (no fake tiles).
- **Activity** — unread DMs plus real comments on your posts, on this
  device. Instagram's full activity feed is not available to a
  web-session client.
- **Settings** — dark / light appearance, accent colour (violet, lime,
  pink, teal), sign out, backup / restore of local data (the login
  session is never included in a backup).

## What is deliberately NOT in v1

- Creating posts / stories / reels on Instagram (the in-app creator
  makes local-only posts; publishing to Instagram is not wired).
- Live streaming (real IG Live needs RTMP — nothing streams).
- Push notifications, realtime DM updates (polling only).
- Reactions on DM messages, the full viewer list for stories, a
  profile page for other people (search results open them on
  instagram.com instead).
- Posting, commenting or liking from a second account — the app signs
  in with one test account.

## Known gaps (honest, not yet verified without a live login)

- The web feed path is built but not yet proven against a live session:
  the homepage-HTML feed blob may not exist on the logged-in home page
  in November 2026, and the feed GraphQL response shape changed
  (`xdt_api__v1__feed__timeline__connection` instead of the old
  `edge_web_feed_timeline`). The captured doc_id
  (`29356681030682853`, `PolarisFeedRootPaginationCachedQuery_subscribe`)
  is deliberately **not** wired in — posting a guessed query would
  break the feed outright.
- The stories-tray GraphQL doc_id is not captured; the tray loads only
  via `reels_media` after a ring is tapped, and is empty until then.
- Like / save / follow / comment and DM send endpoints are verified
  against canned payloads in unit tests only — not against a live
  session yet.

## Build

```sh
# JDK 21, Android SDK, Gradle wrapper 8.11.1
export JAVA_HOME=~/jdk21
export ANDROID_HOME=~/android-sdk
./gradlew assembleDebug testDebugUnitTest   # debug + unit tests
./gradlew assembleRelease                   # release (needs the signing
                                            # properties at
                                            # /home/ubuntu/instaos/gradle.properties)
```

Release signing reads the keystore properties from
`/home/ubuntu/instaos/gradle.properties` on the build machine only.
No passwords live in this repo; a release build fails if the
properties file or any key is absent.

## Attribution

UI base: [InstaGRUM](https://github.com/zyrexdz/InstaGRUM) by zyrexdz,
MIT licence. The simulation, notifications and Live code that shipped
with it were removed in InstaOS 1.1.0; what remains is the Compose UI
shell, restyled to the current iOS Instagram look (floating capsule
tab bar, true-black dark theme, gradient story rings, 3:4 grids).
