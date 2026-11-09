import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api.ts";

export type SurveySettings = { enabled: boolean; validHours: number };

export type SurveyResponse = {
  id: string;
  rating: number;
  comment: string | null;
  answeredAt: string;
  appointment: {
    id: string;
    code: string;
    startsAt: string;
    serviceName: string;
    resourceName: string | null;
    locationName: string | null;
  };
  customer: { id: string; firstName: string; lastName: string };
};

export type SurveyResults = {
  summary: {
    sent: number;
    answered: number;
    /** One decimal; null without answers. */
    average: number | null;
    /** How many gave 1, 2, 3, 4 and 5 stars. */
    stars: number[];
  };
  responses: SurveyResponse[];
};

export function useSurveySettings(businessId: string) {
  return useQuery({
    queryKey: ["businesses", businessId, "surveys", "settings"],
    queryFn: () => api<SurveySettings>(`/api/businesses/${businessId}/surveys/settings`),
  });
}

export function useSaveSurveySettings(businessId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<SurveySettings>) =>
      api<SurveySettings>(`/api/businesses/${businessId}/surveys/settings`, {
        method: "PATCH",
        body: input,
      }),
    onSuccess: (settings) =>
      queryClient.setQueryData(["businesses", businessId, "surveys", "settings"], settings),
  });
}

/** The surveys sent for appointments in a period, at one location or professional or all. */
export function useSurveyResults(
  businessId: string,
  filter: { from: string; to: string; locationId?: string; resourceId?: string },
) {
  const query = Object.fromEntries(
    Object.entries(filter).filter((entry): entry is [string, string] => Boolean(entry[1])),
  );
  return useQuery({
    queryKey: ["businesses", businessId, "surveys", "results", query],
    queryFn: () =>
      api<SurveyResults>(`/api/businesses/${businessId}/surveys?${new URLSearchParams(query)}`),
  });
}
