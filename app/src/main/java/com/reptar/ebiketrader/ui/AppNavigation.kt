package com.reptar.ebiketrader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reptar.ebiketrader.data.AuthViewModel
import com.reptar.ebiketrader.data.InboxViewModel
import com.reptar.ebiketrader.data.ListingFormViewModel
import com.reptar.ebiketrader.data.UserDataViewModel
import com.reptar.ebiketrader.push.PushRegistrar
import com.reptar.ebiketrader.ui.auth.AccountScreen
import com.reptar.ebiketrader.ui.auth.SignInScreen
import com.reptar.ebiketrader.ui.browse.BrowseScreen
import com.reptar.ebiketrader.ui.detail.ListingDetailScreen
import com.reptar.ebiketrader.ui.messages.InboxScreen
import com.reptar.ebiketrader.ui.messages.ThreadScreen
import com.reptar.ebiketrader.ui.lists.MyListingsScreen
import com.reptar.ebiketrader.ui.lists.SavedScreen
import com.reptar.ebiketrader.ui.sell.ListingFormScreen
import com.reptar.ebiketrader.ui.sell.PhotoOrganizerScreen

private const val ROUTE_BROWSE = "browse"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_SIGN_IN = "signin"
private const val ROUTE_INBOX = "messages"
private const val ARG_CONVERSATION_ID = "conversationId"
private const val ROUTE_THREAD = "thread/{$ARG_CONVERSATION_ID}"
// One route for both posting and editing: the optional listingId decides
// which, and it keeps a single ListingFormViewModel to share with the
// photo organizer rather than two near-identical flows.
private const val ROUTE_FORM = "form?listingId={listingId}"
private const val ROUTE_FORM_PHOTOS = "form/photos"
private const val ROUTE_MY_LISTINGS = "my-listings"
private const val ROUTE_SAVED = "saved"
private const val ARG_LISTING_ID = "listingId"
private const val ROUTE_LISTING = "listing/{$ARG_LISTING_ID}"

