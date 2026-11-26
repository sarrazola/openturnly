import { bookingLinkSchema, slugify } from "@openturnly/core";
import { Link2, Plus } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Checklist } from "../../components/checklist.tsx";
import { useConfirm } from "../../components/confirm.tsx";
import { EmptyState, Modal, PageHeader, Switch } from "../../components/controls.tsx";
import { type Column, DataTable } from "../../components/data-table.tsx";
import { toast } from "../../components/toast.tsx";
import {
  Alert,
  Badge,
  Button,
  CopyField,
  Field,
  Input,
  Spinner,
  Textarea,
} from "../../components/ui.tsx";
import type { BookingLink } from "../../lib/api.ts";
import {
  bookingLinkUrl,
  useBookingLinkMutations,
  useBookingLinks,
} from "../../lib/booking-links.ts";
import { useServices } from "../../lib/catalog.ts";
import { formatDuration } from "../../lib/format.ts";
import {
  apiFieldErrors,
  errorMessage,
  type FieldErrors,
  fieldMessage,
  validate,
} from "../../lib/forms.ts";
import { useBusinessContext } from "./business.tsx";

/**
 * Booking links: more ways into the same agenda. Each one has its own address, name,
 * description and services; bookings through any of them take the same professionals'
 * time, and the agenda shows which link each came through.
 */
