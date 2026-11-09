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

Privacy policy and support links appear on the sign-in and account screens when
set, in the phone's language with English as the fallback:

```
BRAND_PRIVACY_POLICY_URL_EN = yourdomain.com/privacy
BRAND_PRIVACY_POLICY_URL_ES = yourdomain.com/es/privacidad
BRAND_SUPPORT_URL_EN = yourdomain.com/support
BRAND_SUPPORT_URL_ES = yourdomain.com/es/ayuda
```

## Assets

In `OpenTurnly/Assets.xcassets`, replace:

- `AppIcon.appiconset/AppIcon.png`: 1024 x 1024, no transparency.
- `BrandLogo.imageset`: the mark in colour, with a dark-appearance variant.
- `BrandLogoWhite.imageset`: the mark in white, shown on the brand colour at sign-in.
- `LaunchIcon.imageset` and the `LaunchBackground` colour: the launch screen.
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
