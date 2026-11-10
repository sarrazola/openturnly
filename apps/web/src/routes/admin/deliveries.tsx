import { todayIn } from "@openturnly/core";
import { useInfiniteQuery } from "@tanstack/react-query";
import { History } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router";
import { EmptyState, PageHeader } from "../../components/controls.tsx";
import {
  DateRangePicker,
  type Period,
  periodPresets,
} from "../../components/date-range-picker.tsx";
import { Alert, Button, Card, Select, Spinner } from "../../components/ui.tsx";
import { api, type DeliveryLog, type DeliveryLogEntry } from "../../lib/api.ts";
import { useBusinesses } from "../../lib/businesses.ts";
import { errorMessage } from "../../lib/forms.ts";
import { useMe } from "../../lib/session.ts";
import { StatusBadge } from "./automation-shared.tsx";

const CHANNELS = ["whatsapp", "email", "sms", "webhook"] as const;
const STATUSES = ["sent", "failed", "pending", "skipped"] as const;

/** The viewer's time zone: the log's days are theirs, across businesses in other zones. */
const viewerZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";

function useDeliveryLog(filter: Record<string, string>) {
  const query = Object.fromEntries(
    Object.entries(filter).filter((entry): entry is [string, string] => Boolean(entry[1])),
  );
  return useInfiniteQuery({
    queryKey: ["deliveries", query],
    initialPageParam: "",
    queryFn: ({ pageParam }) =>
      api<DeliveryLog>(
        `/api/deliveries?${new URLSearchParams({
          ...query,
          ...(pageParam ? { before: pageParam } : {}),
        })}`,
      ),
    getNextPageParam: (last) => (last.more ? last.deliveries.at(-1)?.id : undefined),
    refetchInterval: 30_000,
  });
}

/** Its own page, for whoever runs the install. */
export function DeliveriesPage() {
  const { t } = useTranslation();
  const me = useMe();
  if (!me.data) return <Spinner />;
  if (me.data.role !== "owner") return <Navigate to="/admin/account" replace />;
  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <PageHeader title={t("deliveries.title")} subtitle={t("deliveries.subtitle")} />
      <DeliveriesSection />
    </div>
  );
}

/**
 * Every send of every business's flows: when, which business, flow and step, for whom, how
 * it ended and what it cost. Filtered by business, channel and status.
 */
