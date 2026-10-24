package com.example.baget.prro.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrroPreviousCheckHashTest {

    @Test
    void calculatesSha256AsLowercaseHexForExactPreviousCheckBytes() {
        byte[] previousCheck = "abc".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PrroPreviousCheckHash.sha256Hex(previousCheck));
    }

    @Test
    void firstCheckHasAnEmptyMacValue() {
        assertEquals("", PrroPreviousCheckHash.firstCheckMacValue());
    }

    @Test
    void rejectsMissingPreviousCheckBytesInsteadOfInventingAHash() {
        assertThrows(IllegalArgumentException.class,
                () -> PrroPreviousCheckHash.sha256Hex(null));
        assertThrows(IllegalArgumentException.class,
                () -> PrroPreviousCheckHash.sha256Hex(new byte[0]));
    }

    @Test
    void hashChangesWhenAnyExactByteChanges() {
        assertEquals(false, PrroPreviousCheckHash.sha256Hex(
                        "previous".getBytes(StandardCharsets.UTF_8))
                .equals(PrroPreviousCheckHash.sha256Hex(
                        "previous ".getBytes(StandardCharsets.UTF_8))));
    }
}
