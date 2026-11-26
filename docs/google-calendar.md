# Google Calendar and Google Meet

Each person on the team can connect their own Google account. Their appointments then show up
in their Google Calendar, in a calendar named after each business, and online services get a
Google Meet video call when they're booked.

It's one way: OpenTurnly writes to Google and never reads anything back. Changes made to the
events in Google aren't brought back to OpenTurnly.

## What people see

- **My account → Google Calendar**: **Connect Google Calendar** opens Google's consent screen.
  Once connected, each business the person works in has a switch:
  - a **professional** gets their own appointments;
  - an **owner or admin** gets the whole team's appointments, or only the professionals they
    choose.
- In Google, OpenTurnly creates one calendar per business, named after the business (and renamed
  with it), in the business's time zone. It can be hidden or recoloured like any other calendar.
- Each event is titled with the customer's name and the service. Its description has the
  customer's phone and email, the service, the professional, where it is, the price, the booking
  code, the answers to the booking form's questions and a link to the agenda.
- New, moved and cancelled appointments are written while the change is made. When Google
  doesn't answer, the event is tried again a minute later, then with growing waits.
- **Disconnect** asks whether to delete the business calendars from Google or keep them, and
  revokes OpenTurnly's access.

## Online services

In **Services**, **Where it happens** can be **Online (Google Meet)** once Google Calendar is
set up on the install. When an online service is booked:

1. The professional's own calendar creates the event with a Google Meet call and invites the
   customer (when they left an email), so they join without asking to be let in. Google emails
   them the invitation, and again when it moves or is cancelled.
2. The call's link is kept on the appointment: the booking page shows it with **Join the video
   call**, and so do the agenda and the phone apps.
3. Messages use it too. `{{appointment.meeting_link}}` is the link, and
   `{{appointment.location_address}}` is the link instead of an address, so the ready-made
   WhatsApp, email and text templates say where to join without changes.

The booking page only offers an online service with professionals whose calendar is connected,
since someone has to host the call. A business without professionals needs an owner or admin
connected. Appointments the team books from the agenda are always allowed; without a connected
calendar they have no call, and the agenda says so.

The customer sees the description of the event they're invited to, so the link to the agenda is
left out of it.

## Set it up on your install

You need a Google Cloud project with an OAuth client. It's free.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project (or use
   one) and enable the **Google Calendar API**.
2. Open **Google Auth Platform**:
   - **Branding**: your app's name, a support email, and your domain as an authorized domain.
   - **Audience**: **External** (or **Internal** if everyone uses your Google Workspace).
   - **Data Access**: add `openid`, `.../auth/userinfo.email` and
     `https://www.googleapis.com/auth/calendar.app.created`. All three are non-sensitive:
     OpenTurnly only reaches the calendars it creates, never the person's own events.
   - **Clients → Create client → Web application**, with this authorized redirect URI:
     `https://<your install>/api/integrations/google/callback`.
3. Set the client's id and secret, then restart:

   ```bash
   GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=GOCSPX-...
   ```

Google shows the secret only when the client is created. Without both variables the feature is
hidden.

While the app is in **Testing**, only the test users listed under **Audience** can connect, and
Google asks them to connect again every 7 days. Publish it to stop that. With only these scopes,
Google reviews just the app's name and logo, if you set one.

### Several addresses

Google sends people back to one exact address. If your install answers on several hostnames,
register one of them as the redirect URI and set it in `GOOGLE_REDIRECT_URL`. Every consent
carries the address it started from in its `state` (signed with `SECRET`); read it there with
`stateOrigin` from `apps/server/src/lib/google-calendar.ts` and redirect to the same path on that
address, with the same query string. The install checks everything again when the request
arrives.

## For developers

- `src/lib/google-calendar.ts`: the consent round trip, tokens and the Calendar API calls,
  over `fetch`. `GOOGLE_OAUTH_URL` and `GOOGLE_API_URL` point it elsewhere (tests use a fake).
- `src/lib/calendar-sync.ts`: who gets which appointment, the business calendars, writing and
  retrying the copies. Retries run with the automations' `sweep()`, so anything that drives the
  automations from a scheduler drives them too.
- `src/routes/calendar.ts`: `/api/integrations/google/start` and `/callback`, and
  `GET`/`PATCH`/`DELETE /api/me/calendar`.
- Tokens are encrypted with `SECRET` (`src/lib/secrets.ts`) and never leave the server.