@Composable
fun AppNavigation(
    deepLinkConversationId: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Hoisted here so every screen shares one session rather than each getting
    // its own auth listener and its own presence heartbeat.
    val authViewModel: AuthViewModel = viewModel()
    val userDataViewModel: UserDataViewModel = viewModel()
    val inboxViewModel: InboxViewModel = viewModel()
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val inbox by inboxViewModel.state.collectAsStateWithLifecycle()
    val userData by userDataViewModel.state.collectAsStateWithLifecycle()
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route

    // Same rule as the inbox list: a message you didn't send, in a thread you
    // haven't opened since, from someone you haven't blocked.
    val uid = auth.uid
    val unread = if (uid == null) 0 else inbox.conversations.count { conversation ->
        val other =
            if (conversation.buyerId == uid) conversation.sellerId else conversation.buyerId
        other !in userData.blockedIds && conversation.isUnread(uid)
    }

    // The bar gets out of the way while you are scrolling down a grid and
    // comes straight back on the first upward flick. Nested scroll carries
    // the deltas up from whatever LazyGrid or LazyColumn is on screen, so no
    // individual screen has to know the bar exists.
    var barVisible by remember { mutableStateOf(true) }
    val barScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // A dead zone: fling settle and the rubber-band at the ends of
                // a list emit tiny deltas in both directions, and reacting to
                // those makes the bar flicker.
                if (available.y < -3f) barVisible = false
                if (available.y > 3f) barVisible = true
                return Offset.Zero
            }
        }
    }

    // Arriving on a tab always shows the bar — otherwise you switch to Saved
    // mid-scroll and land on a screen with no visible way off it.
    LaunchedEffect(route) { barVisible = true }

    // Favourites and the block list follow whoever is signed in, and have
    // to be dropped on sign-out so the next person doesn't inherit them.
    val context = LocalContext.current

    PresenceHeartbeat(authViewModel, auth.uid)

    LaunchedEffect(auth.uid) {
        userDataViewModel.bind(auth.uid)
        inboxViewModel.bind(auth.uid)
        // Re-registered on every launch, not just on first grant: a token
        // can rotate while the app is closed, and onNewToken has nowhere to
        // put it if nobody was signed in at the time.
        auth.uid?.let { PushRegistrar.register(context, it) }
    }

    // A tapped notification carries the conversation id; navigating here
    // rather than in the Activity means the back stack is built properly —
    // backing out of the thread lands on Browse, not on nothing.
    LaunchedEffect(deepLinkConversationId, auth.isSignedIn) {
        val id = deepLinkConversationId ?: return@LaunchedEffect
        if (!auth.isSignedIn) return@LaunchedEffect
        navController.navigate("thread/$id")
        onDeepLinkHandled()
    }

    // contentWindowInsets is deliberately zero: every screen already has its
    // own Scaffold handling the status bar, and letting this one add the top
    // inset too would push each TopAppBar out from under the status bar and
    // leave a bare strip above it. All this Scaffold contributes is the height
    // of the bar, which the NavHost then consumes so the inner Scaffolds don't
    // add the navigation-bar inset a second time.
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Leaving a tab drops the bar outright rather than animating it,
            // so opening a listing still feels like one movement. Only the
            // scroll-hide animates.
            if (route in TAB_ROUTES) {
                // Slide and shrink together: the bar rides down past the
                // bottom edge while the slot it occupied closes up, so the
                // grid grows into the space instead of the content jumping
                // the moment the slide ends.
                AnimatedVisibility(
                    visible = barVisible,
                    enter = slideInVertically { it } +
                        expandVertically(expandFrom = Alignment.Top),
                    exit = slideOutVertically { it } +
                        shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    AppBottomBar(
                        current = route,
                        unread = unread,
                        onSelect = { tab ->
                            when (tab) {
                                // Sell and Messages are gated: signed out,
                                // both land on sign-in instead, the same as
                                // the old toolbar buttons did.
                                AppTab.Sell ->
                                    if (auth.isSignedIn) navController.navigate("form")
                                    else navController.navigate(ROUTE_SIGN_IN)

                                AppTab.Messages ->
                                    if (auth.isSignedIn) navController.switchTab(ROUTE_INBOX)
                                    else navController.navigate(ROUTE_SIGN_IN)

                                else -> tab.route?.let { navController.switchTab(it) }
                            }
                        },
                    )
                }
            }
        },
    ) { barInsets ->
        // Wrapping the whole graph means body copy — a seller's description, a
        // serial number, a phone number — is drag-selectable everywhere without
        // each screen opting in. iOS needed a UITextView wrapper for the same
        // thing; on Android it is one composable.
        SelectionContainer {
            NavHost(
                navController = navController,
                startDestination = ROUTE_BROWSE,
                modifier = Modifier.padding(barInsets).consumeWindowInsets(barInsets),
            ) {
                composable(ROUTE_BROWSE) {
                    BrowseScreen(
                        userDataViewModel = userDataViewModel,
                        onOpenListing = { listing -> navController.navigate("listing/${listing.id}") },
                    )
                }

                composable(
                    route = ROUTE_LISTING,
                    arguments = listOf(navArgument(ARG_LISTING_ID) { type = NavType.StringType }),
                ) { entry ->
                    // Passed by id rather than by handing the whole Listing across
                    // the back stack: the detail screen re-subscribes to the
                    // document anyway, so it stays live through a price edit or a
                    // mark-as-sold made from another device.
                    ListingDetailScreen(
                        listingId = entry.arguments?.getString(ARG_LISTING_ID).orEmpty(),
                        authViewModel = authViewModel,
                        userDataViewModel = userDataViewModel,
                        onBack = navController::popBackStack,
                        onSignIn = { navController.navigate(ROUTE_SIGN_IN) },
                        onOpenThread = { id -> navController.navigate("thread/$id") },
                        onEdit = { id -> navController.navigate("form?listingId=$id") },
                    )
                }

                composable(ROUTE_ACCOUNT) {
                    AccountScreen(
                        authViewModel = authViewModel,
                        userDataViewModel = userDataViewModel,
                        onSignIn = { navController.navigate(ROUTE_SIGN_IN) },
                        onMyListings = { navController.navigate(ROUTE_MY_LISTINGS) },
                        // Saved has its own tab now, so this switches to it rather
                        // than stacking a second copy on top of Account.
                        onSaved = { navController.switchTab(ROUTE_SAVED) },
                    )
                }

                composable(ROUTE_INBOX) {
                    InboxScreen(
                        uid = auth.uid.orEmpty(),
                        inboxViewModel = inboxViewModel,
                        userDataViewModel = userDataViewModel,
                        onOpenThread = { navController.navigate("thread/${it.id}") },
                    )
                }

                composable(
                    route = ROUTE_THREAD,
                    arguments = listOf(navArgument(ARG_CONVERSATION_ID) { type = NavType.StringType }),
                ) { entry ->
                    ThreadScreen(
                        conversationId = entry.arguments?.getString(ARG_CONVERSATION_ID).orEmpty(),
                        uid = auth.uid.orEmpty(),
                        onBack = navController::popBackStack,
                    )
                }

                composable(
                    route = ROUTE_FORM,
                    arguments = listOf(
                        navArgument("listingId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { entry ->
                    val listingId = entry.arguments?.getString("listingId")
                    val formViewModel: ListingFormViewModel =
                        viewModel(entry) { ListingFormViewModel(listingId) }

                    ListingFormScreen(
                        viewModel = formViewModel,
                        sellerId = auth.uid.orEmpty(),
                        sellerName = auth.displayName,
                        onBack = navController::popBackStack,
                        onOpenPhotos = { navController.navigate(ROUTE_FORM_PHOTOS) },
                        onSaved = { id ->
                            navController.popBackStack()
                            // Editing returns you to the detail screen you came
                            // from; posting has nowhere to go back to, so it opens
                            // the new listing. Without the distinction an edit
                            // stacks a second copy of the same screen.
                            if (listingId == null) navController.navigate("listing/$id")
                        },
                    )
                }

                composable(ROUTE_FORM_PHOTOS) {
                    // Scoped to the form's back stack entry so both screens share
                    // one ViewModel, and therefore one photo plan.
                    val formEntry = remember(it) { navController.getBackStackEntry(ROUTE_FORM) }
                    val formViewModel: ListingFormViewModel = viewModel(formEntry)
                    val formState by formViewModel.state.collectAsStateWithLifecycle()

                    PhotoOrganizerScreen(
                        plan = formState.plan,
                        onPlanChange = formViewModel::setPlan,
                        onBack = navController::popBackStack,
                    )
                }

                composable(ROUTE_MY_LISTINGS) {
                    MyListingsScreen(
                        uid = auth.uid.orEmpty(),
                        onOpenListing = { navController.navigate("listing/${it.id}") },
                        onBack = navController::popBackStack,
                    )
                }

                composable(ROUTE_SAVED) {
                    SavedScreen(
                        userDataViewModel = userDataViewModel,
                        onOpenListing = { navController.navigate("listing/${it.id}") },
                    )
                }

                composable(ROUTE_SIGN_IN) {
                    SignInScreen(
                        authViewModel = authViewModel,
                        onBack = navController::popBackStack,
                        // popBackStack rather than navigate, so signing in returns
                        // you to whatever you were doing instead of stacking a
                        // second copy of the account screen.
                        onSignedIn = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** The five destinations, in the same order as the iOS tab bar. Sell has no
 * route because it is an action, not a place: it pushes the form full-screen
 * and the bar goes with it, so there is nothing for it to stay selected on. */
private enum class AppTab(val label: String, val icon: ImageVector, val route: String?) {
    Browse("Browse", Icons.Default.DirectionsBike, ROUTE_BROWSE),
    Saved("Saved", Icons.Default.FavoriteBorder, ROUTE_SAVED),
    Sell("Sell", Icons.Default.AddCircleOutline, null),
    Messages("Messages", Icons.AutoMirrored.Filled.Chat, ROUTE_INBOX),
    Account("Account", Icons.Default.AccountCircle, ROUTE_ACCOUNT),
}

private val TAB_ROUTES = AppTab.entries.mapNotNull { it.route }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBottomBar(current: String?, unread: Int, onSelect: (AppTab) -> Unit) {
    NavigationBar {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab.route != null && tab.route == current,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == AppTab.Messages && unread > 0) {
                        BadgedBox(badge = { Badge { Text(unread.toString()) } }) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = { Text(tab.label) },
            )
        }
    }
}

/** Switching tabs replaces the tab rather than stacking on it, and keeps each
 * tab's scroll position, so Back from any tab leaves the app instead of
 * walking you through every tab you visited. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
