import type { BookingLinkInput } from "@openturnly/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type BookingLink } from "./api.ts";

const key = (businessId: string) => ["businesses", businessId, "booking-links"] as const;

export function useBookingLinks(businessId: string) {
  return useQuery({
    queryKey: key(businessId),
    queryFn: async () =>
      (await api<{ links: BookingLink[] }>(`/api/businesses/${businessId}/booking-links`)).links,
  });
}

export function useBookingLinkMutations(businessId: string) {
  const queryClient = useQueryClient();
  const base = `/api/businesses/${businessId}/booking-links`;
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: key(businessId) });
  return {
    save: useMutation({
      mutationFn: ({ id, input }: { id?: string; input: BookingLinkInput }) =>
        id
          ? api(`${base}/${id}`, { method: "PATCH", body: input })
          : api(`${base}`, { method: "POST", body: input }),
      onSuccess,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api(`${base}/${id}`, { method: "DELETE" }),
      onSuccess,
    }),
  };
}

/** Where customers open a booking link. */
export function bookingLinkUrl(businessSlug: string, linkSlug: string) {
  return `${window.location.origin}/${businessSlug}/${linkSlug}`;
}
