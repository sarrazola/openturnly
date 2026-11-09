import { SURVEY_VALID_HOURS, todayIn } from "@openturnly/core";
import { MessageSquareText, Star } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  EmptyState,
  PageHeader,
  PageTabs,
  SettingsCard,
  Switch,
} from "../../components/controls.tsx";
import {
  DateRangePicker,
  type Period,
  periodPresets,
} from "../../components/date-range-picker.tsx";
import { SurveyCard, starKey } from "../../components/survey-card.tsx";
import { toast } from "../../components/toast.tsx";
import { Alert, Button, buttonClass, Card, Field, Select, Spinner } from "../../components/ui.tsx";
import { useLocations, useProfessionals } from "../../lib/catalog.ts";
import { cn } from "../../lib/cn.ts";
import { errorMessage } from "../../lib/forms.ts";
import {
  type SurveyResponse,
  useSaveSurveySettings,
  useSurveyResults,
  useSurveySettings,
} from "../../lib/surveys.ts";
import { useTab } from "../../lib/tabs.ts";
import { useBusinessContext } from "./business.tsx";

const TABS = ["responses", "settings"] as const;

/** What customers say after their appointment: the answers, and turning the survey on. */
export function SurveysPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useTab(TABS);
  return (
    <>
      <PageHeader title={t("surveys.title")} subtitle={t("surveys.subtitle")} />
      <PageTabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: "responses", label: t("surveys.tabs.responses") },
          { value: "settings", label: t("surveys.tabs.settings") },
        ]}
      />
      {tab === "responses" && <Responses onSetUp={() => setTab("settings")} />}
      {tab === "settings" && <Settings />}
    </>
  );
}

function Stars({ value, className }: { value: number; className?: string }) {
  return (
    <span className={cn("inline-flex gap-0.5", className)}>
      {[1, 2, 3, 4, 5].map((star) => (
        <Star
          key={star}
          aria-hidden
          className={cn(
            "size-4",
            star <= Math.round(value) ? "fill-amber-400 text-amber-400" : "text-zinc-300",
          )}
        />
      ))}
    </span>
  );
}

