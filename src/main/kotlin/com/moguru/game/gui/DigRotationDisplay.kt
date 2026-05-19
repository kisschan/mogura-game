package com.moguru.game.gui

import com.moguru.game.model.Rotation

fun rotationSelectionForPendingDig(hasPendingDig: Boolean, pendingRotation: Rotation?): Rotation =
    if (hasPendingDig) pendingRotation ?: Rotation.DEG_0 else Rotation.DEG_0
