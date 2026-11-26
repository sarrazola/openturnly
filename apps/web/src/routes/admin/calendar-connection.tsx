import { CalendarCheck, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { Checklist } from "../../components/checklist.tsx";
import { Modal, SettingsCard, Toggle } from "../../components/controls.tsx";
import { toast } from "../../components/toast.tsx";
import { Alert, Badge, Button } from "../../components/ui.tsx";
import type { CalendarOverview } from "../../lib/api.ts";
import { connectGoogleCalendar, useCalendar, useCalendarMutations } from "../../lib/calendar.ts";
import { errorMessage } from "../../lib/forms.ts";

type Place = CalendarOverview["businesses"][number];

/** What Google's consent screen sends people back with (`?calendar=`). */
const OUTCOMES = [
  "connected",
  "denied",
  "calendar_not_allowed",
  "expired",
  "signed_out",
  "failed",
] as const;

/**
 * "Google Calendar" on the account page: the person connects their own Google account and
 * their appointments show up there, in a calendar named after each business. Hidden on
 * installs without a Google client.
 */
export function CalendarConnectionCard() {
  const { t } = useTranslation();
  const calendar = useCalendar();
  const { setTarget, disconnect } = useCalendarMutations();
  const [params, setParams] = useSearchParams();
  const [leaving, setLeaving] = useState(false);

  // Coming back from Google: say how it went, once.
  const outcome = params.get("calendar");
  useEffect(() => {
    if (!outcome) return;
    if (outcome === "connected") toast.success(t("calendarSync.outcomes.connected"));
    else {
      const known = OUTCOMES.find((item) => item === outcome) ?? "failed";
      toast.error(t(`calendarSync.outcomes.${known}`));
    }
    setParams(
      (current) => {
        current.delete("calendar");
        return current;
      },
      { replace: true },
    );
  }, [outcome, setParams, t]);

  const data = calendar.data;
  if (!data?.available) return null;
  const { connection } = data;

  const save = (input: Parameters<typeof setTarget.mutate>[0]) =>
    setTarget.mutate(input, { onError: (error) => toast.error(errorMessage(t, error)) });

  return (
    <SettingsCard
      title={t("calendarSync.title")}
      description={t("calendarSync.description")}
      action={
        connection ? (
          <Badge tone={connection.status === "connected" ? "success" : "warning"}>
            {t(`calendarSync.status.${connection.status}`)}
          </Badge>
        ) : null
      }
      footerNote={
        connection?.email ? t("calendarSync.connectedAs", { email: connection.email }) : undefined
      }
      footer={
        connection ? (
          <Button variant="secondary" size="sm" onClick={() => setLeaving(true)}>
            {t("calendarSync.disconnect")}
          </Button>
        ) : undefined
      }
    >
      {!connection && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dashed border-zinc-200 p-4">
          <p className="max-w-md text-sm text-zinc-600">{t("calendarSync.pitch")}</p>
          <Button onClick={() => connectGoogleCalendar()}>
            <CalendarCheck className="size-4" aria-hidden />
            {t("calendarSync.connect")}
          </Button>
        </div>
      )}

      {connection?.status === "disconnected" && (
        <Alert>
          <span className="flex flex-wrap items-center justify-between gap-3">
            {t("calendarSync.reconnectHint")}
            <Button size="sm" onClick={() => connectGoogleCalendar()}>
              {t("calendarSync.reconnect")}
            </Button>
          </span>
        </Alert>
      )}

      {connection && data.businesses.length > 0 && (
        <ul className="divide-y divide-zinc-100 rounded-lg border border-zinc-200">
          {data.businesses.map((place) => (
            <BusinessRow key={place.id} place={place} onChange={save} busy={setTarget.isPending} />
          ))}
        </ul>
      )}

      <Modal
        open={leaving}
        onClose={() => setLeaving(false)}
        title={t("calendarSync.disconnectTitle")}
        description={t("calendarSync.disconnectHint")}
        footer={
          <>
            <Button
              variant="secondary"
              loading={disconnect.isPending && disconnect.variables?.keepCalendars === true}
              onClick={() =>
                disconnect.mutate(
                  { keepCalendars: true },
                  {
                    onSuccess: () => setLeaving(false),
                    onError: (error) => toast.error(errorMessage(t, error)),
                  },
                )
              }
            >
              {t("calendarSync.keepCalendars")}
            </Button>
            <Button
              variant="danger"
              loading={disconnect.isPending && disconnect.variables?.keepCalendars === false}
              onClick={() =>
                disconnect.mutate(
                  { keepCalendars: false },
                  {
                    onSuccess: () => setLeaving(false),
                    onError: (error) => toast.error(errorMessage(t, error)),
                  },
                )
              }
            >
              {t("calendarSync.deleteCalendars")}
            </Button>
          </>
        }
      >
        <p className="text-sm text-zinc-600">{t("calendarSync.disconnectBody")}</p>
      </Modal>
    </SettingsCard>
  );
}

/** One business: on or off and, for owners and admins, whose appointments go there. */
function BusinessRow({
  place,
  onChange,
  busy,
}: {
  place: Place;
  onChange: (input: {
    businessId: string;
    enabled: boolean;
    resourceIds?: string[] | null;
  }) => void;
  busy: boolean;
}) {
  const { t } = useTranslation();
  const chooses = place.role !== "professional" && place.professionals.length > 0;
  const [picking, setPicking] = useState(place.resourceIds !== null);
  const everyone = place.professionals.map((member) => member.id);

  return (
    <li className="space-y-3 px-4 py-3">
      <div className="flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-zinc-800">{place.name}</p>
          <p className="text-[13px] text-zinc-500">
            {place.role === "professional"
              ? t("calendarSync.ownAppointments")
              : !place.enabled
                ? t("calendarSync.off")
                : place.resourceIds === null
                  ? t("calendarSync.everyone")
                  : t("calendarSync.some", { count: place.resourceIds.length })}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {place.failing > 0 && (
            <span className="inline-flex items-center gap-1 text-[13px] text-amber-700">
              <TriangleAlert className="size-3.5" aria-hidden />
              {t("calendarSync.failing", { count: place.failing })}
            </span>
          )}
          <Toggle
            checked={place.enabled}
            disabled={busy}
            label={t("calendarSync.toggle", { name: place.name })}
            onChange={(enabled) => onChange({ businessId: place.id, enabled })}
          />
        </div>
      </div>

      {place.enabled && chooses && (
        <div className="space-y-2">
          <div className="flex flex-wrap gap-2 text-[13px]">
            <Button
              size="sm"
              variant={picking ? "ghost" : "secondary"}
              onClick={() => {
                setPicking(false);
                if (place.resourceIds !== null) {
                  onChange({ businessId: place.id, enabled: true, resourceIds: null });
                }
              }}
            >
              {t("calendarSync.everyoneOption")}
            </Button>
            <Button
              size="sm"
              variant={picking ? "secondary" : "ghost"}
              onClick={() => setPicking(true)}
            >
              {t("calendarSync.someOption")}
            </Button>
          </div>
          {picking && (
            <Checklist
              label={t("calendarSync.professionals")}
              items={place.professionals.map((member) => ({ id: member.id, label: member.name }))}
              value={place.resourceIds ?? everyone}
              onChange={(resourceIds) =>
                onChange({ businessId: place.id, enabled: true, resourceIds })
              }
            />
          )}
        </div>
      )}
    </li>
  );
}
