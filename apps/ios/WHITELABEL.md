# Publishing a branded iOS build

The app compiles with whatever identity `Brand/Brand.xcconfig` carries. A
publisher supplies that file, the icon set and signing. Nothing in the source
points at a hosted service.

## Identity

Replace every value in `Brand/Brand.xcconfig`:

```
BRAND_PRODUCT_NAME = Your Bookings
BRAND_BUNDLE_IDENTIFIER = com.yourcompany.bookings
BRAND_URL_SCHEME = yourbookings
MARKETING_VERSION = 1.0.0
CURRENT_PROJECT_VERSION = 1
DEVELOPMENT_TEAM = ABCDE12345
BRAND_PRIMARY_COLOR = 0f766e
BRAND_DEFAULT_SERVER = bookings.yourcompany.com
```

Addresses are written without a scheme because `//` starts a comment in an
xcconfig file. The app prepends `https://` to every one of them. The colour is
six hex digits with no `#`; it paints the sign-in screen. The rest of the app
uses the `AccentColor` colour set, so replace that too (a light and a dark
variant).

A publisher that runs a hosted service may add a preset. The sign-in screen then
starts with it, and an account name is enough:

```
BRAND_HOSTED_LABEL = Your Service
BRAND_HOSTED_SERVER_TEMPLATE = {workspace}.yourdomain.com
```

A hosted service may also run a directory so its people sign in with an e-mail
alone. The app POSTs `{"email", "password"}` to it and expects
`{"accounts": [{"server": "https://...", "name": "...", "token": "...", "user": {...}}]}`:
one account signs in straight away, several let the person choose. Each
`token` and `user` is what that server's `/api/mobile/sign-in` would answer.
"Use another address" always stays available for servers people run themselves.

```
BRAND_HOSTED_SIGN_IN = yourdomain.com/api/directory/sign-in
```

Privacy policy and support links appear on the sign-in screens and at the
bottom of "More" when set, in the phone's language with English as the fallback:

```
BRAND_PRIVACY_POLICY_URL_EN = yourdomain.com/privacy
BRAND_PRIVACY_POLICY_URL_ES = yourdomain.com/es/privacidad
BRAND_SUPPORT_URL_EN = yourdomain.com/support
BRAND_SUPPORT_URL_ES = yourdomain.com/es/ayuda
```

"Forgot your password?" on the sign-in screen opens the server's own
`/forgot-password` page in an in-app browser whenever the app knows the server:
an address typed by the person, or the server an account name resolves to. A
build that signs in through `BRAND_HOSTED_SIGN_IN` does not know the server yet,
so it opens this page instead:

```
BRAND_PASSWORD_RECOVERY_URL = yourdomain.com/forgot-password
```

Without either, the button explains that the business's administrator sets
passwords from the web panel.

## Loading mark

While the app restores the session or opens a business, and in the sign-in
header while signing in, it shows the brand mark on a tile of the brand colour
with the app's name under it. The example brand's logo is a clock, and the app
draws that clock itself and turns its hands:

```
BRAND_ANIMATED_MARK = YES
```

A brand with a logo of its own sets `NO` (or leaves the key out): the loader
then shows `BrandLogoWhite` still, with a gentle pulse. With Reduce Motion on,
the loader holds still either way.

## Push notifications

Off by default. A build without them never asks for notification permission
and never registers the phone; people still see their notifications in the
app's bell. To turn them on:

```
BRAND_APS_ENVIRONMENT = development
```

`development` or `production` is the value of the `aps-environment`
entitlement the build is signed with (`OpenTurnly/OpenTurnly.entitlements`,
attached by `project.yml` only when the value is set). Your App ID needs the
Push Notifications capability, and the server the app talks to has to be set
up to deliver: the app asks `GET /api/mobile/settings` and stays quiet when the
server says it sends nothing.

The app registers the phone's native APNs token with
`POST /api/mobile/devices`, together with the gateway it belongs to:
`sandbox` for a build signed for development, `production` otherwise. The
provisioning profile inside the app decides when there is one, so an archive
exported for the App Store or TestFlight registers as `production` even when
the brand file says `development`; a simulator goes by the brand file.

A push is expected to carry `aps.alert`, `aps.badge` (the person's unread
notifications in every business) and, at the top level, `kind`,
`notificationId`, `businessId`, `appointmentId` and `startsAt`. If your
delivery service wraps those keys in its own envelope, replace
`OpenTurnly/Sources/Rules/NotificationData.swift`, the only file that reads
them.

## Assets

In `OpenTurnly/Assets.xcassets`, replace:

- `AppIcon.appiconset/AppIcon.png`: 1024 x 1024, no transparency.
- `BrandLogo.imageset`: the mark in colour, with a dark-appearance variant.
- `BrandLogoWhite.imageset`: the mark in white, shown on the brand colour tile
  (sign-in header and loader) when `BRAND_ANIMATED_MARK` is `NO`.
- `LaunchIcon.imageset` and the `LaunchBackground` colour: the launch screen.
  The image is the loader's tile as the app draws it: 88 pt with 18 pt continuous
  corners and its shadow, centred on a 136 pt transparent square (136, 272 and
  408 pixels). The colour is the app's background (`#f4f5f5`, dark `#0a0a0b`),
  so the launch screen hands over to the loader without a jump.
- `AccentColor.colorset`: the brand colour for light and dark.

## Keeping the source untouched

The intended way to brand is to copy `apps/ios` somewhere, overwrite the brand
file and the assets, then `xcodegen generate` there. Nothing in `project.yml` or
`OpenTurnly/Sources` needs editing, which keeps your copy trivially upgradable
when the source moves.

## Signing and release

Automatic signing with the team in `DEVELOPMENT_TEAM`. Archive with Xcode or:

```bash
xcodebuild -project OpenTurnly.xcodeproj -scheme OpenTurnly -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/OpenTurnly.xcarchive archive
```

Test against the server version that will serve the app before submitting.
