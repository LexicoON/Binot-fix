@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.obinot.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.obinot.app.ui.components.AudioFilePickerSheet
import com.obinot.app.ui.components.BouncyButton
import com.obinot.app.ui.components.observeBouncyPress
import com.obinot.app.viewmodel.RecordViewModel
import com.obinot.app.ui.components.AudioWaveform
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    userName: String,
    recordMode: Int,
    aiProvider: Int = 0,
    snackbarHostState: SnackbarHostState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onNoteClick: (Int) -> Unit,
    onImportFile: suspend (Uri) -> Int? = { null },
    useNativePicker: Boolean = false
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val isAppInLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val liveTranscriptEnabled by viewModel.liveTranscriptEnabled.collectAsState()

    val visibleNotes = remember(recentNotes) {
        recentNotes.filterNot { note ->
            val t = note.title.trim()
            t.contains("binot_syst", ignoreCase = true) ||
            t.contains("binot_system", ignoreCase = true) ||
            (t.startsWith("[") && t.endsWith("]"))
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var isTappedExpanded by remember { mutableStateOf(false) }
    var isPressExpanded by remember { mutableStateOf(false) }
    val isExpanded = isTappedExpanded || isPressExpanded

    var greetingTapCount by remember { mutableStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var easterEggAnswer by remember { mutableStateOf("") }
    var showLovePopup by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isDragHovering by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetings = remember(hour) {
        when (hour) {
            in 5..11 -> listOf("Good morning,", "Rise and shine,", "A fresh start,", "Morning inspiration,", "Start your day right,")
            in 12..16 -> listOf("Good afternoon,", "Midday thoughts,", "Keep it going,", "Stay productive,", "Afternoon check-in,")
            in 17..20 -> listOf("Good evening,", "Winding down,", "Evening reflection,", "Time to relax,", "Sunset thoughts,")
            else -> listOf("Late night thoughts,", "Midnight notes,", "Still awake?,", "Quiet hours,", "Rest well,")
        }
    }
    val randomGreeting = remember(hour) { greetings.random() }

    val greetingText = buildAnnotatedString {
        append("$randomGreeting\n")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(if (userName.isNotBlank()) userName else "Guest")
        }
        append(".")
    }

    val minutes = (recordingSeconds / 60).toString().padStart(2, '0')
    val seconds = (recordingSeconds % 60).toString().padStart(2, '0')
    val timeString = "$minutes:$seconds"

    val topInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopMargin = if (topInsets < 24.dp) 24.dp else topInsets

    // En Accurate, el cartel depende del toggle "Live Transcript" de Settings:
    // - ON:  hay recognizer corriendo, mostramos texto o "Listening..."
    // - OFF: no hay recognizer, el cartel aclara que la IA transcribirá el audio.
    val displayLiveText = when {
        recognizedText.isNotEmpty() -> recognizedText
        recordMode == 1 && liveTranscriptEnabled -> "Listening... (audio is being saved for AI analysis)"
        recordMode == 1 && !liveTranscriptEnabled -> "Recording audio... it will be transcribed by AI when you open the note."
        else -> "Waiting for voice input..."
    }

    val scrollState = rememberScrollState()

    // Drag & drop handler para audio y .binot. Se usa shouldStartDragAndDrop permisivo
    // porque algunos file managers envían MIME vacío o application/octet-stream para
    // archivos .binot, y rechazarlos en la entrada hace que el target nunca se active.
    val dragDropTarget = remember(context, coroutineScope, snackbarHostState, onImportFile) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragHovering = true
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDragHovering = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragHovering = false
                val activity = context as? android.app.Activity
                val androidEvent = event.toAndroidDragEvent()
                val permission = activity?.requestDragAndDropPermissions(androidEvent)

                val clipData = androidEvent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    var firstImportedId: Int? = null
                    var successCount = 0
                    var failCount = 0
                    coroutineScope.launch {
                        isImporting = true
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            if (uri != null) {
                                val newId = onImportFile(uri)
                                if (newId != null) {
                                    successCount++
                                    if (firstImportedId == null) firstImportedId = newId
                                } else {
                                    failCount++
                                }
                            }
                        }
                        isImporting = false
                        permission?.release()

                        val msg = when {
                            failCount == 0 && successCount > 1 -> "Imported $successCount files!"
                            failCount == 0 -> "Imported successfully!"
                            successCount == 0 -> "Failed to import any files. Ensure format is supported."
                            else -> "Imported $successCount, failed $failCount."
                        }
                        snackbarHostState.showSnackbar(msg)
                        if (firstImportedId != null && clipData.itemCount == 1) {
                            onNoteClick(firstImportedId)
                        }
                    }
                    return true
                }
                permission?.release()
                return false
            }
        }
    }

    with(animatedVisibilityScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        // Permisivo a propósito: aceptamos cualquier drag y filtramos
                        // dentro de onDrop. Los file managers reales no siempre
                        // reportan los MIME types correctos para .binot.
                        event.mimeTypes().isEmpty() ||
                        event.mimeTypes().any { mimeType ->
                            mimeType.startsWith("audio/") ||
                            mimeType == "application/zip" ||
                            mimeType == "application/octet-stream" ||
                            mimeType.startsWith("application/")
                        }
                    },
                    target = dragDropTarget
                )
        ) {
            M3ExpressiveBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isExpanded) {
                        if (isExpanded) {
                            detectTapGestures(
                                onTap = {
                                    isTappedExpanded = false
                                    isPressExpanded = false
                                }
                            )
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(safeTopMargin + 24.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val availableHeight = maxHeight

                    val stiffSpring = spring<Dp>(dampingRatio = 0.9f, stiffness = 400f)

                    val boxHeight by animateDpAsState(
                        targetValue = if (isExpanded) availableHeight else 160.dp,
                        animationSpec = stiffSpring,
                        label = "boxHeight"
                    )
                    val topAlpha by animateFloatAsState(
                        targetValue = if (isExpanded) 0f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "topAlpha"
                    )
                    val cornerRadius by animateDpAsState(
                        targetValue = if (isExpanded) 40.dp else 32.dp,
                        animationSpec = stiffSpring,
                        label = "cornerRadius"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "containerColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "contentColor"
                    )

                    val boxScale by animateFloatAsState(
                        targetValue = if (isPressExpanded) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "boxScale"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 176.dp)
                            .alpha(topAlpha)
                            .animateEnterExit(enter = slideInVertically { -50 } + fadeIn()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            greetingTapCount++
                                            if (greetingTapCount > 4) {
                                                greetingTapCount = 0
                                                showEasterEggDialog = true
                                                easterEggAnswer = ""
                                            }
                                        }
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = when {
                                isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                                isRecording -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                        ) {
                            AnimatedContent(targetState = timeString, label = "timeAnimation") { time ->
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isRecording -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isRecording || isPaused,
                                enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.8f)),
                                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f)
                            ) {
                                AudioWaveform(
                                    amplitude = amplitude,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isRecording && !isPaused && visibleNotes.isNotEmpty(),
                                enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 50 }),
                                exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { 50 })
                            ) {
                                LazyHorizontalStaggeredGrid(
                                    rows = StaggeredGridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalItemSpacing = 12.dp,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                                ) {
                                    items(visibleNotes, key = { it.id }) { note ->
                                        val displayTitle = if (note.title.isBlank()) "No title" else note.title
                                        val randomPadding = remember(note.id) { (note.id * 23 % 40).dp }

                                        val noteInteraction = remember { MutableInteractionSource() }
                                        val noteScale = remember { Animatable(1f) }
                                        LaunchedEffect(noteInteraction) {
                                            observeBouncyPress(
                                                interactionSource = noteInteraction,
                                                scale = noteScale,
                                                pressedScale = 0.95f
                                            )
                                        }

                                        with(sharedTransitionScope) {
                                            Box(
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        scaleX = noteScale.value
                                                        scaleY = noteScale.value
                                                    }
                                                    .sharedBounds(
                                                        sharedContentState = rememberSharedContentState("record_note-${note.id}"),
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                                        boundsTransform = { _, _ -> tween(300) }
                                                    )
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .clickable(
                                                        interactionSource = noteInteraction,
                                                        indication = null,
                                                        onClick = { onNoteClick(note.id) }
                                                    )
                                                    .heightIn(min = 64.dp)
                                                    .padding(
                                                        horizontal = (32.dp + randomPadding),
                                                        vertical = 22.dp
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = displayTitle,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isAppInLightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(0.5f))
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(boxHeight)
                            .scale(boxScale)
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(containerColor)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (!isExpanded && dragAmount < -5) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isTappedExpanded = true
                                    }
                                }
                            }
                            .pointerInput("tap", isTappedExpanded) {
                                if (!isTappedExpanded) {
                                    detectTapGestures(
                                        onTap = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isTappedExpanded = true
                                        }
                                    )
                                }
                            }
                            .pointerInput("hold", isTappedExpanded) {
                                if (!isTappedExpanded) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressExpanded = true
                                            tryAwaitRelease()
                                            isPressExpanded = false
                                        }
                                    )
                                }
                            }
                            .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                    ) {
                        LaunchedEffect(recognizedText, isExpanded) {
                            if (recognizedText.isNotEmpty()) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable(
                                        enabled = isExpanded,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isTappedExpanded = false
                                        isPressExpanded = false
                                    }
                                    .pointerInput(isExpanded) {
                                        if (isExpanded) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 5) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isTappedExpanded = false
                                                    isPressExpanded = false
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.3f))
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Text(
                                        text = "Live Transcription",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            AnimatedContent(
                                targetState = displayLiveText,
                                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                                label = "TranscriptionFade"
                            ) { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = contentColor,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState, enabled = isExpanded)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val isSplit = isRecording || isPaused
                val totalAreaWidth = 280.dp
                val importButtonSize = 64.dp
                val gapBetweenButtons = 12.dp

                Box(
                    modifier = Modifier
                        .widthIn(min = totalAreaWidth)
                        .height(80.dp)
                        .animateEnterExit(enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))),
                    contentAlignment = Alignment.Center
                ) {
                    var isLeftPressed by remember { mutableStateOf(false) }
                    var isStopPressed by remember { mutableStateOf(false) }
                    var isImportPressed by remember { mutableStateOf(false) }

                    val leftTargetWidth = when {
                        isStopPressed && isSplit -> 88.dp
                        isLeftPressed && isSplit -> 152.dp
                        isLeftPressed            -> totalAreaWidth + 56.dp
                        isSplit                  -> 120.dp
                        else                     -> totalAreaWidth
                    }
                    val rightTargetWidth = when {
                        !isSplit                  -> 0.dp
                        isStopPressed              -> 152.dp
                        isLeftPressed               -> 88.dp
                        else                        -> 120.dp
                    }
                    val gapTarget = if (isSplit) 16.dp else 0.dp

                    val leftButtonWidth by animateDpAsState(targetValue = leftTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftWidth")
                    val rightButtonWidth by animateDpAsState(targetValue = rightTargetWidth, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "rightWidth")
                    val rightButtonAlpha by animateFloatAsState(targetValue = if (isSplit) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "rightAlpha")
                    val gapWidth by animateDpAsState(targetValue = gapTarget, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "gap")
                    val leftIconScale by animateFloatAsState(targetValue = if (isLeftPressed && !isSplit) 1.12f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "leftIconScale")
                    val importAlpha by animateFloatAsState(targetValue = if (isSplit) 0f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "importAlpha")
                    val importScale by animateFloatAsState(targetValue = if (isImportPressed) 0.90f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "importScale")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isSplit) gapWidth else gapBetweenButtons),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(leftButtonWidth)
                                .height(80.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSplit && !isPaused -> MaterialTheme.colorScheme.secondaryContainer
                                        isSplit && isPaused  -> MaterialTheme.colorScheme.primaryContainer
                                        else                 -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                .pointerInput(isSplit, isPaused) {
                                    detectTapGestures(
                                        onPress = {
                                            isLeftPressed = true
                                            tryAwaitRelease()
                                            isLeftPressed = false
                                            when {
                                                !isSplit -> {
                                                    if (!hasPermission) {
                                                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")
                                                        viewModel.toggleRecording(isEmulator, recordMode)
                                                    }
                                                }
                                                isPaused -> viewModel.resumeRecording()
                                                else     -> viewModel.pauseRecording()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isSplit && isPaused -> Icons.Default.PlayArrow
                                        isSplit            -> Icons.Default.Pause
                                        else               -> Icons.Default.Mic
                                    },
                                    contentDescription = when {
                                        isSplit && isPaused -> "Resume"
                                        isSplit            -> "Pause"
                                        else               -> "Record"
                                    },
                                    tint = when {
                                        isSplit && !isPaused -> MaterialTheme.colorScheme.onSecondaryContainer
                                        isSplit && isPaused  -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else                 -> MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .scale(leftIconScale)
                                )
                                if (!isSplit) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Record",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }

                        if (rightButtonWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(rightButtonWidth)
                                    .height(80.dp)
                                    .alpha(rightButtonAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isStopPressed = true
                                                tryAwaitRelease()
                                                isStopPressed = false

                                                viewModel.stopRecordingInstant()

                                                coroutineScope.launch {
                                                    val saved = viewModel.saveNote(recordMode, aiProvider)
                                                    snackbarHostState.showSnackbar(
                                                        message = if (saved) "Note saved" else "No text to save",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStopPressed && recordMode == 1) {
                                    LoadingIndicator(
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        if (!isSplit) {
                            Box(
                                modifier = Modifier
                                    .size(importButtonSize)
                                    .graphicsLayer {
                                        scaleX = importScale
                                        scaleY = importScale
                                        alpha = importAlpha
                                    }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isImportPressed = true
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                tryAwaitRelease()
                                                isImportPressed = false
                                                showAudioPicker = true
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Import audio",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Overlay de import (para import activado desde picker O drag & drop)
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Importing...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Overlay de drag hover
            if (isDragHovering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Drop to import",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Audio files or .binot backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    // SAF fallback: se usa cuando el picker nativo (beta) está apagado.
    val safAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val newId = onImportFile(uri)
                isImporting = false
                if (newId != null) onNoteClick(newId)
                else snackbarHostState.showSnackbar("Failed to import audio file.")
            }
        }
    }

    LaunchedEffect(showAudioPicker) {
        if (showAudioPicker && !useNativePicker) {
            showAudioPicker = false
            safAudioLauncher.launch(arrayOf("audio/*"))
        }
    }

    if (showAudioPicker && useNativePicker) {
        AudioFilePickerSheet(
            onDismiss = { showAudioPicker = false },
            onFileSelected = { uri ->
                showAudioPicker = false
                isImporting = true
                coroutineScope.launch {
                    val newId = onImportFile(uri)
                    isImporting = false
                    if (newId != null) {
                        onNoteClick(newId)
                    } else {
                        snackbarHostState.showSnackbar("Failed to import audio file.")
                    }
                }
            }
        )
    }

    if (showEasterEggDialog) {
        AlertDialog(
            onDismissRequest = {
                showEasterEggDialog = false
                easterEggAnswer = ""
            },
            title = {
                Text(
                    text = "✨ Secret Question",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Who is the developer's sweetheart?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = easterEggAnswer,
                        onValueChange = { if (it.length <= 5) easterEggAnswer = it },
                        singleLine = true,
                        placeholder = { Text("Your answer...") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (easterEggAnswer.trim().equals("dinda", ignoreCase = true)) {
                            showEasterEggDialog = false
                            showLovePopup = true
                        }
                        easterEggAnswer = ""
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEasterEggDialog = false
                    easterEggAnswer = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLovePopup) {
        AlertDialog(
            onDismissRequest = { showLovePopup = false },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = "💖", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "For Dinda",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "In every line of code I write,\nin every bug I fix at night —\nit's always you I'm thinking of.\nYou are my favorite feature,\nmy most beautiful exception.\n\nForever yours,\nThe Developer 👨‍💻",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Text(
                        text = "💻 With all the love in the codebase 💻",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { showLovePopup = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✨ Close")
                }
            }
        )
    }
}

@Composable
private fun M3ExpressiveBackground() {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor, Color.Transparent),
                center = Offset(w * 0.5f, h * 0.2f),
                radius = w * 0.8f
            ),
            center = Offset(w * 0.5f, h * 0.2f),
            radius = w * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor, Color.Transparent),
                center = Offset(w * 0.2f, h * 0.7f),
                radius = w * 0.7f
            ),
            center = Offset(w * 0.2f, h * 0.7f),
            radius = w * 0.7f
        )
    }
}