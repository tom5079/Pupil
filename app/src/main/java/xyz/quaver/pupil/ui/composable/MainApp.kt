package xyz.quaver.pupil.ui.composable

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.activity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.viewmodel.SearchState

@Composable
fun MainApp(
    windowSize: WindowSizeClass,
    displayFeatures: List<DisplayFeature>,
    uiState: SearchState,
    navController: NavHostController,
    openGalleryDetails: (GalleryInfo) -> Unit,
    closeGalleryDetails: () -> Unit,
    onQueryChange: (SearchQuery?) -> Unit,
    loadSearchResult: (IntRange) -> Unit,
) {
    val navigationType: NavigationType
    val contentType: ContentType

    val foldingFeature: FoldingFeature? = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    val foldingDevicePosture = when {
        isBookPosture(foldingFeature) -> DevicePosture.BookPosture(foldingFeature.bounds)
        isSeparating(foldingFeature) -> DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)
        else -> DevicePosture.NormalPosture
    }

    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            navigationType = NavigationType.BOTTOM_NAVIGATION
            contentType = ContentType.SINGLE_PANE
        }
        WindowWidthSizeClass.Medium -> {
            navigationType = NavigationType.NAVIGATION_RAIL
            contentType = if (foldingDevicePosture != DevicePosture.NormalPosture) {
                ContentType.DUAL_PANE
            } else {
                ContentType.SINGLE_PANE
            }
        }
        WindowWidthSizeClass.Expanded -> {
            navigationType = if (foldingDevicePosture is DevicePosture.BookPosture) {
                NavigationType.NAVIGATION_RAIL
            } else {
                NavigationType.PERMANENT_NAVIGATION_DRAWER
            }
            contentType = ContentType.DUAL_PANE
        }
        else -> {
            navigationType = NavigationType.BOTTOM_NAVIGATION
            contentType = ContentType.SINGLE_PANE
        }
    }

    val navigationContentPosition = when (windowSize.heightSizeClass) {
        WindowHeightSizeClass.Compact -> NavigationContentPosition.TOP
        WindowHeightSizeClass.Medium,
        WindowHeightSizeClass.Expanded -> NavigationContentPosition.CENTER
        else -> NavigationContentPosition.TOP
    }

    MainNavigationWrapper(
        navigationType,
        contentType,
        displayFeatures,
        navigationContentPosition,
        uiState,
        navController,
        openGalleryDetails = openGalleryDetails,
        closeGalleryDetails = closeGalleryDetails,
        onQueryChange = onQueryChange,
        loadSearchResult = loadSearchResult
    )
}

@Composable
private fun MainNavigationWrapper(
    navigationType: NavigationType,
    contentType: ContentType,
    displayFeatures: List<DisplayFeature>,
    navigationContentPosition: NavigationContentPosition,
    uiState: SearchState,
    navController: NavHostController,
    openGalleryDetails: (GalleryInfo) -> Unit,
    closeGalleryDetails: () -> Unit,
    onQueryChange: (SearchQuery?) -> Unit,
    loadSearchResult: (IntRange) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val openDrawer: () -> Unit = {
        coroutineScope.launch {
            drawerState.open()
        }
    }

    if (navigationType == NavigationType.PERMANENT_NAVIGATION_DRAWER) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentNavigationDrawerContent(
                    selectedDestination = currentRoute,
                    navigateToDestination = { navController.navigate(it.route) {
                        popUpTo(MainDestination.Search.route)
                        launchSingleTop = true
                    } },
                    navigationContentPosition = navigationContentPosition,
                )
            }
        ) {
            MainContent(
                navigationType = navigationType,
                contentType = contentType,
                displayFeatures = displayFeatures,
                uiState = uiState,
                navController = navController,
                onDrawerClicked = openDrawer,
                openGalleryDetails = openGalleryDetails,
                closeGalleryDetails = closeGalleryDetails,
                onQueryChange = onQueryChange,
                loadSearchResult = loadSearchResult,
            )
        }
    } else {
        ModalNavigationDrawer(
            drawerContent = {
                ModalNavigationDrawerContent(
                    selectedDestination = currentRoute,
                    navigateToDestination = { navController.navigate(it.route) {
                        popUpTo(MainDestination.Search.route)
                        launchSingleTop = true
                    } },
                    navigationContentPosition = navigationContentPosition,
                    onDrawerClicked = {
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    }
                )
            },
            drawerState = drawerState
        ) {
            MainContent(
                navigationType = navigationType,
                contentType = contentType,
                displayFeatures = displayFeatures,
                uiState = uiState,
                navController = navController,
                onDrawerClicked = openDrawer,
                openGalleryDetails = openGalleryDetails,
                closeGalleryDetails = closeGalleryDetails,
                onQueryChange = onQueryChange,
                loadSearchResult = loadSearchResult,
            )
        }
    }
}

@Composable
fun NotImplemented() {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("(⁄ ⁄•⁄ω⁄•⁄ ⁄)", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(stringResource(R.string.not_implemented), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MainContent(
    navigationType: NavigationType,
    contentType: ContentType,
    displayFeatures: List<DisplayFeature>,
    uiState: SearchState,
    navController: NavHostController,
    onDrawerClicked: () -> Unit,
    openGalleryDetails: (GalleryInfo) -> Unit,
    closeGalleryDetails: () -> Unit,
    onQueryChange: (SearchQuery?) -> Unit,
    loadSearchResult: (IntRange) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = navigationType == NavigationType.NAVIGATION_RAIL) {
            MainNavigationRail(
                selectedDestination = currentRoute,
                navigateToDestination = { navController.navigate(it.route) {
                    popUpTo(MainDestination.Search.route)
                    launchSingleTop = true
                } },
                onDrawerClicked = onDrawerClicked
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .run {
                        if (navigationType == NavigationType.BOTTOM_NAVIGATION) {
                            this
                                .consumeWindowInsets(WindowInsets.ime)
                                .consumeWindowInsets(WindowInsets.navigationBars)
                        } else this
                    }
            ) {
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainDestination.Search.route
                ) {
                    composable(MainDestination.Search.route) {
                        SearchScreen(
                            contentType = contentType,
                            displayFeatures = displayFeatures,
                            uiState = uiState,
                            openGalleryDetails = openGalleryDetails,
                            closeGalleryDetails = closeGalleryDetails,
                            onQueryChange = onQueryChange,
                            loadSearchResult = loadSearchResult,
                            openGallery = {
                                navController.navigate(MainDestination.ImageViewer(it.id).route) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(MainDestination.History.route) {
                        NotImplemented()
                    }
                    composable(MainDestination.Downloads.route) {
                        NotImplemented()
                    }
                    composable(MainDestination.Favorites.route) {
                        NotImplemented()
                    }
                    composable(MainDestination.Settings.route) {
                        NotImplemented()
                    }
                    composable(MainDestination.ImageViewer.commonRoute) {
                        NotImplemented()
                    }
                }
            }
            AnimatedVisibility(visible = navigationType == NavigationType.BOTTOM_NAVIGATION) {
                BottomNavigationBar(
                    selectedDestination = currentRoute,
                    navigateToDestination = { navController.navigate(it.route) {
                        popUpTo(MainDestination.Search.route)
                        launchSingleTop = true
                    } }
                )
            }
        }
    }
}