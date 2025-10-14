package com.SICV.plurry.security

import android.util.Log
import java.util.regex.Pattern

class AndroidSecurityValidator {

    companion object {
        private const val TAG = "AndroidSecurityValidator"

        // XSS 공격 패턴 - WebView나 HTML 렌더링시 위험
        private val XSS_PATTERNS = listOf(
            Pattern.compile("<script.*?>.*?</script>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL),
            Pattern.compile("<.*?javascript:.*?>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<.*?on\\w+\\s*=", Pattern.CASE_INSENSITIVE), // onclick, onload 등
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe.*?>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object.*?>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed.*?>", Pattern.CASE_INSENSITIVE)
        )

        // 안드로이드 특화 위험 패턴
        private val ANDROID_DANGEROUS_PATTERNS = listOf(
            Pattern.compile("intent://", Pattern.CASE_INSENSITIVE), // 악성 인텐트
            Pattern.compile("content://", Pattern.CASE_INSENSITIVE), // 컨텐츠 프로바이더 접근
            Pattern.compile("file://", Pattern.CASE_INSENSITIVE), // 파일 시스템 접근
            Pattern.compile("android-app://", Pattern.CASE_INSENSITIVE), // 앱 링크 조작
            Pattern.compile("market://", Pattern.CASE_INSENSITIVE) // 플레이스토어 리다이렉트
        )

        // Firebase/NoSQL 인젝션 방지 패턴
        private val INJECTION_PATTERNS = listOf(
            Pattern.compile("(\\$|\\{|\\})", Pattern.CASE_INSENSITIVE), // MongoDB 스타일 인젝션
            Pattern.compile("(__proto__|constructor|prototype)", Pattern.CASE_INSENSITIVE), // JS 프로토타입 오염
            Pattern.compile("(\\||\\&\\&|;)", Pattern.CASE_INSENSITIVE), // 명령어 체이닝
            Pattern.compile("(null|undefined|NaN)", Pattern.CASE_INSENSITIVE) // JS 타입 조작
        )

        // 허용되는 문자 패턴 (한글, 영문, 숫자, 기본 특수문자)
        private val VALID_TEXT_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9\\s.,!?@#%^&*()\\-_+=\\[\\]{}:;\"'~`|\\\\/<>]+$")

        // 허용되는 파일 확장자 (이미지만)
        private val ALLOWED_IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

        // 금지어 목록 (욕설, 부적절한 단어)
        private val BLOCKED_WORDS = listOf(
            "admin", "root", "system", "null", "undefined",
            "javascript", "script", "alert", "prompt", "confirm",
            "password", "secret", "token", "key", "auth","시발"
        )
    }

     //사용자 닉네임 보안 검증 - XSS, 인젝션, 사회공학적 공격
    fun validateNickname(nickname: String): ValidationResult {
        // 1. 기본 검증
        if (nickname.isBlank()) {
            return ValidationResult(false, "닉네임을 입력해주세요.")
        }

        if (nickname.length > 20) {
            return ValidationResult(false, "닉네임은 20자 이하로 입력해주세요.")
        }

        if (nickname.length < 2) {
            return ValidationResult(false, "닉네임은 2자 이상 입력해주세요.")
        }

        // 2. XSS 공격 패턴 검증
        for (pattern in XSS_PATTERNS) {
            if (pattern.matcher(nickname).find()) {
                Log.w(TAG, "XSS 공격 패턴 탐지: $nickname")
                return ValidationResult(false, "허용되지 않는 문자가 포함되어 있습니다.")
            }
        }

        // 3. 안드로이드 특화 위험 패턴 검증
        for (pattern in ANDROID_DANGEROUS_PATTERNS) {
            if (pattern.matcher(nickname).find()) {
                Log.w(TAG, "안드로이드 위험 패턴 탐지: $nickname")
                return ValidationResult(false, "허용되지 않는 프로토콜이 포함되어 있습니다.")
            }
        }

        // 4. 인젝션 공격 패턴 검증
        for (pattern in INJECTION_PATTERNS) {
            if (pattern.matcher(nickname).find()) {
                Log.w(TAG, "인젝션 공격 패턴 탐지: $nickname")
                return ValidationResult(false, "허용되지 않는 특수문자가 포함되어 있습니다.")
            }
        }

        // 5. 금지어 필터링
        for (word in BLOCKED_WORDS) {
            if (nickname.lowercase().contains(word.lowercase())) {
                Log.w(TAG, "금지어 사용 시도: $nickname")
                return ValidationResult(false, "사용할 수 없는 단어가 포함되어 있습니다.")
            }
        }

        // 6. 연속된 특수문자 검증
        if (nickname.contains(Regex("[^가-힣a-zA-Z0-9\\s]{3,}"))) {
            return ValidationResult(false, "특수문자는 연속으로 3개 이상 사용할 수 없습니다.")
        }

        return ValidationResult(true, "검증 성공")
    }

     // 이미지 파일 보안 검증 - 악성 파일 업로드, Path Traversal, 파일 크기 공격
    fun validateImageFile(fileName: String, fileSize: Long, mimeType: String? = null): ValidationResult {
        // 1. 파일명 기본 검증
        if (fileName.isBlank()) {
            return ValidationResult(false, "파일명이 없습니다.")
        }

        // 2. Path Traversal 공격 방지
        val dangerousChars = listOf("../", "..\\", "/", "\\", ":", "*", "?", "\"", "<", ">", "|")
        for (char in dangerousChars) {
            if (fileName.contains(char)) {
                Log.w(TAG, "위험한 파일명 패턴 탐지: $fileName")
                return ValidationResult(false, "올바르지 않은 파일명입니다.")
            }
        }

        // 3. 파일 확장자 검증 (화이트리스트 방식)
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension !in ALLOWED_IMAGE_EXTENSIONS) {
            Log.w(TAG, "허용되지 않는 파일 확장자: $extension")
            return ValidationResult(false, "이미지 파일만 업로드 가능합니다. (jpg, png, gif, webp)")
        }

        // 4. 이중 확장자 검증 (예: image.jpg.exe)
        if (fileName.count { it == '.' } > 1) {
            Log.w(TAG, "이중 확장자 파일 탐지: $fileName")
            return ValidationResult(false, "파일명에 점(.)이 여러 개 포함될 수 없습니다.")
        }

        // 5. 파일 크기 검증 (10MB 제한)
        val maxSize = 10 * 1024 * 1024 // 10MB
        if (fileSize > maxSize) {
            return ValidationResult(false, "파일 크기는 10MB 이하만 가능합니다.")
        }

        if (fileSize < 1024) { // 1KB 미만
            return ValidationResult(false, "너무 작은 파일입니다.")
        }

        // 6. MIME 타입 검증 (있는 경우)
        mimeType?.let { mime ->
            val allowedMimeTypes = listOf(
                "image/jpeg", "image/jpg", "image/png",
                "image/gif", "image/webp", "image/bmp"
            )
            if (mime !in allowedMimeTypes) {
                Log.w(TAG, "허용되지 않는 MIME 타입: $mime")
                return ValidationResult(false, "지원하지 않는 이미지 형식입니다.")
            }
        }

        return ValidationResult(true, "검증 성공")
    }


