package com.odom.lullaby

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 현재 재생 중인 곡 표시
        Text(
            text = currentItem?.mediaId ?: stringResource(R.string.no_playing_song),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 제어 버튼들 (아이콘으로 표시)
        Row(
            horizontalArrangement = Arrangement.Center, //Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { player.seekToPreviousMediaItem() },
                enabled = playlist.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "이전곡"
                )
            }

            IconButton(onClick = {
                if (isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "일시정지" else "재생"
                )
            }

            // isPlaying이 true일 때만 정지 버튼을 표시합니다.
            if (isPlaying) {
                IconButton(onClick = { player.stop() }) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "정지"
                    )
                }
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

//            IconButton(
//                onClick = {
//                    if (currentIndex >= 0 && currentIndex < playlist.size) {
//                        val wasLastItem = currentIndex == playlist.size - 1
//                        playlist.removeAt(currentIndex)
//                        player.removeMediaItem(currentIndex)
//                        // If we removed the last item and there are still items, move to previous
//                        if (wasLastItem && playlist.isNotEmpty() && currentIndex > 0) {
//                            player.seekToPreviousMediaItem()
//                        }
//                    }
//                },
//                enabled = playlist.isNotEmpty() && currentIndex >= 0
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Delete,
//                    contentDescription = "삭제"
//                )
//            }
        }
    }
}

