package com.example.timeboxvibe.engine.core

object TouchColliderManager {
    // 16x16 Grid alignment values scaled to virtual canvas dimensions
    // AABB (Rectangle Bounds): Use this for UI buttons, input boxes, and text fields
    fun checkAABB(touchX: Float, touchY: Float, boxX: Float, boxY: Float, boxW: Float, boxH: Float): Boolean {
        return touchX >= boxX && touchX <= boxX + boxW && touchY >= boxY && touchY <= boxY + boxH
    }

    // Circular Mahoujin hit-detection (Radius distance)
    fun checkCircle(touchX: Float, touchY: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        // Fast broad-phase AABB reject before doing multiplication
        if (!checkAABB(touchX, touchY, centerX - radius, centerY - radius, radius * 2, radius * 2)) {
            return false
        }
        
        val dx = touchX - centerX
        val dy = touchY - centerY
        // Bypasses slow sqrt() operations by comparing squared distances directly
        return (dx * dx) + (dy * dy) <= (radius * radius)
    }
}
