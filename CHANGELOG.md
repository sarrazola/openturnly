# Changelog

All notable changes to OpenTurnly are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org).

## [Unreleased]

## [1.5.0] - 2026-11-09

Whoever runs the install gets a log of every send of every business's flows, and a
deployment can hold back and count the messages that go out on the install's own accounts.
Migration 0037 runs on start and fills in the channel of past sends.

### Added

- **Sends**, in the account menu for whoever runs the install: every message and notice the
  flows of all its businesses sent, newest first, with the business, the flow and step, the
  customer and booking, how it ended (delivered, read, failed and why, waiting for a retry,
  skipped), what it cost, and, opened, what was sent. Filter by business, channel and status.
- **Preview as a phone** in Settings → Booking page, and the recommended size of the logo
  (400×400) and the cover (1200×400). Based on a proposal by José Senén Oviedo.
- A deployment can pass `createApp({ meter })` to hold back and count the messages sent on
  the install's own accounts (the WhatsApp number, email account and texts set in its
  environment). A business's own accounts never go through it. Each send records whether it
  went out on the business's account or the install's, and what it counted for.

## [1.4.2] - 2026-11-09

### Added

- **Add it to your calendar** on the booking confirmation: Google and Outlook open their new
  event page already filled in, and "Apple and others" opens the event from the server
  (`/api/public/businesses/<slug>/appointments/<code>/calendar.ics`), which Apple's calendar
  and the others offer to save as it is. The event carries the service, the business, the
  professional, the booking code, the business's phone and the address, never who booked
  it. Based on a proposal by José Senén Oviedo.

### Security

- Outgoing webhooks and a business's own email server: an IPv4 address written inside an
  IPv6 one (`::ffff:7f00:1`, the way URLs spell `::ffff:127.0.0.1`) is now recognized as the
  address it stands for, so it's refused like the IPv4 one when it's inside the server's
  network. Addresses typed by hand were already refused, so this hardens the check rather
  than closing a way in. Reported by José Senén Oviedo.

## [1.4.1] - 2026-11-09

### Added

- Surveys on WhatsApp: a ready-made `encuesta_cita` template carries the survey link, and
  **Surveys → Settings** starts the survey flow on WhatsApp, email or text message.

### Fixed

- Testing a survey flow with one of the business's appointments sent the message without its
  link. It now sends that appointment's own link, ready to answer.

## [1.4.0] - 2026-11-09

Workflows now talk to customers and the business gets paid up front: WhatsApp, email and
text messages from workflows, deposits and full payments online with Mercado Pago, and a
survey after each visit. The people who run a business get native iPhone and Android apps,
and anyone can reset a forgotten password. Existing installs update by pulling the new
image, which now lives at `ghcr.io/sarrazola/openturnly`; migrations 0030 to 0036 run on
start.

### Added

- **Phone apps** for iPhone and Android, native on each, for the people who run the business:
  the agenda with booking, moving and cancelling, deposits and checkout, customers, the day's
  sales and reports. Setting the business up stays in the web admin. A professional sees only
  their own appointments.
  They sign in to any install with its address, and get a notification when an appointment is
  booked, moved or cancelled. See [docs/mobile.md](docs/mobile.md) and
  [docs/push-notifications.md](docs/push-notifications.md).
