package net.rpcs3.ui.setup.screen

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.rpcs3.FirmwareRepository
import net.rpcs3.ProgressRepository
import net.rpcs3.R
import net.rpcs3.ui.common.ComposePreview
import net.rpcs3.ui.common.installFirmware
import net.rpcs3.ui.settings.util.sizeIn

/**
 * Created using Android Studio
 * User: Muhammad Ashhal
 * Date: Wed, Mar 05, 2025
 * Time: 1:42 pm
 */

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { SetupItem.entries.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val isLoadingInProgress by remember { mutableStateOf(false) }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // The user picked a directory
            result.data?.data?.let { directoryUri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(directoryUri, flags)

                // Save the directory URI
                println("Directory Selected: $directoryUri")
                Toast.makeText(context, "Uri selected", Toast.LENGTH_SHORT).show()
                scope.launch { pagerState.animateScrollToNextPage() }
            }
        }
    }

    val firmwarePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { firmwareUri ->
        if (firmwareUri != null) {
            // Start installing the firmware and show a loading progress
            println("Firmware Content URI: $firmwareUri")
            context.installFirmware(firmwareUri)
            Toast.makeText(context, "Firmware selected", Toast.LENGTH_SHORT).show()
            // Save the setup as completed (Setup screen will close automatically)
        } else {
            // User cancelled the firmware selection
            Toast.makeText(context, "firmware selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Global Back Handler
    // It'll be better to handle back invokation on each screen separately
    // to disable on certain occasions
    // e.g: Firmware is being installed, etc
    BackHandler(
        enabled = pagerState.currentPage != 0 && !isLoadingInProgress
    ) {
        if (pagerState.currentPage == 0) {
            // Finish the activity instead of going back to games screen
            activity?.finish()
        } else {
            scope.launch { pagerState.animateScrollToPreviousPage() }
        }
    }

    BackHandler(
        enabled = isLoadingInProgress
    ) {
        /* no-op */
        // wait for firmware installation to complete
    }

    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false
            ) { currentPageIndex ->
                val currentItem = remember(currentPageIndex) { SetupItem.entries[currentPageIndex] }
                SetupPagerItem(
                    item = currentItem,
                    titleColor = if (currentItem == SetupItem.Welcome) MaterialTheme.colorScheme.primary else LocalContentColor.current
                ) {
                    when (currentItem) {
                        SetupItem.Welcome -> {
                            scope.launch { pagerState.animateScrollToNextPage() }
                        }

                        SetupItem.Directory -> {
                            // Select a directory here
                            directoryPicker.launch(directoryPickerIntent(null))
                        }

                        SetupItem.Firmware -> {
                            // Install firmware
                            if (FirmwareRepository.progressChannel.value == null) {
                                firmwarePicker.launch("*/*")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isLoadingInProgress
            ) {
                FirmwareInstallationProgress()
            }
        }

    }
}

@Composable
fun FirmwareInstallationProgress() {
    val progressChannel = FirmwareRepository.progressChannel
    val progress = remember(progressChannel) { ProgressRepository.getItem(progressChannel.value) }

    if (progress != null) {
        val progressValue = progress.value
        val maximumValue = progressValue.max
        val isIndeterminate by remember(progressValue, maximumValue) {
            derivedStateOf { maximumValue.longValue != 0L }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Installing Firmware")
            if (isIndeterminate) {
                LinearProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = { progressValue.value.longValue.toFloat() / maximumValue.longValue.toFloat() }
                )
            }
        }
    }
}

@Composable
fun SetupPagerItem(
    item: SetupItem,
    modifier: Modifier = Modifier,
    titleColor: Color = LocalContentColor.current,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = item.title,
            style = LocalTextStyle.current.merge(
                MaterialTheme.typography.headlineMedium
            ).copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            color = titleColor
        )

        Icon(
            modifier = Modifier.sizeIn(minSize = 64.dp),
            painter = item.icon(),
            contentDescription = null
        )

        Text(
            text = item.desc,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                modifier = Modifier
                    .padding(8.dp)
                    .sizeIn(
                        minWidth = 256.dp,
                        minHeight = 48.dp
                    ),
                onClick = onButtonClick
            ) {
                Text(
                    text = "Install Firmware",
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

enum class SetupItem(
    val title: String,
    val desc: String,
    private val iconResource: Any,
) {
    Welcome(
        title = "RPCS3",
        desc = "An experimental RPCS3 emulator port for Android.",
        iconResource = R.drawable.ic_rpcs3_foreground,
    ),
    Directory(
        title = "Set Up File System",
        desc = "Choose a directory to store emulator data and game files.",
        iconResource = Icons.Default.Settings,
    ),
    Firmware(
        title = "Install PS3 Firmware",
        desc = "Select and install the official PlayStation 3 firmware.",
        iconResource = Icons.Default.Settings
    );

    @Composable
    fun icon() = when (iconResource) {
        is Int -> painterResource(iconResource)
        is ImageVector -> rememberVectorPainter(iconResource)
        else -> error("Unsupported icon type")
    }
}

@Preview
@Composable
private fun SetupScreenPreview() {
    ComposePreview {
        SetupScreen()
    }
}

private suspend fun PagerState.animateScrollToNextPage(
    animationSpec: AnimationSpec<Float> = tween(
        durationMillis = 400,
        easing = LinearEasing
    )
) {
    safeAnimateScrollToPage(
        page = settledPage.inc(),
        animationSpec = animationSpec
    )
}

private suspend fun PagerState.animateScrollToPreviousPage(
    animationSpec: AnimationSpec<Float> = tween(
        durationMillis = 400,
        easing = LinearEasing
    )
) {
    safeAnimateScrollToPage(
        page = settledPage.dec(),
        animationSpec = animationSpec
    )
}

private suspend fun PagerState.safeAnimateScrollToPage(
    page: Int, animationSpec: AnimationSpec<Float> = spring()
) {
    if (!isScrollInProgress) animateScrollToPage(page = page, animationSpec = animationSpec)
}

private val directoryPickerIntent: (Uri?) -> Intent = { initialUri ->
    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        initialUri?.let {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
        }
    }
}