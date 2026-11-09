import { SURVEY_COMMENT_MAX } from "@openturnly/core";
import type { TFunction } from "i18next";
import { Star } from "lucide-react";
import { useId, useState } from "react";
import { cn } from "../lib/cn.ts";
import { initialsOf } from "./avatar.tsx";
import { Button, Textarea } from "./ui.tsx";

export type SurveyState = "open" | "answered" | "closed";

export type SurveyCardProps = {
  business: { name: string; logoUrl: string | null };
  visit: { startsAt: string; service: string; professional: string | null; timezone: string };
  state: SurveyState;
  /** The answer already given, when it was answered. */
  rating: number | null;
  t: TFunction;
  locale: string;
  /** Saves the answer; without it (the preview in the panel) nothing is sent. */
  onSubmit?: (answer: { rating: number; comment: string }) => Promise<void>;
};

/** The words for a rating: "survey.star5" is «Excelente». */
export function starKey(rating: number) {
  return `survey.star${Math.min(5, Math.max(1, Math.round(rating))) as 1 | 2 | 3 | 4 | 5}` as const;
}

/** "lunes, 9 de noviembre": the day of the visit, in the business's time zone. */
function visitDay(startsAt: string, timezone: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: timezone,
  }).format(new Date(startsAt));
}

/** Five stars, read as a group of choices; the one under the pointer previews its label. */
function StarPicker({
  value,
  onChange,
  t,
  readOnly,
}: {
  value: number;
  onChange?: (value: number) => void;
  t: TFunction;
  readOnly?: boolean;
}) {
  const name = useId();
  if (readOnly) {
    return (
      <div
        role="img"
        aria-label={t("survey.starsLabel", { count: value })}
        className="flex justify-center gap-1.5"
      >
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            aria-hidden
            className={cn(
              "size-7",
              star <= value ? "fill-amber-400 text-amber-400" : "text-zinc-300",
            )}
          />
        ))}
      </div>
    );
  }
  return (
    <div className="flex flex-col items-center gap-2">
      <fieldset className="flex gap-1.5">
        <legend className="sr-only">{t("survey.question")}</legend>
        {[1, 2, 3, 4, 5].map((star) => (
          <label
            key={star}
            className="cursor-pointer rounded-lg p-1 transition-transform hover:scale-110 active:scale-95 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand-600"
          >
            <input
              type="radio"
              name={name}
              value={star}
              checked={value === star}
              onChange={() => onChange?.(star)}
              className="sr-only"
            />
            <Star
              aria-hidden
              className={cn(
                "size-10 transition-colors sm:size-11",
                star <= value ? "fill-amber-400 text-amber-400" : "text-zinc-300",
              )}
              strokeWidth={1.5}
            />
            <span className="sr-only">{t("survey.starsLabel", { count: star })}</span>
          </label>
        ))}
      </fieldset>
      <p className="h-5 text-sm font-medium text-zinc-600" aria-live="polite">
        {value ? t(starKey(value)) : ""}
      </p>
    </div>
  );
}

/**
 * What a survey link opens: who the business is, which visit it's about, then the stars
 * and a comment. The same card shows the thanks, a closed link and, in the panel, a preview.
 */
export function SurveyCard({
  business,
  visit,
  state,
  rating: answered,
  t,
  locale,
  onSubmit,
}: SurveyCardProps) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const commentId = useId();
  const thanks = done || state === "answered";
  const visitLine = [visit.service, visit.professional].filter(Boolean).join(" · ");

  const submit = async () => {
    if (!rating) {
      setError(t("survey.pickRating"));
      return;
    }
    if (!onSubmit) return;
    setSending(true);
    setError(null);
    try {
      await onSubmit({ rating, comment });
      setDone(true);
    } catch (problem) {
      setError(problem instanceof Error && problem.message ? problem.message : t("survey.failed"));
    } finally {
      setSending(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-md overflow-clip rounded-3xl bg-white shadow-[0_32px_80px_-24px_rgb(15_15_35/0.3)] ring-1 ring-zinc-950/5">
      <header className="flex flex-col items-center px-6 pt-8 text-center">
        {business.logoUrl ? (
          <img
            src={business.logoUrl}
            alt=""
            className="size-16 rounded-2xl object-cover ring-1 ring-zinc-950/5"
          />
        ) : (
          <div className="grid size-16 place-items-center rounded-2xl bg-brand-50 text-xl font-semibold text-brand-700">
            {initialsOf(business.name)}
          </div>
        )}
        <p className="mt-3 text-[15px] font-semibold text-zinc-900">{business.name}</p>
        <p className="mt-1 text-sm text-zinc-500">
          {visitLine}
          {visitLine && <br />}
          <span className="first-letter:uppercase">
            {visitDay(visit.startsAt, visit.timezone, locale)}
          </span>
        </p>
      </header>

      <div className="px-6 pt-6 pb-8">
        {state === "closed" && !thanks ? (
          <div className="rounded-2xl bg-zinc-50 px-5 py-6 text-center">
            <h1 className="text-lg font-semibold text-zinc-900">{t("survey.closedTitle")}</h1>
            <p className="mt-1 text-sm text-zinc-500">{t("survey.closedBody")}</p>
          </div>
        ) : thanks ? (
          <div className="flex flex-col items-center gap-3 rounded-2xl bg-brand-50 px-5 py-6 text-center">
            <StarPicker value={done ? rating : (answered ?? 0)} t={t} readOnly />
            <h1 className="text-lg font-semibold text-zinc-900">{t("survey.thanksTitle")}</h1>
            <p className="text-sm text-zinc-600">{t("survey.thanksBody")}</p>
          </div>
        ) : (
          <form
            className="flex flex-col gap-5"
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            <h1 className="text-center text-xl font-semibold tracking-tight text-zinc-900">
              {t("survey.question")}
            </h1>
            <StarPicker
              value={rating}
              onChange={(value) => {
                setRating(value);
                setError(null);
              }}
              t={t}
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor={commentId} className="text-sm font-medium text-zinc-800">
                {t("survey.commentLabel")}{" "}
                <span className="font-normal text-zinc-400">{t("survey.optional")}</span>
              </label>
              <Textarea
                id={commentId}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                maxLength={SURVEY_COMMENT_MAX}
                rows={3}
                placeholder={t("survey.commentPlaceholder")}
              />
            </div>
            {error && (
              <p role="alert" className="text-center text-sm text-red-600">
                {error}
              </p>
            )}
            <Button type="submit" loading={sending} className="h-11 w-full gap-2 text-[15px]">
              {t("survey.send")}
            </Button>
          </form>
        )}
      </div>
    </main>
  );
}
