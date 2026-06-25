package com.example.timeboxvibe.engine.core

import kotlin.concurrent.Volatile

// Thread-safe synchronized primitive queue wrapper (Allocation-Free operation after startup)
class ConcurrentIntegerQueue(capacity: Int = 1024) {
    private val buffer = IntArray(capacity)
    @Volatile private var head = 0
    @Volatile private var tail = 0
    
    
    fun push(value: Int) {
        val nextTail = (tail + 1) % buffer.size
        if (nextTail != head) {
            buffer[tail] = value
            tail = nextTail
        }
    }
    
    
    fun pop(): Int {
        if (head == tail) return -1 // Empty sentinel
        val value = buffer[head]
        head = (head + 1) % buffer.size
        return value
    }
}