function Responses({ onSetUp }: { onSetUp: () => void }) {
  const { t, i18n } = useTranslation();
  const language = i18n.resolvedLanguage ?? "es";
  const { business } = useBusinessContext();
  const settings = useSurveySettings(business.id);
  const [period, setPeriod] = useState<Period>(
    () => periodPresets(todayIn(business.timezone)).month,
  );
  const professionals = (useProfessionals(business.id).data ?? []).filter((item) => item.active);
  const locations = useLocations(business.id).data ?? [];
  const [resourceId, setResourceId] = useState("");
  const [locationId, setLocationId] = useState("");
  const results = useSurveyResults(business.id, {
    from: period.start,
    to: period.end,
    resourceId,
    locationId,
  });

  if (settings.isPending) return <Spinner />;
  if (settings.data && !settings.data.enabled && results.data?.summary.sent === 0) {
    return (
      <EmptyState
        icon={<Star className="size-5" />}
        title={t("surveys.offTitle")}
        body={t("surveys.offBody")}
        action={<Button onClick={onSetUp}>{t("surveys.setUp")}</Button>}
      />
    );
  }

  const summary = results.data?.summary;
  const percent = (part: number, whole: number) => (whole ? Math.round((part / whole) * 100) : 0);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <DateRangePicker
          value={period}
          onChange={setPeriod}
          today={todayIn(business.timezone)}
          locale={language}
          labels={{
            today: t("agenda.periods.today"),
            week: t("agenda.periods.week"),
            month: t("agenda.periods.month"),
            lastMonth: t("agenda.periods.lastMonth"),
            next30: t("agenda.periods.next30"),
            last30: t("agenda.periods.last30"),
            year: t("agenda.periods.year"),
            custom: t("agenda.periods.custom"),
            previous: t("agenda.previous"),
            next: t("agenda.next"),
          }}
        />
        {professionals.length > 1 && (
          <Select
            aria-label={t("surveys.professional")}
            value={resourceId}
            onChange={(event) => setResourceId(event.target.value)}
            className="w-52 [&_select]:h-8 [&_select]:text-[13px]"
          >
            <option value="">{t("surveys.allProfessionals")}</option>
            {professionals.map((professional) => (
              <option key={professional.id} value={professional.id}>
                {professional.name}
              </option>
            ))}
          </Select>
        )}
        {locations.length > 1 && (
          <Select
            aria-label={t("locations.pick")}
            value={locationId}
            onChange={(event) => setLocationId(event.target.value)}
            className="w-52 [&_select]:h-8 [&_select]:text-[13px]"
          >
            <option value="">{t("locations.all")}</option>
            {locations.map((location) => (
              <option key={location.id} value={location.id}>
                {location.name}
              </option>
            ))}
          </Select>
        )}
      </div>

      {results.isError && <Alert tone="error">{errorMessage(t, results.error)}</Alert>}
      {results.isPending ? (
        <Spinner />
      ) : (
        summary &&
        results.data && (
          <>
            <div className="grid gap-3 md:grid-cols-3">
              <Card className="p-4">
                <p className="text-[13px] font-medium text-zinc-500">{t("surveys.average")}</p>
                <div className="mt-1 flex items-baseline gap-2">
                  <span className="text-2xl font-semibold tracking-tight text-zinc-900 tabular-nums">
                    {summary.average === null
                      ? "—"
                      : summary.average.toLocaleString(language, { minimumFractionDigits: 1 })}
                  </span>
                  {summary.average !== null && <Stars value={summary.average} />}
                </div>
                <p className="mt-2 text-xs text-zinc-500">{t("surveys.averageHint")}</p>
              </Card>
              <Card className="p-4">
                <p className="text-[13px] font-medium text-zinc-500">{t("surveys.answered")}</p>
                <p className="mt-1 text-2xl font-semibold tracking-tight text-zinc-900 tabular-nums">
                  {summary.answered}
                </p>
                <p className="mt-2 text-xs text-zinc-500">
                  {t("surveys.answeredHint", {
                    percent: percent(summary.answered, summary.sent),
                    count: summary.sent,
                  })}
                </p>
              </Card>
              <Card className="p-4">
                <p className="text-[13px] font-medium text-zinc-500">{t("surveys.byStars")}</p>
                <ul className="mt-2 space-y-1">
                  {[5, 4, 3, 2, 1].map((star) => {
                    const count = summary.stars[star - 1] ?? 0;
                    return (
                      <li key={star} className="flex items-center gap-2 text-xs text-zinc-600">
                        <span className="flex w-6 items-center gap-0.5 tabular-nums">
                          {star}
                          <Star aria-hidden className="size-3 fill-amber-400 text-amber-400" />
                        </span>
                        <span className="h-1.5 flex-1 rounded-full bg-zinc-100">
                          <span
                            className="block h-1.5 rounded-full bg-amber-400"
                            style={{ width: `${percent(count, summary.answered)}%` }}
                          />
                        </span>
                        <span className="w-6 text-right tabular-nums">{count}</span>
                      </li>
                    );
                  })}
                </ul>
              </Card>
            </div>

            {results.data.responses.length === 0 ? (
              <EmptyState
                icon={<MessageSquareText className="size-5" />}
                title={t("surveys.noAnswersTitle")}
                body={t("surveys.noAnswersBody")}
              />
            ) : (
              <Card className="divide-y divide-zinc-100">
                {results.data.responses.map((response) => (
                  <ResponseRow
                    key={response.id}
                    response={response}
                    language={language}
                    showLocation={locations.length > 1}
                  />
                ))}
              </Card>
            )}
          </>
        )
      )}
    </div>
  );
}

