package com.example.safeassistant

object PolicyEngine {

    private val EXPLICIT_WHITELIST = listOf(
        Regex(".*qcam.*", RegexOption.IGNORE_CASE),
        Regex("com\\.qcam\\..*", RegexOption.IGNORE_CASE)
    )

    private val BLOCKED_PATTERNS = listOf(
        // Dialer & Calls
        Regex(".*dialer.*", RegexOption.IGNORE_CASE),
        Regex(".*telecom.*", RegexOption.IGNORE_CASE),
        Regex(".*incallui.*", RegexOption.IGNORE_CASE),
        Regex("com\\.android\\.phone"),

        // SMS & Messaging
        Regex(".*mms.*", RegexOption.IGNORE_CASE),
        Regex(".*sms.*", RegexOption.IGNORE_CASE),
        Regex(".*messaging.*", RegexOption.IGNORE_CASE),
        Regex("com\\.google\\.android\\.apps\\.messaging"),
        Regex("com\\.whatsapp"),
        Regex("org\\.telegram\\.messenger"),
        Regex("org\\.thoughtcrime\\.securesms"),

        // Social Media
        Regex("com\\.instagram\\.android"),
        Regex("com\\.facebook\\..*"),
        Regex("com\\.twitter\\.android"),
        Regex("com\\.zhiliaoapp\\.musically"),
        Regex("com\\.snapchat\\.android"),
        Regex("com\\.reddit\\.frontpage"),
        Regex("com\\.linkedin\\.android"),

        // Finance, Banking & UPI
        Regex(".*phonepe.*", RegexOption.IGNORE_CASE),
        Regex(".*paytm.*", RegexOption.IGNORE_CASE),
        Regex(".*paisa.*", RegexOption.IGNORE_CASE),
        Regex(".*bhim.*", RegexOption.IGNORE_CASE),
        Regex("in\\.org\\.npci\\.upiapp"),
        Regex("cred\\.android"),
        Regex(".*bank.*", RegexOption.IGNORE_CASE),
        Regex(".*yono.*", RegexOption.IGNORE_CASE),
        Regex(".*wallet.*", RegexOption.IGNORE_CASE),
        Regex("com\\.zerodha\\.kite3"),
        Regex("com\\.groww")
    )

    fun isPackagePermitted(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val cleanPkg = packageName.trim()

        if (EXPLICIT_WHITELIST.any { it.containsMatchIn(cleanPkg) }) {
            return true
        }

        return BLOCKED_PATTERNS.none { it.containsMatchIn(cleanPkg) }
    }
}
