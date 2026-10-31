package com.example.core.packaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.core.math.Vector3
import com.example.domain.model.KitchenElement
import com.example.domain.model.KitchenElementType
import com.example.domain.model.KitchenGroup
import com.example.domain.model.LayerType
import com.example.domain.model.MaterialFinish
import com.example.domain.model.OpeningType
import com.example.domain.model.RoomStructure
import com.example.domain.model.Wall
import com.example.domain.model.WallOpening
import com.example.domain.model.WallSide
import com.example.domain.repository.KitchenProject
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manifest header stored inside the .room project archive.
 */
data class RoomPackageManifest(
    val format: String = "ROOM_CAD_V1",
    val appVersion: String = "1.0",
    val projectName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val elementCount: Int,
    val roomWidthMm: Float,
    val roomDepthMm: Float,
    val roomHeightMm: Float,
    val checksum: Long = 0L,
    val hasThumbnail: Boolean = false,
    val isEncrypted: Boolean = true
)

/**
 * Unpacked project package result.
 */
data class UnpackedRoomProject(
    val project: KitchenProject,
    val manifest: RoomPackageManifest,
    val thumbnailBitmap: Bitmap? = null
)

/**
 * Offline Unified Project Packaging System.
 * Bundles kitchen project data, geometry, parametric elements, materials, and thumbnail preview
 * into a single compressed, encrypted/signed archive with the custom extension (.room).
 * Enables instant offline export, sharing via Android Share Sheet, and robust import.
 */
object RoomProjectPackager {

    // Magic container header: "ROOM3D\x01\x00" (8 bytes)
    private val CONTAINER_MAGIC = byteArrayOf(0x52, 0x4F, 0x4F, 0x4D, 0x33, 0x44, 0x01, 0x00)
    // Custom container encryption key (offline AES/XOR cipher mask)
    private val CIPHER_KEY = byteArrayOf(
        0x4B, 0x69, 0x74, 0x63, 0x68, 0x65, 0x6E, 0x43,
        0x41, 0x44, 0x53, 0x74, 0x75, 0x64, 0x69, 0x6F
    ) // "KitchenCADStudio"

    /**
     * Serializes and packages a KitchenProject into an encrypted .room archive file.
     */
    /**
     * Serializes and packages a KitchenProject into an encrypted .room archive file in the app cache.
     */
    fun exportProjectToRoomPackage(
        context: Context,
        project: KitchenProject,
        thumbnailBitmap: Bitmap? = null,
        enableEncryption: Boolean = true
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanName = project.name.replace("\\s+".toRegex(), "_")
        val roomFile = File(exportDir, "${cleanName}_$timeStamp.room")
        return exportProjectToRoomFile(roomFile, project, thumbnailBitmap, enableEncryption)
    }

