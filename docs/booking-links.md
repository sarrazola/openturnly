# Booking links and booking rules

## Booking links

A business has one booking page, `/<business>`. **Administration → Booking links** adds more
ways into the same agenda, each at `/<business>/<link>`, for example `/studio/clients` and
`/studio/new-inquiries`. Each link has:

- a **name**, shown on the page and in the agenda next to every appointment booked through it;
- a **description**, shown above its services;
- the **services** it offers, in the order chosen.

Links hold no availability of their own. Times come from the professionals' hours and their
appointments, whichever page they were booked from, so a booking through any link takes that
time everywhere. There's nothing to sync and no second calendar.

A service can be left off the main page (**On your booking page**, in the service) and offered
only through the links that include it. A link that's turned off or deleted stops working;
appointments booked through it keep its name.

Where the link's name shows up:

- the agenda (on the appointment and in its details) and the phone apps;
- Google Calendar, at the start of the event's title ("New inquiries: Ana Pérez · First call"),
  see [google-calendar.md](google-calendar.md);
- messages and workflows, as `{{appointment.booking_link}}`;
- AI agents, as `booking_link` on the appointment.

To show a link inside a website, use its path with the embed script:
`<div data-openturnly="studio/new-inquiries"></div>` (see [embed.md](embed.md)).

## Margins before and after

Each service can keep the professional free for some minutes before and after it, to get ready
or take notes. Nobody can book the professional during those minutes, but the appointment
itself keeps its real times: on the booking page, in messages and in Google Calendar.

The margins are copied onto the appointment when it's booked, like its price: changing the
service later doesn't move or free anything already booked. They only clash with other
appointments, not with opening hours, so a service can still start at opening time.

## Most appointments a day

**Hours → Online booking → Most appointments a day** caps how many appointments each
professional takes a day through the booking page (and AI agents). Each professional can have
their own in their profile. A location without professionals uses the business's. The team can
still book over it from the agenda, as with the minimum notice.

## Show fewer times

**Hours → Online booking → Show fewer times** hides a share of the free times on the booking
page (up to 90 %), so the calendar doesn't look empty. The same times are hidden for a date on
every load, a day with free times always keeps at least one, and hidden times can't be booked
through the API either. AI agents that book for customers see the same times as the page; the
team sees everything in the agenda.
