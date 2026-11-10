ALTER TABLE "automation_deliveries" ADD COLUMN "channel" text;--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "account" text;--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "charge" numeric(12, 6);--> statement-breakpoint
ALTER TABLE "automation_deliveries" ADD COLUMN "charge_currency" text;--> statement-breakpoint
UPDATE "automation_deliveries" AS d SET "channel" = a.elem->>'type' FROM "automations" AS au, jsonb_array_elements(au."actions") AS a(elem) WHERE au."id" = d."automation_id" AND a.elem->>'id' = d."action_id";
