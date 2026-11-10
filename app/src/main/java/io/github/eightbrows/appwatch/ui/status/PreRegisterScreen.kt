package io.github.eightbrows.appwatch.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.status.AppStatusEntry
import io.github.eightbrows.appwatch.data.status.ShareParser
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.components.AdminTopBar
import io.github.eightbrows.appwatch.ui.components.StatusMark
import io.github.eightbrows.appwatch.ui.components.labelRes
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme
import kotlinx.coroutines.launch

/** Google Play などから共有で受け取った文面（ACTION_SEND、text/plain）。[id] で同じ文面の2回目の共有も見分ける。 */
data class SharedText(val text: String, val subject: String?, val id: Long)

/**
 * 注意アプリの事前登録（管理者）。画面サンプル 08_pre_register.png、仕様書 6章。
 * 共有で開いたときは、その文面から URL（パッケージ名）とアプリ名を入れる。
 */
@Composable
fun PreRegisterRoute(
    controller: AppStatusController,
    sharedText: SharedText?,
    onSharedTextConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val entries by remember { controller.localEntries }.collectAsState(initial = null)
    var input by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotice = rememberNoticePresenter(controller, snackbarHostState)

    // 共有で受け取った文面を入れる（URL があれば URL だけ、なければ文面のまま）
    LaunchedEffect(sharedText) {
        val shared = sharedText ?: return@LaunchedEffect
        input = ShareParser.findUrl(shared.text) ?: shared.text.trim()
        name = ShareParser.extractAppName(shared.text, shared.subject).orEmpty()
        onSharedTextConsumed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        AdminTopBar(title = stringResource(R.string.settings_pre_register), onBack = onBack)
        val currentEntries = entries
        if (currentEntries == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val all = currentEntries
        val packageName = remember(input) { ShareParser.extractPackageName(input) }
        val existing = remember(all, packageName) { all.firstOrNull { it.packageName == packageName } }
        val registeredAbsent = remember(all) {
            all.filter { it.isPreRegistered && !it.isPresent }.sortedBy { it.name.lowercase() }
        }

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HowTo()
                FieldLabel(stringResource(R.string.prereg_input_label))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    placeholder = {
                        Text(
                            stringResource(R.string.prereg_input_hint),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
                PackageLine(input, packageName)
                if (existing != null) ExistingNotes(existing)
                FieldLabel(stringResource(R.string.prereg_name_label))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
                Text(
                    text = stringResource(R.string.prereg_name_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldLabel(stringResource(R.string.app_admin_memo))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text(stringResource(R.string.prereg_memo_hint)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
                FieldLabel(pluralStringResource(R.plurals.prereg_registered_list, registeredAbsent.size, registeredAbsent.size))
                RegisteredList(registeredAbsent, onOpenDetail)
                Spacer(Modifier.height(72.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp),
            )
        }
        RegisterButton(
            enabled = packageName != null,
            onClick = {
                val target = packageName ?: return@RegisterButton
                val displayName = name.trim().ifEmpty { existing?.name ?: target }
                val newName = name
                val newMemo = memo
                // 登録の後は入力欄を空にする
                input = ""
                name = ""
                memo = ""
                scope.launch {
                    showNotice(controller.preRegister(target, newName, newMemo, displayName))
                }
            },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** 登録のしかたの説明。 */
@Composable
private fun HowTo() {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.prereg_howto_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.prereg_howto_1), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.prereg_howto_2, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.prereg_howto_3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 取り出したパッケージ名（✓）、または「パッケージ名が見つかりません」。入力が空なら何も出さない。 */
@Composable
private fun PackageLine(input: String, packageName: String?) {
    if (input.isBlank()) return
    val colors = AppWatchTheme.colors
    if (packageName != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mark_ok), color = colors.ok, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.prereg_package_found, packageName),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.ok,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.prereg_package_not_found), style = MaterialTheme.typography.bodySmall, color = colors.danger)
        }
    }
}

/** すでに登録済み・端末に入っているときの案内。どちらも登録し直せる。 */
@Composable
private fun ExistingNotes(entry: AppStatusEntry) {
    val colors = AppWatchTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (entry.isPreRegistered) {
            Text(stringResource(R.string.prereg_already_registered), style = MaterialTheme.typography.bodySmall, color = colors.onWarningContainer)
        }
        if (entry.isPresent) {
            Text(
                stringResource(R.string.prereg_installed, stringResource(entry.status.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onWarningContainer,
            )
        }
        if (!entry.isPreRegistered && !entry.isPresent && entry.status != ReviewStatus.UNCONFIRMED) {
            // 端末にないが確認状態が残っているアプリ（以前に入っていたもの）
            Text(
                stringResource(R.string.prereg_installed_before, stringResource(entry.status.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onWarningContainer,
            )
        }
    }
}

/** 登録済み（端末にない）の一覧。行を押すと管理者用の詳細を開く。 */
@Composable
private fun RegisteredList(entries: List<AppStatusEntry>, onOpenDetail: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.prereg_registered_empty),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(entry.packageName) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusMark(status = entry.status, size = 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            entry.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = AppWatchTheme.colors
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onDanger),
        ) {
            Text(stringResource(R.string.prereg_register), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
