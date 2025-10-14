package com.SICV.plurry.crewstep

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.SICV.plurry.security.AndroidSecurityValidator
import com.google.firebase.firestore.FirebaseFirestore

class CrewFindActivity : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageView
    private lateinit var recyclerView: RecyclerView
    private val firestore = FirebaseFirestore.getInstance()
    private val securityValidator = AndroidSecurityValidator()

    private val crewList = mutableListOf<Crew>()
    private lateinit var adapter: CrewAdapter
    private var isLoading = false

    companion object {
        private const val TAG = "CrewFindActivity"
        private const val MAX_SEARCH_LENGTH = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_find)

        searchEditText = findViewById(R.id.editText)
        searchButton = findViewById(R.id.crewSearchBtn)
        recyclerView = findViewById(R.id.findCrewView)

        adapter = CrewAdapter(crewList) { crew ->
            val intent = Intent(this, CrewLineMainActivity::class.java)
            intent.putExtra("crewId", securityValidator.sanitizeForIntent(crew.crewId))
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchEditText.requestFocus()
        searchEditText.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()

            if (query.isEmpty()) {
                Toast.makeText(this, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 검색어 길이
            if (query.length > MAX_SEARCH_LENGTH) {
                Toast.makeText(
                    this,
                    "검색어는 ${MAX_SEARCH_LENGTH}자 이하로 입력해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val validationResult = validateSearchQuery(query)

            if (!validationResult.isValid) {
                Log.w(TAG, "검색어 보안 검증 실패: $query - ${validationResult.message}")
                Toast.makeText(this, validationResult.message, Toast.LENGTH_LONG).show()
                searchEditText.setText("")
                return@setOnClickListener
            }

            val sanitizedQuery = securityValidator.sanitizeForFirebaseQuery(query)
            Log.d(TAG, "검색 수행 - 원본: $query, 정제됨: $sanitizedQuery")

            searchCrew(sanitizedQuery)
        }
    }

    private fun validateSearchQuery(query: String): com.SICV.plurry.security.ValidationResult {
        if (query.isBlank()) {
            return com.SICV.plurry.security.ValidationResult(
                false,
                "검색어를 입력해주세요."
            )
        }

        if (query.length > 50) {
            return com.SICV.plurry.security.ValidationResult(
                false,
                "검색어는 50자 이하로 입력해주세요."
            )
        }

        val securityCheckResult = performSecurityChecks(query)
        if (!securityCheckResult.isValid) {
            return securityCheckResult
        }

        if (query.length > 1) {
            return securityValidator.validateNickname(query)
        }

        return com.SICV.plurry.security.ValidationResult(true, "검증 성공")
    }

    private fun performSecurityChecks(query: String): com.SICV.plurry.security.ValidationResult {
        // XSS 공격
        val xssPatterns = listOf(
            "<", ">", "script", "javascript:", "onclick", "onerror",
            "onload", "iframe", "object", "embed", "eval"
        )
        for (pattern in xssPatterns) {
            if (query.contains(pattern, ignoreCase = true)) {
                Log.w(TAG, "XSS 공격 패턴 탐지: $query")
                return com.SICV.plurry.security.ValidationResult(
                    false,
                    "허용되지 않는 문자가 포함되어 있습니다."
                )
            }
        }

        // 안드로이드 위험 프로토콜
        val dangerousProtocols = listOf("intent", "content", "file", "android", "market")
        for (protocol in dangerousProtocols) {
            if (query.contains(protocol, ignoreCase = true)) {
                Log.w(TAG, "위험 프로토콜 탐지: $query")
                return com.SICV.plurry.security.ValidationResult(
                    false,
                    "허용되지 않는 프로토콜이 포함되어 있습니다."
                )
            }
        }

        // NoSQL 인젝션
        val injectionChars = listOf("$", "{", "}", "|", "&", ";", "\\")
        for (char in injectionChars) {
            if (query.contains(char)) {
                Log.w(TAG, "인젝션 문자 탐지: $query")
                return com.SICV.plurry.security.ValidationResult(
                    false,
                    "허용되지 않는 특수문자가 포함되어 있습니다."
                )
            }
        }

        // 금지어
        val blockedWords = listOf(
            "admin", "root", "system", "null", "undefined",
            "password", "token", "key", "auth"
        )
        for (word in blockedWords) {
            if (query.equals(word, ignoreCase = true)) {
                Log.w(TAG, "금지어 탐지: $query")
                return com.SICV.plurry.security.ValidationResult(
                    false,
                    "사용할 수 없는 단어입니다."
                )
            }
        }

        return com.SICV.plurry.security.ValidationResult(true, "검증 성공")
    }

    private fun searchCrew(query: String) {
        if (isLoading) return
        isLoading = true

        firestore.collection("Crew")
            .get()
            .addOnSuccessListener { documents ->
                crewList.clear()

                if (documents.isEmpty) {
                    adapter.notifyDataSetChanged()
                    isLoading = false
                    Toast.makeText(this, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                var pendingTasks = documents.size()
                var foundCount = 0

                for (doc in documents) {
                    val crewId = doc.id
                    val name = doc.getString("name") ?: ""
                    val crewProfileUrl = doc.getString("CrewProfile") ?: ""
                    val mainField = doc.getString("mainField") ?: ""

                    if (name.contains(query, ignoreCase = true) ||
                        mainField.contains(query, ignoreCase = true)) {

                        foundCount++

                        firestore.collection("Crew").document(crewId)
                            .collection("member")
                            .document("members")
                            .get()
                            .addOnSuccessListener { memberDoc ->
                                val memberCount = memberDoc?.data?.size ?: 0
                                val crew = Crew(
                                    crewId = crewId,
                                    name = name,
                                    crewProfileUrl = crewProfileUrl,
                                    mainField = mainField,
                                    memberCount = memberCount
                                )
                                crewList.add(crew)

                                pendingTasks--
                                if (pendingTasks == 0) {
                                    adapter.notifyDataSetChanged()
                                    isLoading = false

                                    if (foundCount == 0) {
                                        Toast.makeText(
                                            this,
                                            "검색 결과가 없습니다.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Log.d(TAG, "검색 완료: ${foundCount}개의 크루 발견")
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "멤버 수 조회 실패: ${e.message}")
                                pendingTasks--
                                if (pendingTasks == 0) {
                                    adapter.notifyDataSetChanged()
                                    isLoading = false

                                    if (foundCount == 0) {
                                        Toast.makeText(
                                            this,
                                            "검색 결과가 없습니다.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                    } else {
                        pendingTasks--
                        if (pendingTasks == 0) {
                            adapter.notifyDataSetChanged()
                            isLoading = false

                            if (foundCount == 0) {
                                Toast.makeText(
                                    this,
                                    "검색 결과가 없습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "검색 실패: ${e.message}")
                isLoading = false
                Toast.makeText(this, "검색 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }
}