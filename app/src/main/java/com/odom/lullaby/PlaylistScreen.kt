package com.odom.lullaby

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlaylistScreen(
    player: ExoPlayer,
    playlist: SnapshotStateList<MediaItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val currentItem = playlist.getOrNull(currentIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 현재 재생 중인 곡 표시
        Text(
            text = currentItem?.mediaId ?: "재생 중인 곡 없음",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 제어 버튼들 (아이콘으로 표시)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = {
                if (isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "일시정지" else "재생"
                )
            }

            IconButton(onClick = { player.stop() }) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "정지"
                )
            }

            IconButton(
                onClick = { player.seekToNextMediaItem() },
                enabled = playlist.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "다음곡"
                )
            }

            IconButton(
                onClick = { player.seekToPreviousMediaItem() },
                enabled = playlist.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "이전곡"
                )
            }

            IconButton(
                onClick = {
                    if (currentIndex >= 0 && currentIndex < playlist.size) {
                        val wasLastItem = currentIndex == playlist.size - 1
                        playlist.removeAt(currentIndex)
                        player.removeMediaItem(currentIndex)
                        // If we removed the last item and there are still items, move to previous
                        if (wasLastItem && playlist.isNotEmpty() && currentIndex > 0) {
                            player.seekToPreviousMediaItem()
                        }
                    }
                },
                enabled = playlist.isNotEmpty() && currentIndex >= 0
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제"
                )
            }
        }
    }
}

