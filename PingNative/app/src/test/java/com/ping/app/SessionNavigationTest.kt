package com.ping.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionNavigationTest {
    @Test fun resumesActiveRoles() {
        assertEquals("observer", resumeScreen(true, "observer", true, false))
        assertEquals("broadcaster", resumeScreen(true, "broadcaster", false, true))
    }
    @Test fun lastRoleWinsWhenBothSessionsExist() {
        assertEquals("observer", resumeScreen(true, "observer", true, true))
        assertEquals("broadcaster", resumeScreen(true, "broadcaster", true, true))
    }
    @Test fun endedSessionsDoNotReopen() {
        assertEquals("home", resumeScreen(true, "observer", false, false))
        assertEquals("home", resumeScreen(true, "broadcaster", false, false))
        assertEquals("observer", resumeScreen(true, "broadcaster", true, false))
    }
    @Test fun requiresConfigAndSupportsExistingBroadcasts() {
        assertEquals("setup", resumeScreen(false, "observer", true, true))
        assertEquals("broadcaster", resumeScreen(true, null, false, true))
    }
}
