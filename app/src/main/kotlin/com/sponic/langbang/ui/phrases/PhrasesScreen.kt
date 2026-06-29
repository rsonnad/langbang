package com.sponic.langbang.ui.phrases

import com.sponic.langbang.ui.theme.LbColors

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sponic.langbang.BuildConfig
import com.sponic.langbang.LangbangApplication
import com.sponic.langbang.cloud.CloudAiPhraseQuota
import com.sponic.langbang.cloud.CloudAuthResponse
import com.sponic.langbang.cloud.GoogleSignInHelper
import com.sponic.langbang.data.model.PhraseGroup
import com.sponic.langbang.data.model.SentenceExample
import com.sponic.langbang.domain.NowVoicing
import com.sponic.langbang.domain.NowVoicingBus
import com.sponic.langbang.domain.R2AudioDownloader
import com.sponic.langbang.ui.common.StudyQueuePlayer
import com.sponic.langbang.domain.ensureCachedAudio
import com.sponic.langbang.domain.playAudioAndAwait
import com.sponic.langbang.domain.sourceAudioVoice
import com.sponic.langbang.domain.sourceLanguageLabel
import com.sponic.langbang.domain.targetAudioVoice
import com.sponic.langbang.domain.targetLanguageLabel
import com.sponic.langbang.domain.targetSlowVoices
import com.sponic.langbang.domain.targetSlowVoice
import com.sponic.langbang.integrations.AzureTtsClient
import com.sponic.langbang.ui.common.DelayedEnglishTranslation
import com.sponic.langbang.ui.common.LbButton
import com.sponic.langbang.ui.common.LbChip
import com.sponic.langbang.ui.common.SelectionNavButtons
import com.sponic.langbang.ui.common.SectionLabel
import com.sponic.langbang.ui.common.StarToggle
import com.sponic.langbang.ui.common.SubtleCheckbox
import com.sponic.langbang.ui.common.WordAlignedPolish
import com.sponic.langbang.ui.theme.LbShapes
import kotlinx.coroutines.launch

private data class PhraseCollectionGroup(
    val name: String,
    val groups: List<PhraseGroup>
)

private sealed interface PhraseRailItem {
    data class CollectionHeader(val collection: PhraseCollectionGroup, val expanded: Boolean) : PhraseRailItem
    data class GroupRow(val group: PhraseGroup, val inCollection: Boolean = false) : PhraseRailItem
}

/**
 * Phrases tab — multi-sentence real-world utterances (introductions, small-talk, etc.).
 * Two-pane: left list of groups, right detail with each sentence playable individually
 * plus a "Play" queue that plays the whole group EN → PL-slow → PL per sentence.
 *
 * Custom user-added groups merge in from `UserPhraseStore` ahead of the bundled ones
 * (so personal phrases are immediately visible at the top). Editing flow is deferred
 * to a later turn — for now the bundled "Introduction — Rahul" is enough to dogfood.
 */
