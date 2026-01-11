package com.odom.lullaby

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier) {

    val adUnitId = stringResource(R.string.TEST_admob_banner_id)

    AndroidView(modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                // 테스트용 배너 ID입니다. 출시 전 실제 ID로 변경하세요.
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
