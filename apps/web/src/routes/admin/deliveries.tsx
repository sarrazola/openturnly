import { todayIn } from "@openturnly/core";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { History } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router";
import { EmptyState, PageHeader } from "../../components/controls.tsx";
import { type Column, DataTable } from "../../components/data-table.tsx";
import {
  DateRangePicker,
  type Period,
  periodPresets,
} from "../../components/date-range-picker.tsx";
import { Alert, Badge, Card, Select, Spinner } from "../../components/ui.tsx";
import { api, type DeliveryLog, type DeliveryLogEntry } from "../../lib/api.ts";
import { useBusinesses } from "../../lib/businesses.ts";
import { errorMessage } from "../../lib/forms.ts";
import { useMe } from "../../lib/session.ts";
import { StatusBadge } from "./automation-shared.tsx";

const CHANNELS = ["whatsapp", "email", "sms", "webhook"] as const;
const STATUSES = ["sent", "failed", "pending", "skipped"] as const;

/** The viewer's time zone: the log's days are theirs, across businesses in other zones. */
const viewerZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";

/** Its own page, for whoever runs the install. */
export function DeliveriesPage() {
  const { t } = useTranslation();
  const me = useMe();
  if (!me.data) return <Spinner />;
  if (me.data.role !== "owner") return <Navigate to="/admin/account" replace />;
  return (
    <div className="mx-auto w-full max-w-6xl space-y-6">
      <PageHeader title={t("deliveries.title")} subtitle={t("deliveries.subtitle")} />
      <DeliveriesSection />
    </div>
  );
}

