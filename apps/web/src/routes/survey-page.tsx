import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { SurveyCard, type SurveyState } from "../components/survey-card.tsx";
import { Spinner } from "../components/ui.tsx";
import { ApiError, api } from "../lib/api.ts";
import { brandStyle } from "../lib/brand.ts";

type PublicSurvey = {
  state: SurveyState;
  business: {
    name: string;
    slug: string;
    locale: string;
    timezone: string;
    brandColor: string | null;
    logoUrl: string | null;
  };
  visit: { startsAt: string; service: string; professional: string | null };
  rating: number | null;
};

/** What a survey link opens: /e/<code>. In the business's language and colour. */
export function SurveyPage() {
  const { code = "" } = useParams();
  const { i18n } = useTranslation();
  const survey = useQuery({
    queryKey: ["survey", code],
    queryFn: () => api<PublicSurvey>(`/api/public/surveys/${encodeURIComponent(code)}`),
    retry: false,
  });
  const locale = survey.data?.business.locale ?? i18n.resolvedLanguage ?? "es";
  const t = i18n.getFixedT(locale);

  useEffect(() => {
    // A link meant for one person: search engines leave it out.
    const robots = document.createElement("meta");
    robots.name = "robots";
    robots.content = "noindex";
    document.head.append(robots);
    return () => robots.remove();
  }, []);
  useEffect(() => {
    if (survey.data) document.title = survey.data.business.name;
  }, [survey.data]);

  if (survey.isPending) return <Spinner className="min-h-screen" />;
  if (survey.isError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 px-4 text-center">
        <h1 className="text-xl font-semibold">{t("survey.notFoundTitle")}</h1>
        <p className="mt-2 text-sm text-zinc-500">{t("survey.notFoundBody")}</p>
      </div>
    );
  }
  const { business, visit, state, rating } = survey.data;
  return (
    <div
      className="flex min-h-screen items-start justify-center bg-gradient-to-b from-brand-50 to-zinc-50 px-3 py-8 sm:items-center sm:py-14"
      style={brandStyle(business.brandColor)}
    >
      <SurveyCard
        business={business}
        visit={{ ...visit, timezone: business.timezone }}
        state={state}
        rating={rating}
        t={t}
        locale={locale}
        onSubmit={async (answer) => {
          try {
            await api(`/api/public/surveys/${encodeURIComponent(code)}`, {
              method: "POST",
              body: answer,
            });
          } catch (error) {
            const known =
              error instanceof ApiError &&
              ["survey_answered", "survey_closed"].includes(error.code);
            throw new Error(
              known
                ? t(
                    `survey.errors.${(error as ApiError).code as "survey_answered" | "survey_closed"}`,
                  )
                : "",
            );
          }
        }}
      />
    </div>
  );
}
