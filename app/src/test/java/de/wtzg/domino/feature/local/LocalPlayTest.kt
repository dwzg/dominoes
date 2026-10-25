package de.wtzg.domino.feature.local

import de.wtzg.domino.data.local.LocalInvite
import de.wtzg.domino.data.local.isLocalNetworkAddress
import de.wtzg.domino.data.local.localInviteFrom
import de.wtzg.domino.data.local.localServerFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlayTest {
    @Test
    fun privateNetworksAreLocal() {
        assertTrue(isLocalNetworkAddress("http://192.168.1.5:8077"))
        assertTrue(isLocalNetworkAddress("http://10.0.0.12:8077"))
        assertTrue(isLocalNetworkAddress("http://172.20.10.2:8077"))
        assertFalse("public address", isLocalNetworkAddress("http://8.8.8.8:8077"))
        assertFalse("outside 172.16-31", isLocalNetworkAddress("http://172.32.0.1:8077"))
        assertFalse("https is an online server", isLocalNetworkAddress("https://192.168.1.5"))
        assertFalse("names aren't local addresses", isLocalNetworkAddress("http://dominoes.example.com"))
        assertFalse(isLocalNetworkAddress("http://192.168.1.300"))
        assertFalse(isLocalNetworkAddress("nonsense"))
    }

    @Test
    fun readsInvitesToGamesOnAPhone() {
        assertEquals(LocalInvite("http://192.168.1.5:8077", "ABC234"), localInviteFrom("http://192.168.1.5:8077/join/abc234"))
        assertEquals(LocalInvite("http://10.0.0.2:41234", "XYZ789"), localInviteFrom(" http://10.0.0.2:41234/join/XYZ789/ \n"))
        assertNull("online server", localInviteFrom("https://dominoes.example.com/join/ABC234"))
        assertNull("public address", localInviteFrom("http://8.8.8.8:8077/join/ABC234"))
        assertNull("wrong code length", localInviteFrom("http://192.168.1.5:8077/join/ABC"))
        assertNull(localInviteFrom("ABC234"))
    }

    @Test
    fun typedAddressesGetThePortIfMissing() {
        assertEquals("http://192.168.1.5:8077", localServerFrom("192.168.1.5"))
        assertEquals("http://192.168.1.5:41234", localServerFrom("192.168.1.5:41234"))
        assertEquals("http://192.168.1.5:8077", localServerFrom("http://192.168.1.5:8077/"))
        assertNull(localServerFrom("dominoes.example.com"))
        assertNull(localServerFrom(""))
    }
}