/** "US$ 0,0592": an amount with as many decimals as it takes, up to four. */
function money(amount: string | number, currency: string | null, language: string) {
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

/**
 * Every send of every business's flows, as a table: when, which business and flow, the
 * channel, to whom, how it ended and what it cost. Filtered by business, channel and status,
 * 25 to a page.
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
  const [page, setPage] = useState(0);
  // Each filter starts over on the first page.
  const filtered =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value);
      setPage(0);
    };

  const query = new URLSearchParams(
    Object.entries({
      from: period.start,
      to: period.end,
      timezone: zone,
      businessId,
      channel,
      status,
      page: String(page + 1),
    }).filter(([, value]) => Boolean(value)),
  ).toString();
  const log = useQuery({
    queryKey: ["deliveries", query],
    queryFn: () => api<DeliveryLog>(`/api/deliveries?${query}`),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
  const data = log.data;
  const format = (value: string, options: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat(language, { ...options, timeZone: zone }).format(new Date(value));
  const select = "[&_select]:h-8 [&_select]:text-[13px]";

  const columns: Column<DeliveryLogEntry>[] = [
    {
      key: "date",
      header: t("deliveries.columns.date"),
      className: "whitespace-nowrap",
      cell: (entry) => (
        <span className="text-zinc-700 tabular-nums">
          {format(entry.createdAt, {
            day: "numeric",
            month: "short",
            hour: "2-digit",
            minute: "2-digit",
          })}
        </span>
      ),
    },
    {
      key: "flow",
      header: t("deliveries.columns.flow"),
      cell: (entry) => (
        <div className="min-w-40">
          <div className="font-medium text-zinc-900">
            {entry.automationName}
            {entry.payload.test ? (
              <span className="ml-1.5 align-middle">
                <Badge>{t("automations.testLabel")}</Badge>
              </span>
            ) : null}
          </div>
          <div className="text-[13px] text-zinc-500">{entry.business.name}</div>
        </div>
      ),
    },
    {
      key: "channel",
      header: t("deliveries.columns.channel"),
      className: "whitespace-nowrap",
      cell: (entry) => (
        <div>
          <div className="text-zinc-800">
            {entry.channel ? t(`deliveries.channels.${entry.channel}`) : "—"}
          </div>
          {entry.account && (
            <div className="text-[13px] text-zinc-500">
              {t(`deliveries.accounts.${entry.account}`)}
            </div>
          )}
        </div>
      ),
    },
    {
      key: "recipient",
      header: t("deliveries.columns.recipient"),
      cell: (entry) => {
        const name = entry.customer
          ? `${entry.customer.firstName} ${entry.customer.lastName}`.trim()
          : null;
        const to = typeof entry.payload.to === "string" ? entry.payload.to : null;
        return (
          <div className="max-w-48 min-w-0">
            <div className="truncate text-zinc-800">{name || to || "—"}</div>
            {name && to && (
              <div className="truncate text-[13px] text-zinc-500" title={to}>
                {to}
              </div>
            )}
          </div>
        );
      },
    },
    {
      key: "status",
      header: t("deliveries.columns.status"),
      cell: (entry) => {
        const reached =
          entry.messageStatus === "delivered" || entry.messageStatus === "read"
            ? t(`automations.messageStatus.${entry.messageStatus}`)
            : undefined;
        const why = entry.error
          ? t(
              [
                `errors.${entry.error}`,
                "deliveries.unknownError",
              ] as unknown as "errors.unknown_error",
              {
                error: entry.error,
              },
            )
          : null;
        return (
          <div
            className="max-w-44"
            title={
              entry.responseStatus
                ? t("deliveries.response", { code: entry.responseStatus })
                : undefined
            }
          >
            <StatusBadge status={entry.status} label={reached} />
            {why && (
              <div
                className={`mt-1 text-[13px] ${entry.status === "failed" ? "text-red-600" : "text-zinc-500"}`}
              >
                {why}
              </div>
            )}
          </div>
        );
      },
    },
    {
      key: "cost",
      header: t("deliveries.columns.cost"),
      className: "text-right whitespace-nowrap",
      cell: (entry) => {
        // What it took from the install's balance, or what the business's own provider charged.
        const amount = entry.charge ?? entry.cost;
        const currency = entry.charge ? entry.chargeCurrency : entry.costCurrency;
        return (
          <span className="text-zinc-800 tabular-nums">
            {amount ? money(amount, currency, language) : "—"}
          </span>
        );
      },
    },
  ];

  const charged = data?.totals.charged ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <DateRangePicker
          value={period}
          onChange={filtered(setPeriod)}
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
        <Select
          aria-label={t("deliveries.business")}
          value={businessId}
          onChange={(event) => filtered(setBusinessId)(event.target.value)}
          className={`w-52 ${select}`}
        >
          <option value="">{t("deliveries.allBusinesses")}</option>
          {businesses.map((business) => (
            <option key={business.id} value={business.id}>
              {business.name}
            </option>
          ))}
        </Select>
        <Select
          aria-label={t("deliveries.channel")}
          value={channel}
          onChange={(event) => filtered(setChannel)(event.target.value)}
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
          onChange={(event) => filtered(setStatus)(event.target.value)}
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

      {data && (
        <div className="grid grid-cols-3 gap-3">
          <Stat label={t("deliveries.stats.sends")} value={String(data.totals.sends)} />
          <Stat
            label={t("deliveries.stats.failed")}
            value={String(data.totals.failed)}
            danger={data.totals.failed > 0}
          />
          <Stat
            label={t("deliveries.stats.cost")}
            value={
              charged.length === 0
                ? money(0, "USD", language)
                : charged.map((item) => money(item.amount, item.currency, language)).join(" + ")
            }
          />
        </div>
      )}

      {log.isPending ? (
        <Spinner />
      ) : log.isError ? (
        <Alert>{errorMessage(t, log.error)}</Alert>
      ) : !data || data.totals.sends === 0 ? (
        <EmptyState
          icon={<History className="size-5" aria-hidden />}
          title={t("deliveries.emptyTitle")}
          body={t("deliveries.emptyBody")}
        />
      ) : (
        <DataTable
          columns={columns}
          rows={data?.deliveries ?? []}
          rowKey={(entry) => entry.id}
          paging={{
            page,
            pageSize: data?.pageSize ?? 25,
            total: data?.totals.sends ?? 0,
            onChange: setPage,
          }}
          empty={t("deliveries.emptyTitle")}
        />
      )}
    </div>
  );
}

function Stat({ label, value, danger }: { label: string; value: string; danger?: boolean }) {
  return (
    <Card className="px-4 py-3">
      <div className="text-[13px] text-zinc-500">{label}</div>
      <div
        className={`mt-0.5 text-xl font-semibold tracking-tight tabular-nums ${danger ? "text-red-600" : "text-zinc-900"}`}
      >
        {value}
      </div>
    </Card>
  );
}
