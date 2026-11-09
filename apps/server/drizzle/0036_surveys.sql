CREATE TABLE "surveys" (
	"id" text PRIMARY KEY NOT NULL,
	"code" text NOT NULL,
	"business_id" text NOT NULL,
	"appointment_id" text NOT NULL,
	"sent_at" timestamp with time zone,
	"expires_at" timestamp with time zone,
	"rating" integer,
	"comment" text,
	"answered_at" timestamp with time zone,
	"created_at" timestamp with time zone NOT NULL,
	CONSTRAINT "surveys_code_unique" UNIQUE("code"),
	CONSTRAINT "surveys_appointment_id_unique" UNIQUE("appointment_id")
);
--> statement-breakpoint
ALTER TABLE "businesses" ADD COLUMN "survey_enabled" boolean DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE "businesses" ADD COLUMN "survey_valid_hours" integer DEFAULT 48 NOT NULL;--> statement-breakpoint
ALTER TABLE "surveys" ADD CONSTRAINT "surveys_business_id_businesses_id_fk" FOREIGN KEY ("business_id") REFERENCES "public"."businesses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "surveys" ADD CONSTRAINT "surveys_appointment_id_appointments_id_fk" FOREIGN KEY ("appointment_id") REFERENCES "public"."appointments"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "surveys_business_answered_idx" ON "surveys" USING btree ("business_id","answered_at");--> statement-breakpoint
CREATE INDEX "surveys_business_sent_idx" ON "surveys" USING btree ("business_id","sent_at");