export function DeliveriesSection() {
  const { t, i18n } = useTranslation();
  const language = i18n.resolvedLanguage ?? "es";
  const zone = viewerZone();
  const businesses = useBusinesses().data ?? [];
  const [period, setPeriod] = useState<Period>(() => periodPresets(todayIn(zone)).last30);
  const [businessId, setBusinessId] = useState("");
  const [channel, setChannel] = useState("");
  const [status, setStatus] = useState("");
  const log = useDeliveryLog({
    from: period.start,
    to: period.end,
    timezone: zone,
    businessId,
    channel,
    status,
  });
  const entries = log.data?.pages.flatMap((page) => page.deliveries) ?? [];
  const totals = log.data?.pages[0]?.totals;
  const select = "[&_select]:h-8 [&_select]:text-[13px]";

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <DateRangePicker
          value={period}
          onChange={setPeriod}
          today={todayIn(zone)}
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
        {businesses.length > 1 && (
          <Select
            aria-label={t("deliveries.business")}
            value={businessId}
            onChange={(event) => setBusinessId(event.target.value)}
            className={`w-52 ${select}`}
          >
            <option value="">{t("deliveries.allBusinesses")}</option>
            {businesses.map((business) => (
              <option key={business.id} value={business.id}>
                {business.name}
              </option>
            ))}
          </Select>
        )}
        <Select
          aria-label={t("deliveries.channel")}
          value={channel}
          onChange={(event) => setChannel(event.target.value)}
          className={`w-44 ${select}`}
        >
          <option value="">{t("deliveries.allChannels")}</option>
          {CHANNELS.map((value) => (
            <option key={value} value={value}>
              {t(`deliveries.channels.${value}`)}
            </option>
          ))}
        </Select>
        <Select
          aria-label={t("deliveries.status")}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className={`w-40 ${select}`}
        >
          <option value="">{t("deliveries.allStatuses")}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`automations.status.${value}`)}
            </option>
          ))}
        </Select>
      </div>

      {totals && (
        <p className="text-sm text-zinc-600">
          {t("deliveries.sends", { count: totals.sends })}
          {totals.charged.map((item) => (
            <span key={item.currency}>
              {" · "}
              {t("deliveries.charged", { amount: money(item.amount, item.currency, language) })}
            </span>
          ))}
        </p>
      )}

      {log.isPending ? (
        <Spinner />
      ) : log.isError ? (
        <Alert>{errorMessage(t, log.error)}</Alert>
      ) : entries.length === 0 ? (
        <EmptyState
          icon={<History className="size-5" aria-hidden />}
          title={t("deliveries.emptyTitle")}
          body={t("deliveries.emptyBody")}
        />
      ) : (
        <>
          <Card className="divide-y divide-zinc-100">
            {entries.map((entry) => (
              <LogRow
                key={entry.id}
                entry={entry}
                language={language}
                zone={zone}
                showBusiness={businesses.length > 1}
              />
            ))}
          </Card>
          {log.hasNextPage && (
            <div className="flex justify-center">
              <Button
                variant="secondary"
                loading={log.isFetchingNextPage}
                onClick={() => log.fetchNextPage()}
              >
                {t("deliveries.more")}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

/** "US$ 0,0592": an amount with as many decimals as it takes, up to four. */
function money(amount: string, currency: string | null, language: string) {
  try {
    return new Intl.NumberFormat(language, {
      style: "currency",
      currency: currency ?? "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(Number(amount));
  } catch {
    return `${amount} ${currency ?? ""}`.trim();
  }
}

function LogRow({
  entry,
  language,
  zone,
  showBusiness,
}: {
  entry: DeliveryLogEntry;
  language: string;
  zone: string;
  showBusiness: boolean;
}) {
  const { t } = useTranslation();
  const format = (value: string, options: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat(language, { ...options, timeZone: zone }).format(new Date(value));
  const who = entry.customer
    ? `${entry.customer.firstName} ${entry.customer.lastName}`.trim()
    : null;
  const errorText = entry.error
    ? t([`errors.${entry.error}`, "deliveries.unknownError"] as unknown as "errors.unknown_error", {
        error: entry.error,
      })
    : null;
  const reached =
    entry.messageStatus === "delivered" || entry.messageStatus === "read"
      ? t(`automations.messageStatus.${entry.messageStatus}`)
      : entry.responseStatus
        ? String(entry.responseStatus)
        : undefined;
  // What it took from the install's balance, or what the business's own provider charged.
  const cost = entry.charge
    ? money(entry.charge, entry.chargeCurrency, language)
    : entry.cost
      ? money(entry.cost, entry.costCurrency, language)
      : null;

  return (
    <details className="px-4 py-3">
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-4 gap-y-1 [&::-webkit-details-marker]:hidden">
        <span className="w-28 shrink-0 text-[13px] text-zinc-500 tabular-nums">
          {format(entry.createdAt, {
            day: "numeric",
            month: "short",
            hour: "2-digit",
            minute: "2-digit",
          })}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-zinc-900">
            {showBusiness && <span className="text-zinc-500">{entry.business.name} · </span>}
            {entry.automationName}
            {entry.channel && (
              <span className="font-normal text-zinc-500">
                {" · "}
                {t(`deliveries.channels.${entry.channel}`)}
              </span>
            )}
            {entry.payload.test ? (
              <span className="font-normal text-zinc-400"> · {t("automations.testLabel")}</span>
            ) : null}
          </span>
          <span className="block truncate text-[13px] text-zinc-500">
            {[who, entry.appointment?.code].filter(Boolean).join(" · ") ||
              t("deliveries.noAppointment")}
            {errorText && <span className="text-red-600"> · {errorText}</span>}
          </span>
        </span>
        {cost && <span className="text-[13px] text-zinc-600 tabular-nums">{cost}</span>}
        <StatusBadge status={entry.status} label={reached} />
      </summary>
      <div className="mt-3 space-y-2 rounded-lg bg-zinc-50 p-3 text-[13px] text-zinc-600">
        <p>
          {t("deliveries.attempts", { count: entry.attempts })}
          {entry.account && ` · ${t(`deliveries.accounts.${entry.account}`)}`}
          {entry.sentAt &&
            ` · ${t("deliveries.sentAt", {
              time: format(entry.sentAt, { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
            })}`}
          {entry.status === "pending" &&
            ` · ${t("deliveries.nextAttempt", {
              time: format(entry.nextAttemptAt, {
                day: "numeric",
                month: "short",
                hour: "2-digit",
                minute: "2-digit",
              }),
            })}`}
          {entry.charge &&
            entry.cost &&
            ` · ${t("deliveries.providerCost", { amount: money(entry.cost, entry.costCurrency, language) })}`}
        </p>
        <p className="font-medium text-zinc-700">{t("deliveries.payload")}</p>
        <pre className="max-h-64 overflow-auto rounded-md bg-white p-2 text-xs ring-1 ring-zinc-200">
          {JSON.stringify(entry.payload, null, 2)}
        </pre>
      </div>
    </details>
  );
}