@Composable
fun PhrasesScreen(
    app: LangbangApplication,
    nowVoicing: @Composable () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val cloudState by app.cloudConfig.state.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    val data = remember(refreshKey, cloudState.bootstrap?.syncedAt) { app.lessonRepo.lesson5() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = data.groups.firstOrNull { it.id == selectedId } ?: data.groups.firstOrNull()
    val starred by app.starredPhrases.starred.collectAsState()
    val auth by app.authStore.state.collectAsState()
    val pendingAudio = remember { mutableStateListOf<SentenceExample>() }
    var showAccountGateForGroup by remember { mutableStateOf(false) }
    var showAddGroup by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var generationStatus by remember { mutableStateOf<String?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(data.groups.map { it.id }) {
        if (selectedId == null || data.groups.none { it.id == selectedId }) {
            selectedId = data.groups.firstOrNull { it.collection.isNullOrBlank() }?.id
                ?: data.groups.firstOrNull()?.id
        }
    }

    fun reload(selectGroupId: String? = null) {
        refreshKey += 1
        if (selectGroupId != null) selectedId = selectGroupId
    }

    fun requestAddGroup() {
        if (auth.customItemGateSatisfied) {
            showAddGroup = true
        } else {
            showAccountGateForGroup = true
        }
    }

    fun generatePendingAudio() {
        if (generating || pendingAudio.isEmpty()) return
        scope.launch {
            generating = true
            generationError = null
            generationStatus = "Preparing ${pendingAudio.size} new phrases"
            val phrases = audioPhrasesFor(app, pendingAudio)
            val result = app.r2Audio.downloadPhrases(phrases) { done, total, current ->
                generationStatus = if (total == 0) {
                    "Audio already cached"
                } else {
                    val name = current.takeIf { it.isNotBlank() } ?: "audio"
                    "Generating $done/$total · $name"
                }
            }
            result.fold(
                onSuccess = { summary ->
                    if (summary.failed == 0) {
                        pendingAudio.clear()
                        generationStatus = "Generated ${summary.fetched} · cached ${summary.alreadyCached}"
                    } else {
                        generationError = "Generated with ${summary.failed} audio failures"
                        generationStatus = null
                    }
                },
                onFailure = { t ->
                    generationError = t.message ?: t.javaClass.simpleName
                    generationStatus = null
                }
            )
            generating = false
        }
    }

    fun syncPhraseGroups() {
        if (!auth.signedIn || auth.syncingPhrases) return
        scope.launch {
            generationError = null
            generationStatus = "Refreshing phrase groups"
            app.phraseSync.syncNow().fold(
                onSuccess = { response ->
                    generationStatus = "Phrase groups refreshed"
                    reload(response.groups.firstOrNull()?.id)
                },
                onFailure = { t ->
                    generationError = "Refresh failed: ${t.message ?: t.javaClass.simpleName}"
                    generationStatus = null
                }
            )
        }
    }

    if (showAccountGateForGroup) {
        AccountGateDialog(
            app = app,
            onDismiss = { showAccountGateForGroup = false },
            onReady = {
                showAccountGateForGroup = false
                showAddGroup = true
            }
        )
    }

    if (showAddGroup) {
        AddPhraseGroupDialog(
            onDismiss = { showAddGroup = false },
            onCreate = { title, subtitle ->
                val group = PhraseGroup(
                    id = slugPhraseId(title),
                    title = title,
                    subtitle = subtitle,
                    sentences = emptyList()
                )
                app.lessonRepo.addUserPhraseGroup(group)
                showAddGroup = false
                reload(group.id)
                scope.launch { app.phraseSync.pushLocalAfterEdit() }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        PhraseGroupList(
            groups = data.groups,
            selected = selected,
            starred = starred,
            pendingCount = pendingAudio.size,
            generating = generating,
            generationStatus = generationStatus,
            generationError = generationError,
            canSync = auth.signedIn,
            syncing = auth.syncingPhrases,
            onAddGroup = ::requestAddGroup,
            onSync = ::syncPhraseGroups,
            onGeneratePending = ::generatePendingAudio,
            onSelect = { selectedId = it.id },
            modifier = Modifier
                .width(324.dp)
                .fillMaxHeight()
                .background(LbColors.Surface)
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            nowVoicing()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                selected?.let { group ->
                    PhraseDetail(
                        app = app,
                        group = group,
                        groups = data.groups,
                        onSelectGroup = { selectedId = it.id },
                        onPhraseAdded = { sentence ->
                            pendingAudio.add(sentence)
                            reload(group.id)
                        },
                        onGroupEdited = { reload(group.id) }
                    )
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No phrases yet.",
                        fontSize = 13.sp, color = LbColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseGroupList(
    groups: List<PhraseGroup>,
    selected: PhraseGroup?,
    starred: Set<String>,
    pendingCount: Int,
    generating: Boolean,
    generationStatus: String?,
    generationError: String?,
    canSync: Boolean,
    syncing: Boolean,
    onAddGroup: () -> Unit,
    onSync: () -> Unit,
    onGeneratePending: () -> Unit,
    onSelect: (PhraseGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsedCollections by remember { mutableStateOf(setOf("Songs")) }
    val collectionGroups = remember(groups) { phraseCollections(groups) }
    val railItems = remember(groups, selected?.id, collapsedCollections) {
        phraseRailItems(
            groups = groups,
            collections = collectionGroups,
            selected = selected,
            collapsed = collapsedCollections
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "phrase-rail-head") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Phrase groups")
                    Spacer(Modifier.width(8.dp))
                    HeaderIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Add phrase group",
                        onClick = onAddGroup
                    )
                    Spacer(Modifier.width(6.dp))
                    HeaderIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh phrase groups",
                        enabled = canSync && !syncing,
                        onClick = onSync
                    )
                    if (pendingCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        LbButton.Primary(
                            label = if (generating) "Gen..." else "Gen New",
                            icon = Icons.Default.CloudDownload,
                            count = pendingCount,
                            enabled = !generating,
                            onClick = onGeneratePending
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    LbChip(
                        label = groups.size.toString(),
                        selected = false,
                        onClick = {},
                        enabled = false,
                        showCheck = false
                    )
                }
                generationStatus?.let {
                    Text(it, fontSize = 12.sp, color = LbColors.TextMuted)
                }
                generationError?.let {
                    Text(it, fontSize = 12.sp, color = LbColors.Stop, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = LbColors.SurfaceTint,
                    shape = LbShapes.Inset,
                    border = BorderStroke(1.dp, LbColors.Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = LbColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search groups...", fontSize = 13.sp, color = LbColors.TextMuted)
                    }
                }
            }
        }
        itemsIndexed(railItems, key = { _, item ->
            when (item) {
                is PhraseRailItem.CollectionHeader -> "collection-${item.collection.name}"
                is PhraseRailItem.GroupRow -> "g-${item.group.id}"
            }
        }) { _, item ->
            when (item) {
                is PhraseRailItem.CollectionHeader -> {
                    PhraseCollectionRow(
                        collection = item.collection,
                        expanded = item.expanded,
                        onToggle = {
                            collapsedCollections = if (item.collection.name in collapsedCollections) {
                                collapsedCollections - item.collection.name
                            } else {
                                collapsedCollections + item.collection.name
                            }
                        }
                    )
                }
                is PhraseRailItem.GroupRow -> {
                    PhraseGroupRow(
                        group = item.group,
                        selected = item.group == selected,
                        starCount = item.group.sentences.count { it.pl in starred },
                        inCollection = item.inCollection,
                        onSelect = { onSelect(item.group) }
                    )
                }
            }
        }
    }
}

private fun phraseCollections(groups: List<PhraseGroup>): List<PhraseCollectionGroup> =
    groups
        .filter { !it.collection.isNullOrBlank() }
        .groupBy { it.collection!!.trim() }
        .map { (name, collectionGroups) -> PhraseCollectionGroup(name, collectionGroups) }

private fun phraseRailItems(
    groups: List<PhraseGroup>,
    collections: List<PhraseCollectionGroup>,
    selected: PhraseGroup?,
    collapsed: Set<String>
): List<PhraseRailItem> {
    val groupedIds = collections.flatMap { it.groups }.map { it.id }.toSet()
    val byCollection = collections.associateBy { it.name }
    val emittedCollections = mutableSetOf<String>()
    val items = mutableListOf<PhraseRailItem>()
    for (group in groups) {
        val collectionName = group.collection?.trim().orEmpty()
        if (collectionName.isBlank()) {
            items += PhraseRailItem.GroupRow(group)
            continue
        }
        if (collectionName in emittedCollections) continue
        val collection = byCollection[collectionName] ?: continue
        emittedCollections += collectionName
        val selectedId = selected?.id
        val selectedInside = selectedId != null && collection.groups.any { it.id == selectedId }
        val expanded = collectionName !in collapsed || selectedInside
        items += PhraseRailItem.CollectionHeader(collection, expanded)
        if (expanded) {
            collection.groups.forEach { items += PhraseRailItem.GroupRow(it, inCollection = true) }
        }
    }
    for (collection in collections) {
        if (collection.name !in emittedCollections) {
            val expanded = collection.name !in collapsed
            items += PhraseRailItem.CollectionHeader(collection, expanded)
            if (expanded) collection.groups.forEach { items += PhraseRailItem.GroupRow(it, inCollection = true) }
        }
    }
    return items.filterNot { item -> item is PhraseRailItem.GroupRow && !item.inCollection && item.group.id in groupedIds }
}

@Composable
private fun PhraseCollectionRow(
    collection: PhraseCollectionGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = LbColors.SurfaceTint,
        shape = LbShapes.Card,
        border = BorderStroke(1.dp, LbColors.Line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(LbShapes.Card)
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse collection" else "Expand collection",
                tint = LbColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                collection.name,
                color = LbColors.TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "[${collection.groups.size}]",
                fontSize = 11.sp,
                color = LbColors.TextMuted,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PhraseGroupRow(
    group: PhraseGroup,
    selected: Boolean,
    starCount: Int,
    inCollection: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = LbShapes.Card,
        border = BorderStroke(1.dp, if (selected) LbColors.Primary.copy(alpha = 0.25f) else LbColors.Line),
        shadowElevation = if (selected) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (inCollection) 16.dp else 0.dp)
            .clip(LbShapes.Card)
            .clickable { onSelect() }
    ) {
        Row {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (selected) LbColors.Primary else Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.title,
                        color = if (selected) LbColors.Primary else LbColors.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    if (starCount > 0) {
                        MetaChip("$starCount", star = true)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "[${group.sentences.size}]",
                        fontSize = 11.sp,
                        color = LbColors.TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhraseDetail(
    app: LangbangApplication,
    group: PhraseGroup,
    groups: List<PhraseGroup>,
    onSelectGroup: (PhraseGroup) -> Unit,
    onPhraseAdded: (SentenceExample) -> Unit,
    onGroupEdited: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val player = remember(group) { StudyQueuePlayer(app, scope) }
    val playingIndex = player.playingIndex
    val playing = player.hasQueue
    val displaySentences = remember(group.sentences) { orderedPhraseSentences(group.sentences) }
    val titleSentence = remember(group.title) { playableTitleSentence(group.title) }
    val playbackSentences = remember(titleSentence, displaySentences) {
        titleSentence?.let { listOf(it) + displaySentences } ?: displaySentences
    }
    val starred by app.starredPhrases.starred.collectAsState()
    // "Starred only" scopes the quiz to the learner's personal deck (across ALL groups),
    // not just the current group. Sticky within this composition.
    var starredOnly by remember { mutableStateOf(false) }
    var slowFirst by remember { mutableStateOf(app.practicePrefs.slowFirst()) }
    val auth by app.authStore.state.collectAsState()
    var showAccountGateForPhrase by remember(group.id) { mutableStateOf(false) }
    var showAddPhrase by remember(group.id) { mutableStateOf(false) }
    var addPhraseBusy by remember(group.id) { mutableStateOf(false) }
    var addPhraseError by remember(group.id) { mutableStateOf<String?>(null) }
    var aiPhraseBusy by remember(group.id) { mutableStateOf(false) }
    var aiPhraseStatus by remember(group.id) { mutableStateOf<String?>(null) }
    var aiPhraseError by remember(group.id) { mutableStateOf<String?>(null) }
    var aiPhraseQuota by remember(group.id) { mutableStateOf<CloudAiPhraseQuota?>(null) }
    var editingSentence by remember(group.id) { mutableStateOf<SentenceExample?>(null) }
    var editPhraseBusy by remember(group.id) { mutableStateOf(false) }
    var editPhraseError by remember(group.id) { mutableStateOf<String?>(null) }
    var showEditGroup by remember(group.id) { mutableStateOf(false) }
    fun requestAddPhrase() {
        if (auth.customItemGateSatisfied) {
            showAddPhrase = true
        } else {
            showAccountGateForPhrase = true
        }
    }
    // Stop playback when this group's detail leaves composition.
    DisposableEffect(group) {
        onDispose { player.stop() }
    }

    if (showEditGroup) {
        EditPhraseGroupDialog(
            group = group,
            onDismiss = { showEditGroup = false },
            onSave = { newTitle, newSubtitle ->
                showEditGroup = false
                app.lessonRepo.addUserPhraseGroup(group.copy(title = newTitle, subtitle = newSubtitle))
                scope.launch { app.phraseSync.pushLocalAfterEdit() }
                onGroupEdited()
            }
        )
    }

    if (showAccountGateForPhrase) {
        AccountGateDialog(
            app = app,
            onDismiss = { showAccountGateForPhrase = false },
            onReady = {
                showAccountGateForPhrase = false
                showAddPhrase = true
            }
        )
    }

    if (showAddPhrase) {
        AddPhraseDialog(
            sourceLabel = app.sourceLanguageLabel(),
            targetLabel = app.targetLanguageLabel(),
            busy = addPhraseBusy,
            error = addPhraseError,
            signedIn = auth.signedIn,
            aiBusy = aiPhraseBusy,
            aiStatus = aiPhraseStatus,
            aiError = aiPhraseError,
            aiQuota = aiPhraseQuota,
            onDismiss = {
                if (!addPhraseBusy && !aiPhraseBusy) {
                    showAddPhrase = false
                    addPhraseError = null
                    aiPhraseStatus = null
                    aiPhraseError = null
                }
            },
            onCreate = { sourceText, targetText, literal ->
                if (!addPhraseBusy) {
                    scope.launch {
                        addPhraseBusy = true
                        addPhraseError = null
                        app.cloudBackend.completePhrase(
                            sourceText = sourceText,
                            targetText = targetText,
                            literalText = literal,
                            sourceLanguage = app.sourceLanguageLabel(),
                            targetLanguage = app.targetLanguageLabel()
                        ).fold(
                            onSuccess = { sentence ->
                                val updated = app.lessonRepo.addUserPhraseSentence(group.id, sentence)
                                if (updated != null) {
                                    showAddPhrase = false
                                    onPhraseAdded(sentence)
                                    app.phraseSync.pushLocalAfterEdit()
                                } else {
                                    addPhraseError = "Phrase group is no longer available."
                                }
                            },
                            onFailure = { t ->
                                addPhraseError = t.message ?: t.javaClass.simpleName
                                android.widget.Toast.makeText(
                                    context,
                                    addPhraseError,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        addPhraseBusy = false
                    }
                }
            },
            onGenerateAi = { prompt, count ->
                if (!aiPhraseBusy) {
                    if (!auth.signedIn) {
                        aiPhraseError = "Sign in first to use AI phrase generation."
                    } else {
                        scope.launch {
                            aiPhraseBusy = true
                            aiPhraseStatus = null
                            aiPhraseError = null
                            app.cloudBackend.generateAiPhrases(
                                sessionToken = auth.sessionToken,
                                instanceId = app.cloudConfig.state.value.selectedInstanceId,
                                groupId = group.id,
                                groupTitle = group.title,
                                groupSubtitle = group.subtitle,
                                prompt = prompt,
                                count = count
                            ).fold(
                                onSuccess = { response ->
                                    response.group?.let { app.lessonRepo.addUserPhraseGroup(it) }
                                    if (response.phrases.isNotEmpty()) {
                                        response.phrases.forEach(onPhraseAdded)
                                    }
                                    aiPhraseQuota = response.quota
                                    aiPhraseStatus = "Generated ${response.phrases.size} phrase(s). ${response.quota.remaining} left."
                                    app.phraseSync.syncNow()
                                },
                                onFailure = { t ->
                                    aiPhraseError = t.message ?: t.javaClass.simpleName
                                }
                            )
                            aiPhraseBusy = false
                        }
                    }
                }
            },
            onRequestAiQuota = { message ->
                if (!aiPhraseBusy && auth.signedIn) {
                    scope.launch {
                        aiPhraseBusy = true
                        aiPhraseStatus = null
                        aiPhraseError = null
                        app.cloudBackend.requestAiPhraseQuota(
                            sessionToken = auth.sessionToken,
                            instanceId = app.cloudConfig.state.value.selectedInstanceId,
                            message = message
                        ).fold(
                            onSuccess = { response ->
                                aiPhraseQuota = response.quota
                                aiPhraseStatus = if (response.sent) {
                                    "Quota request sent."
                                } else {
                                    "Quota request recorded; email sender is not configured."
                                }
                            },
                            onFailure = { t ->
                                aiPhraseError = "Quota request failed: ${t.message ?: t.javaClass.simpleName}"
                            }
                        )
                        aiPhraseBusy = false
                    }
                }
            }
        )
    }

    editingSentence?.let { sentence ->
        EditPhraseDialog(
            sourceLabel = app.sourceLanguageLabel(),
            targetLabel = app.targetLanguageLabel(),
            sentence = sentence,
            phraseCount = displaySentences.size,
            busy = editPhraseBusy,
            error = editPhraseError,
            onDismiss = {
                if (!editPhraseBusy) {
                    editingSentence = null
                    editPhraseError = null
                }
            },
            onSave = { sourceText, targetText, newIndex, sourceChanged, targetChanged ->
                if (!editPhraseBusy) {
                    scope.launch {
                        editPhraseBusy = true
                        editPhraseError = null
                        if (!sourceChanged && !targetChanged) {
                            val replacement = sentence.copy(index = newIndex)
                            val updated = app.lessonRepo.replaceUserPhraseSentence(group.id, sentence, replacement)
                            if (updated != null) {
                                editingSentence = null
                                editPhraseError = null
                                onPhraseAdded(replacement)
                                app.phraseSync.pushLocalAfterEdit()
                            } else {
                                editPhraseError = "Phrase is no longer available."
                            }
                        } else {
                            val sourceForCompletion = if (targetChanged && !sourceChanged) "" else sourceText
                            val targetForCompletion = if (sourceChanged && !targetChanged) "" else targetText
                            app.cloudBackend.completePhrase(
                                sourceText = sourceForCompletion,
                                targetText = targetForCompletion,
                                literalText = "",
                                sourceLanguage = app.sourceLanguageLabel(),
                                targetLanguage = app.targetLanguageLabel()
                            ).fold(
                                onSuccess = { completed ->
                                    val replacement = completed.copy(index = newIndex)
                                    val updated = app.lessonRepo.replaceUserPhraseSentence(group.id, sentence, replacement)
                                    if (updated != null) {
                                        editingSentence = null
                                        editPhraseError = null
                                        onPhraseAdded(replacement)
                                        app.phraseSync.pushLocalAfterEdit()
                                    } else {
                                        editPhraseError = "Phrase is no longer available."
                                    }
                                },
                                onFailure = { t ->
                                    editPhraseError = t.message ?: t.javaClass.simpleName
                                }
                            )
                        }
                        editPhraseBusy = false
                    }
                }
            }
        )
    }

    // Warm each tappable word's (normal-voice) audio in the background the moment the
    // group opens, so tapping a word in the list voices instantly instead of waiting on
    // an on-demand synth. Cancelled + restarted automatically when the group changes.
    LaunchedEffect(displaySentences) {
        displaySentences
            .flatMap { it.pl.split(PHRASE_WHITESPACE) }
            .map { it.polishWordForPhraseAudio() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { w -> app.ensureCachedAudio(w, app.targetAudioVoice().locale, app.targetAudioVoice().voice) }
    }

    fun startPlayback(items: List<SentenceExample>, quiz: Boolean) {
        if (items.isEmpty()) return
        player.stop()
        val slowPlVoice = app.targetSlowVoice()
        val slow = app.practicePrefs.slowFirst()
        val speakEnglish = quiz || app.practicePrefs.speakEnglishFirst()
        player.start(
            total = items.size,
            publishParked = { i ->
                publishNV(items[i], "pause", "${i + 1}/${items.size}", plHidden = quiz, quiz = quiz)
            },
            prefetchItem = { i ->
                val s = items[i]
                if (speakEnglish) {
                    app.ensureCachedAudio(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                }
                app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                if (slow) app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, slowPlVoice)
            },
        ) { i ->
            val s = items[i]
            val pos = "${i + 1}/${items.size}"
            if (quiz) {
                publishNV(s, "en", pos, plHidden = true, quiz = true)
                say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                publishNV(s, "pause", pos, plHidden = true, quiz = true)
                reveal(1500L)
                if (slow) {
                    publishNV(s, "pl-slow", pos, plHidden = false, quiz = true)
                    say(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                }
                publishNV(s, "pl", pos, plHidden = false, quiz = true)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            } else {
                if (speakEnglish) {
                    publishNV(s, "en", pos)
                    say(s.en, app.sourceAudioVoice().locale, app.sourceAudioVoice().voice)
                }
                if (slow) {
                    publishNV(s, "pl-slow", pos)
                    say(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                }
                publishNV(s, "pl", pos)
                say(s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            }
        }
    }

    fun playSingleWord(raw: String) {
        val word = raw.polishWordForPhraseAudio()
        if (word.isEmpty()) return
        player.stop()
        scope.launch {
            // A list word-tap is a quick lookup: one normal-rate pronunciation, played
            // straight from cache once the group warm-up above has reached it. (The
            // deliberate slow→normal study pass still lives in the Now Voicing word taps.)
            app.ensureCachedAudio(word, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
            playAndAwait(app, word, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(phraseGroupTag(group))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            group.title,
                            fontSize = 23.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LbColors.TextPrimary
                        )
                        HeaderIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Edit group name",
                            onClick = { showEditGroup = true }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${playbackSentences.size} phrases", fontSize = 12.sp, color = LbColors.TextMuted)
                        Text("•", fontSize = 12.sp, color = LbColors.TextMuted)
                        Text("${group.sentences.count { it.pl in starred }} starred", fontSize = 12.sp, color = LbColors.TextMuted)
                    }
                }
                SelectionNavButtons(
                    items = groups,
                    selected = group,
                    onSelect = onSelectGroup,
                    previousContentDescription = "Previous phrase group",
                    nextContentDescription = "Next phrase group"
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
	                if (!playing) {
	                    LbButton.Audio(
	                        label = "Play",
	                        count = playbackSentences.size,
	                        onClick = {
	                            startPlayback(playbackSentences, quiz = false)
	                        }
	                    )
	                }
                Surface(
                    color = if (starredOnly) LbColors.PrimarySoft else Color.White,
                    shape = LbShapes.Button,
                    border = BorderStroke(1.dp, if (starredOnly) LbColors.Primary else LbColors.Line),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(LbShapes.Button)
                        .clickable { starredOnly = !starredOnly }
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (starredOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (starredOnly) LbColors.Primary else LbColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Starred only",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LbColors.TextPrimary
                        )
                    }
                }
                HeaderIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Add phrase",
                    enabled = !playing,
                    onClick = ::requestAddPhrase
                )
            }
        }

        // Sentence list
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            displaySentences.forEachIndexed { i, s ->
                SentenceRow(
                    sentence = s,
                    isCurrent = i == playingIndex,
                    isStarred = s.pl in starred,
                    onToggleStar = {
                        app.starredPhrases.toggle(s.pl)
                        scope.launch { app.phraseSync.pushLocalAfterEdit() }
                    },
                    onEdit = {
                        editPhraseError = null
                        editingSentence = s
                    },
                    onWordClick = ::playSingleWord,
                    onPlay = {
                        // Inline one-off playback stays local; queue playback drives
                        // Now Voicing so tapping a row doesn't jump the user around.
                        scope.launch {
                            val slowPlVoice = app.targetSlowVoice()
                            if (slowFirst) {
                                app.ensureCachedAudio(s.pl, app.targetAudioVoice().locale, slowPlVoice)
                                playAndAwait(app, s.pl, app.targetAudioVoice().locale, slowPlVoice)
                            }
                            playAndAwait(app, s.pl, app.targetAudioVoice().locale, app.targetAudioVoice().voice)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountGateDialog(
    app: LangbangApplication,
    onDismiss: () -> Unit,
    onReady: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val cleanedEmail = email.trim()
    val emailLooksValid = cleanedEmail.contains("@") && cleanedEmail.contains(".")
    val googleConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save custom phrases") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Sign in before creating personal content so custom phrases can be tied to your account. " +
                        "If you skip, they stay only on this tablet and may be lost if app data is cleared or the app is reinstalled.",
                    fontSize = 13.sp,
                    color = LbColors.TextSecondary
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("Email code") },
                    singleLine = true
                )
                status?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = if (it.contains("failed", ignoreCase = true)) LbColors.Stop else LbColors.TextSecondary
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Working...", fontSize = 12.sp, color = LbColors.TextMuted)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = !busy && emailLooksValid,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = app.cloudBackend.startEmailSignIn(cleanedEmail).fold(
                                onSuccess = { "Code sent to ${it.email}" },
                                onFailure = { t -> "Email sign-in failed: ${t.message ?: t.javaClass.simpleName}" }
                            )
                            busy = false
                        }
                    }
                ) {
                    Text("Send code")
                }
                TextButton(
                    enabled = !busy && emailLooksValid && code.length == 6,
                    onClick = {
                        scope.launch {
                            busy = true
                            app.cloudBackend.verifyEmailSignIn(
                                email = cleanedEmail,
                                code = code,
                                instanceId = app.cloudConfig.state.value.selectedInstanceId
                            ).fold(
                                onSuccess = { response ->
                                    app.authStore.saveAuth(response)
                                    app.phraseSync.syncNow()
                                    onReady()
                                },
                                onFailure = { t ->
                                    status = "Email verify failed: ${t.message ?: t.javaClass.simpleName}"
                                }
                            )
                            busy = false
                        }
                    }
                ) {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !busy && googleConfigured,
                    onClick = {
                        scope.launch {
                            busy = true
                            customPhraseGoogleSignIn(app, context).fold(
                                onSuccess = { onReady() },
                                onFailure = { t ->
                                    status = "Google sign-in failed: ${t.message ?: t.javaClass.simpleName}"
                                }
                            )
                            busy = false
                        }
                    }
                ) { Text("Google") }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        app.authStore.skipCustomSyncWarning()
                        onReady()
                    }
                ) { Text("Skip") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private suspend fun customPhraseGoogleSignIn(
    app: LangbangApplication,
    context: android.content.Context
): Result<CloudAuthResponse> {
    val token = GoogleSignInHelper(context)
        .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .getOrElse { return Result.failure(it) }
    return app.cloudBackend.signInWithGoogle(
        idToken = token.idToken,
        nonce = token.nonce,
        instanceId = app.cloudConfig.state.value.selectedInstanceId
    ).onSuccess { response ->
        app.authStore.saveAuth(response)
        app.phraseSync.syncNow()
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, LbColors.Line.copy(alpha = if (enabled) 1f else 0.45f)),
        modifier = Modifier
            .size(30.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) LbColors.Primary else LbColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddPhraseGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New phrase group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onCreate(title.trim(), subtitle.trim()) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditPhraseGroupDialog(
    group: PhraseGroup,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(group.title) }
    var subtitle by remember { mutableStateOf(group.subtitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit phrase group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Description") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), subtitle.trim()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddPhraseDialog(
    sourceLabel: String,
    targetLabel: String,
    busy: Boolean,
    error: String?,
    signedIn: Boolean,
    aiBusy: Boolean,
    aiStatus: String?,
    aiError: String?,
    aiQuota: CloudAiPhraseQuota?,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
    onGenerateAi: (String, Int) -> Unit,
    onRequestAiQuota: (String) -> Unit
) {
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var literal by remember { mutableStateOf("") }
    var aiPrompt by remember { mutableStateOf("") }
    var aiCount by remember { mutableStateOf(5) }
    var quotaMessage by remember { mutableStateOf("") }
    val hasAnyField = source.trim().isNotEmpty() ||
        target.trim().isNotEmpty() ||
        literal.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy && !aiBusy) onDismiss() },
        title = { Text("New phrase") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                error?.let {
                    Surface(
                        color = LbColors.Stop.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LbColors.Stop),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = LbColors.Stop,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(
                    "Manual phrase",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LbColors.TextMuted
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("$sourceLabel cue") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("$targetLabel answer") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = literal,
                    onValueChange = { literal = it },
                    label = { Text("Literal gloss") },
                    minLines = 2
                )
                Surface(
                    color = LbColors.SurfaceTint,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, LbColors.Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "AI phrase generation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LbColors.Primary
                        )
                        Text(
                            aiQuota?.let { "Quota: ${it.remaining}/${it.limit} generated phrases left." }
                                ?: "Generate up to 50 custom phrases before requesting more quota.",
                            fontSize = 11.sp,
                            color = LbColors.TextMuted
                        )
                        OutlinedTextField(
                            value = aiPrompt,
                            onValueChange = { aiPrompt = it },
                            label = { Text("Topic or goal") },
                            minLines = 2,
                            enabled = !aiBusy
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Count", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = !aiBusy && aiCount > 1,
                                onClick = { aiCount = (aiCount - 1).coerceAtLeast(1) }
                            ) { Text("-") }
                            Text(
                                "$aiCount",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            TextButton(
                                enabled = !aiBusy && aiCount < 10,
                                onClick = { aiCount = (aiCount + 1).coerceAtMost(10) }
                            ) { Text("+") }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                enabled = signedIn && !aiBusy && aiPrompt.trim().isNotEmpty(),
                                onClick = { onGenerateAi(aiPrompt.trim(), aiCount) }
                            ) {
                                Text(if (aiBusy) "Generating..." else "Generate")
                            }
                        }
                        if (!signedIn) {
                            Text(
                                "Sign in to use AI phrase generation.",
                                fontSize = 11.sp,
                                color = LbColors.Stop,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        aiStatus?.let {
                            Text(it, fontSize = 11.sp, color = LbColors.Success, fontWeight = FontWeight.Bold)
                        }
                        aiError?.let {
                            Text(it, fontSize = 11.sp, color = LbColors.Stop, fontWeight = FontWeight.Bold)
                        }
                        if ((aiQuota?.remaining == 0 || aiError?.contains("quota", ignoreCase = true) == true) && signedIn) {
                            OutlinedTextField(
                                value = quotaMessage,
                                onValueChange = { quotaMessage = it },
                                label = { Text("Quota request note") },
                                minLines = 2,
                                enabled = !aiBusy
                            )
                            TextButton(
                                enabled = !aiBusy,
                                onClick = { onRequestAiQuota(quotaMessage.trim()) }
                            ) { Text("Request more quota") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasAnyField && !busy,
                onClick = { onCreate(source.trim(), target.trim(), literal.trim()) }
            ) {
                Text(if (busy) "Checking..." else "Add")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy && !aiBusy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditPhraseDialog(
    sourceLabel: String,
    targetLabel: String,
    sentence: SentenceExample,
    phraseCount: Int,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Boolean, Boolean) -> Unit
) {
    var source by remember(sentence) { mutableStateOf(sentence.en) }
    var target by remember(sentence) { mutableStateOf(sentence.pl) }
    var indexText by remember(sentence) { mutableStateOf((sentence.index ?: 1).toString()) }
    val parsedIndex = indexText.toIntOrNull()?.coerceIn(1, phraseCount.coerceAtLeast(1))
    val sourceChanged = source.trim() != sentence.en.trim()
    val targetChanged = target.trim() != sentence.pl.trim()
    val indexChanged = parsedIndex != null && parsedIndex != (sentence.index ?: 1)
    val canSave = !busy && parsedIndex != null && (sourceChanged || targetChanged || indexChanged) &&
        (source.trim().isNotEmpty() || target.trim().isNotEmpty())
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = LbColors.SurfaceRaised,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Edit phrase",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = LbColors.TextPrimary
                    )
                    CompactIndexEditor(
                        value = indexText,
                        max = phraseCount.coerceAtLeast(1),
                        enabled = !busy,
                        onValueChange = { indexText = it }
                    )
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text("$sourceLabel cue") },
                        minLines = 1,
                        maxLines = 3,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("$targetLabel phrase") },
                        minLines = 1,
                        maxLines = 3,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            color = LbColors.Stop,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LbButton.Ghost(
                        label = "Cancel",
                        enabled = !busy,
                        onClick = onDismiss
                    )
                    Spacer(Modifier.width(8.dp))
                    LbButton.Primary(
                        label = if (busy) "Translating..." else "Save",
                        enabled = canSave,
                        onClick = { onSave(source.trim(), target.trim(), parsedIndex ?: 1, sourceChanged, targetChanged) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIndexEditor(
    value: String,
    max: Int,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val current = value.toIntOrNull()?.coerceIn(1, max) ?: 1
    Row(
        modifier = Modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Index",
            fontSize = 11.sp,
            color = if (enabled) LbColors.TextSecondary else LbColors.TextMuted
        )
        Spacer(Modifier.width(4.dp))
        Surface(
            color = LbColors.Sheet,
            shape = LbShapes.Button,
            border = BorderStroke(1.dp, LbColors.Line),
            modifier = Modifier
                .width(54.dp)
                .height(30.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IndexStepperArrow(
                    decrement = true,
                    enabled = enabled && current > 1,
                    onClick = { onValueChange((current - 1).toString()) }
                )
                Text(
                    current.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) LbColors.TextPrimary else LbColors.TextMuted,
                    modifier = Modifier.width(20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IndexStepperArrow(
                    decrement = false,
                    enabled = enabled && current < max,
                    onClick = { onValueChange((current + 1).toString()) }
                )
            }
        }
    }
}

@Composable
private fun IndexStepperArrow(
    decrement: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = LbShapes.Button,
        modifier = Modifier
            .width(17.dp)
            .height(30.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (decrement) {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = if (decrement) "Move earlier" else "Move later",
                tint = if (enabled) LbColors.TextSecondary else LbColors.TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun PhraseToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = if (checked) LbColors.PrimarySoft else Color.White,
        shape = LbShapes.Button,
        border = BorderStroke(1.dp, if (checked) LbColors.Primary else LbColors.Line),
        modifier = Modifier
            .height(30.dp)
            .clip(LbShapes.Button)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubtleCheckbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) LbColors.TextPrimary else LbColors.TextMuted
            )
        }
    }
}

@Composable
private fun SentenceRow(
    sentence: SentenceExample,
    isCurrent: Boolean,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    onEdit: () -> Unit,
    onWordClick: (String) -> Unit,
    onPlay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LbColors.SuccessSoft else LbColors.SurfaceRaised
        ),
        border = BorderStroke(1.dp, if (isCurrent) LbColors.Audio.copy(alpha = 0.45f) else LbColors.Line),
        shape = LbShapes.Card,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = LbColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(18.dp)
                    .clickable(onClick = onPlay)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (sentence.index ?: 1).toString().padStart(2, '0'),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LbColors.TextMuted.copy(alpha = 0.58f),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    DelayedEnglishTranslation(
                        text = sentence.en,
                        fontSize = 12.sp,
                        color = LbColors.TextMuted
                    )
                }
                WordAlignedPolish(
                    sentence = sentence,
                    plFontSize = 19.sp,
                    plFontWeight = FontWeight.Bold,
                    glossFontSize = 10.sp,
                    onPlWordClick = onWordClick
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeaderIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit phrase",
                    onClick = onEdit
                )
                StarToggle(selected = isStarred, onClick = onToggleStar)
            }
        }
    }
}

private fun orderedPhraseSentences(sentences: List<SentenceExample>): List<SentenceExample> =
    sentences
        .mapIndexed { fallback, sentence -> sentence.copy(index = sentence.index ?: fallback + 1) }
        .sortedWith(compareBy<SentenceExample> { it.index ?: Int.MAX_VALUE })
        .mapIndexed { index, sentence -> sentence.copy(index = index + 1) }

private fun playableTitleSentence(title: String): SentenceExample? {
    val cleaned = title.trim()
    if (cleaned.isEmpty() || !looksLikePolishTargetText(cleaned)) return null
    return SentenceExample(
        pl = cleaned,
        en = cleaned,
        literal = cleaned,
        words = cleaned.split(PHRASE_WHITESPACE)
            .filter { it.isNotBlank() }
            .map { com.sponic.langbang.data.model.TokenPair(pl = it, en = it) },
        index = 0
    )
}

private fun looksLikePolishTargetText(text: String): Boolean =
    text.any { it in "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ" }

@Composable
private fun MetaChip(text: String, star: Boolean = false) {
    Surface(
        color = if (star) LbColors.GoldSoft else LbColors.SurfaceTint,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (star) LbColors.Star.copy(alpha = 0.25f) else LbColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (star) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = LbColors.Star, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(text, fontSize = 11.sp, color = if (star) LbColors.Star else LbColors.TextMuted, fontWeight = FontWeight.Bold)
        }
    }
}

private fun phraseGroupTag(group: PhraseGroup): String {
    val text = "${group.title} ${group.subtitle}".lowercase()
    return when {
        "intro" in text || "meet" in text -> "conversation"
        "home" in text || "daily" in text || "food" in text -> "daily life"
        "travel" in text || "city" in text || "shop" in text -> "around town"
        else -> "practice"
    }
}

private fun audioPhrasesFor(
    app: LangbangApplication,
    sentences: List<SentenceExample>
): List<R2AudioDownloader.Phrase> {
    val source = app.sourceAudioVoice()
    val target = app.targetAudioVoice()
    val slowVoices = app.lessonRepo.targetSlowVoices().ifEmpty {
        listOf(
            "${target.voice}${AzureTtsClient.SLOW_SUFFIX_V2}",
            "${target.voice}${AzureTtsClient.SLOW_SUFFIX_V3}"
        )
    }
    return buildList {
        sentences.forEach { sentence ->
            if (sentence.en.isNotBlank()) {
                add(R2AudioDownloader.Phrase(sentence.en, source.voice, source.locale))
            }
            if (sentence.pl.isNotBlank()) {
                add(R2AudioDownloader.Phrase(sentence.pl, target.voice, target.locale))
                slowVoices.forEach { slowVoice ->
                    add(R2AudioDownloader.Phrase(sentence.pl, slowVoice, target.locale))
                }
            }
        }
    }.distinctBy { "${it.locale}|${it.voice}|${it.text}" }
}

private fun slugPhraseId(title: String): String {
    val base = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "phrase-group" }
    return "$base-${System.currentTimeMillis().toString(36)}"
}

private const val PHRASE_WORD_EDGE_PUNCTUATION = ".,;:!?()[]{}\"'"
private val PHRASE_WHITESPACE = Regex("\\s+")

private fun String.polishWordForPhraseAudio(): String =
    trim().trim { PHRASE_WORD_EDGE_PUNCTUATION.contains(it) }

private fun publishNV(
    s: SentenceExample,
    lang: String,
    position: String,
    plHidden: Boolean = false,
    quiz: Boolean = false
) {
    NowVoicingBus.publish(
        NowVoicing(
            en = s.en, pl = s.pl, literal = s.literal,
            lang = lang, position = position, words = s.words,
            plHidden = plHidden, quizMode = quiz
        )
    )
}

private suspend fun playAndAwait(
    app: LangbangApplication,
    text: String,
    locale: String,
    voice: String
) {
    app.playAudioAndAwait(text, locale, voice)
}
