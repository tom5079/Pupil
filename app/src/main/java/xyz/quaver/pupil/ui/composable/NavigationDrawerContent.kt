package xyz.quaver.pupil.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import xyz.quaver.pupil.R

@Composable
fun PermanentNavigationDrawerContent(
    selectedDestination: MainDestination,
    navigationContentPosition: NavigationContentPosition,
    navigateToDestination: (MainDestination) -> Unit
) {
    PermanentDrawerSheet(
        modifier = Modifier.sizeIn(minWidth = 200.dp, maxWidth = 300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Layout(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(16.dp),
            content = {
                Row(
                    modifier = Modifier.layoutId(LayoutType.HEADER),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.app_icon),
                        tint = Color.Unspecified,
                        contentDescription = "app icon"
                    )
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "Pupil",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    modifier = Modifier
                        .layoutId(LayoutType.CONTENT)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainDestinations.forEach { destination ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = stringResource(destination.textId),
                                    modifier = Modifier.padding(16.dp)
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.textId)
                                )
                            },
                            selected = selectedDestination.route == destination.route,
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent
                            ),
                            onClick = { navigateToDestination(destination) }
                        )
                    }
                }
            },
            measurePolicy = navigationMeasurePolicy(navigationContentPosition)
        )
    }
}

@Composable
fun ModalNavigationDrawerContent(
    selectedDestination: MainDestination,
    navigationContentPosition: NavigationContentPosition,
    navigateToDestination: (MainDestination) -> Unit,
    onDrawerClicked: () -> Unit
) {
    ModalDrawerSheet {
        Layout(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(16.dp),
            content = {
                Row(
                    modifier = Modifier
                        .layoutId(LayoutType.HEADER)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            painter = painterResource(R.drawable.app_icon),
                            tint = Color.Unspecified,
                            contentDescription = "app icon"
                        )
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = "Pupil",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDrawerClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.MenuOpen,
                            contentDescription = stringResource(R.string.main_open_navigation_drawer)
                        )
                    }
                }

                Column (
                    modifier = Modifier
                        .layoutId(LayoutType.CONTENT)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainDestinations.forEach { destination ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = stringResource(destination.textId),
                                    modifier = Modifier.padding(16.dp)
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.textId)
                                )
                            },
                            selected = selectedDestination.route == destination.route,
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent
                            ),
                            onClick = { navigateToDestination(destination) }
                        )
                    }
                }
            },
            measurePolicy = navigationMeasurePolicy(navigationContentPosition)
        )
    }
}

@Composable
fun MainNavigationRail(
    selectedDestination: MainDestination,
    navigationContentPosition: NavigationContentPosition,
    navigateToDestination: (MainDestination) -> Unit,
    onDrawerClicked: () -> Unit
) {
    NavigationRail (
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        NavigationRailItem(
            selected = false,
            onClick = onDrawerClicked,
            icon = { 
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.main_open_navigation_drawer)
                )
            }
        )
        
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            mainDestinations.forEach { destination ->
                NavigationRailItem(
                    selected = selectedDestination.route == destination.route,
                    onClick = { navigateToDestination(destination) },
                    icon = { 
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.textId)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedDestination: MainDestination,
    navigateToDestination: (MainDestination) -> Unit
) {
    NavigationBar(modifier = Modifier.fillMaxWidth(), windowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)) {
        mainDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination.route == destination.route,
                onClick = { navigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.textId)
                    )
                }
            )
        }
    }
}

fun navigationMeasurePolicy(
    navigationContentPosition: NavigationContentPosition,
): MeasurePolicy {
    return MeasurePolicy { measurables, constraints ->
        lateinit var headerMeasurable: Measurable
        lateinit var contentMeasurable: Measurable
        measurables.forEach {
            when (it.layoutId) {
                LayoutType.HEADER -> headerMeasurable = it
                LayoutType.CONTENT -> contentMeasurable = it
                else -> error("Unknown layoutId encountered!")
            }
        }

        val headerPlaceable = headerMeasurable.measure(constraints)
        val contentPlaceable = contentMeasurable.measure(
            constraints.offset(vertical = -headerPlaceable.height)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative(0, 0)

            val nonContentVerticalSpace = constraints.maxHeight - contentPlaceable.height

            val contentPlaceableY = when (navigationContentPosition) {
                NavigationContentPosition.TOP -> 0
                NavigationContentPosition.CENTER -> nonContentVerticalSpace / 2
            }.coerceAtLeast(headerPlaceable.height)

            contentPlaceable.placeRelative(0, contentPlaceableY)
        }
    }
}

enum class LayoutType {
    HEADER, CONTENT
}

