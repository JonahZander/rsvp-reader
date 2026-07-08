# RSVP Reader for Pixel Watch

Read books and articles on your wrist, one word at a time.

RSVP Reader is a Wear OS speed reader using Rapid Serial Visual Presentation:
words are flashed at a fixed point at a speed you control, with the optimal
recognition point (ORP) letter highlighted so your eyes never move. A phone
companion app sends EPUB books, shared articles, or pasted text to the watch.

Inspired by [rsvpnano](https://github.com/ionutdecebal/rsvpnano) (an ESP32
reading device); reimplemented from scratch in Kotlin for Wear OS.

## Modules

- `:core` — pure Kotlin: RSVP engine (tokenization, per-word timing, ORP),
  book model, JSON serialization, file storage. Fully unit tested.
- `:wear` — the watch app: Compose for Wear OS. Library → chapters → reader.
  Tap to play/pause, rotary crown adjusts speed while playing and scrubs
  while paused. Reading position and settings persist via DataStore.
- `:mobile` — the phone companion: import EPUBs (file picker or share sheet),
  share/paste text, manage the sent library, set default speed/font. Syncs
  to the watch over the Wearable Data Layer (books as Assets at
  `/books/<id>`, settings at `/settings`).

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew assembleDebug        # both APKs
./gradlew test                 # all unit tests
./gradlew :wear:installDebug   # install on a connected watch/emulator
./gradlew :mobile:installDebug # install on a connected phone
```

Both apps share the `com.jonahzander.rsvpreader` application id and must be
signed with the same key for the Data Layer to route between them.

## Reading controls (watch)

| Input | Playing | Paused |
|---|---|---|
| Tap | pause | play |
| Crown | change WPM (100–900) | scrub word by word |
| Swipe right | back | back |

Words get extra display time when they are long or end a clause/sentence.
The screen stays on only while words are streaming.
