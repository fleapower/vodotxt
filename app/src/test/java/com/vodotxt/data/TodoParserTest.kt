package com.vodotxt.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TodoParserTest {

    @Test
    fun testParseBasicTask() {
        val line = "Call Mom"
        val item = TodoParser.parse(line)
        assertEquals("Call Mom", item.description)
        assertFalse(item.completed)
        assertNull(item.priority)
    }

    @Test
    fun testParsePriority() {
        val line = "(A) Call Mom"
        val item = TodoParser.parse(line)
        assertEquals('A', item.priority)
        assertEquals("Call Mom", item.description)
    }

    @Test
    fun testParseCompleted() {
        val line = "x 2026-08-25 Call Mom"
        val item = TodoParser.parse(line)
        assertTrue(item.completed)
        assertEquals(LocalDate.of(2026, 8, 25), item.completionDate)
        assertEquals("Call Mom", item.description)
    }

    @Test
    fun testParseProjectsAndContexts() {
        val line = "(B) 2026-08-25 Finish project +vodotxt @home"
        val item = TodoParser.parse(line)
        assertEquals('B', item.priority)
        assertEquals(LocalDate.of(2026, 8, 25), item.creationDate)
        assertTrue(item.projects.contains("vodotxt"))
        assertTrue(item.contexts.contains("home"))
    }

    @Test
    fun testToLine() {
        val item = TodoParser.parse("(A) 2026-08-25 Test task")
        val line = TodoParser.toLine(item)
        assertEquals("(A) 2026-08-25 Test task", line)
    }
}
