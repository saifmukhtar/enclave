package dev.saifmukhtar.enclave.crypto

import java.io.ByteArrayOutputStream

object ExifStripper {

    /**
     * Parses JPEG segment markers in-memory and drops APP1 (EXIF, 0xE1)
     * and APP13 (Photoshop IRB, 0xED) segments cleanly.
     */
    fun stripJpegExif(input: ByteArray): ByteArray {
        if (input.size < 4) return input

        // Validate JPEG SOI (FF D8)
        if (input[0] != 0xFF.toByte() || input[1] != 0xD8.toByte()) {
            return input // Not a JPEG image, return unaltered
        }

        val output = ByteArrayOutputStream()
        output.write(0xFF)
        output.write(0xD8)

        var i = 2
        val size = input.size
        while (i < size - 1) {
            // Find segment marker
            if (input[i] == 0xFF.toByte()) {
                val marker = input[i + 1].toInt() and 0xFF

                // EOI (End of Image)
                if (marker == 0xD9) {
                    output.write(0xFF)
                    output.write(0xD9)
                    i += 2
                    break
                }

                // Check for padding/restart markers which do not have payload length bytes
                if (marker == 0x00 || marker == 0xFF) {
                    output.write(0xFF)
                    output.write(input[i + 1].toInt())
                    i += 2
                    continue
                }

                // Read segment length (2 bytes, big-endian)
                if (i + 3 >= size) break
                val len = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)

                // APP1 (0xE1) = EXIF, APP13 (0xED) = Photoshop/IPTC
                if (marker == 0xE1 || marker == 0xED) {
                    // Skip the marker, the length bytes, and the payload segment
                    i += 2 + len
                } else {
                    // Copy the marker, length bytes, and payload segment cleanly
                    output.write(input, i, 2 + len)
                    i += 2 + len
                }
            } else {
                output.write(input[i].toInt())
                i++
            }
        }

        // Copy any remaining trailing visual bytes (ECS scanner data) to the output stream
        if (i < size) {
            output.write(input, i, size - i)
        }

        return output.toByteArray()
    }
}