function ResponseRow({
  response,
  language,
  showLocation,
}: {
  response: SurveyResponse;
  language: string;
  showLocation: boolean;
}) {
  const { t } = useTranslation();
  const { business } = useBusinessContext();
  const { appointment, customer } = response;
  const day = new Intl.DateTimeFormat(language, {
    day: "numeric",
    month: "short",
    timeZone: business.timezone,
  }).format(new Date(appointment.startsAt));
  return (
    <div className="flex flex-col gap-1.5 px-4 py-3.5 sm:flex-row sm:items-start sm:gap-4">
      <div className="flex shrink-0 items-center gap-2 sm:w-28 sm:flex-col sm:items-start sm:gap-1">
        <Stars value={response.rating} />
        <span className="text-xs text-zinc-500">{t(starKey(response.rating))}</span>
      </div>
      <div className="min-w-0 flex-1">
        {response.comment ? (
          <p className="text-sm whitespace-pre-line text-zinc-800">{response.comment}</p>
        ) : (
          <p className="text-sm text-zinc-400 italic">{t("surveys.noComment")}</p>
        )}
        <p className="mt-1 text-xs text-zinc-500">
          <span className="font-medium text-zinc-700">
            {customer.firstName} {customer.lastName}
          </span>
          {" · "}
          {[
            appointment.serviceName,
            appointment.resourceName,
            showLocation ? appointment.locationName : null,
          ]
            .filter(Boolean)
            .join(" · ")}
          {" · "}
          {day} · {appointment.code}
        </p>
      </div>
    </div>
  );
}

function Settings() {
  const { t, i18n } = useTranslation();
  const { business } = useBusinessContext();
  const settings = useSurveySettings(business.id);
  const save = useSaveSurveySettings(business.id);
  const language = business.locale === "en" ? "en" : "es";
  const preview = i18n.getFixedT(language);

  if (settings.isPending) return <Spinner />;
  if (settings.isError) return <Alert tone="error">{errorMessage(t, settings.error)}</Alert>;
  const update = (input: { enabled?: boolean; validHours?: number }) =>
    save.mutate(input, {
      onSuccess: () => toast.success(t("common.saved")),
      onError: (error) => toast.error(errorMessage(t, error)),
    });

  return (
    <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_380px]">
      <div className="space-y-4">
        <SettingsCard title={t("surveys.settingsTitle")} description={t("surveys.settingsBody")}>
          <Switch
            checked={settings.data.enabled}
            onChange={(enabled) => update({ enabled })}
            label={t("surveys.enabled")}
            description={t("surveys.enabledHint")}
            disabled={save.isPending}
          />
          <Field label={t("surveys.validFor")} hint={t("surveys.validForHint")}>
            {(control) => (
              <Select
                {...control}
                value={String(settings.data.validHours)}
                onChange={(event) => update({ validHours: Number(event.target.value) })}
                disabled={save.isPending}
                className="max-w-60"
              >
                {SURVEY_VALID_HOURS.map((hours) => (
                  <option key={hours} value={hours}>
                    {t(`surveys.hours${hours}`)}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </SettingsCard>
        <SettingsCard title={t("surveys.howTitle")} description={t("surveys.howBody")}>
          <ol className="list-decimal space-y-1.5 pl-5 text-sm text-zinc-600">
            <li>{t("surveys.howStep1")}</li>
            <li>{t("surveys.howStep2")}</li>
            <li>{t("surveys.howStep3")}</li>
          </ol>
          <div className="flex flex-wrap gap-2">
            <Link
              to="../automations/new?template=survey"
              className={buttonClass({ variant: "primary" })}
            >
              {t("surveys.createFlow")}
            </Link>
            <Link
              to="../settings?tab=automations"
              className={buttonClass({ variant: "secondary" })}
            >
              {t("surveys.seeFlows")}
            </Link>
          </div>
        </SettingsCard>
      </div>
      <div>
        <p className="mb-2 text-[13px] font-medium text-zinc-500">{t("surveys.preview")}</p>
        <div className="rounded-2xl bg-gradient-to-b from-brand-50 to-zinc-50 p-4">
          <SurveyCard
            business={{ name: business.name, logoUrl: business.logoUrl ?? null }}
            visit={{
              startsAt: new Date().toISOString(),
              service: preview("surveys.sampleService"),
              professional: preview("surveys.sampleProfessional"),
              timezone: business.timezone,
            }}
            state="open"
            rating={null}
            t={preview}
            locale={language}
          />
        </div>
      </div>
    </div>
  );
}
