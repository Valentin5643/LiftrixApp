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
        return User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName,
            photoUrl = firebaseUser.photoUrl?.toString(),
            isAnonymous = firebaseUser.isAnonymous,
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
        return User(
            uid = userDto.uid,
            email = userDto.email,
            displayName = userDto.displayName,
            photoUrl = userDto.photoUrl,
            isAnonymous = userDto.isAnonymous,
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