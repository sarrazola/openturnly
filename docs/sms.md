# Text messages

A workflow can text the appointment's customer: a confirmation when they book, a reminder the
day before. Texts go from your server to [Twilio](https://www.twilio.com), which delivers them
to the customer's phone company.

## Who sets it up

Text messages belong to whoever runs the install, not to one business: they're set in
**Channels**, in the account menu, and every business sends through them. There are two ways:

- **An account in the panel.** In **Channels → SMS**, paste your Twilio account's details.
  Once Twilio accepts them, texts go out from that account, and you can reword each message in
  the workflow.
- **An account in the environment.** Set it in `.env` and it's used from the first day. It only
  sends the ready-made texts, as with email. An account connected in the panel takes over from
  it, and removing that one goes back.

## What you need from Twilio

1. **The Account SID**, which starts with `AC`, from **Account → API keys & tokens**.
2. **An API key**, from the same page: **Create API key**, of the _Standard_ kind. Copy its
   SID, which starts with `SK`, and its secret, which Twilio only shows once. A key of its own
   can be revoked without touching anything else on the account.
3. **Where texts go out from**: one of the account's numbers that can send SMS, with its
   country code (`+15551234567`), or a messaging service (`MG…`).
4. **The countries you send to**, turned on in **Messaging → Settings → Geo permissions**.
   Turn on only those where your customers are: the booking page is public, and every country
   left on is one somebody could make you pay to text.

Each country has its own rules. Some change the sender for a short code of their own; the
United States asks for the number to be registered before it carries texts to its phones.
Twilio's guidelines for each country say which.

The key's secret is stored encrypted with the install's `SECRET` and never comes back to the
browser. If you change `SECRET` it can't be read anymore and has to be pasted again.

## The messages

**Send SMS** in a workflow has four ready-made texts: a confirmation, a reminder, a
rescheduling notice and a cancellation notice, in Spanish and English, each with the booking
code. They use the same placeholders as webhooks (`{{customer.first_name}}`).

A text is charged by the part: 160 plain characters, or 70 when any character needs the wider
alphabet, such as an emoji. Before a text goes out, letters it can't carry plainly are swapped
for close ones (`ó` becomes `o`), so one accent doesn't double what it costs. The editor counts
the parts as you write.

## What each one did and cost

Every text carries an address where Twilio reports on it, and the workflow's history shows
whether it arrived. When Twilio reports, OpenTurnly asks it about the message rather than
trusting the report, and saves what Twilio charged for it, in the account's currency.

The report needs the install to be reachable from the internet at `APP_URL`. Otherwise, and
when the price comes after the last report, the server asks Twilio itself every few minutes,
for a day after the text went out.

## Testing

A test in a workflow only goes to a real appointment you choose, never to the example data: the
text is real, and that example number could be somebody's. To try the account without an
appointment, use **Test the SMS** in **Channels → SMS**.

## How sending behaves

Texts go through the same queue as webhooks, with the same history. Passing failures are
retried after 1, 5 and 30 minutes. A refusal from Twilio, such as a number that isn't a phone
or a country that isn't turned on, fails at once with the reason. A customer without a phone,
or an install without a connected account, shows as failed too.

## Settings

Set these in `.env` for an account in the environment:

| Variable                | What it is                                                                         |
| ----------------------- | ---------------------------------------------------------------------------------- |
| `TWILIO_ACCOUNT_SID`    | The account, `AC…`. With the next three, it turns this account on                  |
| `TWILIO_API_KEY_SID`    | The API key, `SK…`                                                                 |
| `TWILIO_API_KEY_SECRET` | The API key's secret                                                               |
| `TWILIO_FROM_NUMBER`    | The number texts go out from, with its country code, or a messaging service, `MG…` |
