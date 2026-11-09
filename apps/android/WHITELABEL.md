# Publishing a branded Android build

The app compiles with whatever identity `brand/brand.properties` carries and
whatever `brand/res` holds. A publisher supplies that directory and signing.
Nothing in the source points at a hosted service.

## Identity

Replace every value in `brand/brand.properties`:

```
BRAND_PRODUCT_NAME=Your Salon App
BRAND_APPLICATION_ID=com.yourcompany.salon
BRAND_URL_SCHEME=yoursalon
MARKETING_VERSION=1.0.0
CURRENT_PROJECT_VERSION=1
BRAND_PRIMARY_COLOR=0f766e
BRAND_DEFAULT_SERVER=turnos.yourcompany.com
```

Addresses are written without a scheme; the app prepends `https://` to every
one of them. The colour is six hex digits with no `#`; the app builds its
light and dark colour schemes from it (no dynamic colour from the wallpaper).

A publisher that runs a hosted service may add a preset, so people type their
account name instead of an address, and a directory so they sign in with an
e-mail alone:

```
BRAND_HOSTED_LABEL=Your Service
BRAND_HOSTED_SERVER_TEMPLATE={workspace}.yourdomain.com
BRAND_HOSTED_SIGN_IN=yourdomain.com/api/directory/sign-in
BRAND_PRIVACY_POLICY_URL_EN=yourdomain.com/privacy
BRAND_PRIVACY_POLICY_URL_ES=yourdomain.com/es/privacidad
BRAND_SUPPORT_URL_EN=yourdomain.com/support
BRAND_SUPPORT_URL_ES=yourdomain.com/es/ayuda
```

The directory receives `{email, password}` and answers
`{accounts: [{server, name, token, user}]}`; with several accounts the app
offers a picker. "Use another address" stays available for custom domains.

## Push notifications

The app always shows the server's notifications in its inbox (the bell on the
agenda). To also ring the phone, through Firebase Cloud Messaging, add the
values of your own Android app in a Firebase project. They are the ones its
`google-services.json` lists:

```
BRAND_FIREBASE_PROJECT_ID=your-project          # project_info.project_id
BRAND_FIREBASE_APPLICATION_ID=1:1234:android:ab # client_info.mobilesdk_app_id
BRAND_FIREBASE_API_KEY=AIza...                  # api_key.current_key
BRAND_FIREBASE_SENDER_ID=1234                   # project_info.project_number
```

The app starts Firebase itself from these values: there is no Google services
Gradle plugin and no `google-services.json` in the build. With any of the four
empty or missing (an older brand file), the build has no push at all: Firebase
never starts, its automatic start-up is removed from the manifest, and the app
never asks for notification permission.

With them, and with a server whose `GET /api/mobile/settings` answers
`{"push": {"enabled": true}}`, the app asks for permission once from the
agenda, registers the phone's token with `POST /api/mobile/devices` and takes
it back on sign-out or when the person switches notifications off under
"My account". The server must be set up to send through the same Firebase
project; see the server's documentation.

Messages arrive as data: `title`, `body`, `badge` (unread across every
business), `kind`, `notificationId`, `businessId`, `appointmentId` and
`startsAt`, and a platform `notification` block's title and body are used when
present. All of that is read in one file,
`app/src/main/kotlin/org/openturnly/mobile/rules/NotificationData.kt`. If your
delivery wraps the payload differently, replace that file in your copy, keeping
its class and `parse` function; nothing else in the app reads a message's keys.

The status bar icon is `brand/res/drawable/ic_notification.xml`: one colour on
transparency. Replace it with your own mark under the same name.

## Assets

Replace the files under `brand/res`: the adaptive launcher icon
(`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, with the
`drawable-nodpi/ic_launcher_foreground.png` and `ic_launcher_monochrome.png`
layers and the `launcher_background` colour in `values/brand_colors.xml`) and
`drawable-nodpi/brand_logo.png`, shown on the sign-in and loading screens.
Foreground and monochrome layers are 432×432 pixels with the mark inside the
central 66% safe zone. Bitmaps may also go in density folders; the names must
stay the same.

## Keeping the source untouched

Copy `apps/android` somewhere, replace the `brand` directory, then build there.
Nothing in `app/` needs editing, which keeps your copy trivially upgradable
when the source moves.

## Signing and release

Store builds are App Bundles signed with your upload key:

```bash
./gradlew :app:bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

Provide the upload keystore outside the repository, as Gradle properties
(`uploadKey.STORE_FILE`, `uploadKey.STORE_PASSWORD`, `uploadKey.KEY_ALIAS`,
`uploadKey.KEY_PASSWORD` in `~/.gradle/gradle.properties`) or as the
environment variables `UPLOAD_KEY_STORE_FILE`, `UPLOAD_KEY_STORE_PASSWORD`,
`UPLOAD_KEY_KEY_ALIAS` and `UPLOAD_KEY_KEY_PASSWORD`. With them absent the
release bundle is built unsigned. Never put the keystore in the source tree.
