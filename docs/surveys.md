# Surveys

After an appointment, OpenTurnly can ask the customer one question: how they would rate their
visit, from 1 to 5 stars, and a comment if they want. The answers show up in **Surveys** in
the menu.

## Turning them on

**Surveys → Settings**:

- **Send surveys**: on or off. While it's off, a workflow's survey step is skipped.
- **The link works for**: 24 hours, 48 hours or 7 days, counted from when it's sent. After
  that the link says the survey is closed.

The same tab shows what the customer will see.

## Sending them

A survey goes out in a [workflow](workflows.md) that runs **when an appointment ends**, so
it waits for the visit to be over and never goes out for a cancelled appointment or a no-show.
**Create survey flow** in Settings makes one: it waits an hour after the appointment ends,
then emails the ready-made **Satisfaction survey** message. You can change the wait, or
send it another way:

- **Email** or **text message**: pick the **Satisfaction survey** message.
- **WhatsApp**: a template of your own with a variable for the link, filled with
  **Survey link** (`{{appointment.survey_link}}`).
- **Webhook**: add **Survey link** to the data you send, or put
  `{{appointment.survey_link}}` in your message or JSON.

The link only exists in workflows that run when an appointment ends; elsewhere it's empty.
A test run sends an example link that opens the "closed" page.

## The rules

- **One link per appointment**, made when the step is queued. Its hours start when the
  message goes out.
- **One answer per link.** Opening it again shows the thanks.
- **At most one survey every 30 days per customer**, however often they come. A step that
  would send another one is skipped, and the workflow's history says why.
- The page shows the business's name and logo, the service, the professional and the day,
  nothing else, in the business's language and colour. Search engines are asked not to index
  it.

## Reading the answers

**Surveys → Responses**, for a period of appointments, one professional or all, one location
or all:

- **Average rating**, out of 5.
- **Responses**: how many answered, and what share of the surveys sent that is.
- **By stars**: how many gave each number of stars.
- Every answer, newest first, with its comment, the customer, the service, the professional
  and the booking code.

## API

| Method | Path | |
| --- | --- | --- |
| `GET` | `/api/businesses/:id/surveys/settings` | `{ enabled, validHours }` |
| `PATCH` | `/api/businesses/:id/surveys/settings` | Either or both; `validHours` is 24, 48 or 168 |
| `GET` | `/api/businesses/:id/surveys?from=&to=&resourceId=&locationId=` | `{ summary, responses }` |
| `GET` | `/api/public/surveys/:code` | What the page shows; 404 for an unknown or unsent link |
| `POST` | `/api/public/surveys/:code` | `{ rating, comment? }`; 409 `survey_answered`, 410 `survey_closed` |

The admin routes are for the business's admins (and admin API keys), not its professionals.
