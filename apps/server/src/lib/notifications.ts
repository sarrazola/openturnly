/**
 * Who hears about what, and the unread count behind the number on the apps' icon.
 *
 * A new booking becomes one notification per person who should know: the instance's owners,
 * the business's admins and the professional it's booked with. The person who booked it
 * doesn't get one. Each notification is kept, so the apps can list and count them, and
 * pushed to that person's phones when the install has a push provider.
 */

import { instantToZoned } from "@openturnly/core";
import { and, count, eq, gt, inArray, isNull } from "drizzle-orm";
import type { Db } from "../db/client.ts";
import {
  appointments,
  businesses,
  businessMembers,
  customers,
  devices,
  notifications,
  sessions,
  users,
} from "../db/schema.ts";
import type { PushProvider } from "./push.ts";

export type Notifications = ReturnType<typeof createNotifications>;

type Copy = { title: string; withProfessional: string };

const COPY: Record<string, Copy> = {
  es: { title: "Nueva reserva", withProfessional: "con" },
  en: { title: "New booking", withProfessional: "with" },
};

/** "Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan", in the business's language. */
export function bookingText(
  business: { locale: string; timezone: string },
  appointment: { startsAt: Date; serviceName: string; resourceName: string | null },
  customer: { firstName: string; lastName: string },
  showProfessional: boolean,
) {
  const copy = COPY[business.locale] ?? (COPY.en as Copy);
  const start = instantToZoned(appointment.startsAt.getTime(), business.timezone);
  const at = new Date(`${start.date}T${start.time}:00Z`);
  const when = new Intl.DateTimeFormat(business.locale, {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
    timeZone: "UTC",
  }).format(at);
  const parts = [
    `${customer.firstName} ${customer.lastName}`.trim(),
    appointment.serviceName,
    when,
  ];
  if (showProfessional && appointment.resourceName) {
    parts.push(`${copy.withProfessional} ${appointment.resourceName}`);
  }
  return { title: copy.title, body: parts.join(" · ") };
}

export function createNotifications({ db, push }: { db: Db; push: PushProvider | null }) {
  const unreadCount = async (userId: string) => {
    const [row] = await db
      .select({ n: count() })
      .from(notifications)
      .where(and(eq(notifications.userId, userId), isNull(notifications.readAt)));
    return row?.n ?? 0;
  };

  /** The phones a person is signed in on right now. */
  const activeDevices = async (userId: string) =>
    db
      .select({
        token: devices.token,
        platform: devices.platform,
        environment: devices.environment,
      })
      .from(devices)
      .innerJoin(sessions, eq(sessions.id, devices.sessionId))
      .where(and(eq(devices.userId, userId), gt(sessions.expiresAt, new Date())));

  const deliver = async (
    userId: string,
    message: { title: string; body: string; data: Record<string, string> },
  ) => {
    if (!push) return;
    const list = await activeDevices(userId);
    if (list.length === 0) return;
    const result = await push.send({
      ...message,
      userId,
      badge: await unreadCount(userId),
      devices: list,
    });
    const invalid = result?.invalidTokens ?? [];
    if (invalid.length > 0) await db.delete(devices).where(inArray(devices.token, invalid));
  };

  return {
    pushEnabled: push !== null,
    unreadCount,

    /**
     * Tells the people who should know about a new booking. Never fails the booking: a
     * problem is logged and the booking stands.
     */
    async appointmentBooked(appointmentId: string, bookedByUserId: string | null) {
      try {
        const [row] = await db
          .select({ appointment: appointments, customer: customers, business: businesses })
          .from(appointments)
          .innerJoin(customers, eq(customers.id, appointments.customerId))
          .innerJoin(businesses, eq(businesses.id, appointments.businessId))
          .where(eq(appointments.id, appointmentId));
        if (!row) return;
        const { appointment, customer, business } = row;

        const owners = await db
          .select({ userId: users.id })
          .from(users)
          .where(eq(users.role, "owner"));
        const members = await db
          .select({
            userId: businessMembers.userId,
            role: businessMembers.role,
            resourceId: businessMembers.resourceId,
          })
          .from(businessMembers)
          .where(eq(businessMembers.businessId, business.id));

        // Owners and admins see the whole agenda; a professional, their own appointments.
        const recipients = new Map<string, { professional: boolean }>();
        for (const { userId } of owners) recipients.set(userId, { professional: false });
        for (const member of members) {
          if (member.role === "admin") recipients.set(member.userId, { professional: false });
          else if (
            appointment.resourceId &&
            member.resourceId === appointment.resourceId &&
            !recipients.has(member.userId)
          ) {
            recipients.set(member.userId, { professional: true });
          }
        }
        if (bookedByUserId) recipients.delete(bookedByUserId);
        if (recipients.size === 0) return;

        const created = await db
          .insert(notifications)
          .values(
            [...recipients].map(([userId, { professional }]) => ({
              userId,
              businessId: business.id,
              appointmentId: appointment.id,
              kind: "appointment_booked" as const,
              ...bookingText(business, appointment, customer, !professional),
            })),
          )
          .returning();

        for (const notification of created) {
          try {
            await deliver(notification.userId, {
              title: notification.title,
              body: notification.body,
              data: {
                kind: notification.kind,
                notificationId: notification.id,
                businessId: business.id,
                appointmentId: appointment.id,
                startsAt: appointment.startsAt.toISOString(),
              },
            });
          } catch (error) {
            console.error("notifications: could not push", error);
          }
        }
      } catch (error) {
        console.error("notifications: could not notify a booking", error);
      }
    },
  };
}
