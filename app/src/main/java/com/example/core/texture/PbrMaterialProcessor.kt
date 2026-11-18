package com.example.core.texture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import kotlin.math.*

/**
 * Utility object for generating PBR‑ready texture maps from raw photos of
 * granite, marble and wood.
 *
 * The processing pipeline for each source bitmap is:
 * 1. **De‑lighting** – remove global illumination bias.
 * 2. **Seamless tiling** – blend opposite edges to make the texture repeatable.
 * 3. **Roughness map** – inverse of luminance (smooth surfaces → low roughness).
 * 4. **Metallic / Specular map** – for stone: very low metallic, high specular;
 *    for wood: low metallic, medium specular.
 * 5. **Normal map** – simple Sobel‑based height‑to‑normal conversion.
 *
 * All functions are pure and return new Bitmaps; the original bitmap is never mutated.
 */
object PbrMaterialProcessor {

    /** Public entry‑point – creates a full set of PBR maps for a given source image. */
    fun generatePbrMaps(
        source: Bitmap,
        materialType: MaterialType,
        tileSize: Int = 1024   // final square size of each generated map
    ): PbrMaps {
        // 1. Resize to a power‑of‑two square for easier tiling & mip‑mapping.
        val resized = source.resizeSquare(tileSize)

        // 2. De‑light (remove ambient bias)
        val deLit = deLight(resized)

        // 3. Make seamless
        val seamless = makeSeamless(deLit)

        // 4. Generate auxiliary maps
        val roughness = generateRoughnessMap(seamless)
        val metallic = generateMetallicMap(seamless, materialType)
        val normal = generateNormalMap(seamless)

        return PbrMaps(
            albedo = seamless,
            roughness = roughness,
            metallic = metallic,
            normal = normal
        )
    }

    /** --------------------------------------------------------------------- */
    /** 1️⃣ De‑lighting – subtract the image‑wide average luminance. */
    private fun deLight(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        var sum = 0L

        // Compute average luminance (Rec. 709)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = src.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                sum += (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt()
            }
        }
        val avg = sum / (width * height).toDouble()

        // Subtract average and clamp
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = src.getPixel(x, y)
                val r = (Color.red(c) - avg).coerceIn(0.0, 255.0).toInt()
                val g = (Color.green(c) - avg).coerceIn(0.0, 255.0).toInt()
                val b = (Color.blue(c) - avg).coerceIn(0.0, 255.0).toInt()
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return result
    }

    /** --------------------------------------------------------------------- */
    /** 2️⃣ Seamless tiling – blend opposite borders using a linear fall‑off. */
    private fun makeSeamless(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val result = src.copy(src.config, true)

        // Horizontal blend
        for (y in 0 until h) {
            for (x in 0 until w) {
                val left = src.getPixel(x, y)
                val right = src.getPixel((x + w / 2) % w, y)
                val blend = blendPixels(left, right, x.toFloat() / w)
                result.setPixel(x, y, blend)
            }
        }

        // Vertical blend
        val finalResult = result.copy(result.config, true)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val top = result.getPixel(x, y)
                val bottom = result.getPixel(x, (y + h / 2) % h)
                val blend = blendPixels(top, bottom, y.toFloat() / h)
                finalResult.setPixel(x, y, blend)
            }
        }
        return finalResult
    }

    /** Linear interpolation between two colors based on factor [t] (0‑1). */
    private fun blendPixels(a: Int, b: Int, t: Float): Int {
        val inv = 1f - t
        val r = (Color.red(a) * inv + Color.red(b) * t).roundToInt()
        val g = (Color.green(a) * inv + Color.green(b) * t).roundToInt()
        val bC = (Color.blue(a) * inv + Color.blue(b) * t).roundToInt()
        return Color.rgb(r, g, bC)
    }

    /** --------------------------------------------------------------------- */
    /** 3️⃣ Roughness map – inverse of luminance (smooth → dark, rough → bright). */
    private fun generateRoughnessMap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                val lum = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c))
                // Invert & clamp to [0,255]
                val rough = (255 - lum).roundToInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.argb(255, rough, rough, rough))
            }
        }
        return result
    }

    /** --------------------------------------------------------------------- */
    /** 4️⃣ Metallic / Specular map – material‑dependent. */
    private fun generateMetallicMap(src: Bitmap, type: MaterialType): Bitmap {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)

        // Simple heuristic:
        // - Granite / Marble: almost non‑metallic, but a faint specular highlight where
        //   bright particles exist.
        // - Wood: low metallic, moderate specular.
        val metallicFactor = when (type) {
            MaterialType.GRANITE, MaterialType.MARBLE -> 0.02f   // almost 0
            MaterialType.OAK -> 0.05f
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                val lum = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255f
                // Use brightness to drive a subtle specular intensity
                val spec = (lum * 0.3f + 0.1f) * metallicFactor
                val value = (spec * 255).roundToInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.argb(255, value, value, value))
            }
        }
        return result
    }

    /** --------------------------------------------------------------------- */
    /** 5️⃣ Normal map – height‑to‑normal using Sobel operator on the luminance. */
    private fun generateNormalMap(src: Bitmap, strength: Float = 2.0f): Bitmap {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // Helper to fetch luminance safely
        fun lum(x: Int, y: Int): Float {
            val px = src.getPixel(
                x.coerceIn(0, w - 1),
                y.coerceIn(0, h - 1)
            )
            return (0.2126f * Color.red(px) + 0.7152f * Color.green(px) + 0.0722f * Color.blue(px)) / 255f
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Sobel X/Y
                val gx = -lum(x - 1, y - 1) - 2 * lum(x - 1, y) - lum(x - 1, y + 1) +
                         lum(x + 1, y - 1) + 2 * lum(x + 1, y) + lum(x + 1, y + 1)
                val gy = -lum(x - 1, y - 1) - 2 * lum(x, y - 1) - lum(x + 1, y - 1) +
                         lum(x - 1, y + 1) + 2 * lum(x, y + 1) + lum(x + 1, y + 1)

                // Normal vector (X right, Y up, Z out of surface)
                var nx = -gx * strength
                var ny = -gy * strength
                var nz = 1.0f
                // Normalize
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= len
                ny /= len
                nz /= len

                // Encode to RGB (0‑255)
                val r = ((nx + 1f) * 0.5f * 255f).roundToInt().coerceIn(0, 255)
                val g = ((ny + 1f) * 0.5f * 255f).roundToInt().coerceIn(0, 255)
                val b = ((nz + 1f) * 0.5f * 255f).roundToInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return result
    }

    /** --------------------------------------------------------------------- */
    /** Helper – resize any bitmap to a square (keeping aspect ratio by cropping). */
    private fun Bitmap.resizeSquare(targetSize: Int): Bitmap {
        val size = min(width, height)
        val cropped = Bitmap.createBitmap(this, (width - size) / 2, (height - size) / 2, size, size)
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }

    /** --------------------------------------------------------------------- */
    /** Public data classes */
    data class PbrMaps(
        val albedo: Bitmap,
        val roughness: Bitmap,
        val metallic: Bitmap,
        val normal: Bitmap
    )

    enum class MaterialType { GRANITE, MARBLE, OAK }
}
