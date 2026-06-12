package app.pwhs.universalinstaller.presentation.install

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

@Composable
fun InstallTargetPicker(
    profiles: List<DeviceUserProfile>,
    allUsers: Boolean,
    selectedUserId: Int?,
    onSelectAllUsers: (Boolean) -> Unit,
    onSelectUserId: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TargetOptionRow(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.dialog_menu_target_current),
            subtitle = stringResource(R.string.dialog_menu_target_current_sub),
            selected = !allUsers && selectedUserId == null,
            onClick = {
                onSelectAllUsers(false)
                onSelectUserId(null)
            },
        )
        TargetOptionRow(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.dialog_menu_target_all),
            subtitle = stringResource(R.string.dialog_menu_target_all_sub),
            selected = allUsers,
            onClick = {
                onSelectAllUsers(true)
                onSelectUserId(null)
            },
        )

        if (profiles.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text(
                text = stringResource(R.string.dialog_menu_target_visible_profiles),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
            )
            profiles.forEach { profile ->
                TargetOptionRow(
                    icon = when {
                        profile.isWorkProfile -> Icons.Rounded.Work
                        profile.isOwner -> Icons.Rounded.Person
                        else -> Icons.Rounded.Lock
                    },
                    title = profile.displayName,
                    subtitle = "User ID: ${profile.id}",
                    selected = !allUsers && selectedUserId == profile.id,
                    onClick = {
                        onSelectAllUsers(false)
                        onSelectUserId(profile.id)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.dialog_menu_target_hidden_users_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp, end = 8.dp),
        )
    }
}

@Composable
private fun TargetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .background(container)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
