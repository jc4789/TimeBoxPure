package com.example.timeboxvibe.engine.core

// Reset-Safe Object Pool (Best for Complex UI Effects / Ripples)
interface Poolable {
    var isPoolActive: Boolean
    fun reset()
}

class ObjectPool<T : Poolable>(val maxCapacity: Int, private val factory: () -> T) {
    private val pool: Array<Any?> = Array(maxCapacity) { factory() }
    private var nextAvailable = 0

    @Suppress("UNCHECKED_CAST")
    fun obtain(): T? {
        for (i in 0 until maxCapacity) {
            val idx = (nextAvailable + i) % maxCapacity
            val instance = pool[idx] as T
            if (!instance.isPoolActive) {
                instance.reset()
                instance.isPoolActive = true
                nextAvailable = (idx + 1) % maxCapacity
                return instance
            }
        }
        return null
    }
}

// Struct of Arrays (SoA) Pattern for Zero-Allocation Entities (Mass Particles)
class ParticleSystem(val maxParticles: Int = 2048) {
    val isActive = BooleanArray(maxParticles)
    val posX = FloatArray(maxParticles)
    val posY = FloatArray(maxParticles)
    val velX = FloatArray(maxParticles)
    val velY = FloatArray(maxParticles)
    val color = IntArray(maxParticles)
    val lifeNormalized = FloatArray(maxParticles)

    var activeCount = 0

    fun hasActiveParticles(): Boolean = activeCount > 0

    private var nextAvailableIndex = 0

    fun emit(x: Float, y: Float, vx: Float, vy: Float, particleColor: Int) {
        for (i in 0 until maxParticles) {
            val idx = (nextAvailableIndex + i) % maxParticles
            if (!isActive[idx]) {
                isActive[idx] = true
                activeCount++
                posX[idx] = x
                posY[idx] = y
                velX[idx] = vx
                velY[idx] = vy
                color[idx] = particleColor
                lifeNormalized[idx] = 1.0f
                
                nextAvailableIndex = (idx + 1) % maxParticles
                return
            }
        }
    }

    fun update(deltaTime: Float) {
        for (i in 0 until maxParticles) {
            if (!isActive[i]) continue

            posX[i] += velX[i] * deltaTime
            posY[i] += velY[i] * deltaTime
            lifeNormalized[i] -= 0.05f // Decay rate

            if (lifeNormalized[i] <= 0.0f) {
                isActive[i] = false
                activeCount--
            }
        }
    }
}
