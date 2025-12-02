package com.group_12.backstage.notifications

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private val db = FirebaseFirestore.getInstance()

    /**
     * Send a friend request notification
     * @param recipientUid The user who will receive the notification
     * @param senderName The name of the user sending the friend request
     * @param senderUid The UID of the user sending the request
     */
    fun sendFriendRequestNotification(
        recipientUid: String,
        senderName: String,
        senderUid: String
    ) {
        // First, get the recipient's FCM token
        db.collection("users").document(recipientUid).get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.w(TAG, "Recipient has no FCM token")
                    return@addOnSuccessListener
                }

                // Create notification request document
                val notificationData = hashMapOf(
                    "recipientUid" to recipientUid,
                    "fcmToken" to fcmToken,
                    "title" to "New Friend Request",
                    "body" to "$senderName sent you a friend request",
                    "type" to "friend_request",
                    "senderUid" to senderUid,
                    "senderName" to senderName,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "processed" to false
                )

                // Add to notifications collection (Cloud Function will process this)
                db.collection("notification_requests")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Notification request created successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create notification request", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch recipient data", e)
            }
    }

    /**
     * Send a friend request accepted notification
     * @param recipientUid The user who will receive the notification (original requester)
     * @param accepterName The name of the user who accepted the request
     * @param accepterUid The UID of the user who accepted
     */
    fun sendFriendRequestAcceptedNotification(
        recipientUid: String,
        accepterName: String,
        accepterUid: String
    ) {
        // First, get the recipient's FCM token
        db.collection("users").document(recipientUid).get()
            .addOnSuccessListener { document ->
                val fcmToken = document.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.w(TAG, "Recipient has no FCM token")
                    return@addOnSuccessListener
                }

                // Create notification request document
                val notificationData = hashMapOf(
                    "recipientUid" to recipientUid,
                    "fcmToken" to fcmToken,
                    "title" to "Friend Request Accepted",
                    "body" to "$accepterName accepted your friend request",
                    "type" to "friend_request_accepted",
                    "senderUid" to accepterUid,
                    "senderName" to accepterName,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "processed" to false
                )

                // Add to notifications collection (Cloud Function will process this)
                db.collection("notification_requests")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Notification request created successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create notification request", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch recipient data", e)
            }
    }
}
