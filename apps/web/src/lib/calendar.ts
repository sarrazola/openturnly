import type { CalendarTargetInput } from "@openturnly/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type CalendarOverview } from "./api.ts";

const key = ["me", "calendar"] as const;

export function useCalendar() {
  return useQuery({ queryKey: key, queryFn: () => api<CalendarOverview>("/api/me/calendar") });
}

/** Sends the browser to Google's consent screen; it comes back to `returnTo`. */
export function connectGoogleCalendar(returnTo = "/admin/account") {
  window.location.assign(`/api/integrations/google/start?returnTo=${encodeURIComponent(returnTo)}`);
}

export function useCalendarMutations() {
  const queryClient = useQueryClient();
  const store = (overview: CalendarOverview) => queryClient.setQueryData(key, overview);
  return {
    setTarget: useMutation({
      mutationFn: (body: CalendarTargetInput) =>
        api<CalendarOverview>("/api/me/calendar", { method: "PATCH", body }),
      onSuccess: store,
    }),
    disconnect: useMutation({
      mutationFn: ({ keepCalendars }: { keepCalendars: boolean }) =>
        api<CalendarOverview>(`/api/me/calendar${keepCalendars ? "?calendars=keep" : ""}`, {
          method: "DELETE",
        }),
      onSuccess: store,
    }),
  };
}