- **Forgot your password?** on the sign-in pages: with an email account set up, anyone gets a
  link to set a new password, good once for an hour, and is signed out everywhere when they do.
  Without email, whoever runs the server sets a temporary password with
  `reset-password`. See [docs/self-hosting.md](docs/self-hosting.md#forgotten-passwords).
- WhatsApp messages in workflows: a new step sends the customer an approved template with the
  appointment's details, such as a reminder the day before. See [docs/whatsapp.md](docs/whatsapp.md).
- Email in workflows: a new step sends the customer a confirmation, a reminder, or a notice that
  their appointment moved or was cancelled, through any provider that takes SMTP. See
  [docs/email.md](docs/email.md).
- **Channels**, in the account menu: where whoever runs the install connects WhatsApp and email
  for all of its businesses.
- Text messages in workflows: a new step texts the customer a confirmation, a reminder, or a
  notice that their appointment moved or was cancelled, through Twilio. The workflow's history
  shows whether each one arrived and what it cost. See [docs/sms.md](docs/sms.md).
- **Surveys**: after an appointment, a workflow sends the customer a short link to rate it from
  1 to 5 stars and leave a comment. Each link belongs to one appointment, takes one answer and
  closes after 24 hours, 48 hours or a week. **Surveys** in the menu shows the average, how many
  answered and every comment, by period, professional and location. See
  [docs/surveys.md](docs/surveys.md).
- **Online payments** with Mercado Pago: each business connects its own account, and each
  service can ask for a deposit or the full price when booking. The money goes straight to the
  business, the time is held until it's paid, and a payment that arrives late either keeps the
  appointment or goes back to the customer. See [docs/payments.md](docs/payments.md).

### Changed

- The image is published as `ghcr.io/sarrazola/openturnly`, where the project lives now:
  change `image:` in your `docker-compose.yml`. The old name stays at 1.3.0.
- A business's link can't change once the business exists, because its booking page, QR codes,
  embeds and MCP clients all depend on it. A sample business can still change it.
- The ready-made WhatsApp templates carry the booking code, and their names are now in Spanish:
  `confirmacion_cita`, `recordatorio_cita`, `cita_reprogramada` and `cita_cancelada`.
- The sign-in, setup and invitation pages are new: the form on a floating card, and beside it a
  line and a live look at a day in the calendar, in your brand colour. The language switch moved
  to the corner.
- "My account" is now a page instead of a dialog, with your details, your password and your
  language and theme. The menu on your name is down to that and signing out.

### Fixed

- The sample business lost its no-show when the appointment fell on a short day.
- An appointment whose deposits already cover its price no longer reads "Not charged": it shows
  as paid, in the agenda and in the day's report, with only the sale left to close.

### Security

- Product import: reading a size ("400 ml") from a spreadsheet cell took quadratic time on
  a huge cell of digits, enough for a signed-in admin to stall the server for seconds. It
  now answers at once whatever the cell holds.
- Workflow data: field names can no longer reach an object's prototype. They are always
  OpenTurnly's own names, so this was not reachable; it closes a code scanning warning.

## [1.3.0] - 2026-11-03

Easier to try and easier to sell from: a new install can fill a sample business in one
click, and the booking page can live inside the business's own website. Existing installs
update by pulling the new image; migration 0029 runs on start and changes nothing for
existing businesses.

### Added

- **Sample business**: on a new install, "Try a sample" (or "Create sample business" on the
  New business page) creates a barbershop with services in categories, three professionals,
  three customers and a week of appointments, in the owner's language and with prices that
  fit their currency. A banner marks it as sample data on every page, with a button to
  delete it in one step.
- **Booking page on your own website**: Settings → Booking page → Share your page → "Add to
  your website" gives the code to show the booking page as a section of any site (it grows
  with each step, with no inner scrollbar, and leaves out the logo and cover the site
  already has), or a floating button in the corner that opens it on top, with your text
  and colour (full screen on phones). A plain iframe is offered for site builders that
  don't take scripts. Only booking pages opened with `?embed` can be shown inside other
  websites; the admin and sign-in pages still refuse to be framed. See
  [docs/embed.md](docs/embed.md).

### Changed

- **Account menu**: shorter. It no longer repeats the name and email its button already
  shows (it still does with the sidebar collapsed) nor the role; "See the tour again" is a
  compass icon next to "My account", with a tooltip; the version sits small next to "Sign
  out".
- New README screenshots of 1.3.0, taken from the sample business.

## [1.2.0] - 2026-11-02

A business can now run its front desk in OpenTurnly: several locations, charging and sales,
gift cards, inventory, the cash register and reports. Existing installs update by pulling the
new image; migrations run on start and every business gets one location with what it had.

### Added

- **Locations**: a business can work from several locations (Administration → Locations),
  each with its own address, phone, opening hours, closures, services and team. Someone
  can work at several locations, with their own hours at each, and is never booked at two
  at the same time. With more than one location the booking page starts with "Where would
  you like your appointment?", and a link with `?local=<id>` goes straight to one. The
  calendar filters by location and appointments can move between them. Existing businesses
  get one location with their current hours, services, team and appointments.
