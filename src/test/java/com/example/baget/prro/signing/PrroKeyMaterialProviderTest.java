package com.example.baget.prro.signing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrroKeyMaterialProviderTest {

    @Test
    void keyMaterialDefensivelyCopiesPfxBytes() {
        byte[] original = new byte[] {1, 2, 3};
        PrroKeyMaterialProvider.KeyMaterial material =
                new PrroKeyMaterialProvider.KeyMaterial(original, "test-password");

        original[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, material.pfxBytes());

        byte[] returned = material.pfxBytes();
        returned[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, material.pfxBytes());
    }

    @Test
    void rejectsEmptyPfx() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrroKeyMaterialProvider.KeyMaterial(new byte[0], "password"));
    }
}
