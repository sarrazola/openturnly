import { bookingLinkSchema } from "@openturnly/core";
import { and, asc, eq, inArray } from "drizzle-orm";
import { Hono } from "hono";
import { bookingLinks, services } from "../db/schema.ts";
import { apiError, type BusinessEnv, type Deps, parseBody } from "../lib/http.ts";

/**
 * The business's booking links: more ways into the same agenda, each at
 * `/<business>/<slug>` with its own name, description and services. They hold no
 * availability of their own, so a booking through any of them takes the professionals'
 * time everywhere.
 */
export function bookingLinkRoutes({ db }: Deps) {
  const findLink = async (businessId: string, linkId: string) => {
    const [link] = await db
      .select()
      .from(bookingLinks)
      .where(and(eq(bookingLinks.businessId, businessId), eq(bookingLinks.id, linkId)));
    if (!link) throw apiError(404, "link_not_found");
    return link;
  };

  /** Only the business's own services, in the order given. */
  const ownServices = async (businessId: string, serviceIds: string[]) => {
    const rows = await db
      .select({ id: services.id })
      .from(services)
      .where(and(eq(services.businessId, businessId), inArray(services.id, serviceIds)));
    const known = new Set(rows.map((row) => row.id));
    const kept = [...new Set(serviceIds)].filter((id) => known.has(id));
    if (kept.length === 0) throw apiError(400, "validation_failed", { serviceIds: "required" });
    return kept;
  };

  const slugTaken = async (businessId: string, slug: string, except?: string) => {
    const [other] = await db
      .select({ id: bookingLinks.id })
      .from(bookingLinks)
      .where(and(eq(bookingLinks.businessId, businessId), eq(bookingLinks.slug, slug)));
    if (other && other.id !== except) {
      throw apiError(409, "slug_taken", { slug: "slug_taken" });
    }
  };

  return new Hono<BusinessEnv>()
    .get("/", async (c) => {
      const rows = await db
        .select()
        .from(bookingLinks)
        .where(eq(bookingLinks.businessId, c.get("business").id))
        .orderBy(asc(bookingLinks.createdAt));
      return c.json({ links: rows });
    })

    .post("/", async (c) => {
      const businessId = c.get("business").id;
      const input = await parseBody(c, bookingLinkSchema);
      await slugTaken(businessId, input.slug);
      const [link] = await db
        .insert(bookingLinks)
        .values({
          ...input,
          businessId,
          serviceIds: await ownServices(businessId, input.serviceIds),
        })
        .returning();
      return c.json({ link }, 201);
    })

    .patch("/:linkId", async (c) => {
      const businessId = c.get("business").id;
      const current = await findLink(businessId, c.req.param("linkId"));
      const input = await parseBody(c, bookingLinkSchema.partial());
      if (input.slug && input.slug !== current.slug) {
        await slugTaken(businessId, input.slug, current.id);
      }
      const [link] = await db
        .update(bookingLinks)
        .set({
          ...input,
          ...(input.serviceIds
            ? { serviceIds: await ownServices(businessId, input.serviceIds) }
            : {}),
        })
        .where(eq(bookingLinks.id, current.id))
        .returning();
      return c.json({ link });
    })

    .delete("/:linkId", async (c) => {
      const link = await findLink(c.get("business").id, c.req.param("linkId"));
      // Appointments booked through it keep its name.
      await db.delete(bookingLinks).where(eq(bookingLinks.id, link.id));
      return c.body(null, 204);
    });
}
