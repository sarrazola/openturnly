# Push notifications

OpenTurnly ships the notification plumbing and no notification provider. A default install sends
nothing, needs no account anywhere and costs nothing: `PUSH_PROVIDER` is `none`.

## What gets notified

When an appointment is booked, moved or cancelled, from the booking page, the web admin, a phone
app or an AI agent, the people who should know get a notification:

- the instance's owners and the business's admins, for every appointment;
- the professional it's with, for their own; when it moves to someone else, the one it was with
  hears too.

The person who made the change doesn't get one. Each notification is kept on the server, so the phone
apps list them, count the unread ones on a bell in the agenda and on the app's icon, and mark them
read when they're opened. This works with or without push.

## The constraint worth reading first

On iOS and Android, push credentials belong to the app, not to the server. Apple issues an APNs
key to the developer account that publishes the app; Google issues a Firebase Cloud Messaging
sender to the package name. A server can only notify an app built against the credentials its
provider sends with.

So push comes with building your own app: under your own bundle identifier and package name, with
your own APNs key and Firebase project (see each app's `WHITELABEL.md`). Then you pick the
provider and hold the keys.

## How it works

1. **Devices.** After signing in, an app whose build has push asks the server whether it sends
   any (`GET /api/mobile/settings`). If so, and the person allows notifications, it registers the
   token Apple or Google gave it: `POST /api/mobile/devices` with `token`, `platform` (`ios` or
   `android`) and, on iOS, `environment` (`sandbox` for development builds). The device is tied
   to that session: signing out, or the session ending, stops it. Switching notifications off in
   the app removes it (`DELETE /api/mobile/devices`).
2. **Notifications.** `GET /api/me/notifications?businessId=…` lists a person's latest ones with
   the unread count for that business and for all of them; `POST /api/me/notifications/read`
   marks some, one business's or all of them read.
3. **A provider** delivers each notification to the person's devices.

## Providers

### `none` (default)

Sends nothing. The apps still show their notifications and counts.

### `webhook`

Every notification is POSTed as JSON to a URL you control, and you deliver it from there: through
APNs and FCM directly, or any service you already use.

```bash
PUSH_PROVIDER=webhook
PUSH_WEBHOOK_URL=https://example.com/openturnly-push
PUSH_WEBHOOK_SECRET=a-shared-secret   # optional; sent as a Bearer token
```

The body:

```json
{
  "userId": "…",
  "title": "Nueva reserva",
  "body": "Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan",
  "badge": 3,
  "data": {
    "kind": "appointment_booked",
    "notificationId": "…",
    "businessId": "…",
    "appointmentId": "…",
    "startsAt": "2026-11-14T14:00:00.000Z"
  },
  "devices": [{ "token": "…", "platform": "ios", "environment": "production" }]
}
```

`kind` is `appointment_booked`, `appointment_rescheduled` or `appointment_cancelled`.
`userId` is the person it's for, as this install knows them. `badge` is their unread count, for
the number on the app's icon. Answer `200`; to have the
server forget tokens that will never work again, answer `{ "invalidTokens": ["…"] }`.

What the apps expect to receive:

- **iOS**: an APNs alert with `title` and `body`, `badge`, and the keys of `data` at the top level
  of the payload.
- **Android**: an FCM data message with `title`, `body`, `badge` (as a string) and the keys of
  `data`.

Each app reads them in one file (`Rules/NotificationData` in both), so a build that receives a
different envelope only changes that file.

### Your own provider

A deployment that runs the server from code can pass `createApp` a `push` provider: an object with
a `send(message)` method taking the same message as the webhook's body.