     // Firebase 쿼리용 문자열 정제 - NoSQL 인젝션, 데이터 오염
    fun sanitizeForFirebaseQuery(input: String): String {
        return input
            .replace(Regex("[{}$]"), "") // MongoDB 스타일 인젝션 제거
            .replace(Regex("[|&;]"), "") // 명령어 체이닝 제거
            .trim()
            .take(100) // 최대 길이 제한
    }

     // 웹뷰용 HTML 안전 변환 - XSS 공격

    fun sanitizeForWebView(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }


     // Intent용 데이터 정제 - Intent 조작 공격
    fun sanitizeForIntent(input: String): String {
        return input
            .replace(Regex("(intent|content|file|android-app)://"), "") // 위험한 프로토콜 제거
            .replace(Regex("[<>\"']"), "") // HTML/JS 문자 제거
            .trim()
            .take(200) // 최대 길이 제한
    }
}


 // 검증 결과를 담는 데이터 클래스

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val securityLevel: SecurityLevel = SecurityLevel.MEDIUM
)


// 보안 위험 수준
enum class SecurityLevel {
    LOW,    // 일반적인 입력 오류
    MEDIUM, // 의심스러운 패턴
    HIGH,   // 명확한 공격 시도
    CRITICAL // 즉시 차단 필요
}