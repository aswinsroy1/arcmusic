package com.aeswox.arcmusic.sharing

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class NfcShareService : HostApduService() {

    companion object {
        private const val TAG = "NfcShareService"
        var currentToken: String? = null
        
        // Select APDU instruction
        private val SELECT_APDU_HEADER = byteArrayOf(
            0x00.toByte(), // CLA
            0xA4.toByte(), // INS
            0x04.toByte(), // P1
            0x00.toByte()  // P2
        )
        // AID matching the XML
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        
        // Success response
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())
        // Failure response
        private val FAILURE_SW = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: Received APDU")
        
        // Check if it's a SELECT APDU command
        if (commandApdu.size >= SELECT_APDU_HEADER.size + AID.size) {
            var isSelect = true
            for (i in SELECT_APDU_HEADER.indices) {
                if (commandApdu[i] != SELECT_APDU_HEADER[i]) {
                    isSelect = false
                    break
                }
            }
            
            if (isSelect) {
                val token = currentToken
                if (token != null) {
                    val tokenBytes = token.toByteArray(Charsets.UTF_8)
                    return tokenBytes + SUCCESS_SW
                }
            }
        }
        
        return FAILURE_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: reason = $reason")
    }
}
