package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.Home
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.lang.IllegalStateException

private const val TAG = "HomeActivity"
data class Screen(val route: String, val title: String, val icon: ImageVector)

val NavDestination.depth: Int get() = this.route?.let { route -> route.count { it == '/' } + 1 } ?: 0

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        setContent {
            EnableVoLTETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    PixelIMSApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp() {
    val navController = rememberNavController()
    val carrierModer = CarrierModer(LocalContext.current)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var subscriptions by rememberSaveable { mutableStateOf(listOf<SubscriptionInfo>()) }
    var navBuilder by remember {
        mutableStateOf<NavGraphBuilder.() -> Unit>({
            composable("home", "Home") {
                Home(navController)
            }
        })
    }

    fun generateNavBuilder(): (NavGraphBuilder.() -> Unit) {
        return {
            composable("home", "Home") {
                Home(navController)
            }
            for (subscription in subscriptions) {
                navigation(startDestination = "config${subscription.subscriptionId}", route = "config${subscription.subscriptionId}root") {
                    composable("config${subscription.subscriptionId}", "SIM Config") {
                        Config(navController, subscription.subscriptionId)
                    }
                    composable("config${subscription.subscriptionId}/dump", "Config Dump Viewer") {
                        DumpedConfig(subscription.subscriptionId)
                    }
                    composable("config${subscription.subscriptionId}/edit", "Expert Mode") {
                        Editor(subscription.subscriptionId)
                    }
                }
            }
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            try {
                if (checkShizukuPermission(0)) {
                    Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                    subscriptions = carrierModer.subscriptions
                    navBuilder = generateNavBuilder()
                } else {
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                            subscriptions = carrierModer.subscriptions
                            navBuilder = generateNavBuilder()
                        }
                    }
                }
            } catch (_: IllegalStateException) {
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentBackStackEntry?.destination?.label?.toString() ?: stringResource(R.string.app_name), color = MaterialTheme.colorScheme.onPrimary)
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.depth?.let { it > 1 } == true) {
                        IconButton(onClick = { navController.popBackStack() }, colors = IconButtonDefaults.filledIconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Localized description",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
            )
        },
        bottomBar = {
            if (currentBackStackEntry?.destination?.depth?.let { it == 1 } == true) {
                NavigationBar {
                    val currentDestination = currentBackStackEntry?.destination
                    val items = arrayListOf(
                        Screen("home", stringResource(R.string.home), Icons.Filled.Home),
                    )
                    for (subscription in subscriptions) {
                        items.add(
                            Screen("config${subscription.subscriptionId}", subscription.uniqueName, Icons.Filled.Settings),
                        )
                    }

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = {
                                Text(screen.title)
                            },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    // on the back stack as users select items
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination when
                                    // reselecting the same item
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = "home", Modifier.padding(innerPadding), builder = navBuilder)
    }
}
