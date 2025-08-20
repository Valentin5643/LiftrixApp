package com.example.liftrix.data.mapper

import com.example.liftrix.data.remote.dto.UserDto
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import java.time.LocalDateTime
import java.time.ZoneId

object UserMapper {
    
    fun fromFirebaseUser(firebaseUser: FirebaseUser): User {
        val now = LocalDateTime.now()
        
        // Handle email validation based on user type
        val email = firebaseUser.email ?: ""
        val isAnonymous = firebaseUser.isAnonymous
        
        // For non-anonymous users, ensure email is not blank
        // If email is missing for non-anonymous user, log warning and treat as anonymous
        val effectiveIsAnonymous = if (!isAnonymous && email.isBlank()) {
            timber.log.Timber.w("Non-anonymous Firebase user has blank email: ${firebaseUser.uid}. Treating as anonymous.")
            true
        } else {
            isAnonymous
        }
        
        return User(
            uid = firebaseUser.uid,
            email = if (effectiveIsAnonymous) "" else email,
            displayName = firebaseUser.displayName,
            photoUrl = firebaseUser.photoUrl?.toString(),
            isAnonymous = effectiveIsAnonymous,
            subscriptionTier = SubscriptionTier.FREE, // Default for new users
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false, // Default to false
            onboardingCompleted = false, // New users need onboarding
            profileVersion = 1L,
            createdAt = firebaseUser.metadata?.creationTimestamp?.let { 
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: now,
            lastSignInAt = firebaseUser.metadata?.lastSignInTimestamp?.let {
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: now,
            updatedAt = now
        )
    }
    
    fun toUserDto(user: User): UserDto {
        return UserDto(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            isAnonymous = user.isAnonymous,
            subscriptionTier = user.subscriptionTier.name.lowercase(),
            subscriptionStatus = user.subscriptionStatus.name.lowercase(),
            subscriptionExpiresAt = user.subscriptionExpiresAt?.let { expiryDate ->
                Timestamp(
                    expiryDate.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                    0
                )
            },
            premiumFeaturesEnabled = user.premiumFeaturesEnabled,
            onboardingCompleted = user.onboardingCompleted,
            profileVersion = user.profileVersion,
            createdAt = Timestamp(
                user.createdAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                0
            ),
            lastSignInAt = Timestamp(
                user.lastSignInAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                0
            ),
            updatedAt = Timestamp(
                user.updatedAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                0
            )
        )
    }
    
    fun fromUserDto(userDto: UserDto): User {
        // Handle email validation based on user type (same as fromFirebaseUser)
        val email = userDto.email
        val isAnonymous = userDto.isAnonymous
        
        // For non-anonymous users, ensure email is not blank
        // If email is missing for non-anonymous user, log warning and treat as anonymous
        val effectiveIsAnonymous = if (!isAnonymous && email.isBlank()) {
            timber.log.Timber.w("Non-anonymous UserDto has blank email: ${userDto.uid}. Treating as anonymous.")
            true
        } else {
            isAnonymous
        }
        
        return User(
            uid = userDto.uid,
            email = if (effectiveIsAnonymous) "" else email,
            displayName = userDto.displayName,
            photoUrl = userDto.photoUrl,
            isAnonymous = effectiveIsAnonymous,
            subscriptionTier = try {
                SubscriptionTier.valueOf(userDto.subscriptionTier.uppercase())
            } catch (e: IllegalArgumentException) {
                SubscriptionTier.FREE // Default fallback
            },
            subscriptionStatus = try {
                SubscriptionStatus.valueOf(userDto.subscriptionStatus.uppercase())
            } catch (e: IllegalArgumentException) {
                SubscriptionStatus.ACTIVE // Default fallback
            },
            subscriptionExpiresAt = userDto.subscriptionExpiresAt?.let { expiryTimestamp ->
                LocalDateTime.ofInstant(
                    expiryTimestamp.toDate().toInstant(),
                    ZoneId.systemDefault()
                )
            },
            premiumFeaturesEnabled = userDto.premiumFeaturesEnabled,
            onboardingCompleted = userDto.onboardingCompleted,
            profileVersion = userDto.profileVersion,
            createdAt = LocalDateTime.ofInstant(
                userDto.createdAt.toDate().toInstant(),
                ZoneId.systemDefault()
            ),
            lastSignInAt = LocalDateTime.ofInstant(
                userDto.lastSignInAt.toDate().toInstant(),
                ZoneId.systemDefault()
            ),
            updatedAt = LocalDateTime.ofInstant(
                userDto.updatedAt.toDate().toInstant(),
                ZoneId.systemDefault()
            )
        )
    }
} 