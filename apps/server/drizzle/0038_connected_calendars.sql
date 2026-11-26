CREATE TABLE "calendar_connections" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"provider" text DEFAULT 'google' NOT NULL,
	"account_id" text NOT NULL,
	"account_email" text,
	"encrypted_refresh_token" text NOT NULL,
	"encrypted_access_token" text,
	"access_token_expires_at" timestamp with time zone,
	"status" text DEFAULT 'connected' NOT NULL,
	"last_error" text,
	"created_at" timestamp with time zone NOT NULL,
	"updated_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "calendar_events" (
	"id" text PRIMARY KEY NOT NULL,
	"target_id" text NOT NULL,
	"appointment_id" text NOT NULL,
	"external_event_id" text,
	"with_guest" boolean DEFAULT false NOT NULL,
	"synced_hash" text,
	"due_at" timestamp with time zone,
	"attempts" integer DEFAULT 0 NOT NULL,
	"last_error" text,
	"created_at" timestamp with time zone NOT NULL,
	"updated_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "calendar_targets" (
	"id" text PRIMARY KEY NOT NULL,
	"connection_id" text NOT NULL,
	"business_id" text NOT NULL,
	"external_calendar_id" text,
	"calendar_name" text,
	"resource_ids" jsonb,
	"created_at" timestamp with time zone NOT NULL,
	"updated_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "mode" text DEFAULT 'in_person' NOT NULL;--> statement-breakpoint
ALTER TABLE "appointments" ADD COLUMN "meeting_url" text;--> statement-breakpoint
ALTER TABLE "services" ADD COLUMN "mode" text DEFAULT 'in_person' NOT NULL;--> statement-breakpoint
ALTER TABLE "calendar_connections" ADD CONSTRAINT "calendar_connections_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "calendar_events" ADD CONSTRAINT "calendar_events_target_id_calendar_targets_id_fk" FOREIGN KEY ("target_id") REFERENCES "public"."calendar_targets"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "calendar_events" ADD CONSTRAINT "calendar_events_appointment_id_appointments_id_fk" FOREIGN KEY ("appointment_id") REFERENCES "public"."appointments"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "calendar_targets" ADD CONSTRAINT "calendar_targets_connection_id_calendar_connections_id_fk" FOREIGN KEY ("connection_id") REFERENCES "public"."calendar_connections"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "calendar_targets" ADD CONSTRAINT "calendar_targets_business_id_businesses_id_fk" FOREIGN KEY ("business_id") REFERENCES "public"."businesses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "calendar_connections_user_provider_idx" ON "calendar_connections" USING btree ("user_id","provider");--> statement-breakpoint
CREATE UNIQUE INDEX "calendar_events_target_appointment_idx" ON "calendar_events" USING btree ("target_id","appointment_id");--> statement-breakpoint
CREATE INDEX "calendar_events_appointment_idx" ON "calendar_events" USING btree ("appointment_id");--> statement-breakpoint
CREATE INDEX "calendar_events_due_idx" ON "calendar_events" USING btree ("due_at");--> statement-breakpoint
CREATE UNIQUE INDEX "calendar_targets_connection_business_idx" ON "calendar_targets" USING btree ("connection_id","business_id");--> statement-breakpoint
CREATE INDEX "calendar_targets_business_idx" ON "calendar_targets" USING btree ("business_id");