package io.github.eightbrows.appwatch.data.db

import io.github.eightbrows.appwatch.data.record.AppChange
import io.github.eightbrows.appwatch.data.record.PolicyState
import io.github.eightbrows.appwatch.data.record.TrackedApp

// 表の行（Room）と、記録の形（data.record）の変換。

fun AppCurrentEntity.toTracked() = TrackedApp(
    packageName = packageName,
    name = name,
    versionName = versionName,
    versionCode = versionCode,
    kind = kind,
    source = source,
    installerPackage = installerPackage,
    initiatingPackage = initiatingPackage,
    isEnabled = isEnabled,
    staticWarnings = warnings,
    signerSha256 = signerSha256,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isPresent = isPresent,
    firstSeenAt = firstSeenAt,
    addedAt = addedAt,
    removedAt = removedAt,
    reinstalledAt = reinstalledAt,
    signatureChangedAt = signatureChangedAt,
    updatedAt = updatedAt,
)

fun TrackedApp.toCurrentEntity(datasetId: String) = AppCurrentEntity(
    datasetId = datasetId,
    packageName = packageName,
    name = name,
    versionName = versionName,
    versionCode = versionCode,
    kind = kind,
    source = source,
    installerPackage = installerPackage,
    initiatingPackage = initiatingPackage,
    isEnabled = isEnabled,
    warnings = staticWarnings,
    signerSha256 = signerSha256,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isPresent = isPresent,
    firstSeenAt = firstSeenAt,
    addedAt = addedAt,
    removedAt = removedAt,
    reinstalledAt = reinstalledAt,
    signatureChangedAt = signatureChangedAt,
    updatedAt = updatedAt,
)

fun TrackedApp.toBaselineEntity(datasetId: String, capturedAt: Long) = BaselineAppEntity(
    datasetId = datasetId,
    packageName = packageName,
    capturedAt = capturedAt,
    name = name,
    versionName = versionName,
    versionCode = versionCode,
    kind = kind,
    source = source,
    installerPackage = installerPackage,
    initiatingPackage = initiatingPackage,
    isEnabled = isEnabled,
    warnings = staticWarnings,
    signerSha256 = signerSha256,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
)

fun AppChange.toEntity(datasetId: String) = AppEventEntity(
    datasetId = datasetId,
    occurredAt = occurredAt,
    type = type,
    packageName = packageName,
    name = name,
    versionName = versionName,
    versionCode = versionCode,
    oldVersionName = oldVersionName,
    oldVersionCode = oldVersionCode,
    source = source,
    isReinstall = isReinstall,
    oldStatus = oldStatus,
    newStatus = newStatus,
    reason = reason,
    oldAdminMemo = oldAdminMemo,
    newAdminMemo = newAdminMemo,
    oldReinstalledAt = oldReinstalledAt,
    oldSignatureChangedAt = oldSignatureChangedAt,
    operationId = operationId,
)

fun AppEventEntity.toChange() = AppChange(
    type = type,
    packageName = packageName,
    name = name,
    occurredAt = occurredAt,
    versionName = versionName,
    versionCode = versionCode,
    oldVersionName = oldVersionName,
    oldVersionCode = oldVersionCode,
    source = source,
    isReinstall = isReinstall,
    oldStatus = oldStatus,
    newStatus = newStatus,
    reason = reason,
    oldAdminMemo = oldAdminMemo,
    newAdminMemo = newAdminMemo,
    oldReinstalledAt = oldReinstalledAt,
    oldSignatureChangedAt = oldSignatureChangedAt,
    operationId = operationId,
)

fun AppPolicyEntity.toPolicy() = PolicyState(
    packageName = packageName,
    status = reviewStatus,
    adminMemo = adminMemo,
    isPreRegistered = isPreRegistered,
    provisionalName = provisionalName,
    statusReason = statusReason,
    updatedAt = updatedAt,
)

fun PolicyState.toEntity(datasetId: String) = AppPolicyEntity(
    datasetId = datasetId,
    packageName = packageName,
    reviewStatus = status,
    adminMemo = adminMemo,
    isPreRegistered = isPreRegistered,
    provisionalName = provisionalName,
    statusReason = statusReason,
    updatedAt = updatedAt,
)
