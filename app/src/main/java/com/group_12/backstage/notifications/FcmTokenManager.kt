package com.group_12.backstage.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    /**
     * Initialize and save FCM token for the current user
     * Call this after successful login or registration
     */
    fun initializeFcmToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "Cannot initialize FCM token: User not logged in")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            saveFcmToken(userId, token)
        }
    }

    /**
     * Save FCM token to Firestore
     */
    private fun saveFcmToken(userId: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
                // If update fails, try to set it (in case the field doesn't exist)
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    /**
     * Clear FCM token on logout
     */
    fun clearFcmToken() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update("fcmToken", "")
            .addOnSuccessListener {
                Log.d(TAG, "FCM token cleared successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to clear FCM token", e)
            }
    }
}
