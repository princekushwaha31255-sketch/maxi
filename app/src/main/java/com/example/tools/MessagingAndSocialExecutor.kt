package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.ContactMatch
import com.example.data.InteractionStatus
import com.example.data.MaxDao
import com.example.data.PendingConfirmation
import com.example.data.ToolExecutionResult
import java.net.URLEncoder
import java.util.UUID

class MessagingAndSocialExecutor(
    private val context: Context,
    private val maxDao: MaxDao,
    private val appLaunchManager: AppLaunchManager
) {

    fun getAllContacts(limit: Int = 250): List<ContactMatch> {
        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPerm) return emptyList()

        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && results.size < limit) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val normalized = normalizePhoneNumber(rawNumber)
                    if (name.isNotEmpty() && normalized.isNotEmpty()) {
                        results.add(ContactMatch(name = name, phoneNumber = normalized))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results.distinctBy { "${it.name.lowercase()}_${it.phoneNumber}" }
    }

    fun resolveContactsByName(nameQuery: String): List<ContactMatch> {
        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPerm || nameQuery.isBlank()) return emptyList()

        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${nameQuery.trim()}%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val normalized = normalizePhoneNumber(rawNumber)
                    if (name.isNotEmpty() && normalized.isNotEmpty()) {
                        results.add(ContactMatch(name = name, phoneNumber = normalized))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results.distinctBy { "${it.name.lowercase()}_${it.phoneNumber}" }
    }

    /**
     * Section 7: "prepare_message"
     * Strictly enforces:
     * 1. Extract recipient, application (WhatsApp or WhatsApp Business), and message.
     * 2. Resolve recipient through authorized contacts or request a phone number.
     * 3. Never proceed with an uncertain recipient.
     * 4. Present composed message for review & require confirmation unless narrowly scoped trusted rule exists.
     * 5. Opening an application or populating a draft must NEVER be reported as message delivery;
     *    instruct user to tap Send if final user tap is required.
     */
    suspend fun executePrepareMessage(
        application: String = "WhatsApp",
        recipientName: String? = null,
        phoneNumber: String? = null,
        message: String? = null,
        mode: String = "draft",
        isConfirmed: Boolean = false,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val isBusiness = application.trim().lowercase().contains("business") ||
            application.trim().lowercase() == "w4b"
        val targetAppLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"
        val targetPackage = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"

        if (message.isNullOrBlank()) {
            return ToolExecutionResult(
                toolName = "prepare_message",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, $targetAppLabel पर क्या मैसेज लिखना है? कृपया मैसेज बताइए।",
                    en = "What message would you like to compose for $targetAppLabel?",
                    hinglish = "Boss, $targetAppLabel par kya message bhejna hai? Message bataiye."
                ),
                verificationEvidence = "Missing message content in prepare_message"
            )
        }

        // Resolve phone number or contact
        var resolvedPhone = normalizePhoneNumber(phoneNumber.orEmpty())
        var resolvedDisplayRecipient = recipientName?.trim().orEmpty()

        if (resolvedPhone.isEmpty()) {
            if (resolvedDisplayRecipient.isEmpty()) {
                return ToolExecutionResult(
                    toolName = "prepare_message",
                    status = InteractionStatus.CLARIFICATION_REQUIRED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, यह मैसेज किसे भेजना है? कृपया संपर्क का नाम या फ़ोन नंबर बताइए।",
                        en = "Who is the recipient? Please provide a contact name or phone number.",
                        hinglish = "Boss, ye message kise bhejna hai? Recipient ka naam ya phone number bataiye."
                    ),
                    verificationEvidence = "Uncertain recipient: both recipient_name and phone_number are empty"
                )
            }

            // Check READ_CONTACTS permission
            val hasContactsPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasContactsPerm) {
                return ToolExecutionResult(
                    toolName = "prepare_message",
                    status = InteractionStatus.PERMISSION_REQUIRED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, '$resolvedDisplayRecipient' का नंबर खोजने के लिए Contacts अनुमति चाहिए। कृपया इसे allow करें या सीधे फ़ोन नंबर बताइए।",
                        en = "Contacts permission is required to resolve '$resolvedDisplayRecipient'. Please grant Contacts access or provide their phone number directly.",
                        hinglish = "Boss, '$resolvedDisplayRecipient' ka number dekhne ke liye contacts permission chahiye. Please allow kar dijiye ya phone number bataiye."
                    ),
                    verificationEvidence = "READ_CONTACTS permission not granted; refused to proceed with uncertain recipient '$resolvedDisplayRecipient'",
                    requiredPermission = Manifest.permission.READ_CONTACTS
                )
            }

            val matches = resolveContactsByName(resolvedDisplayRecipient)
            if (matches.isEmpty()) {
                return ToolExecutionResult(
                    toolName = "prepare_message",
                    status = InteractionStatus.CLARIFICATION_REQUIRED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, Contacts में '$resolvedDisplayRecipient' नाम का कोई नंबर नहीं मिला। कृपया उनका फ़ोन नंबर बता दीजिए।",
                        en = "No contact named '$resolvedDisplayRecipient' was found in your Contacts. Please provide their phone number.",
                        hinglish = "Boss, Contacts mein '$resolvedDisplayRecipient' naam ka koi number nahi mila. Please unka phone number bata dijiye."
                    ),
                    verificationEvidence = "Contacts query returned 0 matches for '$resolvedDisplayRecipient'; refused uncertain recipient"
                )
            } else if (matches.size > 1) {
                val options = matches.take(4).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                return ToolExecutionResult(
                    toolName = "prepare_message",
                    status = InteractionStatus.CLARIFICATION_REQUIRED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, '$resolvedDisplayRecipient' नाम के कई संपर्क मिले: $options। किस नंबर पर भेजना है?",
                        en = "Multiple contacts match '$resolvedDisplayRecipient': $options. Which one should I use?",
                        hinglish = "Boss, '$resolvedDisplayRecipient' ke liye multiple contacts mile: $options. Kaunse number par message prepare karoon?"
                    ),
                    verificationEvidence = "Ambiguous recipient: ${matches.size} contacts matched '$resolvedDisplayRecipient'"
                )
            } else {
                val single = matches.first()
                resolvedDisplayRecipient = single.name
                resolvedPhone = single.phoneNumber
            }
        }

        if (resolvedDisplayRecipient.isEmpty()) {
            resolvedDisplayRecipient = resolvedPhone
        }

        // Check if narrowly scoped trusted rule exists for this recipient
        val scopeKey = "${targetPackage}:$resolvedPhone"
        val trustedRule = maxDao.findActiveTrustedRule("prepare_message", scopeKey)

        if (!isConfirmed && trustedRule == null) {
            val pending = PendingConfirmation(
                id = UUID.randomUUID().toString(),
                toolName = "prepare_message",
                title = "Review $targetAppLabel Message",
                summary = "To: $resolvedDisplayRecipient ($resolvedPhone)\nApp: $targetAppLabel\nMessage: \"$message\"",
                parameters = mapOf(
                    "application" to targetAppLabel,
                    "recipient_name" to resolvedDisplayRecipient,
                    "phone_number" to resolvedPhone,
                    "message" to message,
                    "mode" to mode
                ),
                riskReason = "Externally visible message to $resolvedDisplayRecipient ($resolvedPhone). Review draft before opening $targetAppLabel.",
                languageStyle = languageStyle,
                canCreateTrustedRule = true,
                trustedRuleScopeKey = scopeKey,
                trustedRuleDescription = "Trusted $targetAppLabel draft to $resolvedDisplayRecipient ($resolvedPhone)"
            )
            return ToolExecutionResult(
                toolName = "prepare_message",
                status = InteractionStatus.PENDING_CONFIRMATION,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "मैसेज तैयार है ($resolvedDisplayRecipient: \"$message\")। क्या मैं इसे $targetAppLabel में खोल दूँ?",
                    en = "Message is ready for $resolvedDisplayRecipient: \"$message\". Should I proceed?",
                    hinglish = "Message ready hai ($resolvedDisplayRecipient: \"$message\"). Kya main ise send ke liye prepare kar doon?"
                ),
                verificationEvidence = "Composed draft presented for mandatory user review before launching $targetAppLabel",
                pendingConfirmation = pending
            )
        }

        // Verify target WhatsApp / WhatsApp Business installation or open official wa.me intent
        val encodedText = URLEncoder.encode(message, "UTF-8")
        val cleanDigits = resolvedPhone.replace(Regex("""[^\d]"""), "")
        val deepLinkUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText")

        val sendIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pm = context.packageManager
        val canHandleSpecific = sendIntent.resolveActivity(pm) != null
        return if (canHandleSpecific) {
            context.startActivity(sendIntent)
            ToolExecutionResult(
                toolName = "prepare_message",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, $targetAppLabel में $resolvedDisplayRecipient के लिए मैसेज ड्राफ्ट भर दिया है। कृपया भेजने के लिए Send बटन दबाएँ।",
                    en = "Populated your message draft to $resolvedDisplayRecipient in $targetAppLabel. Please tap Send in $targetAppLabel to complete delivery.",
                    hinglish = "Ji boss, $targetAppLabel mein $resolvedDisplayRecipient ke liye message draft populate kar diya hai. Final delivery ke liye please Send button tap kar dijiye."
                ),
                verificationEvidence = "Verified $targetPackage handled ACTION_VIEW draft intent for $resolvedPhone. Disclosed that final Send tap is required (draft != delivery)."
            )
        } else {
            // Disclose honestly that target WhatsApp package is not installed on this device
            ToolExecutionResult(
                toolName = "prepare_message",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, इस फ़ोन में $targetAppLabel इंस्टॉल नहीं है, इसलिए मैसेज ड्राफ्ट खोला नहीं जा सका।",
                    en = "$targetAppLabel ($targetPackage) is not installed on this device. Your draft for $resolvedDisplayRecipient (\"$message\") was not sent.",
                    hinglish = "Boss, $targetAppLabel phone mein installed nahi hai, isliye $resolvedDisplayRecipient ka message send nahi ho sakta."
                ),
                verificationEvidence = "PackageManager verified '$targetPackage' is not installed; draft was not reported as delivered."
            )
        }
    }

    /**
     * Section 8: Social & Media Workflows
     * Supports YouTube search, opening channels/profiles, and preparing captions/comments/DMs
     * with confirmation before externally visible actions.
     */
    fun executeSocialMediaWorkflow(
        platform: String,
        actionType: String,
        queryOrTarget: String,
        draftText: String? = null,
        isConfirmed: Boolean = false,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val platClean = platform.trim().lowercase()
        val actionClean = actionType.trim().lowercase()

        // Externally visible actions (post, comment, reply, dm, follow, delete) require confirmation
        val requiresConfirmation = actionClean in listOf("post", "comment", "reply", "dm", "follow", "delete")
        if (requiresConfirmation && !isConfirmed) {
            val pending = PendingConfirmation(
                id = UUID.randomUUID().toString(),
                toolName = "social_media_action",
                title = "Confirm $platform ${actionType.replaceFirstChar { it.uppercase() }}",
                summary = "Platform: $platform\nAction: $actionType\nTarget: $queryOrTarget" +
                    (if (!draftText.isNullOrBlank()) "\nContent: \"$draftText\"" else ""),
                parameters = mapOf(
                    "platform" to platform,
                    "action_type" to actionType,
                    "query_or_target" to queryOrTarget,
                    "draft_text" to (draftText ?: "")
                ),
                riskReason = "Externally visible social media action ($actionType on $platform).",
                languageStyle = languageStyle
            )
            return ToolExecutionResult(
                toolName = "social_media_action",
                status = InteractionStatus.PENDING_CONFIRMATION,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, $platform पर '$actionType' करने से पहले कृपया कन्फर्म करें।",
                    en = "Please confirm before proceeding with '$actionType' on $platform.",
                    hinglish = "Boss, $platform par '$actionType' externally visible action hai. Kya main aage badhoon?"
                ),
                verificationEvidence = "Confirmation required before externally visible social media workflow ($actionType)",
                pendingConfirmation = pending
            )
        }

        // YouTube search or channel deep link
        if (platClean.contains("youtube") || platClean == "yt") {
            val encoded = URLEncoder.encode(queryOrTarget.trim(), "UTF-8")
            val url = if (actionClean.contains("channel") || queryOrTarget.startsWith("@")) {
                val handle = if (queryOrTarget.startsWith("@")) queryOrTarget else "@$encoded"
                "https://www.youtube.com/$handle"
            } else {
                "https://www.youtube.com/results?search_query=$encoded"
            }
            return launchDeepLinkOrBrowser(
                url = url,
                preferredPackage = "com.google.android.youtube",
                platformLabel = "YouTube",
                description = "YouTube ($queryOrTarget)",
                languageStyle = languageStyle
            )
        }

        if (platClean.contains("instagram") || platClean == "insta") {
            val cleanHandle = queryOrTarget.trim().removePrefix("@")
            val url = if (cleanHandle.isNotEmpty() && (actionClean.contains("profile") || actionClean.contains("open"))) {
                "https://www.instagram.com/$cleanHandle/"
            } else {
                "https://www.instagram.com/"
            }
            return launchDeepLinkOrBrowser(
                url = url,
                preferredPackage = "com.instagram.android",
                platformLabel = "Instagram",
                description = "Instagram ($queryOrTarget)",
                languageStyle = languageStyle
            )
        }

        if (platClean.contains("facebook") || platClean == "fb") {
            val encoded = URLEncoder.encode(queryOrTarget.trim(), "UTF-8")
            val url = if (encoded.isNotEmpty()) {
                "https://www.facebook.com/search/top/?q=$encoded"
            } else {
                "https://www.facebook.com/"
            }
            return launchDeepLinkOrBrowser(
                url = url,
                preferredPackage = "com.facebook.katana",
                platformLabel = "Facebook",
                description = "Facebook ($queryOrTarget)",
                languageStyle = languageStyle
            )
        }

        return appLaunchManager.executeLaunchApp(platform, languageStyle = languageStyle)
    }

    private fun launchDeepLinkOrBrowser(
        url: String,
        preferredPackage: String,
        platformLabel: String,
        description: String,
        languageStyle: String
    ): ToolExecutionResult {
        return try {
            val uri = Uri.parse(url)
            val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(preferredPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            if (appIntent.resolveActivity(pm) != null) {
                context.startActivity(appIntent)
                ToolExecutionResult(
                    toolName = "social_media_action",
                    status = InteractionStatus.VERIFIED_SUCCESS,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "जी बॉस, $platformLabel में $description खोल दिया है।",
                        en = "Opened $description in the $platformLabel app.",
                        hinglish = "Ji boss, $platformLabel app mein $description khol diya hai."
                    ),
                    verificationEvidence = "Launched deep link '$url' inside installed package '$preferredPackage'"
                )
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (browserIntent.resolveActivity(pm) != null) {
                    context.startActivity(browserIntent)
                    ToolExecutionResult(
                        toolName = "social_media_action",
                        status = InteractionStatus.VERIFIED_SUCCESS,
                        userMessage = formatMsg(
                            languageStyle,
                            hi = "बॉस, $platformLabel ऐप इंस्टॉल नहीं है, इसलिए मैंने इसे ब्राउज़र में खोल दिया है।",
                            en = "$platformLabel app is not installed; opened $description via web link instead.",
                            hinglish = "Boss, $platformLabel app installed nahi hai, isliye maine $description browser mein open kar diya hai."
                        ),
                        verificationEvidence = "Package '$preferredPackage' not installed; verified fallback ACTION_VIEW launched '$url'"
                    )
                } else {
                    ToolExecutionResult(
                        toolName = "social_media_action",
                        status = InteractionStatus.EXECUTION_FAILED,
                        userMessage = "$platformLabel is not installed and no browser is available to open $url.",
                        verificationEvidence = "Neither '$preferredPackage' nor a browser resolved '$url'"
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "social_media_action",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = "Failed to open $description: ${e.message}",
                verificationEvidence = "Exception launching deep link '$url': ${e.message}"
            )
        }
    }

    companion object {
        fun normalizePhoneNumber(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val hasPlus = trimmed.startsWith("+")
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length < 7) return ""
            return if (hasPlus) "+$digits" else if (digits.length == 10) "+91$digits" else "+$digits"
        }
    }

    private fun formatMsg(style: String, hi: String, en: String, hinglish: String): String {
        return when {
            style.equals("Hindi", ignoreCase = true) -> hi
            style.equals("English", ignoreCase = true) -> en
            else -> hinglish
        }
    }
}