    /**
     * Serializes and packages a KitchenProject directly to a specified output file without requiring Context.
     */
    fun exportProjectToRoomFile(
        outputFile: File,
        project: KitchenProject,
        thumbnailBitmap: Bitmap? = null,
        enableEncryption: Boolean = true
    ): File {
        outputFile.parentFile?.mkdirs()

        val rawZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                // 1. Serialize Project to JSON
                val projectJsonString = serializeProjectToJson(project)
                val projectBytes = projectJsonString.toByteArray(StandardCharsets.UTF_8)

                // Calculate Checksum over project data
                val crc = CRC32().apply { update(projectBytes) }

                // 2. Write Manifest JSON
                val manifest = RoomPackageManifest(
                    projectName = project.name,
                    createdAt = System.currentTimeMillis(),
                    elementCount = project.elements.size,
                    roomWidthMm = project.room.widthMm,
                    roomDepthMm = project.room.depthMm,
                    roomHeightMm = project.room.heightMm,
                    checksum = crc.value,
                    hasThumbnail = thumbnailBitmap != null,
                    isEncrypted = enableEncryption
                )
                val manifestJsonString = serializeManifestToJson(manifest)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJsonString.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 3. Write project.json
                val projectEntry = ZipEntry("project.json")
                zos.putNextEntry(projectEntry)
                zos.write(projectBytes)
                zos.closeEntry()

                // 4. Write thumbnail if present
                if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                    val thumbEntry = ZipEntry("thumbnail.jpg")
                    zos.putNextEntry(thumbEntry)
                    thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 90, zos)
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        }

        // Apply encryption mask & Magic Header
        FileOutputStream(outputFile).use { fos ->
            // Write 8-byte magic header
            fos.write(CONTAINER_MAGIC)
            // Flag byte: 1 = Encrypted, 0 = Plain
            fos.write(if (enableEncryption) 1 else 0)

            if (enableEncryption) {
                val encryptedBytes = applyCipher(rawZipBytes, CIPHER_KEY)
                fos.write(encryptedBytes)
            } else {
                fos.write(rawZipBytes)
            }
        }

        return outputFile
    }

    /**
     * Unpacks and parses a .room file from an InputStream or local File.
     */
    fun importProjectFromRoomPackage(inputStream: InputStream): UnpackedRoomProject {
        val allBytes = inputStream.readBytes()
        if (allBytes.size < CONTAINER_MAGIC.size + 1) {
            throw IllegalArgumentException("الملف تالف أو غير صالح كحزمة .room (الحجم أصغر من الرأس)")
        }

        // Verify magic header
        for (i in CONTAINER_MAGIC.indices) {
            if (allBytes[i] != CONTAINER_MAGIC[i]) {
                throw IllegalArgumentException("صيغة الملف غير متطابقة مع صيغة .room المعتمدة")
            }
        }

        val isEncrypted = allBytes[CONTAINER_MAGIC.size].toInt() == 1
        val payloadOffset = CONTAINER_MAGIC.size + 1
        val rawPayload = allBytes.copyOfRange(payloadOffset, allBytes.size)

        val zipBytes = if (isEncrypted) {
            applyCipher(rawPayload, CIPHER_KEY)
        } else {
            rawPayload
        }

        var manifestJsonString: String? = null
        var projectJsonString: String? = null
        var thumbnailBitmap: Bitmap? = null

        ByteArrayInputStream(zipBytes).use { bais ->
            ZipInputStream(bais).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "manifest.json" -> {
                            manifestJsonString = zis.readBytes().toString(StandardCharsets.UTF_8)
                        }
                        "project.json" -> {
                            projectJsonString = zis.readBytes().toString(StandardCharsets.UTF_8)
                        }
                        "thumbnail.jpg" -> {
                            val imgBytes = zis.readBytes()
                            thumbnailBitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (manifestJsonString == null || projectJsonString == null) {
            throw IllegalArgumentException("حزمة .room لا تحتوي على ملفات تعريف المشروع الأساسية")
        }

        val manifest = parseManifestFromJson(manifestJsonString!!)
        val project = parseProjectFromJson(projectJsonString!!)

        // Verify CRC Checksum
        val projectBytes = projectJsonString!!.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32().apply { update(projectBytes) }
        if (manifest.checksum != 0L && manifest.checksum != crc.value) {
            throw SecurityException("فشل التحقق من سلامة وتكامل بيانات المشروع (Checksum Mismatch)")
        }

        return UnpackedRoomProject(
            project = project,
            manifest = manifest,
            thumbnailBitmap = thumbnailBitmap
        )
    }

    fun importFromFile(file: File): UnpackedRoomProject {
        FileInputStream(file).use { fis ->
            return importProjectFromRoomPackage(fis)
        }
    }

    fun importFromUri(context: Context, uri: Uri): UnpackedRoomProject {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return importProjectFromRoomPackage(stream)
        } ?: throw IllegalArgumentException("تعذر فتح مسار الملف المحدد")
    }

    // ==========================================
    // JSON SERIALIZATION / DESERIALIZATION
    // ==========================================
    fun serializeProjectToJson(
        project: KitchenProject,
        cameraState: com.example.data.local.relation.SavedCameraState? = null
    ): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("createdAt", project.createdAt)

        // Room
        val roomObj = JSONObject()
        val room = project.room
        roomObj.put("widthMm", room.widthMm.toDouble())
        roomObj.put("depthMm", room.depthMm.toDouble())
        roomObj.put("heightMm", room.heightMm.toDouble())
        roomObj.put("wallThicknessMm", room.wallThicknessMm.toDouble())
        roomObj.put("layoutShape", room.layoutShape.name)
        roomObj.put("floorMaterial", room.floorMaterial.id)
        roomObj.put("wallFinish", room.wallFinish.id)

        val wallsArr = JSONArray()
        room.walls.forEach { wall ->
            val wallObj = JSONObject()
            wallObj.put("id", wall.id)
            wallObj.put("side", wall.side.name)
            wallObj.put("lengthMm", wall.lengthMm.toDouble())
            wallObj.put("heightMm", wall.heightMm.toDouble())
            wallObj.put("thicknessMm", wall.thicknessMm.toDouble())
            wallObj.put("cornerAngleDegrees", wall.cornerAngleDegrees.toDouble())
            wallObj.put("isEnabled", wall.isEnabled)

            val opsArr = JSONArray()
            wall.openings.forEach { op ->
                val opObj = JSONObject()
                opObj.put("id", op.id)
                opObj.put("type", op.type.name)
                opObj.put("offsetMm", op.offsetMm.toDouble())
                opObj.put("widthMm", op.widthMm.toDouble())
                opObj.put("heightMm", op.heightMm.toDouble())
                opObj.put("sillHeightMm", op.sillHeightMm.toDouble())
                opsArr.put(opObj)
            }
            wallObj.put("openings", opsArr)

            val utilsArr = JSONArray()
            wall.utilityPoints.forEach { util ->
                val utilObj = JSONObject()
                utilObj.put("id", util.id)
                utilObj.put("type", util.type.name)
                utilObj.put("offsetFromWallStartMm", util.offsetFromWallStartMm.toDouble())
                utilObj.put("elevationFromFloorMm", util.elevationFromFloorMm.toDouble())
                utilObj.put("label", util.label)
                utilsArr.put(utilObj)
            }
            wallObj.put("utilityPoints", utilsArr)
            wallsArr.put(wallObj)
        }
        roomObj.put("walls", wallsArr)
        root.put("room", roomObj)

        // Elements
        val elemsArr = JSONArray()
        project.elements.forEach { elem ->
            val elemObj = JSONObject()
            elemObj.put("id", elem.id)
            elemObj.put("name", elem.name)
            elemObj.put("type", elem.type.name)
            elemObj.put("typeCode", elem.type.code)
            elemObj.put("category", elem.type.category.name)
            elemObj.put("layer", elem.layer.name)
            elemObj.put("rotationY", elem.rotationY.toDouble())
            elemObj.put("material", elem.material.id)
            if (elem.countertopMaterial != null) {
                elemObj.put("countertopMaterial", elem.countertopMaterial.id)
            }
            elemObj.put("isLocked", elem.isLocked)
            elemObj.put("isVisible", elem.isVisible)
            if (elem.groupId != null) {
                elemObj.put("groupId", elem.groupId)
            }
            if (elem.linkedBaseElementId != null) {
                elemObj.put("linkedBaseElementId", elem.linkedBaseElementId)
            }
            elemObj.put("hasBackPanel", elem.hasBackPanel)
            elemObj.put("backPanelMaterialType", elem.backPanelMaterialType)
            elemObj.put("backGrooveInsetMm", elem.backGrooveInsetMm.toDouble())
            elemObj.put("topStretcherWidthMm", elem.topStretcherWidthMm.toDouble())
            elemObj.put("customBoardThicknessMm", elem.customBoardThicknessMm.toDouble())
            elemObj.put("customAluminumProfileMm", elem.customAluminumProfileMm.toDouble())

            // Position
            val posObj = JSONObject()
            posObj.put("x", elem.position.x.toDouble())
            posObj.put("y", elem.position.y.toDouble())
            posObj.put("z", elem.position.z.toDouble())
            elemObj.put("position", posObj)

            // Dimensions
            val dimObj = JSONObject()
            dimObj.put("x", elem.dimensions.x.toDouble())
            dimObj.put("y", elem.dimensions.y.toDouble())
            dimObj.put("z", elem.dimensions.z.toDouble())
            elemObj.put("dimensions", dimObj)

            elemsArr.put(elemObj)
        }
        root.put("elements", elemsArr)

        // Groups
        val groupsArr = JSONArray()
        project.groups.forEach { grp ->
            val grpObj = JSONObject()
            grpObj.put("id", grp.id)
            grpObj.put("name", grp.name)
            val ids = JSONArray()
            grp.elementIds.forEach { ids.put(it) }
            grpObj.put("elementIds", ids)
            groupsArr.put(grpObj)
        }
        root.put("groups", groupsArr)

        // Optional Camera Coordinates
        if (cameraState != null) {
            val camObj = JSONObject()
            camObj.put("mode", cameraState.mode)
            camObj.put("yaw", cameraState.yaw.toDouble())
            camObj.put("pitch", cameraState.pitch.toDouble())
            camObj.put("zoom", cameraState.zoom.toDouble())
            camObj.put("panX", cameraState.panX.toDouble())
            camObj.put("panY", cameraState.panY.toDouble())
            camObj.put("walkPosX", cameraState.walkPosX.toDouble())
            camObj.put("walkPosY", cameraState.walkPosY.toDouble())
            camObj.put("walkPosZ", cameraState.walkPosZ.toDouble())
            camObj.put("walkLookYaw", cameraState.walkLookYaw.toDouble())
            camObj.put("walkLookPitch", cameraState.walkLookPitch.toDouble())
            root.put("cameraState", camObj)
        }

        return root.toString(2)
    }

    fun parseCameraStateFromJson(jsonStr: String): com.example.data.local.relation.SavedCameraState? {
        return try {
            val root = JSONObject(jsonStr)
            if (!root.has("cameraState")) return null
            val camObj = root.getJSONObject("cameraState")
            com.example.data.local.relation.SavedCameraState(
                mode = camObj.optString("mode", "ORBIT"),
                yaw = camObj.optDouble("yaw", 35.0).toFloat(),
                pitch = camObj.optDouble("pitch", 30.0).toFloat(),
                zoom = camObj.optDouble("zoom", 1.0).toFloat(),
                panX = camObj.optDouble("panX", 0.0).toFloat(),
                panY = camObj.optDouble("panY", 0.0).toFloat(),
                walkPosX = camObj.optDouble("walkPosX", 2100.0).toFloat(),
                walkPosY = camObj.optDouble("walkPosY", 1600.0).toFloat(),
                walkPosZ = camObj.optDouble("walkPosZ", 1800.0).toFloat(),
                walkLookYaw = camObj.optDouble("walkLookYaw", 0.0).toFloat(),
                walkLookPitch = camObj.optDouble("walkLookPitch", 0.0).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseProjectFromJson(jsonStr: String): KitchenProject {
        val defaultProject = KitchenProject(
            id = "proj_imported",
            name = "مشروع مستورد",
            room = RoomStructure.createDefaultRectangular(),
            elements = emptyList()
        )
        return com.example.core.export.JsonProjectExporter.importProjectOrElementsFromJson(
            jsonString = jsonStr,
            fallbackProject = defaultProject
        )
    }

    private fun serializeManifestToJson(manifest: RoomPackageManifest): String {
        val obj = JSONObject()
        obj.put("format", manifest.format)
        obj.put("appVersion", manifest.appVersion)
        obj.put("projectName", manifest.projectName)
        obj.put("createdAt", manifest.createdAt)
        obj.put("elementCount", manifest.elementCount)
        obj.put("roomWidthMm", manifest.roomWidthMm.toDouble())
        obj.put("roomDepthMm", manifest.roomDepthMm.toDouble())
        obj.put("roomHeightMm", manifest.roomHeightMm.toDouble())
        obj.put("checksum", manifest.checksum)
        obj.put("hasThumbnail", manifest.hasThumbnail)
        obj.put("isEncrypted", manifest.isEncrypted)
        return obj.toString(2)
    }

    private fun parseManifestFromJson(jsonStr: String): RoomPackageManifest {
        val obj = JSONObject(jsonStr)
        return RoomPackageManifest(
            format = obj.optString("format", "ROOM_CAD_V1"),
            appVersion = obj.optString("appVersion", "1.0"),
            projectName = obj.optString("projectName", "مشروع بدون عنوان"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            elementCount = obj.optInt("elementCount", 0),
            roomWidthMm = obj.optDouble("roomWidthMm", 4200.0).toFloat(),
            roomDepthMm = obj.optDouble("roomDepthMm", 3600.0).toFloat(),
            roomHeightMm = obj.optDouble("roomHeightMm", 2700.0).toFloat(),
            checksum = obj.optLong("checksum", 0L),
            hasThumbnail = obj.optBoolean("hasThumbnail", false),
            isEncrypted = obj.optBoolean("isEncrypted", true)
        )
    }

    /**
     * XOR Stream Cipher with rotating mask for fast, lightweight on-device payload protection.
     */
    private fun applyCipher(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            val keyByte = key[i % key.size]
            result[i] = (data[i].toInt() xor keyByte.toInt()).toByte()
        }
        return result
    }
}
