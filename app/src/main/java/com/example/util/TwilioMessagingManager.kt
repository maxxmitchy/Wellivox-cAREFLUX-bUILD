package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.OutboundSmsLog
import com.example.data.PharmacyDao
import com.example.data.TwilioConstants
import com.example.data.TwilioMessageResponse
import com.example.data.TwilioRetrofitClient
import com.example.data.remote.FirestoreRemoteDataSourceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object TwilioMessagingManager {

    private const val TAG = "TwilioMessagingManager"
    private const val DUP_CACHE_PREFS = "twilio_dedup_cache"

    /**
     * Sanitizes raw phone string to standard E.164 format.
     * e.g., "08147578314" -> "+2348147578314"
     * "2348147578314" -> "+2348147578314"
     */
    fun sanitizeE164Phone(rawPhone: String, defaultCountryCode: String = "+234"): String {
        var clean = rawPhone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (clean.startsWith("+")) {
            return clean
        }
        if (clean.startsWith("0") && clean.length == 11) {
            return defaultCountryCode + clean.substring(1)
        }
        if (clean.startsWith("234") && clean.length == 13) {
            return "+$clean"
        }
        return if (clean.startsWith("+")) clean else "$defaultCountryCode$clean"
    }

    /**
     * Checks if current time is within Quiet Hours (9:00 PM to 8:00 AM).
     */
    fun isQuietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 21 || hour < 8
    }

    /**
     * Daily Message Cap Guardrail for Twilio Trial Mode & Pay-As-You-Go Safety Budget.
     * In Trial mode: Default cap is 50 messages/day.
     * In Pay-As-You-Go mode: Configurable daily safety budget cap (default 200 msgs/day, or 0 for unlimited).
     */
    private const val TWILIO_CONFIG_PREFS = "twilio_config_and_limits"
    private const val DAILY_CAP_PREFS = "twilio_daily_cap_cache"
    private const val PATIENT_THROTTLE_PREFS = "twilio_patient_frequency_cache"

    var TRIAL_DAILY_MESSAGE_CAP = 50 // Default for Trial mode
    var PAYG_DAILY_SAFETY_CAP = 200 // Safety budget cap for Pay-As-You-Go (0 = Unlimited)
    var MAX_MESSAGES_PER_PATIENT_PER_DAY = 2 // Anti-spam patient throttle limit

    private var lastDispatchTimestamp: Long = 0L
    private const val MIN_DISPATCH_INTERVAL_MS = 1000L // 1 msg/sec anti-burst rate limit

    fun isPayAsYouGoMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(TWILIO_CONFIG_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("is_payg_mode", false)
    }

    fun setPayAsYouGoMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(TWILIO_CONFIG_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_payg_mode", enabled).apply()
    }

    fun getEffectiveDailyCap(context: Context): Int {
        return if (isPayAsYouGoMode(context)) PAYG_DAILY_SAFETY_CAP else TRIAL_DAILY_MESSAGE_CAP
    }

    private fun getTodayDateKey(): String {
        val cal = Calendar.getInstance()
        return "cap_%04d_%02d_%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    fun getDailyMessageCount(context: Context): Int {
        val prefs = context.getSharedPreferences(DAILY_CAP_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(getTodayDateKey(), 0)
    }

    fun incrementDailyMessageCount(context: Context) {
        val prefs = context.getSharedPreferences(DAILY_CAP_PREFS, Context.MODE_PRIVATE)
        val key = getTodayDateKey()
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    fun isDailyCapExceeded(context: Context): Boolean {
        val cap = getEffectiveDailyCap(context)
        if (cap <= 0) return false // 0 = Unlimited Pay-As-You-Go
        return getDailyMessageCount(context) >= cap
    }

    /**
     * Patient-Level Frequency Throttle: Checks if a specific patient phone number
     * has received more than MAX_MESSAGES_PER_PATIENT_PER_DAY messages in the last 24 hours.
     */
    fun isPatientFrequencyThrottled(context: Context, phone: String): Boolean {
        val prefs = context.getSharedPreferences(PATIENT_THROTTLE_PREFS, Context.MODE_PRIVATE)
        val clean = sanitizeE164Phone(phone)
        val keyCount = "${clean}_${getTodayDateKey()}_count"
        val count = prefs.getInt(keyCount, 0)
        return count >= MAX_MESSAGES_PER_PATIENT_PER_DAY
    }

    fun incrementPatientMessageCount(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PATIENT_THROTTLE_PREFS, Context.MODE_PRIVATE)
        val clean = sanitizeE164Phone(phone)
        val keyCount = "${clean}_${getTodayDateKey()}_count"
        val count = prefs.getInt(keyCount, 0)
        prefs.edit().putInt(keyCount, count + 1).apply()
    }

    /**
     * Deduplication check: checks if a message with (phone + medicationId + type) was sent in the last 24h.
     */
    fun isDuplicateDispatch(context: Context, phone: String, medIdOrKey: String, messageType: String, windowMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        val prefs = context.getSharedPreferences(DUP_CACHE_PREFS, Context.MODE_PRIVATE)
        val key = "${sanitizeE164Phone(phone)}_${medIdOrKey}_$messageType"
        val lastSent = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        return (now - lastSent) < windowMillis
    }

    fun markDispatchRecord(context: Context, phone: String, medIdOrKey: String, messageType: String) {
        val prefs = context.getSharedPreferences(DUP_CACHE_PREFS, Context.MODE_PRIVATE)
        val key = "${sanitizeE164Phone(phone)}_${medIdOrKey}_$messageType"
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    sealed class DispatchResult {
        data class Success(val sid: String, val channel: String, val status: String, val cost: String) : DispatchResult()
        data class Blocked(val reason: String) : DispatchResult()
        data class Failed(val error: String) : DispatchResult()
    }

    /**
     * Dispatch single message via WhatsApp priority with SMS Fallback.
     */
    suspend fun dispatchMessage(
        context: Context,
        dao: PharmacyDao?,
        rawPhone: String,
        messageContent: String,
        messageType: String = "General",
        medicationIdOrKey: String = "general",
        forceOverrideQuietHours: Boolean = false,
        templateContentSid: String? = null
    ): DispatchResult = withContext(Dispatchers.IO) {

        val cleanPhone = sanitizeE164Phone(rawPhone)

        // Guardrail 4: Quiet Hours Protocol
        if (!forceOverrideQuietHours && isQuietHours() && (messageType.contains("Refill", true) || messageType.contains("Promo", true))) {
            val blockMsg = "Blocked: Quiet Hours active (9:00 PM - 8:00 AM). Message queued for morning delivery."
            logDispatchToDb(dao, cleanPhone, messageContent, "Queued / Quiet Hours", "System Guardrail", blockMsg, "WhatsApp", messageType, null, "$0.00")
            return@withContext DispatchResult.Blocked(blockMsg)
        }

        // Guardrail 2: 24h Deduplication
        if (isDuplicateDispatch(context, cleanPhone, medicationIdOrKey, messageType)) {
            val blockMsg = "Blocked: Duplicate $messageType sent to $cleanPhone within last 24 hours."
            logDispatchToDb(dao, cleanPhone, messageContent, "Blocked Duplicate", "System Guardrail", blockMsg, "WhatsApp", messageType, null, "$0.00")
            return@withContext DispatchResult.Blocked(blockMsg)
        }

        // Guardrail 6: Daily Message & Budget Cap Check
        val activeCap = getEffectiveDailyCap(context)
        val modeName = if (isPayAsYouGoMode(context)) "Pay-As-You-Go Budget Cap" else "Trial Mode Cap"
        if (isDailyCapExceeded(context)) {
            val blockMsg = "Blocked: Daily message quota ($activeCap msgs/day for $modeName) reached."
            logDispatchToDb(dao, cleanPhone, messageContent, "Blocked Daily Cap", "System Guardrail", blockMsg, "WhatsApp", messageType, null, "$0.00")
            return@withContext DispatchResult.Blocked(blockMsg)
        }

        // Guardrail 7: Patient-Level Frequency Throttle (Anti-Spam)
        if (!forceOverrideQuietHours && isPatientFrequencyThrottled(context, cleanPhone)) {
            val blockMsg = "Blocked: Patient $cleanPhone has already received $MAX_MESSAGES_PER_PATIENT_PER_DAY messages today."
            logDispatchToDb(dao, cleanPhone, messageContent, "Blocked Patient Frequency", "System Guardrail", blockMsg, "WhatsApp", messageType, null, "$0.00")
            return@withContext DispatchResult.Blocked(blockMsg)
        }

        // Guardrail 8: Anti-Burst Throttle (1 msg/sec API rate limit)
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastDispatchTimestamp
        if (elapsed < MIN_DISPATCH_INTERVAL_MS) {
            delay(MIN_DISPATCH_INTERVAL_MS - elapsed)
        }
        lastDispatchTimestamp = System.currentTimeMillis()

        val authHeader = TwilioConstants.getBasicAuthHeader()
        val accountSid = TwilioConstants.ACCOUNT_SID

        // Guardrail 5: WhatsApp Priority with SMS Fallback
        var channelUsed = "WhatsApp"
        var twilioResponse: TwilioMessageResponse? = null
        var lastError: String? = null

        try {
            val waTo = "whatsapp:$cleanPhone"
            val waFrom = TwilioConstants.WHATSAPP_SENDER

            if (!templateContentSid.isNullOrBlank()) {
                twilioResponse = TwilioRetrofitClient.service.sendWhatsAppTemplate(
                    accountSid = accountSid,
                    authHeader = authHeader,
                    to = waTo,
                    from = waFrom,
                    contentSid = templateContentSid
                )
            } else {
                twilioResponse = TwilioRetrofitClient.service.sendWhatsAppMessage(
                    accountSid = accountSid,
                    authHeader = authHeader,
                    to = waTo,
                    from = waFrom,
                    body = messageContent
                )
            }

            if (twilioResponse.sid.isNullOrBlank() && twilioResponse.errorCode != null) {
                lastError = "WhatsApp Error ${twilioResponse.errorCode}: ${twilioResponse.errorMessage}"
                twilioResponse = null
            }
        } catch (e: Exception) {
            lastError = "WhatsApp Exception: ${e.localizedMessage}"
            Log.w(TAG, "WhatsApp dispatch failed, falling back to SMS: $lastError")
        }

        // Fallback to SMS if WhatsApp failed or returned no SID
        if (twilioResponse?.sid == null) {
            channelUsed = "SMS"
            try {
                twilioResponse = TwilioRetrofitClient.service.sendSms(
                    accountSid = accountSid,
                    authHeader = authHeader,
                    to = cleanPhone,
                    from = TwilioConstants.SMS_NUMBER,
                    body = messageContent
                )
            } catch (e: Exception) {
                lastError = "SMS Exception: ${e.localizedMessage}"
                Log.e(TAG, "SMS fallback failed: $lastError")
            }
        }

        if (twilioResponse?.sid != null) {
            val sid = twilioResponse.sid!!
            val status = twilioResponse.status ?: "queued"
            val costEst = if (channelUsed == "WhatsApp") "$0.0050" else "$0.0075"

            markDispatchRecord(context, cleanPhone, medicationIdOrKey, messageType)
            incrementDailyMessageCount(context)
            incrementPatientMessageCount(context, cleanPhone)
            logDispatchToDb(dao, cleanPhone, messageContent, status, "Twilio Multi-Channel", null, channelUsed, messageType, sid, costEst)

            return@withContext DispatchResult.Success(sid = sid, channel = channelUsed, status = status, cost = costEst)
        } else {
            val errMsg = lastError ?: "Twilio API dispatch failed with null SID"
            logDispatchToDb(dao, cleanPhone, messageContent, "Failed", "Twilio Multi-Channel", errMsg, channelUsed, messageType, null, "$0.00")
            return@withContext DispatchResult.Failed(errMsg)
        }
    }

    private fun logDispatchToDb(
        dao: PharmacyDao?,
        recipient: String,
        content: String,
        status: String,
        gateway: String,
        error: String?,
        channel: String,
        messageType: String,
        twilioSid: String?,
        costEst: String
    ) {
        val log = OutboundSmsLog(
            recipientPhone = recipient,
            messageContent = content,
            deliveryStatus = status,
            timestamp = System.currentTimeMillis(),
            gatewayUsed = gateway,
            errorMessage = error,
            channel = channel,
            messageType = messageType,
            twilioSid = twilioSid,
            costEstimate = costEst
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                dao?.insertSmsLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val remoteDataSource = FirestoreRemoteDataSourceImpl()
            val map = hashMapOf<String, Any?>(
                "recipientPhone" to recipient,
                "messageContent" to content,
                "deliveryStatus" to status,
                "timestamp" to System.currentTimeMillis(),
                "gatewayUsed" to gateway,
                "errorMessage" to error,
                "channel" to channel,
                "messageType" to messageType,
                "twilioSid" to twilioSid,
                "costEstimate" to costEst
            )
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                remoteDataSource.logOutboundSms(map)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
