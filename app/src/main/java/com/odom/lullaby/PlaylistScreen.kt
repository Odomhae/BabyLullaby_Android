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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.exoplayer2.Player

@Composable
fun PlaylistScreen(
    player: ExoPlayer,
    playlist: SnapshotStateList<MediaItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    onResetTimer: () -> Unit = {},
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
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { player.seekToPreviousMediaItem() },
                enabled = playlist.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    modifier = Modifier.size(40.dp),
                    contentDescription = "이전곡"

                )
            }

            IconButton(
                onClick = {
                  //  if (isPlaying) player.pause() else player.play()
                    if (isPlaying) {
                        player.pause()
                    } else {
                        if (player.playbackState == Player.STATE_IDLE) {
                            player.prepare()
                        }
                        player.play()
                    }
                },
                enabled = playlist.isNotEmpty()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    modifier = Modifier.size(40.dp),
                    contentDescription = if (isPlaying) "일시정지" else "재생"
                )
            }

            // 플레이리스트에 아이템이 있고 플레이어가 정지 상태가 아닐 때 정지 버튼을 표시합니다.
            if (playlist.isNotEmpty() && player.playbackState != Player.STATE_IDLE) {
                IconButton(onClick = { 
                    player.stop()
                    onResetTimer()
                }) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        modifier = Modifier.size(40.dp),
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
                    modifier = Modifier.size(40.dp),
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

