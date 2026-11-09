# Phone apps

OpenTurnly has two native apps for the people who run a business: the owner, the admins and
the staff. One is for iPhone (SwiftUI), the other for Android (Kotlin and Jetpack Compose).
They cover the day at the business: the agenda, booking and moving appointments, charging,
customers and the day's sales. Setting the business up stays in the web admin, which the apps open
from "More". Customers keep booking from the booking page; the apps are not for them.

Both apps talk to your own server with the same API the web admin uses. They hold no data of
their own and send nothing anywhere else: no analytics, no trackers.

## What each person sees

| Tab | Admins | Professionals |
| --- | --- | --- |
| Agenda | Every professional's appointments; the filter button narrows them to one professional or location. Book, move, cancel, mark no-shows, take deposits and charge. | Their own appointments. They book with themselves and move their own. |
| Customers | Search, history, create and edit. | Only when booking. |
| Sales | The day's sales and deposits, totals by payment method, void, counter sales. | — |
| More | The booking link and its QR code, reports, a shortcut to the web admin and their account. | The booking link and their account. |

The server decides what each role may do. The apps only hide what the server would refuse.

Everything else is done in the web admin: opening hours, closures and holidays, services, staff
and team access, the cash register, inventory, gift cards, the business's details and booking
rules, and deleting or merging customers.

## Signing in

The first screen asks for the server's address, such as `bookings.example.com`. The app adds
`https://` and checks `/api/health`. Then it asks for the email and password of an account on
that server. Plain HTTP is only accepted for local addresses, for trying the apps against a
development server.

The apps sign in with `POST /api/mobile/sign-in` and keep the session token in the phone's
secure storage: the Keychain on iPhone, an encrypted file with a hardware-backed key on Android.
Every request sends it as `Authorization: Bearer <token>`. Signing out calls
`POST /api/mobile/sign-out`, which ends the session on the server too. If the server ever
answers `401`, the app goes back to the sign-in screen and says why.

The server needs nothing new for this: the routes are part of every install.

## Building them

Each app has its own README with the details:

- iPhone: [apps/ios/README.md](../apps/ios/README.md). Needs Xcode 16 and
  [XcodeGen](https://github.com/yonaskolb/XcodeGen).
- Android: [apps/android/README.md](../apps/android/README.md). Needs Android Studio with the
  Android 16 SDK (API 36).

To try them against a development server on your computer, start it with `npm run dev` and type
its API's address on the first screen: `localhost:4100` from the iOS Simulator, `10.0.2.2:4100`
from the Android emulator.

## Publishing your own

The source carries a neutral example identity. To publish the apps under your own name, replace
the identity files, the icons and the signing. Nothing in the code needs to change. Each app's
`WHITELABEL.md` lists every value:

- iPhone: [apps/ios/WHITELABEL.md](../apps/ios/WHITELABEL.md), with `Brand/Brand.xcconfig`.
- Android: [apps/android/WHITELABEL.md](../apps/android/WHITELABEL.md), with
  `brand/brand.properties` and `brand/res`.

There, a build can also come with a server already filled in, so people only type their email
and password.
