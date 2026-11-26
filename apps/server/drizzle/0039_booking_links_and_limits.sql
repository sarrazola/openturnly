CREATE TABLE "booking_links" (
	"id" text PRIMARY KEY NOT NULL,
	"business_id" text NOT NULL,
	"slug" text NOT NULL,
	"name" text NOT NULL,
	"description" text,
	"service_ids" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone NOT NULL,
	"updated_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "buffer_before_min" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "buffer_after_min" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "booking_link_id" text;--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "booking_link_name" text;--> statement-breakpoint
ALTER TABLE "businesses" ADD COLUMN "hidden_slots_percent" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "businesses" ADD COLUMN "max_daily_appointments" integer;--> statement-breakpoint
ALTER TABLE "resources" ADD COLUMN "max_daily_appointments" integer;--> statement-breakpoint
ALTER TABLE "services" ADD COLUMN "buffer_before_min" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "services" ADD COLUMN "buffer_after_min" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "services" ADD COLUMN "listed" boolean DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE "booking_links" ADD CONSTRAINT "booking_links_business_id_businesses_id_fk" FOREIGN KEY ("business_id") REFERENCES "public"."businesses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "booking_links_business_slug_idx" ON "booking_links" USING btree ("business_id","slug");--> statement-breakpoint
ALTER TABLE "appointments" ADD CONSTRAINT "appointments_booking_link_id_booking_links_id_fk" FOREIGN KEY ("booking_link_id") REFERENCES "public"."booking_links"("id") ON DELETE set null ON UPDATE no action;