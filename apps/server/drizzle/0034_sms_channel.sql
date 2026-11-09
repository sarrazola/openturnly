CREATE TABLE "sms_connections" (
	"id" text PRIMARY KEY NOT NULL,
	"account_sid" text NOT NULL,
	"api_key_sid" text NOT NULL,
	"encrypted_api_key_secret" text,
	"sender" text NOT NULL,
	"status" text DEFAULT 'disconnected' NOT NULL,
	"last_error" text,
	"last_connected_at" timestamp with time zone,
	"created_at" timestamp with time zone NOT NULL,
	"updated_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "cost" numeric(12, 6);--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "cost_currency" text;--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "message_checked_at" timestamp with time zone;