export function BookingLinksPage() {
  const { t } = useTranslation();
  const { business } = useBusinessContext();
  const links = useBookingLinks(business.id);
  const services = useServices(business.id);
  const [editing, setEditing] = useState<BookingLink | "new" | null>(null);

  const newButton = (
    <Button onClick={() => setEditing("new")}>
      <Plus className="size-4" aria-hidden />
      {t("bookingLinks.new")}
    </Button>
  );
  const names = new Map((services.data ?? []).map((service) => [service.id, service.name]));

  const columns: Column<BookingLink>[] = [
    {
      key: "name",
      header: t("bookingLinks.colLink"),
      cell: (link) => (
        <div className="min-w-0 max-w-md">
          <div className="truncate font-medium text-zinc-900">{link.name}</div>
          <div className="truncate text-[13px] text-zinc-500">
            /{business.slug}/{link.slug}
          </div>
        </div>
      ),
    },
    {
      key: "services",
      header: t("bookingLinks.colServices"),
      className: "hidden md:table-cell",
      cell: (link) => (
        <span className="text-zinc-600">
          {link.serviceIds
            .map((id) => names.get(id))
            .filter(Boolean)
            .join(", ")}
        </span>
      ),
    },
    {
      key: "status",
      header: t("bookingLinks.colStatus"),
      className: "whitespace-nowrap",
      cell: (link) =>
        link.active ? (
          <Badge tone="success">{t("bookingLinks.on")}</Badge>
        ) : (
          <Badge>{t("bookingLinks.off")}</Badge>
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title={t("bookingLinks.title")}
        subtitle={t("bookingLinks.subtitle")}
        actions={newButton}
      />
      {links.isPending ? (
        <Spinner />
      ) : links.isError ? (
        <Alert>{errorMessage(t, links.error)}</Alert>
      ) : links.data.length === 0 ? (
        <EmptyState
          icon={<Link2 className="size-5" aria-hidden />}
          title={t("bookingLinks.emptyTitle")}
          body={t("bookingLinks.emptyBody")}
          action={newButton}
        />
      ) : (
        <DataTable
          columns={columns}
          rows={links.data}
          rowKey={(link) => link.id}
          onRowClick={setEditing}
          searchPlaceholder={t("bookingLinks.search")}
          matches={(link, query) => `${link.name} ${link.slug}`.toLowerCase().includes(query)}
          empty={t("bookingLinks.emptyTitle")}
        />
      )}
      {editing && (
        <BookingLinkForm
          link={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}

function BookingLinkForm({ link, onClose }: { link: BookingLink | null; onClose: () => void }) {
  const { t } = useTranslation();
  const { business } = useBusinessContext();
  const services = useServices(business.id);
  const { save, remove } = useBookingLinkMutations(business.id);
  const confirm = useConfirm();
  const [name, setName] = useState(link?.name ?? "");
  const [slug, setSlug] = useState(link?.slug ?? "");
  // The address follows the name until it's edited by hand.
  const [slugEdited, setSlugEdited] = useState(Boolean(link));
  const [description, setDescription] = useState(link?.description ?? "");
  const [serviceIds, setServiceIds] = useState<string[]>(link?.serviceIds ?? []);
  const [active, setActive] = useState(link?.active ?? true);
  const [errors, setErrors] = useState<FieldErrors>({});

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const result = validate(bookingLinkSchema, { name, slug, description, serviceIds, active });
    setErrors(result.errors ?? {});
    if (!result.data) return;
    save.mutate(
      { id: link?.id, input: result.data },
      {
        onSuccess: () => {
          toast.success(link ? t("common.saved") : t("bookingLinks.created"));
          onClose();
        },
        onError: (error) => {
          setErrors(apiFieldErrors(error));
          toast.error(errorMessage(t, error));
        },
      },
    );
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={link ? t("bookingLinks.edit") : t("bookingLinks.new")}
      description={t("bookingLinks.formHint")}
      footer={
        <>
          {link && (
            <Button
              variant="ghost"
              className="mr-auto text-red-600 hover:bg-red-50 hover:text-red-700"
              loading={remove.isPending}
              onClick={async () => {
                const ok = await confirm({
                  title: t("bookingLinks.deleteTitle", { name: link.name }),
                  description: t("bookingLinks.deleteBody"),
                  confirmLabel: t("common.delete"),
                  tone: "danger",
                });
                if (ok) {
                  remove.mutate(link.id, {
                    onSuccess: () => {
                      toast.success(t("bookingLinks.deleted"));
                      onClose();
                    },
                  });
                }
              }}
            >
              {t("common.delete")}
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" form="booking-link-form" loading={save.isPending}>
            {t("common.save")}
          </Button>
        </>
      }
    >
      <form id="booking-link-form" onSubmit={submit} noValidate className="space-y-4">
        <Field
          label={t("bookingLinks.name")}
          hint={t("bookingLinks.nameHint")}
          error={fieldMessage(t, "name", errors.name)}
        >
          {(props) => (
            <Input
              {...props}
              placeholder={t("bookingLinks.namePlaceholder")}
              value={name}
              onChange={(event) => {
                setName(event.target.value);
                if (!slugEdited) setSlug(slugify(event.target.value));
              }}
            />
          )}
        </Field>
        <Field label={t("bookingLinks.address")} error={fieldMessage(t, "slug", errors.slug)}>
          {(props) => (
            <div className="flex">
              <span className="block max-w-[55%] shrink-0 truncate rounded-l-md border border-r-0 border-zinc-200 bg-zinc-50 px-3 text-sm leading-[34px] text-zinc-500">
                /{business.slug}/
              </span>
              <Input
                {...props}
                className="min-w-0 rounded-l-none"
                value={slug}
                onChange={(event) => {
                  setSlugEdited(true);
                  setSlug(event.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""));
                }}
              />
            </div>
          )}
        </Field>
        {link?.active && (
          <CopyField
            value={bookingLinkUrl(business.slug, link.slug)}
            label={t("bookingLinks.copy")}
          />
        )}
        <Field
          label={t("bookingLinks.description")}
          optional
          hint={t("bookingLinks.descriptionHint")}
        >
          {(props) => (
            <Textarea
              {...props}
              rows={2}
              className="min-h-0"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          )}
        </Field>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-zinc-800">
            {t("bookingLinks.services")}
          </legend>
          <Checklist
            label={t("bookingLinks.services")}
            value={serviceIds}
            onChange={setServiceIds}
            items={(services.data ?? [])
              .filter((service) => service.active || serviceIds.includes(service.id))
              .map((service) => ({
                id: service.id,
                label: service.name,
                detail: service.listed
                  ? formatDuration(service.durationMin)
                  : `${formatDuration(service.durationMin)} · ${t("services.onlyLinks")}`,
              }))}
          />
          {errors.serviceIds && (
            <p className="text-[13px] text-red-600">{t("bookingLinks.servicesRequired")}</p>
          )}
        </fieldset>
        <div className="border-t border-zinc-100 pt-4">
          <Switch
            checked={active}
            onChange={setActive}
            label={t("bookingLinks.active")}
            description={t("bookingLinks.activeHint")}
          />
        </div>
      </form>
    </Modal>
  );
}
