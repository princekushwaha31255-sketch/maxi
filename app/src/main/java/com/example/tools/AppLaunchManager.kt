package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.AppCandidate
import com.example.data.DisambiguationPrompt
import com.example.data.InteractionStatus
import com.example.data.ToolExecutionResult

class AppLaunchManager(private val context: Context) {

    // Canonical mapping of common spoken names (including Hindi/Hinglish/typos) to known package identifiers
    private val canonicalAppMap: Map<String, List<Pair<String, String>>> = mapOf(
        "whatsapp" to listOf("WhatsApp" to "com.whatsapp"),
        "whats app" to listOf("WhatsApp" to "com.whatsapp"),
        "watsapp" to listOf("WhatsApp" to "com.whatsapp"),
        "whatsapp business" to listOf("WhatsApp Business" to "com.whatsapp.w4b"),
        "wa business" to listOf("WhatsApp Business" to "com.whatsapp.w4b"),
        "instagram" to listOf("Instagram" to "com.instagram.android"),
        "insta" to listOf("Instagram" to "com.instagram.android"),
        "facebook" to listOf("Facebook" to "com.facebook.katana", "Facebook Lite" to "com.facebook.lite"),
        "fb" to listOf("Facebook" to "com.facebook.katana"),
        "youtube" to listOf("YouTube" to "com.google.android.youtube"),
        "yt" to listOf("YouTube" to "com.google.android.youtube"),
        "google gemini" to listOf("Google Gemini" to "com.google.android.apps.bard"),
        "gemini" to listOf("Google Gemini" to "com.google.android.apps.bard"),
        "google search" to listOf("Google" to "com.google.android.googlequicksearchbox"),
        "google" to listOf("Google" to "com.google.android.googlequicksearchbox"),
        "free fire max" to listOf("Free Fire MAX" to "com.dts.freefiremax"),
        "free fire" to listOf("Free Fire MAX" to "com.dts.freefiremax", "Free Fire" to "com.dts.freefireth"),
        "ff max" to listOf("Free Fire MAX" to "com.dts.freefiremax"),
        "google maps" to listOf("Google Maps" to "com.google.android.apps.maps"),
        "maps" to listOf("Google Maps" to "com.google.android.apps.maps"),
        "map" to listOf("Google Maps" to "com.google.android.apps.maps"),
        "gmail" to listOf("Gmail" to "com.google.android.gm"),
        "mail" to listOf("Gmail" to "com.google.android.gm"),
        "chrome" to listOf("Chrome" to "com.android.chrome"),
        "google chrome" to listOf("Chrome" to "com.android.chrome"),
        "browser" to listOf("Chrome" to "com.android.chrome"),
        "phone" to listOf(
            "Phone" to "com.google.android.dialer",
            "Phone" to "com.android.dialer",
            "Phone" to "com.samsung.android.dialer"
        ),
        "dialer" to listOf(
            "Phone" to "com.google.android.dialer",
            "Phone" to "com.android.dialer"
        ),
        "messages" to listOf(
            "Messages" to "com.google.android.apps.messaging",
            "Messages" to "com.android.mms",
            "Messages" to "com.samsung.android.messaging"
        ),
        "sms" to listOf(
            "Messages" to "com.google.android.apps.messaging",
            "Messages" to "com.android.mms"
        ),
        "camera" to listOf(
            "Camera" to "com.android.camera2",
            "Camera" to "com.google.android.GoogleCamera",
            "Camera" to "com.android.camera",
            "Camera" to "com.sec.android.app.camera"
        ),
        "settings" to listOf("Settings" to "com.android.settings"),
        "setting" to listOf("Settings" to "com.android.settings"),
        "clock" to listOf(
            "Clock" to "com.google.android.deskclock",
            "Clock" to "com.android.deskclock"
        ),
        "contacts" to listOf(
            "Contacts" to "com.google.android.contacts",
            "Contacts" to "com.android.contacts"
        ),
        "calendar" to listOf(
            "Calendar" to "com.google.android.calendar",
            "Calendar" to "com.android.calendar"
        ),
        "files" to listOf(
            "Files" to "com.google.android.documentsui",
            "Files" to "com.android.documentsui"
        )
    )

