import Charts
import SwiftUI

/// How the business is doing: bookings, occupancy, new customers and sales for a
/// period, compared with the one before; and what each professional earned.
struct ReportsView: View {
    @Environment(Workspace.self) private var workspace
    @State private var period: Period = .month
    @State private var tab: Tab = .summary
    @State private var summary: ReportSummary?
    @State private var commissions: [ReportCommission] = []
    @State private var error: Error?
    @State private var loading = false

    enum Tab: Hashable { case summary, commissions }

    enum Period: String, CaseIterable, Identifiable {
        case today, week, month, lastMonth, last30
        var id: String { rawValue }

        var title: String {
            switch self {
            case .today: return L.Common.today
            case .week: return L.Common.thisWeek
            case .month: return L.Common.thisMonth
            case .lastMonth: return L.Common.lastMonth
            case .last30: return L.Common.last30
            }
        }

        func range(today: LocalDate) -> (LocalDate, LocalDate) {
            switch self {
            case .today: return (today, today)
            case .week: return (today.startOfWeek(firstWeekday: 1), today.startOfWeek(firstWeekday: 1).adding(days: 6))
            case .month: return (today.firstOfMonth, today.lastOfMonth)
            case .lastMonth:
                let previous = today.firstOfMonth.adding(days: -1)
                return (previous.firstOfMonth, previous.lastOfMonth)
            case .last30: return (today.adding(days: -29), today)
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Period.allCases) { option in
                            Button {
                                Haptics.selection()
                                period = option
                            } label: {
                                Text(option.title)
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
                    Text(L.Reports.commissions).tag(Tab.commissions)
                }
                .pickerStyle(.segmented)

                if let error {
                    ErrorState(message: ErrorText.message(error)) { Task { await load() } }.card()
                } else if tab == .summary {
                    if let summary {
                        summaryContent(summary)
                    } else {
                        CardsSkeleton(count: 3)
                    }
                } else {
                    commissionsContent
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
            StatTile(label: L.Reports.bookings, value: "\(current.bookings)", detail: before.map { L.Reports.vs("\($0.bookings)") },
                     symbol: "calendar")
            StatTile(label: L.Reports.occupancy, value: current.occupancy.map { Fmt.percent($0) } ?? "—",
                     detail: before?.occupancy.map { L.Reports.vs(Fmt.percent($0)) }, symbol: "chart.pie", tint: Color(hex: "#7c3aed"))
            StatTile(label: L.Reports.newCustomers, value: "\(current.newCustomers)", detail: before.map { L.Reports.vs("\($0.newCustomers)") },
                     symbol: "person.badge.plus", tint: Color(hex: "#db2777"))
            StatTile(label: L.Reports.billed, value: workspace.money(current.billed), detail: before.map { L.Reports.vs(workspace.money($0.billed)) },
                     symbol: "banknote", tint: Color(hex: "#0284c7"))
        }

        if let series = summary.series, series.points.count > 1 {
            VStack(alignment: .leading, spacing: 10) {
                SectionTitle(text: L.Reports.chart(series.unit))
                Chart(series.points, id: \.start) { point in
                    BarMark(x: .value(L.Common.date, LocalDate(point.start)?.noonUTC ?? Date(), unit: series.unit == "month" ? .month : series.unit == "week" ? .weekOfYear : .day),
                            y: .value(L.Reports.bookings, point.bookings))
                        .foregroundStyle(Theme.brand.gradient)
                        .cornerRadius(4)
                }
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 5)) { _ in
                        AxisGridLine()
                        AxisValueLabel(format: .dateTime.day().month(.abbreviated), centered: false)
                    }
                }
                .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                .frame(height: 180)
                .card(padding: 14)
            }
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

        if let top = summary.topServices, !top.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                SectionTitle(text: L.Reports.topSold)
                VStack(spacing: 0) {
                    ForEach(Array(top.enumerated()), id: \.offset) { index, item in
                        if index > 0 { Divider() }
                        HStack {
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
            EmptyState(symbol: "person.2", title: L.Reports.nothing).card()
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
                    commissionRow("\(L.Professionals.serviceCommission) (\(person.servicesCount))", person.serviceCommission,
                                  rate: person.serviceRate, base: person.services)
                    commissionRow("\(L.Professionals.productCommission) (\(person.productsCount))", person.productCommission,
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
                if let rate {
                    Text(L.Reports.rateOf(rate.formatted(.number.precision(.fractionLength(0...1))), workspace.money(base)))
                        .font(.caption).foregroundStyle(Theme.subtle)
                }
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
            async let c = workspace.api.reportCommissions(workspace.id, from: from, to: to)
            let (foundSummary, foundCommissions) = try await (s, c)
            summary = foundSummary
            commissions = foundCommissions
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
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
