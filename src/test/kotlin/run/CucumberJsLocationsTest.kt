package com.wulfindustries.plugins.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CucumberJsLocationsTest {
    @Test
    fun urls() {
        assertEquals("file://C:/p/a.feature", CucumberJsLocations.url("C:\\p\\a.feature"))
        assertEquals("file://C:/p/a.feature:7", CucumberJsLocations.url("C:\\p\\a.feature", 7))
        assertEquals("file://C:/p/a.feature:7:1", CucumberJsLocations.url("C:\\p\\a.feature", 7, 1))
        assertEquals("file://C:/p/a.feature", CucumberJsLocations.url("C:\\p\\a.feature", null, 1))
    }

    @Test
    fun parseIgnoresTheColumn() {
        assertEquals("C:/p/a.feature" to null, CucumberJsLocations.parse("file://C:/p/a.feature"))
        assertEquals("C:/p/a.feature" to 7, CucumberJsLocations.parse("file://C:/p/a.feature:7"))
        assertEquals("C:/p/a.feature" to 7, CucumberJsLocations.parse("file://C:/p/a.feature:7:1"))
        assertNull(CucumberJsLocations.parse("java:suite://Foo"))
    }
}