- **Sales and checkout**: charge an appointment from the calendar ("Charge") or make a sale
  at the counter, with extra services, a discount, tips for each professional and payment
  split across cash, card, transfer or other. Sales are numbered (#1, #2…), can be voided
  (they stay in the history) and are listed in **Sales** with filters by period and location
  and Excel/CSV export. Internal sales (for the team or the business's own use) are marked
  apart. OpenTurnly records how customers paid; it still doesn't take payments itself.
- **Deposits**: record money a customer leaves in advance, for an appointment or a later
  visit. The calendar shows it, checkout uses it, and it can be returned while unused.
- **Gift cards**: discount codes (a percentage or an amount off, with optional dates and a
  limit of uses) that customers type on the booking page ("Have a discount code?") or that
  are applied at the counter, and gift cards with a balance that the business sells (recorded
  as a sale) or gives away, then spends as a payment at checkout. Uses and balances follow
  the appointments and sales that used them: cancelling or voiding gives them back.
- **Inventory**: products with a size (an amount and a unit: ml, l, g, kg, oz or units),
  category and brand from the business's own lists, SKU, sale price and cost, VAT, a
  commission and whether they're sold or only used. Categories and brands are managed in
  place, with a search, how many products use each, renaming, and a confirmation before
  deleting. **Bulk upload** from Excel or CSV previews what will be created or updated, row
  by row, and reads the usual column names (including "Formato"). Stock is kept per
  location, with an alarm at the level you choose and a count in the sidebar. Stock in,
  counts, internal use and transfers between locations are recorded by hand; products sold
  at checkout leave stock and come back if the sale is voided. Every movement is listed with
  what it left, by period, location and type, with Excel/CSV export.
- **Cash register**: per location and period, what came in, what went out, the result and
  the cash that should be in the drawer; where the money came from (sales, deposits,
  internal sales, gift cards, other income, expenses) and how it was paid, with the lines
  behind each figure. "Record movement" explains each option: income, expense, transfer
  (cash taken out to the bank or another location) and balancing the drawer after counting
  it. Sales charged from the calendar land there on their own. The report downloads as
  Excel with a summary sheet and every line.
- **Reports**: a summary per period and location with bookings, occupancy, new customers
  and sales, each compared with the period before and explained behind an ⓘ; a chart of
  bookings or sales by day, week or month; where bookings came from; best sellers;
  appointments per professional; and today's appointments. **Tips** per professional with
  every tip and export, and **commissions**: each professional's percentage of the services
  and products they sold (after each sale's discount) plus their tips, with the total to pay.
- **No-shows**: an appointment that already started can be marked "No-show" from the
  calendar, which frees its time, and undone if the customer did come. It has its own
  filter in the list, shows greyed out on the calendar and in the customer's history, and
  workflows can start with it (`appointment.no_show`).
- **QR code** for the booking page with the business's logo in the middle (its plain
  background is removed), ready to download as an image with a line on top ("Book here")
  and the business name below, both editable, on the brand colour or white.
- **Service categories**: services can have a category; the booking page then groups them
  under headings with chips to show one group, a search once the menu is long, and a
  compact list.
- **Professional photos**: an optional photo per professional, shown when customers choose
  who to book with (their initials otherwise).
- **Customer details**: optional birth date, ID number and gender on each customer, and the
  booking form can ask for each one as required, optional or not at all. Customers can be
  found by their ID number.
- **Customer import and export**: bring customers from Excel or CSV, matching the file's
  columns and previewing what will happen, and export them as Excel or CSV.
- **Short links**: businesses and customers get short random ids in admin addresses
  (`/admin/<id>`); old links keep working.
- **Sidebar** grouped into "Sales" and "Administration".
- **AI agents (MCP)**: `location_id` for finding times, booking, moving appointments, hours
  and closures; `get_business_info` lists each location with its hours; the period summary
  counts no-shows apart from cancellations.
- **Workflows**: `appointment.location` and `appointment.location_address` data, and the
  "A customer doesn't come" trigger.

### Changed

- **Settings → Booking page** is shorter: the link and the QR code together, then the
  logo, cover, colour and background in one card, with the colour and background folded
  until you open them.
- On the booking page the customer chooses who first, then the day and its times side by
  side.
- Birth dates are chosen with day, month and year lists instead of the browser's calendar.

### Fixed

- The names of the page background styles were hard to read in dark mode.
- Collapsing the sidebar no longer shows two lines between groups.

## [1.1.0] - 2026-10-22

### Added

- **AI agents (MCP)**: administration keys can update the business's name, description,
  phone, email and address with the new `update_business_details` tool, so an assistant can
  set up a business's booking page end to end.

### Changed

- **Long service menus**: when a business offers more than six services, its booking page
  picks them from a searchable dropdown (with each one's length and price) and shows the
  chosen one in full, instead of a long wall of cards.
- **Staff services**: choosing which services a professional does is a checklist with a
  count, all/none shortcuts and, for long menus, a search, instead of a pile of buttons.

### Security

- **Development tools**: the old esbuild that drizzle-kit pulled in (a moderate advisory for
  its dev server) is replaced with a patched one. It was never part of the Docker image.

## [1.0.0] - 2026-10-22

The first public release, under the MIT license.

### Added

- **Install**: one `docker compose up -d` starts OpenTurnly and PostgreSQL 18, with images for
  amd64 and arm64. The first visitor creates the owner account; public sign-up is closed.
  Many businesses per install, each with its own booking page at `/<slug>`. Spanish and
  English.
- **Booking page**: one centered card with the business's cover, logo, name and contact
  details, then service, date and time, details and confirmation. Customers can pick who
  attends them or leave it to whoever is free, and get a short booking code (e.g.
  `K7Q4XM`). Their details are remembered on their own device for next time, never looked up
  on the server. Brand colour and five background styles, with a live preview.
- **Booking form**: phone and email can each be required, optional or not asked, plus up to
  10 extra questions (short text, long text or options). Phones are stored in E.164 with a
  country-code picker that starts on the business's country.
- **Calendar**: day, week, month and list views. Book appointments for customers, move them
  to another day, time, staff member or service among the times that are actually free,
  and cancel them. The list view covers any period with search, filters and totals, and
  exports to Excel or CSV.
- **Customers**: every customer with their bookings, visits, total spent and private notes.
  Customers are recognized by mobile number first, then email; bookings only fill in
  missing details. Duplicates are pointed out and can be merged, and the same customer
  can't hold two appointments at the same time.
- **Services, staff and hours**: services with duration and price; staff (or courts, rooms…)
  with the services they provide and optional own hours; weekly hours with split shifts on
  an hour timeline; closures over one or several days; public holidays detected from the
  business's time zone, each one open or closed; booking rules (time step, minimum notice,
  how far ahead) with a preview of the times customers will see. Availability is time-zone
  and DST aware, and double bookings are prevented.
- **Team access**: admins run their business, professionals see and book only their own
  calendar. Access is given from Settings → General → Users with a generated strong
  password, and each business has its own branded sign-in page at `/<slug>/login`.
- **Your account**: a menu at the bottom of the sidebar with your details (name, email, and
  password, which signs you out on your other devices), the language and the theme.
- **Light and dark themes** for the admin, or following the device; booking pages always keep
  the look each business chose. The interface starts in Spanish.
- **Made for phones too**: tables turn into cards, dialogs and menus fit the screen, tabs
  scroll sideways, and the agenda offers day, three-day and list views.
- **Guided tours**: the first time someone opens a business, a short tour shows what each
  section is for, and each section explains its main parts the first time it's opened.
  Professionals get a shorter one. Each shows once per person, and "See the tour again" in
  the account menu brings them back.
- **Settings** in four tabs (General, Booking page, Integrations, Workflows), each saved
  with one bar that shows up only when something changed. Deleting a business lists
  everything that goes with it and asks for its name, then once more, before deleting it.
- **AI agents (MCP)**: an MCP server per business at `/<slug>/mcp`, with access keys shown
  once and stored hashed. Booking agent keys answer questions, find free times and book,
  move or cancel with the booking code; administration keys also run the calendar,
  customers, catalog and hours. See [docs/mcp.md](docs/mcp.md).
- **Workflows**: send appointment data to any system that takes webhooks when an
  appointment is booked, moved, cancelled or over, or some time before it starts. Flows are
  built on a canvas with webhook and wait steps, can start from templates, and are tested
  with a real appointment or example data to see each system's answer. Each webhook picks
  its method, authentication (Basic, Header or JWT), extra headers and body:
  chosen fields or a JSON written in the receiver's own format, with the data picked by
  group (`{{customer.first_name}}`, `{{appointment.time}}`…). Deliveries are
  signed, sent only over HTTPS, retried and kept away from the server's own network. See
  [docs/workflows.md](docs/workflows.md).
- **Protection against fake bookings**, free and self-hosted: a proof-of-work puzzle
  (ALTCHA) solved in the background, limits per network, phone and email, and a surge brake
  per business that hardens the page and warns admins when bookings spike.
- **Account security**: changes made with the session cookie must come from the install's
  own pages, and "My account" waits 15 minutes after 5 wrong current passwords. See
  [SECURITY.md](SECURITY.md) for everything that's built in.

[Unreleased]: https://github.com/sarrazola/openturnly/compare/v1.5.0...HEAD
[1.5.0]: https://github.com/sarrazola/openturnly/compare/v1.4.2...v1.5.0
[1.4.2]: https://github.com/sarrazola/openturnly/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/sarrazola/openturnly/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/sarrazola/openturnly/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/sarrazola/openturnly/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/sarrazola/openturnly/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/sarrazola/openturnly/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/sarrazola/openturnly/releases/tag/v1.0.0
