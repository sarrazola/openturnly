# Native iOS app

A SwiftUI app for the people who run a business on OpenTurnly: the owner, the
admins and the staff. It signs in with `/api/mobile/sign-in`, keeps the bearer
token in the Keychain and calls the same API the web admin uses. The server
enforces every role and rule; the app mirrors them only to offer the actions
the server will accept.

Requirements: Xcode 16 or later, iOS 17 or later, and
[XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).
There are no third-party dependencies.

```bash
cd apps/ios
xcodegen generate            # writes OpenTurnly.xcodeproj, which is never committed
open OpenTurnly.xcodeproj    # pick a simulator and run
```

From the command line:

```bash
xcodebuild -project OpenTurnly.xcodeproj -scheme OpenTurnly \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project OpenTurnly.xcodeproj -scheme OpenTurnly \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO test
```

To try it against a development server on the same Mac, start the server and
type `localhost:<port>` on the first screen.

## What it does

| Tab | For | Screens |
| --- | --- | --- |
| Agenda | everyone | Week strip and calendar, professional and location filters, the day's appointments by morning, afternoon and evening, appointment detail (call, WhatsApp, e-mail, booking answers, customer notes), new appointment with the server's free times, reschedule, cancel, no-show and "they came", deposit, charge and close sale |
| Clientes | admins | Searchable list, detail with visits and history, create and edit, book for a customer |
| Ventas | admins | The day's sales and deposits with totals by payment method, sale detail and void, counter sale |
| Más | everyone | The booking link with share and QR code; reports (admins); a shortcut to the web panel (admins); my account; sign out |

A professional only gets their own agenda: they can book with themselves, move
their own appointments and share the booking link. Everything the server
refuses them is hidden rather than shown as an error.

Setting the business up (opening hours, services, staff, team access, the cash
register, inventory, gift cards and the rest of the settings) is done in the web
panel; "More" opens it in the browser.

## Layout

| Path | Purpose |
| --- | --- |
| `project.yml` | XcodeGen specification; identity settings reference `$(BRAND_*)` variables |
| `Brand/Brand.xcconfig` | The identity this build compiles with (see `WHITELABEL.md`) |
| `OpenTurnly/Info.plist` | Reads the brand variables into a `Brand` dictionary the app loads at launch |
| `OpenTurnly/Sources/App` | Entry point, brand values, the session and the open business |
| `OpenTurnly/Sources/API` | Models and the client; one function per route |
| `OpenTurnly/Sources/Storage` | Keychain-backed session and small preferences |
| `OpenTurnly/Sources/Rules` | Money, business time, server addresses, phones, what each appointment allows, checkout arithmetic |
| `OpenTurnly/Sources/Localization` | English and Spanish copy, chosen by the phone's language, and server errors in plain words |
| `OpenTurnly/Sources/Theme` | Colours, cards, chips, skeletons, toasts and inputs |
| `OpenTurnly/Sources/Views` | Sign-in, agenda, booking, customers, sales and the "More" screens |
| `OpenTurnlyTests` | Unit tests for the rules that do not need a server |

## Behaviour worth knowing

- The server address is typed at sign-in, or derived from an account name when
  the brand carries a hosted preset. `https://` is assumed; plain HTTP is
  accepted only for local network addresses (`localhost`, `127.x`, `10.x`,
  `192.168.x`, `172.16-31.x`, `*.local`), which is what `NSAllowsLocalNetworking`
  in Info.plist allows. The address is checked with `/api/health` first.
- Any `401` ends the session: the token is forgotten and the sign-in screen says
  why. Signing out also ends the session on the server.
- Amounts are integers in the currency's minor unit and are formatted with the
  same fixed table as the server (COP has no decimals). Dates and times are
  always the business's, in its own time zone, never the phone's.
- The agenda loads a week at a time, so moving between days is instant and the
  strip marks the busy ones. After any change (a booking, a sale) the screens
  that show that data reload.
- A simulator build compiled without code signing has no Keychain entitlement.
  Only there, the session is kept in a file inside the app's container so the
  app can still sign in; device builds are always signed and always use the
  Keychain.
- No analytics, no trackers, no push notifications.
