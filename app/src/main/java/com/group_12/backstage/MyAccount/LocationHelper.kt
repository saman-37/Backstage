package com.group_12.backstage.MyAccount

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.group_12.backstage.util.NotificationHelper
import java.util.Locale

object LocationHelper {

    fun getFusedClient(context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    private fun toCityStateCountry(context: Context, location: Location): Triple<String, String, String> {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val addr = addresses?.firstOrNull()

        val city = addr?.locality ?: ""
        val state = addr?.adminArea ?: ""
        val country = addr?.countryName ?: ""

        return Triple(city, state, country)
    }

    /**
     * Called ONLY:
     *  - right after user registration succeeds
     *  - right after a fresh manual login succeeds
     *
     * It gets ONE lastLocation reading and updates city/state/country
     * and sets locationBasedContent = true. No continuous tracking.
     */
    @SuppressLint("MissingPermission")
    fun updateUserLocationIfPossible(
        context: Context,
        uid: String,
        docId: String = uid
    ) {
        val fusedClient = getFusedClient(context)
        val db = FirebaseFirestore.getInstance()

        // 🔔 Show brief “location is being saved” notification
        NotificationHelper.showLocationSavingNotification(context)

        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val (city, state, country) = toCityStateCountry(context, location)

                    val updates = mapOf(
                        "city" to city,
                        "state" to state,
                        "country" to country,
                        "locationBasedContent" to true
                    )

                    db.collection("users").document(docId)
                        .update(updates)
                        .addOnCompleteListener {
                            // Done (success OR failure) → hide notification
                            NotificationHelper.hideLocationSavingNotification(context)
                        }
                } else {
                    Log.w("LocationHelper", "lastLocation null; no update")
                    NotificationHelper.hideLocationSavingNotification(context)
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationHelper", "Failed to get last location", e)
                NotificationHelper.hideLocationSavingNotification(context)
            }
    }
}
