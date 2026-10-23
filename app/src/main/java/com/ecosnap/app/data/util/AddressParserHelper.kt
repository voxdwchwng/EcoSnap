package com.ecosnap.app.data.util

import android.location.Address

object AddressParserHelper {

    fun extractWard(address: Address): String {
        // 1. Lấy fullAddress. Nếu rỗng trả về "Chưa xác định phường"
        val fullAddress = address.getAddressLine(0) ?: ""
        if (fullAddress.isBlank()) return "Chưa xác định phường"

        // 2. Tách chuỗi theo dấu phẩy
        val parts = fullAddress.split(",").map { it.trim() }

        // 3. Tìm vị trí (index) của phần tử chứa cấp Quận / Huyện / Thị xã / Thành phố Thủ Đức / Q.
        val districtIndex = parts.indexOfFirst { part ->
            part.contains("Quận", ignoreCase = true) ||
            part.contains("Huyện", ignoreCase = true) ||
            part.contains("Thị xã", ignoreCase = true) ||
            part.contains("Thành phố Thủ Đức", ignoreCase = true) ||
            part.startsWith("Q.", ignoreCase = true) ||
            part.startsWith("Q ", ignoreCase = true)
        }

        // 4. Nếu tìm thấy districtIndex và districtIndex > 0: phần tử ngay trước districtIndex chính là Phường/Xã
        if (districtIndex > 0) {
            val rawWard = parts[districtIndex - 1]
            if (rawWard.isNotBlank() && !rawWard.contains("/")) {
                return normalizeWardName(rawWard)
            }
        }

        // 5. Fallback: Tìm kiếm từ khóa Phường, P., Xã, Thị trấn trong toàn bộ chuỗi
        val wardRegex = Regex("""(?:Phường|P\.|Xã|Thị trấn)\s+([^,]+)""", RegexOption.IGNORE_CASE)
        val match = wardRegex.find(fullAddress)
        if (match != null) {
            return normalizeWardName(match.value.trim())
        }

        val subLoc = address.subLocality
        if (!subLoc.isNullOrBlank() && (
                subLoc.contains("Phường", ignoreCase = true) ||
                subLoc.contains("Xã", ignoreCase = true) ||
                subLoc.startsWith("P.", ignoreCase = true)
            )
        ) {
            return normalizeWardName(subLoc.trim())
        }

        return "Chưa xác định phường"
    }

    fun normalizeWardName(raw: String): String {
        val trimmed = raw.trim()

        if (trimmed.startsWith("Phường", ignoreCase = true)) {
            return trimmed.replace(Regex("""^[Pp]hường\s*"""), "Phường ").trim()
        }
        if (trimmed.startsWith("Xã", ignoreCase = true)) {
            return trimmed.replace(Regex("""^[Xx]ã\s*"""), "Xã ").trim()
        }
        if (trimmed.startsWith("Thị trấn", ignoreCase = true)) {
            return trimmed.replace(Regex("""^[Tt]hị\s+trấn\s*"""), "Thị trấn ").trim()
        }
        if (trimmed.startsWith("P.", ignoreCase = true) || trimmed.startsWith("P ", ignoreCase = true)) {
            return trimmed.replace(Regex("""^[Pp][.\s]\s*"""), "Phường ").trim()
        }

        if (trimmed.all { it.isDigit() }) {
            return "Phường $trimmed"
        }

        return "Phường $trimmed"
    }
}