    fun getInstalledLaunchableApps(): List<AppCandidate> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val candidates = mutableMapOf<String, AppCandidate>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            val label = try {
                info.loadLabel(pm).toString().trim()
            } catch (_: Exception) {
                pkg
            }
            val isSys = (info.activityInfo?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            candidates[pkg] = AppCandidate(
                appName = label.ifEmpty { pkg },
                packageName = pkg,
                isSystemApp = isSys
            )
        }
        return candidates.values.sortedBy { it.appName.lowercase() }
    }

    fun executeLaunchApp(
        spokenName: String,
        packageName: String? = null,
        disambiguationChoice: String? = null,
        languageStyle: String = "Hinglish"
    ): ToolExecutionResult {
        val pm = context.packageManager
        val installed = getInstalledLaunchableApps()
        val installedByPkg = installed.associateBy { it.packageName }

        // 1. If disambiguationChoice or explicit packageName was provided, resolve directly
        val directPkg = disambiguationChoice?.takeIf { it.contains(".") } ?: packageName?.takeIf { it.isNotBlank() }
        if (!directPkg.isNullOrBlank()) {
            return launchByPackage(pm, directPkg, spokenName.ifBlank { directPkg }, languageStyle)
        }

        val cleanQuery = (disambiguationChoice?.takeIf { it.isNotBlank() } ?: spokenName)
            .trim()
            .lowercase()

        if (cleanQuery.isBlank()) {
            return ToolExecutionResult(
                toolName = "launch_app",
                status = InteractionStatus.CLARIFICATION_REQUIRED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "बॉस, कौन सा ऐप खोलना है? कृपया नाम बताइए।",
                    en = "Which application would you like me to open?",
                    hinglish = "Boss, kaunsa app kholna hai? Naam bataiye."
                ),
                verificationEvidence = "Empty spoken_name parameter rejected by validator"
            )
        }

        // Special case: if user says "WhatsApp" and BOTH WhatsApp and WhatsApp Business are installed,
        // ask for disambiguation per Section 5.4
        if (cleanQuery == "whatsapp" || cleanQuery == "whats app") {
            val waInstalled = installedByPkg["com.whatsapp"]
            val wabInstalled = installedByPkg["com.whatsapp.w4b"]
            if (waInstalled != null && wabInstalled != null) {
                val both = listOf(waInstalled, wabInstalled)
                return buildDisambiguationResult(spokenName, both, languageStyle)
            }
        }

        // 2. Check exact match in installed app labels
        val exactInstalledMatches = installed.filter {
            it.appName.equals(cleanQuery, ignoreCase = true)
        }
        if (exactInstalledMatches.size == 1) {
            val match = exactInstalledMatches.first()
            return launchByPackage(pm, match.packageName, match.appName, languageStyle)
        } else if (exactInstalledMatches.size > 1) {
            return buildDisambiguationResult(spokenName, exactInstalledMatches, languageStyle)
        }

        // 3. Check canonical map against installed packages
        val canonicalTargets = canonicalAppMap[cleanQuery]
        if (canonicalTargets != null) {
            val matchingInstalled = canonicalTargets.mapNotNull { (canonicalLabel, pkg) ->
                val found = installedByPkg[pkg]
                if (found != null) found else if (pm.getLaunchIntentForPackage(pkg) != null) {
                    AppCandidate(canonicalLabel, pkg)
                } else null
            }.distinctBy { it.packageName }

            if (matchingInstalled.size == 1) {
                val target = matchingInstalled.first()
                return launchByPackage(pm, target.packageName, target.appName, languageStyle)
            } else if (matchingInstalled.size > 1) {
                return buildDisambiguationResult(spokenName, matchingInstalled, languageStyle)
            } else {
                // Canonical app known (e.g. Instagram, Free Fire MAX, WhatsApp), but not installed on device!
                val displayAppName = canonicalTargets.first().first
                val expectedPackages = canonicalTargets.joinToString(", ") { it.second }
                return ToolExecutionResult(
                    toolName = "launch_app",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, $displayAppName आपके फ़ोन में इंस्टॉल नहीं है।",
                        en = "$displayAppName is not installed on this device.",
                        hinglish = "Boss, $displayAppName phone mein installed nahi hai."
                    ),
                    verificationEvidence = "PackageManager query verified $displayAppName ($expectedPackages) is not installed on device."
                )
            }
        }

        // 4. Conservative partial / similarity matching across installed apps
        val partialMatches = installed.filter { candidate ->
            val labelLower = candidate.appName.lowercase()
            labelLower.contains(cleanQuery) || cleanQuery.contains(labelLower)
        }

        if (partialMatches.size == 1) {
            val match = partialMatches.first()
            return launchByPackage(pm, match.packageName, match.appName, languageStyle)
        } else if (partialMatches.size > 1) {
            return buildDisambiguationResult(spokenName, partialMatches.take(5), languageStyle)
        }

        // 5. Not installed / no match
        return ToolExecutionResult(
            toolName = "launch_app",
            status = InteractionStatus.EXECUTION_FAILED,
            userMessage = formatMsg(
                languageStyle,
                hi = "यह ऐप ($spokenName) फ़ोन में इंस्टॉल नहीं है।",
                en = "$spokenName is not installed on this device.",
                hinglish = "Ye app ($spokenName) phone mein installed nahi hai."
            ),
            verificationEvidence = "PackageManager scanned ${installed.size} launchable apps; no package matched '$spokenName'."
        )
    }

    private fun buildDisambiguationResult(
        spokenName: String,
        candidates: List<AppCandidate>,
        languageStyle: String
    ): ToolExecutionResult {
        val namesList = candidates.joinToString(", ") { it.appName }
        return ToolExecutionResult(
            toolName = "launch_app",
            status = InteractionStatus.CLARIFICATION_REQUIRED,
            userMessage = formatMsg(
                languageStyle,
                hi = "बॉस, '$spokenName' से मिलते-जुलते कई ऐप्स मिले: $namesList। आप कौन सा खोलना चाहते हैं?",
                en = "Multiple apps match '$spokenName': $namesList. Which one should I open?",
                hinglish = "Boss, '$spokenName' ke liye multiple apps mile: $namesList. Kaunsa open karoon?"
            ),
            verificationEvidence = "Ambiguous match: ${candidates.size} installed packages matched '$spokenName'",
            disambiguationPrompt = DisambiguationPrompt(
                originalSpokenName = spokenName,
                candidates = candidates,
                languageStyle = languageStyle
            )
        )
    }

    private fun launchByPackage(
        pm: PackageManager,
        packageName: String,
        displayLabel: String,
        languageStyle: String
    ): ToolExecutionResult {
        return try {
            var launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                val queryIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
                val resolved = pm.queryIntentActivities(queryIntent, 0).firstOrNull()
                    ?: pm.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
                        0
                    ).firstOrNull { it.activityInfo?.packageName == packageName }

                if (resolved?.activityInfo != null) {
                    launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(packageName, resolved.activityInfo.name)
                    }
                }
            }
            if (launchIntent == null) {
                return ToolExecutionResult(
                    toolName = "launch_app",
                    status = InteractionStatus.EXECUTION_FAILED,
                    userMessage = formatMsg(
                        languageStyle,
                        hi = "बॉस, $displayLabel फ़ोन में इंस्टॉल नहीं है या लॉन्च नहीं हो सकता।",
                        en = "$displayLabel is not installed or does not expose a launch activity.",
                        hinglish = "Boss, $displayLabel phone mein installed nahi hai ya launch nahi ho sakta."
                    ),
                    verificationEvidence = "pm.getLaunchIntentForPackage('$packageName') returned null"
                )
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolExecutionResult(
                toolName = "launch_app",
                status = InteractionStatus.VERIFIED_SUCCESS,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "जी बॉस, $displayLabel खोल रहा हूँ।",
                    en = "Opening $displayLabel now.",
                    hinglish = "Ji boss, $displayLabel khol raha hoon."
                ),
                verificationEvidence = "Verified launch intent resolved and startActivity() succeeded for package '$packageName'"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "launch_app",
                status = InteractionStatus.EXECUTION_FAILED,
                userMessage = formatMsg(
                    languageStyle,
                    hi = "$displayLabel खोलने में त्रुटि हुई: ${e.message}",
                    en = "Failed to launch $displayLabel: ${e.message}",
                    hinglish = "Boss, $displayLabel open karne mein error aaya: ${e.message}"
                ),
                verificationEvidence = "Exception during startActivity('$packageName'): ${e.javaClass.simpleName}"
            )
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
