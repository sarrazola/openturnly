import Charts
import SwiftUI

/// How the business is doing: bookings, occupancy, new customers and sales for a
/// period, compared with the one before; the tips; and what each professional earned.
struct ReportsView: View {
    @Environment(Workspace.self) private var workspace
    @State private var period: ReportRules.Preset = .month
    @State private var tab: Tab = .summary
    @State private var measure: Measure = .bookings
    @State private var summary: ReportSummary?
    @State private var tips: TipsReport?
    @State private var commissions: [ReportCommission] = []
    @State private var error: Error?
    @State private var loading = false

    enum Tab: Hashable { case summary, tips, commissions }
    enum Measure: Hashable { case bookings, sales }

    private func title(_ preset: ReportRules.Preset) -> String {
        switch preset {
        case .today: return L.Common.today
        case .week: return L.Common.thisWeek
        case .month: return L.Common.thisMonth
        case .lastMonth: return L.Common.lastMonth
        case .last30: return L.Common.last30
        case .year: return L.Common.thisYear
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ReportRules.Preset.allCases) { option in
                            Button {
                                Haptics.selection()
                                period = option
                            } label: {
                                Text(title(option))
                                    .font(.subheadline.weight(.medium))
                                    .padding(.horizontal, 14)
                                    .frame(minHeight: 34)
                                    .foregroundStyle(period == option ? Theme.onBrand : Theme.ink)
                                    .background(period == option ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.fill), in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                Picker("", selection: $tab) {
                    Text(L.Reports.summary).tag(Tab.summary)
                    Text(L.Reports.tips).tag(Tab.tips)
                    Text(L.Reports.commissions).tag(Tab.commissions)
                }
                .pickerStyle(.segmented)

                if let error {
                    ErrorState(message: ErrorText.message(error)) { Task { await load() } }.card()
                } else {
                    switch tab {
                    case .summary:
                        if let summary { summaryContent(summary) } else { CardsSkeleton(count: 3) }
                    case .tips:
                        if let tips { tipsContent(tips) } else { CardsSkeleton(count: 2) }
                    case .commissions:
                        commissionsContent
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.canvas)
        .navigationTitle(L.Reports.title)
        .refreshable { await load() }
        .task(id: period) { await load() }
    }

    // MARK: Summary

    @ViewBuilder
    private func summaryContent(_ summary: ReportSummary) -> some View {
        let current = summary.current
        let before = summary.before
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            KpiTile(label: L.Reports.bookings, value: "\(current.bookings)",
                    change: before.flatMap { ReportRules.change(Double(current.bookings), Double($0.bookings)) }.map(Fmt.change),
                    up: before.map { current.bookings >= $0.bookings }, before: before.map { "\($0.bookings)" })
            KpiTile(label: L.Reports.occupancy, value: current.occupancy.map { Fmt.percent($0) } ?? "—",
                    change: ReportRules.points(current.occupancy, before?.occupancy).map(points),
                    up: ReportRules.points(current.occupancy, before?.occupancy).map { $0 >= 0 },
                    before: before?.occupancy.map { Fmt.percent($0) })
            KpiTile(label: L.Reports.newCustomers, value: "\(current.newCustomers)",
                    change: before.flatMap { ReportRules.change(Double(current.newCustomers), Double($0.newCustomers)) }.map(Fmt.change),
                    up: before.map { current.newCustomers >= $0.newCustomers }, before: before.map { "\($0.newCustomers)" })
            KpiTile(label: L.Reports.billed, value: workspace.money(current.billed),
                    change: before.flatMap { ReportRules.change(Double(current.billed), Double($0.billed)) }.map(Fmt.change),
                    up: before.map { current.billed >= $0.billed }, before: before.map { workspace.money($0.billed) })
        }

        if let series = summary.series {
            chartCard(series)
        }

        if let origin = summary.origin, origin.values.reduce(0, +) > 0 {
            let total = Double(origin.values.reduce(0, +))
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: L.Reports.origin)
                VStack(spacing: 10) {
                    ForEach(origin.sorted { $0.value > $1.value }, id: \.key) { entry in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(originName(entry.key)).font(.subheadline)
                                Spacer()
                                Text("\(entry.value) · \(Fmt.percent(Double(entry.value) / total))")
                                    .font(.subheadline.weight(.semibold)).monospacedDigit()
                            }
                            ProgressBar(value: Double(entry.value) / total)
                        }
                    }
                }
                .card(padding: 14)
            }
        }

        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Reports.topSold)
            if let top = summary.topServices, !top.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(top.enumerated()), id: \.offset) { index, item in
                        if index > 0 { Divider() }
                        HStack(spacing: 10) {
                            Text("\(index + 1)").font(.subheadline.weight(.bold)).monospacedDigit().foregroundStyle(Theme.brand).frame(width: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name).font(.subheadline.weight(.medium))
                                Text(L.Reports.soldCount(item.quantity)).font(.caption).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            Text(workspace.money(item.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                        }
                        .padding(.vertical, 8)
                    }
                }
                .card(padding: 14)
            } else {
                Text(L.Reports.nothing).font(.subheadline).foregroundStyle(Theme.muted).card(padding: 14)
            }
        }

        if let people = summary.byProfessional, !people.isEmpty {
            let most = Double(people.map(\.bookings).max() ?? 1)
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: L.Reports.byProfessional)
                VStack(spacing: 12) {
                    ForEach(people, id: \.name) { person in
                        let id = workspace.professionals.first { $0.name == person.name }?.id
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(person.name.isEmpty ? L.Reports.noProfessional : person.name).font(.subheadline)
                                Spacer()
                                Text(L.Agenda.count(person.bookings)).font(.subheadline.weight(.semibold)).monospacedDigit()
                            }
                            ProgressBar(value: most > 0 ? Double(person.bookings) / most : 0, color: workspace.color(for: id))
                        }
                    }
                }
                .card(padding: 14)
            }
        }

        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Reports.today, trailing: "\(summary.today?.count ?? 0)")
            if let today = summary.today, !today.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(today.enumerated()), id: \.element.id) { index, item in
                        if index > 0 { Divider() }
                        HStack(spacing: 12) {
                            Text(item.time).font(.subheadline.weight(.bold)).fontDesign(.rounded).monospacedDigit().frame(width: 48, alignment: .leading)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.customer ?? "—").font(.subheadline.weight(.medium))
                                Text([item.service, item.professional].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            if item.paid == true { Chip(text: L.Reports.paid, tone: .info, symbol: "checkmark") }
                        }
                        .padding(.vertical, 8)
                    }
                }
                .card(padding: 14)
            } else {
                Text(L.Reports.noneToday).font(.subheadline).foregroundStyle(Theme.muted).card(padding: 14)
            }
        }
    }

    private func points(_ value: Int) -> String {
        let sign = value > 0 ? "+" : value < 0 ? "\u{2212}" : ""
        return "\(sign)\(abs(value)) \(L.Reports.points)"
    }

    /// Bookings or sales per day, week or month, with the period's total and its first and last dates.
    private func chartCard(_ series: ReportSeries) -> some View {
        let points = series.points
        let total = points.reduce(0) { $0 + (measure == .bookings ? $1.bookings : $1.billed) }
        return VStack(alignment: .leading, spacing: 12) {
            Picker("", selection: $measure) {
                Text(L.Reports.bookings).tag(Measure.bookings)
                Text(L.Reports.billed).tag(Measure.sales)
            }
            .pickerStyle(.segmented)
            .fixedSize()
            Text(measure == .bookings ? "\(total)" : workspace.money(total))
                .font(.title2.weight(.bold)).fontDesign(.rounded).monospacedDigit().foregroundStyle(Theme.ink)
            if points.isEmpty {
                Text(L.Reports.nothing).font(.subheadline).foregroundStyle(Theme.muted)
            } else {
                Chart(points, id: \.start) { point in
                    BarMark(x: .value(L.Common.date, point.start),
                            y: .value(L.Reports.bookings, measure == .bookings ? point.bookings : point.billed))
                        .foregroundStyle(Theme.brand.gradient)
                        .cornerRadius(3)
                }
                .chartXAxis(.hidden)
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine()
                        AxisValueLabel {
                            if let number = value.as(Int.self) {
                                Text(measure == .bookings ? "\(number)" : workspace.money(number)).font(.caption2)
                            }
                        }
                    }
                }
                .frame(height: 170)
                HStack {
                    Text(LocalDate(points.first?.start ?? "").map(Fmt.dayShort) ?? "")
                    Spacer()
                    Text(LocalDate(points.last?.start ?? "").map(Fmt.dayShort) ?? "")
                }
                .font(.caption2)
                .foregroundStyle(Theme.muted)
            }
        }
        .card(padding: 14)
    }

    // MARK: Tips

    @ViewBuilder
    private func tipsContent(_ report: TipsReport) -> some View {
        if report.totals.isEmpty {
            EmptyState(symbol: "heart", title: L.Reports.noTips).card()
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text(L.Reports.tips).font(.subheadline.weight(.medium)).foregroundStyle(Theme.muted)
                Text(workspace.money(report.totals.reduce(0) { $0 + $1.amount }))
                    .font(.system(.title, design: .rounded).weight(.bold)).monospacedDigit().foregroundStyle(Theme.brand)
            }
            .card(padding: 14)
            VStack(spacing: 0) {
                ForEach(Array(report.totals.enumerated()), id: \.offset) { index, total in
                    if index > 0 { Divider() }
                    InfoRow(label: "\(total.name.isEmpty ? L.Reports.noProfessional : total.name) · \(total.count)", value: workspace.money(total.amount))
                }
            }
            .card(padding: 14)
            VStack(spacing: 0) {
                ForEach(Array(report.tips.enumerated()), id: \.element.id) { index, tip in
                    if index > 0 { Divider() }
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tip.resourceName ?? "—").font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                            Text([Fmt.dayMedium(workspace.clock.localDate(of: tip.createdAt)), tip.sale.map { L.Checkout.paidIn($0.number) }, tip.customer]
                                .compactMap { $0 }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        Text(workspace.money(tip.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                    }
                    .padding(.vertical, 8)
                }
            }
            .card(padding: 14)
        }
    }

    private func originName(_ key: String) -> String {
        switch key {
        case "online": return L.Reports.originOnline
        case "admin": return L.Reports.originAdmin
        case "agent": return L.Reports.originAgent
        default: return key
        }
    }

    // MARK: Commissions

    @ViewBuilder
    private var commissionsContent: some View {
        if loading && commissions.isEmpty {
            CardsSkeleton(count: 2)
        } else if commissions.isEmpty {
            EmptyState(symbol: "person.2", title: L.Reports.noProfessionals).card()
        } else {
            let total = commissions.reduce(0) { $0 + $1.total }
            StatTile(label: L.Reports.toPay, value: workspace.money(total), symbol: "banknote")
            ForEach(commissions) { person in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 10) {
                        Avatar(name: person.name, colorHex: workspace.colorHex(for: person.id), size: 36)
                        Text(person.name).font(.headline)
                        Spacer()
                        Text(workspace.money(person.total)).font(.headline).monospacedDigit()
                    }
                    Divider()
                    commissionRow(L.Reports.servicesCount(person.servicesCount), person.serviceCommission,
                                  rate: person.serviceRate, base: person.services)
                    commissionRow(L.Reports.productsCount(person.productsCount), person.productCommission,
                                  rate: person.productRate, base: person.products)
                    InfoRow(label: L.Reports.tips, value: workspace.money(person.tips))
                }
                .card(padding: 14)
            }
        }
    }

    private func commissionRow(_ label: String, _ amount: Int, rate: Double?, base: Int) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.subheadline).foregroundStyle(Theme.muted)
                Text(L.Reports.rateOf((rate ?? 0).formatted(.number.precision(.fractionLength(0...1))), workspace.money(base)))
                    .font(.caption).foregroundStyle(Theme.subtle)
            }
            Spacer()
            Text(workspace.money(amount)).font(.subheadline.weight(.medium)).monospacedDigit()
        }
        .padding(.vertical, 6)
    }

    // MARK: Loading

    private func load() async {
        let (from, to) = period.range(today: workspace.clock.today())
        loading = true
        defer { loading = false }
        do {
            async let s = workspace.api.reportSummary(workspace.id, from: from, to: to)
            async let t = workspace.api.reportTips(workspace.id, from: from, to: to)
            async let c = workspace.api.reportCommissions(workspace.id, from: from, to: to)
            let (foundSummary, foundTips, foundCommissions) = try await (s, t, c)
            summary = foundSummary
            tips = foundTips
            commissions = foundCommissions
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
}

/// A key number with its change against the period before.
private struct KpiTile: View {
    let label: String
    let value: String
    /// "+12 %", "−5 %", "+3 pts"; nil when there is nothing to compare.
    let change: String?
    let up: Bool?
    let before: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption.weight(.medium)).foregroundStyle(Theme.muted).lineLimit(1)
            Text(value)
                .font(.title3.weight(.bold))
                .fontDesign(.rounded)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let change, let before {
                HStack(spacing: 4) {
                    Image(systemName: up == false ? "arrow.down.right" : "arrow.up.right").font(.caption2.weight(.bold))
                    Text(change).font(.caption.weight(.semibold)).monospacedDigit()
                }
                .foregroundStyle(up == false ? Theme.danger : Theme.success)
                Text(L.Reports.vs(before)).font(.caption).foregroundStyle(Theme.subtle).lineLimit(1).minimumScaleFactor(0.8)
            } else {
                Text(L.Reports.noComparison).font(.caption).foregroundStyle(Theme.subtle).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(padding: 14)
        .accessibilityElement(children: .combine)
    }
}

/// A slim horizontal bar.
struct ProgressBar: View {
    let value: Double
    var color: Color = Theme.brand

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.fill)
                Capsule().fill(color.gradient).frame(width: max(6, geometry.size.width * min(1, max(0, value))))
            }
        }
        .frame(height: 7)
        .accessibilityHidden(true)
    }
}
