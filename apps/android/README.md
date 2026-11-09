# Native Android app

A Kotlin and Jetpack Compose app for the people who run a business on
OpenTurnly: the owner, the admins and the professionals. It does from the
phone what they do in the web admin: the agenda, booking and moving
appointments, charging them, customers, sales, and the business's settings. It
is built with Android's own frameworks (AndroidX, Compose, Material 3,
kotlinx.serialization and coroutines), with no analytics and no trackers. The
one other library is Firebase Cloud Messaging, the platform's push channel,
which only starts when the brand file carries a Firebase app's values (see
`WHITELABEL.md`). The customers' booking page is not part of it.

It signs a person in with `/api/mobile/sign-in`, keeps the server and the
bearer token in a file encrypted with a hardware-backed key, and calls the same
API the web admin uses. The server decides what each role may do; the app
hides what a role cannot use instead of showing errors.

Requirements: Android Studio with the Android 16 SDK (API 36), and the JDK it
bundles (the Gradle build targets 17 from any newer JDK).

```bash
cd apps/android
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # the rules that do not need a server
./gradlew :app:installDebug           # onto the running emulator or a USB device
```

Open the `apps/android` directory in Android Studio to run it on a device or an
emulator. On the emulator, a server on the development machine is reached at
`10.0.2.2` (for example `10.0.2.2:4100`), which the app accepts over plain HTTP
like any local address.

## Layout

| Path | Purpose |
| --- | --- |
| `brand/brand.properties` | The identity this build compiles with (see `WHITELABEL.md`) |
| `brand/res` | Launcher icon layers, sign-in logo and icon background, beside the brand file |
| `app/build.gradle.kts` | Reads the brand file into the application id, version and `BuildConfig` |
| `app/src/main/kotlin/.../api` | Typed models and the API client; one function per route |
| `app/src/main/kotlin/.../storage` | The encrypted session, the last server used and this phone's push settings |
| `app/src/main/kotlin/.../push` | Firebase Cloud Messaging: starting it from the brand, the token, the messaging service and the posted notifications |
| `app/src/main/kotlin/.../rules` | Server addresses, money, business time, appointment states and actions, checkout arithmetic, phones, QR codes, the inbox and reading a push message (`NotificationData.kt`) |
| `app/src/main/kotlin/.../localization` | English and Spanish copy, chosen by the phone's language |
| `app/src/main/kotlin/.../ui` | Theme, components and the screens: sign-in, agenda, customers, sales, more |
| `app/src/test` | Unit tests for the rules that do not need a server |

## What it does

- **Agenda**: a week strip (swipe for other weeks, or pick any day from the
  calendar), the day's appointments in morning, afternoon and evening, filters
  by professional, location and cancelled, pull to refresh, and a "now" line on
  today. Each appointment opens its detail: the customer with call, WhatsApp and
  e-mail shortcuts, the payment state, the booking code and source, the
  customer's private notes, and exactly the actions the web offers for its
  status: edit (another day, time, professional or service), charge or close
  the sale, record a deposit, mark a no-show or undo it, cancel.
- **New appointment**: find a customer or create one, the service, a
  professional or whoever is free, the day and one of the free times the server
  offers.
- **Customers**: the whole list with search, a detail with visits, upcoming
  and past appointments and notes, creating and editing.
- **Sales** (admins): the day, week or month's sales and deposits with totals
  and payment methods, a sale's detail and voiding it, and the checkout for an
  appointment or a walk-in sale: services and products, a discount code or gift
  card, a tip, the customer's deposits, and the payment split across methods.
- **Notifications**: a bell on the agenda with the open business's unread
  count, and its inbox: new bookings, and appointments moved or cancelled,
  newest first, with pull to refresh. Opening it marks them read; a row opens
  the appointment. Owners, admins and the professional booked are notified,
  never whoever made the change.
- **Push** (when the brand carries Firebase and the server sends push): the
  same notifications on the phone, even with the app closed. Tapping one opens
  its business and the appointment. "My account" switches them off on this
  phone.
- **More**: the booking link to share and its QR code, reports (summary, tips,
  commissions), a shortcut to the web panel, the account and signing out.

A professional sees their own agenda, books and moves their own appointments,
and shares the booking link, as the server allows. Charging, cancelling,
no-shows, customer records, sales and reports are for owners and admins.

Setting the business up (opening hours, services, staff, team access, the cash
register, inventory, gift cards and the rest of the settings) is done in the web
panel; "More" opens it in the browser.

## Behaviour worth knowing

- The server address is typed at sign-in, or derived from an account name when
  the brand carries a hosted preset. `https://` is assumed; plain HTTP is
  accepted only for local addresses (`localhost`, `127.x`, `10.x`,
  `192.168.x`, `172.16-31.x`, `.local`, and the emulator's `10.0.2.2`). Android
  cannot express IP ranges in `network_security_config.xml`, so it allows
  plain HTTP for `localhost`, `127.0.0.1`, `10.0.2.2` and `.local` names; a
  development server on another LAN address needs HTTPS or its address added
  there in a development copy.
- Every time and date is shown in the business's time zone, not the phone's,
  and money is formatted with the business's currency using the server's own
  table of decimals (COP has none, for example).
- A `401` from any call ends the session and returns to sign-in. Coming back
  to the app refreshes the shown week and the bell.
- Push is asked for once, from the agenda after it loads, never on the sign-in
  screens. Signing out takes this phone's token back from the server and
  removes the app's notifications from the phone; reading everything removes
  them too, and the launcher's dot with them.
- The app follows the phone's light or dark theme and its language (Spanish
  or English).
