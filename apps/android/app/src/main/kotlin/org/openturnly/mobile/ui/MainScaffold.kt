@file:OptIn(ExperimentalMaterial3Api::class)

package org.openturnly.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.openturnly.mobile.AppModel
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.ui.agenda.AgendaScreen
import org.openturnly.mobile.ui.agenda.AppointmentDetailScreen
import org.openturnly.mobile.ui.agenda.NewAppointmentScreen
import org.openturnly.mobile.ui.agenda.RescheduleScreen
import org.openturnly.mobile.ui.customers.CustomerDetailScreen
import org.openturnly.mobile.ui.customers.CustomerFormScreen
import org.openturnly.mobile.ui.customers.CustomersScreen
import org.openturnly.mobile.ui.more.AccountScreen
import org.openturnly.mobile.ui.more.BusinessScreen
import org.openturnly.mobile.ui.more.CashScreen
import org.openturnly.mobile.ui.more.GiftCardsScreen
import org.openturnly.mobile.ui.more.HoursScreen
import org.openturnly.mobile.ui.more.InventoryScreen
import org.openturnly.mobile.ui.more.MoreScreen
import org.openturnly.mobile.ui.more.ProductDetailScreen
import org.openturnly.mobile.ui.more.ProfessionalDetailScreen
import org.openturnly.mobile.ui.more.ProfessionalsScreen
import org.openturnly.mobile.ui.more.ReportsScreen
import org.openturnly.mobile.ui.more.ServiceFormScreen
import org.openturnly.mobile.ui.more.ServicesScreen
import org.openturnly.mobile.ui.more.TeamScreen
import org.openturnly.mobile.ui.sales.CheckoutScreen
import org.openturnly.mobile.ui.sales.SaleDetailScreen
import org.openturnly.mobile.ui.sales.SalesScreen

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The signed-in frame: four tabs (Ventas only for admins), each with its own
 * stack of pushed screens. The navigation bar steps aside on pushed screens,
 * which carry their own bottom actions.
 */
@Composable
fun MainScaffold(model: AppModel) {
    val nav = model.nav
    val holder = rememberSaveableStateHolder()
    val items = buildList {
        add(TabItem(Tab.AGENDA, Copy.nav.agenda, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth))
        add(TabItem(Tab.CUSTOMERS, Copy.nav.customers, Icons.Outlined.People, Icons.Filled.People))
        if (model.isAdmin) add(TabItem(Tab.SALES, Copy.nav.sales, Icons.Outlined.PointOfSale, Icons.Filled.PointOfSale))
        add(TabItem(Tab.MORE, Copy.nav.more, Icons.Outlined.Menu, Icons.Filled.Menu))
    }
    if (nav.tab == Tab.SALES && !model.isAdmin) nav.select(Tab.AGENDA)

    BackHandler(enabled = nav.top != null) { nav.pop() }
    BackHandler(enabled = nav.top == null && nav.tab != Tab.AGENDA) { nav.select(Tab.AGENDA) }

    // Coming back to the app refreshes the day, and the clock keeps statuses honest.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START && model.business != null) model.agenda.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); model.agenda.now = java.time.Instant.now() }
    }

    // Whatever had keyboard focus on the sign-in screen lets go of it here.
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(force = true) }

    val top = nav.top
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(model.snackbar, Modifier.navigationBarsPadding().imePadding().padding(bottom = if (top != null) 84.dp else 0.dp)) },
        bottomBar = {
            AnimatedVisibility(top == null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    items.forEach { item ->
                        val selected = nav.tab == item.tab
                        NavigationBarItem(
                            selected = selected, onClick = { nav.select(item.tab) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = Triple(nav.tab, top, nav.forward),
            transitionSpec = {
                val (fromTab, fromTop, _) = initialState
                val (toTab, toTop, forward) = targetState
                if (fromTab != toTab && (fromTop == null && toTop == null)) {
                    (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.98f)) togetherWith fadeOut(tween(120))
                } else if (forward) {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            contentKey = { (tab, entry, _) -> entry?.id ?: "root-$tab" },
            label = "screens",
        ) { (tab, entry, _) ->
            if (entry == null) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    holder.SaveableStateProvider("root-$tab") { RootScreen(model, tab) }
                }
            } else {
                holder.SaveableStateProvider(entry.id) { RouteScreen(model, entry.route) }
            }
        }
    }

    if (model.showBusinessPicker) BusinessSwitcherSheet(model)
}

@Composable
private fun RootScreen(model: AppModel, tab: Tab) {
    when (tab) {
        Tab.AGENDA -> AgendaScreen(model)
        Tab.CUSTOMERS -> CustomersScreen(model)
        Tab.SALES -> SalesScreen(model)
        Tab.MORE -> MoreScreen(model)
    }
}

@Composable
private fun RouteScreen(model: AppModel, route: Route) {
    when (route) {
        is Route.AppointmentDetail -> AppointmentDetailScreen(model, route.appointment)
        is Route.NewAppointment -> NewAppointmentScreen(model, route.customer, route.date)
        is Route.Reschedule -> RescheduleScreen(model, route.appointment)
        is Route.Checkout -> CheckoutScreen(model, route.appointment)
        is Route.CustomerDetail -> CustomerDetailScreen(model, route.customer)
        is Route.CustomerForm -> CustomerFormScreen(model, route.customer)
        is Route.SaleDetail -> SaleDetailScreen(model, route.saleId, route.sale)
        Route.Services -> ServicesScreen(model)
        is Route.ServiceForm -> ServiceFormScreen(model, route.service)
        Route.Professionals -> ProfessionalsScreen(model)
        is Route.ProfessionalDetail -> ProfessionalDetailScreen(model, route.professional)
        Route.Hours -> HoursScreen(model)
        Route.Business -> BusinessScreen(model)
        Route.Reports -> ReportsScreen(model)
        Route.Account -> AccountScreen(model)
        Route.Cash -> CashScreen(model)
        Route.Inventory -> InventoryScreen(model)
        is Route.ProductDetail -> ProductDetailScreen(model, route.product)
        Route.GiftCards -> GiftCardsScreen(model)
        Route.Team -> TeamScreen(model)
    }
}

/** The business switcher, from the agenda's header or from "More". */
@Composable
fun BusinessSwitcherSheet(model: AppModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { model.refreshBusinesses() }
    ModalBottomSheet(onDismissRequest = { model.showBusinessPicker = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(Copy.nav.switchBusiness, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            model.businesses.forEach { business ->
                BusinessRow(model, business, selected = business.id == model.business?.id) { model.openBusiness(business) }
            }
        }
    }
}

/** Standard padding for root screens' scrolling content, leaving room for a FAB. */
val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
