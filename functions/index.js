const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require('firebase-admin');
const logger = require("firebase-functions/logger");

admin.initializeApp();

// Set global options for cost control
setGlobalOptions({ maxInstances: 10 });

/**
 * Cloud Function to send push notifications for friend requests
 * Triggers when a new document is added to the 'notification_requests' collection
 */
exports.sendPushNotification = onDocumentCreated(
    "notification_requests/{requestId}",
    async (event) => {
        try {
            // Get the notification data
            const data = event.data.data();

            logger.info('Processing notification request:', data);

            // Check if already processed
            if (data.processed === true) {
                logger.info('Notification already processed, skipping...');
                return null;
            }

            // Validate required fields
            if (!data.fcmToken || !data.title || !data.body) {
                logger.error('Missing required fields:', data);
                return null;
            }

            // Create the notification payload
            const payload = {
                notification: {
                    title: data.title,
                    body: data.body,
                    sound: 'default',
                },
                data: {
                    type: data.type || 'friend_notification',
                    senderUid: data.senderUid || '',
                    senderName: data.senderName || '',
                }
            };

            // Send the notification
            logger.info('Sending notification to token:', data.fcmToken.substring(0, 20) + '...');

            const response = await admin.messaging().sendToDevice(data.fcmToken, payload);

            logger.info('Notification sent successfully:', response);

            // Mark as processed
            await event.data.ref.update({
                processed: true,
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
                result: 'success'
            });

            // Optional: Delete old processed notifications (older than 7 days)
            const sevenDaysAgo = new Date();
            sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

            const oldNotifications = await admin.firestore()
                .collection('notification_requests')
                .where('processed', '==', true)
                .where('timestamp', '<', sevenDaysAgo)
                .limit(10)
                .get();

            const deletePromises = [];
            oldNotifications.forEach(doc => {
                deletePromises.push(doc.ref.delete());
            });

            if (deletePromises.length > 0) {
                await Promise.all(deletePromises);
                logger.info(`Cleaned up ${deletePromises.length} old notifications`);
            }

            return response;

        } catch (error) {
            logger.error('Error sending notification:', error);

            // Mark as failed
            try {
                await event.data.ref.update({
                    processed: true,
                    processedAt: admin.firestore.FieldValue.serverTimestamp(),
                    result: 'failed',
                    error: error.message
                });
            } catch (updateError) {
                logger.error('Error updating document:', updateError);
            }

            return null;
        }
    }
);
