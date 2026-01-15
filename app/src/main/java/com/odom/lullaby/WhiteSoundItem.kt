package com.odom.lullaby


// WhiteSound 데이터 클래스
data class WhiteSoundItem(
    val soundFileName: String,  // asset whitesound 폴더의 파일명
    val displayName: String,    // 표시할 이름
    val imageResId: Int = R.drawable.ic_launcher4  // 이미지 리소스 ID (기본값: launcher)
)
