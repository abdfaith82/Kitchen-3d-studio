package com.example.presentation.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.core.math.OBB
import com.example.core.math.Vector3
import com.example.domain.model.*
import kotlin.math.*

/**
 * Architectural 3D Visualization (ArchViz) rendering style modes for the real-time 3D viewport.
 */
enum class ArchVizRenderMode(val labelAr: String, val badgeAr: String) {
    PHOTOREAL_DAYLIGHT("واقعي PBR نهاري", "V-Ray Daylight"),
    LUXURY_NIGHT_3000K("إضاءة ليلية 3000K", "Night 3000K LED"),
    ARCHITECTURAL_CLAY("ماكيت معماري Clay", "Clay + AO Maquette"),
    TECHNICAL_XRAY("مخطط شفاف X-Ray", "CAD Wireframe X-Ray")
}

/**
 * Photorealistic 3D architectural renderer for kitchen interiors, ceiling lighting,
 * glossy floor reflections, backsplash LED washes, volumetric window sunbeams,
 * directional cast shadows, and detailed cabinet sub-components.
 */
object RealisticKitchenRenderer3D {

    /**
     * Helper to project a 3D quad onto the 2D Canvas and fill/stroke it if visible.
     */
    fun DrawScope.drawQuad3D(
        p0: Vector3,
        p1: Vector3,
        p2: Vector3,
        p3: Vector3,
        project: (Vector3) -> Pair<Offset, Float>?,
        fillColor: Color,
        strokeColor: Color? = null,
        strokeWidth: Float = 1f
    ) {
        val s0 = project(p0)?.first ?: return
        val s1 = project(p1)?.first ?: return
        val s2 = project(p2)?.first ?: return
        val s3 = project(p3)?.first ?: return

        val path = Path().apply {
            moveTo(s0.x, s0.y)
            lineTo(s1.x, s1.y)
            lineTo(s2.x, s2.y)
            lineTo(s3.x, s3.y)
            close()
        }
        drawPath(path, color = fillColor)
        if (strokeColor != null) {
            drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }

    /**
     * Helper to project a 3D line segment onto the 2D Canvas.
     */
    fun DrawScope.drawLine3D(
        a: Vector3,
        b: Vector3,
        project: (Vector3) -> Pair<Offset, Float>?,
        color: Color,
        strokeWidth: Float = 2f
    ) {
        val sa = project(a)?.first ?: return
        val sb = project(b)?.first ?: return
        drawLine(color = color, start = sa, end = sb, strokeWidth = strokeWidth)
    }

    /**
     * Converts local coordinates on an element's front face:
     * u in [-0.5, 0.5] along width (X),
     * v in [-0.5, 0.5] along height (Y),
     * depthOffsetMm outward from the front face (+Z).
     */
    private fun frontPoint(obb: OBB, u: Float, v: Float, depthOffsetMm: Float = 3f): Vector3 {
        return obb.center +
                (obb.axisX * (u * obb.dimensions.x)) +
                (obb.axisY * (v * obb.dimensions.y)) +
                (obb.axisZ * (obb.halfExtents.z + depthOffsetMm))
    }

    /**
     * Converts local coordinates on an element's top face:
     * u in [-0.5, 0.5] along width (X),
     * w in [-0.5, 0.5] along depth (Z),
     * heightOffsetMm above the top face (+Y).
     */
    private fun topPoint(obb: OBB, u: Float, w: Float, heightOffsetMm: Float = 3f): Vector3 {
        return obb.center +
                (obb.axisX * (u * obb.dimensions.x)) +
                (obb.axisY * (obb.halfExtents.y + heightOffsetMm)) +
                (obb.axisZ * (w * obb.dimensions.z))
    }

    /**
     * 1. Renders the realistic floor slab with 600x600mm porcelain tiles, Calacatta marble veins,
     * real-time planar cabinet reflections, specular reflections of ceiling/under-cabinet lights,
     * window daylight sun patches, directional cast shadows, and multi-layer soft Ambient Occlusion (AO).
     */
    fun DrawScope.renderRealisticFloor(
        room: RoomStructure,
        elements: List<KitchenElement>,
        project: (Vector3) -> Pair<Offset, Float>?,
        archVizMode: ArchVizRenderMode = ArchVizRenderMode.PHOTOREAL_DAYLIGHT
    ) {
        val rawFloorColor = Color(room.floorMaterial.colorHex)
        val floorColor = when (archVizMode) {
            ArchVizRenderMode.ARCHITECTURAL_CLAY -> Color(0xFFDAD4C8)
            ArchVizRenderMode.TECHNICAL_XRAY -> Color(0xFF0B1528)
            ArchVizRenderMode.LUXURY_NIGHT_3000K -> rawFloorColor.copy(
                red = (rawFloorColor.red * 0.62f).coerceIn(0f, 1f),
                green = (rawFloorColor.green * 0.60f).coerceIn(0f, 1f),
                blue = (rawFloorColor.blue * 0.65f).coerceIn(0f, 1f)
            )
            ArchVizRenderMode.PHOTOREAL_DAYLIGHT -> rawFloorColor
        }
        val p00 = project(Vector3(0f, 0f, 0f))
        val p10 = project(Vector3(room.widthMm, 0f, 0f))
        val p11 = project(Vector3(room.widthMm, 0f, room.depthMm))
        val p01 = project(Vector3(0f, 0f, room.depthMm))

        if (p00 != null && p10 != null && p11 != null && p01 != null) {
            val floorPath = Path().apply {
                moveTo(p00.first.x, p00.first.y)
                lineTo(p10.first.x, p10.first.y)
                lineTo(p11.first.x, p11.first.y)
                lineTo(p01.first.x, p01.first.y)
                close()
            }
            // Rich directional gradient across the floor slab
            val darkerFloor = floorColor.copy(
                red = (floorColor.red * 0.82f).coerceIn(0f, 1f),
                green = (floorColor.green * 0.82f).coerceIn(0f, 1f),
                blue = (floorColor.blue * 0.82f).coerceIn(0f, 1f)
            )
            drawPath(
                floorPath,
                brush = Brush.linearGradient(
                    colors = listOf(darkerFloor, floorColor, darkerFloor),
                    start = p00.first,
                    end = p11.first
                )
            )

            // Subtle specular sheen across glossy porcelain floor
            if (room.floorMaterial.roughness <= 0.28f && archVizMode != ArchVizRenderMode.ARCHITECTURAL_CLAY) {
                drawPath(
                    floorPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color(0xFFFFFBEB).copy(alpha = if (archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K) 0.26f else 0.20f),
                            Color(0xFFBAE6FD).copy(alpha = 0.08f)
                        ),
                        start = p00.first,
                        end = p11.first
                    )
                )
            }

            // Real-Time Planar Floor Reflections of Base & Tall Cabinets on Glossy Porcelain
            if (room.floorMaterial.roughness <= 0.30f &&
                (archVizMode == ArchVizRenderMode.PHOTOREAL_DAYLIGHT || archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K)
            ) {
                elements.filter { it.position.y <= 60f }.forEach { elem ->
                    val obb = elem.getOBB()
                    val reflColor = Color(elem.material.colorHex).copy(
                        alpha = if (archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K) 0.22f else 0.16f
                    )
                    val fBL = frontPoint(obb, -0.48f, -0.48f, 4f).copy(y = 1.2f)
                    val fBR = frontPoint(obb, 0.48f, -0.48f, 4f).copy(y = 1.2f)
                    val reflReachMm = (elem.dimensions.y * 0.28f).coerceIn(140f, 380f)
                    val fOutL = (fBL + (obb.axisZ * reflReachMm)).copy(y = 1.2f)
                    val fOutR = (fBR + (obb.axisZ * reflReachMm)).copy(y = 1.2f)
                    drawQuad3D(
                        fBL, fBR, fOutR, fOutL,
                        project = project,
                        fillColor = reflColor
                    )
                }
            }

            // 600mm Porcelain Tile Joints & Subtle Calacatta Gold/Grey Veins
            val tileGridColor = when (archVizMode) {
                ArchVizRenderMode.TECHNICAL_XRAY -> Color(0x4438BDF8)
                ArchVizRenderMode.ARCHITECTURAL_CLAY -> Color(0x2864748B)
                else -> Color(0x33475569)
            }
            val tileStep = 600f
            var gx = tileStep
            while (gx < room.widthMm) {
                drawLine3D(
                    Vector3(gx, 1f, 0f),
                    Vector3(gx, 1f, room.depthMm),
                    project,
                    tileGridColor,
                    1.2f
                )
                gx += tileStep
            }
            var gz = tileStep
            while (gz < room.depthMm) {
                drawLine3D(
                    Vector3(0f, 1f, gz),
                    Vector3(room.widthMm, 1f, gz),
                    project,
                    tileGridColor,
                    1.2f
                )
                gz += tileStep
            }

            // Calacatta Gold / Marble Veins on floor tiles (in Photoreal & Night modes)
            if ((archVizMode == ArchVizRenderMode.PHOTOREAL_DAYLIGHT || archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K) &&
                (room.floorMaterial.id.contains("marble") || room.floorMaterial.id.contains("travertine") || room.floorMaterial.roughness <= 0.22f)
            ) {
                var vx = 180f
                while (vx < room.widthMm - 360f) {
                    var vz = 140f
                    while (vz < room.depthMm - 360f) {
                        drawLine3D(
                            Vector3(vx, 1.5f, vz),
                            Vector3(vx + 340f, 1.5f, vz + 250f),
                            project,
                            Color(0x2EB48A4E), // Warm Calacatta gold vein
                            1.6f
                        )
                        drawLine3D(
                            Vector3(vx + 150f, 1.5f, vz + 110f),
                            Vector3(vx + 400f, 1.5f, vz + 80f),
                            project,
                            Color(0x2464748B),
                            1.1f
                        )
                        vz += 600f
                    }
                    vx += 600f
                }
            }

            // Window Daylight Sun Floor Patches (projected solar light shafts on the floor from any window opening)
            if (archVizMode == ArchVizRenderMode.PHOTOREAL_DAYLIGHT || archVizMode == ArchVizRenderMode.ARCHITECTURAL_CLAY) {
                room.walls.filter { it.isEnabled }.forEach { wall ->
                    wall.openings.filter { it.type == OpeningType.WINDOW }.forEach { win ->
                        val patchDepth = 1250f
                        val skewShift = 220f
                        val (s0, s1, s2, s3) = when (wall.side) {
                            WallSide.NORTH -> listOf(
                                Vector3(win.offsetMm + 40f, 1.8f, 120f),
                                Vector3((win.offsetMm + win.widthMm - 40f).coerceAtMost(room.widthMm), 1.8f, 120f),
                                Vector3((win.offsetMm + win.widthMm + skewShift).coerceAtMost(room.widthMm - 40f), 1.8f, (120f + patchDepth).coerceAtMost(room.depthMm - 40f)),
                                Vector3((win.offsetMm + skewShift).coerceAtMost(room.widthMm - 80f), 1.8f, (120f + patchDepth).coerceAtMost(room.depthMm - 40f))
                            )
                            WallSide.SOUTH -> listOf(
                                Vector3(win.offsetMm + 40f, 1.8f, room.depthMm - 120f),
                                Vector3((win.offsetMm + win.widthMm - 40f).coerceAtMost(room.widthMm), 1.8f, room.depthMm - 120f),
                                Vector3((win.offsetMm + win.widthMm - skewShift).coerceAtLeast(40f), 1.8f, (room.depthMm - 120f - patchDepth).coerceAtLeast(40f)),
                                Vector3((win.offsetMm - skewShift).coerceAtLeast(40f), 1.8f, (room.depthMm - 120f - patchDepth).coerceAtLeast(40f))
                            )
                            WallSide.WEST -> listOf(
                                Vector3(120f, 1.8f, win.offsetMm + 40f),
                                Vector3(120f, 1.8f, (win.offsetMm + win.widthMm - 40f).coerceAtMost(room.depthMm)),
                                Vector3((120f + patchDepth).coerceAtMost(room.widthMm - 40f), 1.8f, (win.offsetMm + win.widthMm + skewShift).coerceAtMost(room.depthMm - 40f)),
                                Vector3((120f + patchDepth).coerceAtMost(room.widthMm - 40f), 1.8f, (win.offsetMm + skewShift).coerceAtMost(room.depthMm - 80f))
                            )
                            WallSide.EAST -> listOf(
                                Vector3(room.widthMm - 120f, 1.8f, win.offsetMm + 40f),
                                Vector3(room.widthMm - 120f, 1.8f, (win.offsetMm + win.widthMm - 40f).coerceAtMost(room.depthMm)),
                                Vector3((room.widthMm - 120f - patchDepth).coerceAtLeast(40f), 1.8f, (win.offsetMm + win.widthMm + skewShift).coerceAtMost(room.depthMm - 40f)),
                                Vector3((room.widthMm - 120f - patchDepth).coerceAtLeast(40f), 1.8f, (win.offsetMm + skewShift).coerceAtMost(room.depthMm - 80f))
                            )
                        }
                        drawQuad3D(
                            s0, s1, s2, s3,
                            project = project,
                            fillColor = Color(0x30FFFBEB),
                            strokeColor = Color(0x22FEF08A),
                            strokeWidth = 1f
                        )
                    }
                }
            }

            // Glossy Floor Specular Reflections of Ceiling Spotlights & Warm LEDs
            if (room.floorMaterial.roughness <= 0.28f && archVizMode != ArchVizRenderMode.ARCHITECTURAL_CLAY && archVizMode != ArchVizRenderMode.TECHNICAL_XRAY) {
                val spotCols = 3
                val spotRows = 3
                val haloAlpha = if (archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K) 0x44 else 0x28
                for (r in 1..spotRows) {
                    for (c in 1..spotCols) {
                        val rx = (room.widthMm / (spotCols + 1)) * c
                        val rz = (room.depthMm / (spotRows + 1)) * r
                        val reflPt = project(Vector3(rx, 2f, rz))?.first
                        if (reflPt != null) {
                            drawCircle(
                                color = Color((haloAlpha shl 24) or 0xFEF08A),
                                radius = if (archVizMode == ArchVizRenderMode.LUXURY_NIGHT_3000K) 42f else 32f,
                                center = reflPt
                            )
                            drawCircle(
                                color = Color(0x55FFFFFF),
                                radius = 12f,
                                center = reflPt
                            )
                        }
                    }
                }
            }

            // Directional Soft Cast Shadows + Multi-Layer Soft Ambient Occlusion (AO) Contact Shadows underneath floor-standing cabinets
            elements.filter { it.position.y <= 50f }.forEach { elem ->
                val corners = elem.getFloorCorners()
                if (corners.size == 4) {
                    val cCenter = elem.position.copy(y = 2.0f)
                    // 1. Directional Sun Cast Shadow (skewed along opposite of solar light vector)
                    if (archVizMode != ArchVizRenderMode.TECHNICAL_XRAY) {
                        val shadowShiftX = (elem.dimensions.y * 0.09f).coerceIn(45f, 180f)
                        val shadowShiftZ = (elem.dimensions.y * 0.08f).coerceIn(40f, 160f)
                        val castCorners = corners.map { c ->
                            Vector3(
                                (c.x - shadowShiftX).coerceIn(0f, room.widthMm),
                                1.9f,
                                (c.z + shadowShiftZ).coerceIn(0f, room.depthMm)
                            )
                        }
                        drawQuad3D(
                            castCorners[0],
                            castCorners[1],
                            castCorners[2],
                            castCorners[3],
                            project = project,
                            fillColor = if (archVizMode == ArchVizRenderMode.ARCHITECTURAL_CLAY) Color(0x360F172A) else Color(0x28020617)
                        )
                    }

                    // 2. Outer soft penumbra shadow
                    val outerCorners = corners.map { c ->
                        val dir = (c - cCenter)
                        Vector3(c.x + dir.x * 0.08f, 2.0f, c.z + dir.z * 0.08f)
                    }
                    drawQuad3D(
                        outerCorners[0],
                        outerCorners[1],
                        outerCorners[2],
                        outerCorners[3],
                        project = project,
                        fillColor = Color(0x38020617)
                    )
                    // 3. Core contact AO shadow
                    drawQuad3D(
                        corners[0].copy(y = 2.8f),
                        corners[1].copy(y = 2.8f),
                        corners[2].copy(y = 2.8f),
                        corners[3].copy(y = 2.8f),
                        project = project,
                        fillColor = if (archVizMode == ArchVizRenderMode.ARCHITECTURAL_CLAY) Color(0x88020617) else Color(0x78020617)
                    )
                }
            }

            // Wall-Floor Baseboard Ambient Occlusion Shadow Lines
            val aoColor = if (archVizMode == ArchVizRenderMode.TECHNICAL_XRAY) Color(0x8838BDF8) else Color(0x66020617)
            drawLine3D(Vector3(0f, 3f, 0f), Vector3(room.widthMm, 3f, 0f), project, aoColor, 3.5f)
            drawLine3D(Vector3(0f, 3f, 0f), Vector3(0f, 3f, room.depthMm), project, aoColor, 3.5f)
            drawLine3D(Vector3(room.widthMm, 3f, 0f), Vector3(room.widthMm, 3f, room.depthMm), project, aoColor, 3.5f)

            drawPath(floorPath, color = if (archVizMode == ArchVizRenderMode.TECHNICAL_XRAY) Color(0xFF38BDF8) else Color(0xFF475569), style = Stroke(width = 2f))
        }
    }

    /**
     * 2. Renders the gypsum drop ceiling, recessed warm spotlights, linear LED channels,
     * volumetric window sunbeams, vertical corner Ambient Occlusion, and Calacatta marble backsplash cladding with warm 3000K LED wash.
     */
    fun DrawScope.renderCeilingAndBacksplashLighting(
        room: RoomStructure,
        elements: List<KitchenElement>,
        project: (Vector3) -> Pair<Offset, Float>?,
        archVizMode: ArchVizRenderMode = ArchVizRenderMode.PHOTOREAL_DAYLIGHT
    ) {
        val h = room.heightMm
        val w = room.widthMm
        val d = room.depthMm

        // Vertical Wall-Corner Ambient Occlusion (AO) creases for realistic room depth
        drawLine3D(Vector3(2f, 0f, 2f), Vector3(2f, h, 2f), project, Color(0x550F172A), 4f)
        drawLine3D(Vector3(w - 2f, 0f, 2f), Vector3(w - 2f, h, 2f), project, Color(0x550F172A), 4f)

        // A. Calacatta Marble Backsplash Slab (between y=850mm and y=1450mm on North, West, East walls)
        val backsplashBottom = 850f
        val backsplashTop = 1450f

        // North Wall Backsplash Slab
        drawQuad3D(
            Vector3(0f, backsplashBottom, 4f),
            Vector3(w, backsplashBottom, 4f),
            Vector3(w, backsplashTop, 4f),
            Vector3(0f, backsplashTop, 4f),
            project = project,
            fillColor = Color(0xFF1F242D).copy(alpha = 0.88f),
            strokeColor = Color(0x6694A3B8),
            strokeWidth = 1.2f
        )

        // Porcelain / Calacatta Gold Diagonal Veining on North Backsplash
        var bx = 220f
        while (bx < w - 350f) {
            drawLine3D(
                Vector3(bx, backsplashBottom + 40f, 5.5f),
                Vector3(bx + 290f, backsplashTop - 50f, 5.5f),
                project,
                Color(0x44D4AF37), // Warm gold Calacatta vein
                1.6f
            )
            drawLine3D(
                Vector3(bx + 110f, backsplashBottom + 120f, 5.5f),
                Vector3(bx + 340f, backsplashTop - 160f, 5.5f),
                project,
                Color(0x33CBD5E1),
                1.0f
            )
            bx += 520f
        }

        // West Wall Backsplash Slab + Veining
        drawQuad3D(
            Vector3(4f, backsplashBottom, 0f),
            Vector3(4f, backsplashBottom, d),
            Vector3(4f, backsplashTop, d),
            Vector3(4f, backsplashTop, 0f),
            project = project,
            fillColor = Color(0xFF1B2028).copy(alpha = 0.84f),
            strokeColor = Color(0x5594A3B8),
            strokeWidth = 1.1f
        )
        var bz = 220f
        while (bz < d - 350f) {
            drawLine3D(
                Vector3(5.5f, backsplashBottom + 50f, bz),
                Vector3(5.5f, backsplashTop - 60f, bz + 280f),
                project,
                Color(0x3CD4AF37),
                1.4f
            )
            bz += 540f
        }

        // East Wall Backsplash Slab + Veining
        drawQuad3D(
            Vector3(w - 4f, backsplashBottom, 0f),
            Vector3(w - 4f, backsplashBottom, d),
            Vector3(w - 4f, backsplashTop, d),
            Vector3(w - 4f, backsplashTop, 0f),
            project = project,
            fillColor = Color(0xFF1B2028).copy(alpha = 0.84f),
            strokeColor = Color(0x5594A3B8),
            strokeWidth = 1.1f
        )
        var ez = 240f
        while (ez < d - 350f) {
            drawLine3D(
                Vector3(w - 5.5f, backsplashBottom + 45f, ez),
                Vector3(w - 5.5f, backsplashTop - 55f, ez + 270f),
                project,
                Color(0x3CD4AF37),
                1.4f
            )
            drawLine3D(
                Vector3(w - 5.5f, backsplashBottom + 130f, ez + 80f),
                Vector3(w - 5.5f, backsplashTop - 140f, ez + 310f),
                project,
                Color(0x2ECBD5E1),
                1.0f
            )
            ez += 540f
        }

        // Global Illumination (GI) Soft Warm Indirect Bounce at Wall-Ceiling & Backsplash Junctions
        drawLine3D(Vector3(0f, backsplashBottom + 2f, 6f), Vector3(w, backsplashBottom + 2f, 6f), project, Color(0x44FDE68A), 2.4f)
        drawLine3D(Vector3(6f, backsplashBottom + 2f, 0f), Vector3(6f, backsplashBottom + 2f, d), project, Color(0x38FDE68A), 2.2f)

        // Warm 3000K Volumetric Under-Cabinet LED Wash on Wall behind every Wall Cabinet
        elements.filter { it.type.category == ElementCategory.WALL_CABINET }.forEach { wallCab ->
            val obb = wallCab.getOBB()
            val washTopY = wallCab.position.y - 4f
            val washBottomY = (wallCab.position.y - 520f).coerceAtLeast(850f)
            val leftBottom = obb.center - (obb.axisX * (obb.halfExtents.x * 0.96f)) - (obb.axisZ * (obb.halfExtents.z - 8f))
            val rightBottom = obb.center + (obb.axisX * (obb.halfExtents.x * 0.96f)) - (obb.axisZ * (obb.halfExtents.z - 8f))

            // Multi-layered warm 3000K volumetric wash
            drawQuad3D(
                leftBottom.copy(y = washBottomY),
                rightBottom.copy(y = washBottomY),
                rightBottom.copy(y = washTopY),
                leftBottom.copy(y = washTopY),
                project = project,
                fillColor = Color(0x38F59E0B)
            )
            drawQuad3D(
                leftBottom.copy(y = (washTopY - 220f).coerceAtLeast(850f)),
                rightBottom.copy(y = (washTopY - 220f).coerceAtLeast(850f)),
                rightBottom.copy(y = washTopY),
                leftBottom.copy(y = washTopY),
                project = project,
                fillColor = Color(0x55FDE047)
            )
            // Bright LED emitter core strip at top of backsplash
            drawQuad3D(
                leftBottom.copy(y = washTopY - 65f),
                rightBottom.copy(y = washTopY - 65f),
                rightBottom.copy(y = washTopY),
                leftBottom.copy(y = washTopY),
                project = project,
                fillColor = Color(0x99FEF9C3)
            )
        }

        // B. Gypsum Ceiling Tray & Recessed LED Spotlights + Linear LED Channels
        val trayMargin = 550f
        if (w > trayMargin * 2 && d > trayMargin * 2) {
            // Central Gypsum Tray with Hidden Cove Warm LED Glow
            drawQuad3D(
                Vector3(trayMargin, h - 40f, trayMargin),
                Vector3(w - trayMargin, h - 40f, trayMargin),
                Vector3(w - trayMargin, h - 40f, d - trayMargin),
                Vector3(trayMargin, h - 40f, d - trayMargin),
                project = project,
                fillColor = Color(0x28F8FAFC),
                strokeColor = Color(0xCCFEF08A), // Hidden cove LED strip glow
                strokeWidth = 3.5f
            )

            // Geometric Linear LED Ceiling Strip Channels
            val linMargin = trayMargin + 220f
            if (w > linMargin * 2 && d > linMargin * 2) {
                drawLine3D(
                    Vector3(linMargin, h - 42f, linMargin),
                    Vector3(w - linMargin, h - 42f, linMargin),
                    project,
                    Color(0xEEFFFFFF),
                    3.8f
                )
                drawLine3D(
                    Vector3(w - linMargin, h - 42f, linMargin),
                    Vector3(w - linMargin, h - 42f, d - linMargin),
                    project,
                    Color(0xEEFFFFFF),
                    3.8f
                )
                drawLine3D(
                    Vector3(w - linMargin, h - 42f, d - linMargin),
                    Vector3(linMargin, h - 42f, d - linMargin),
                    project,
                    Color(0xEEFFFFFF),
                    3.8f
                )
                drawLine3D(
                    Vector3(linMargin, h - 42f, d - linMargin),
                    Vector3(linMargin, h - 42f, linMargin),
                    project,
                    Color(0xEEFFFFFF),
                    3.8f
                )
            }
        }

        // Perimeter Recessed Downlight Spotlights (سبوتات الإضاءة الغاطسة بالسقف)
        val spotPositions = listOf(
            Vector3(w * 0.20f, h - 10f, 350f),
            Vector3(w * 0.50f, h - 10f, 350f),
            Vector3(w * 0.80f, h - 10f, 350f),
            Vector3(350f, h - 10f, d * 0.35f),
            Vector3(350f, h - 10f, d * 0.70f),
            Vector3(w - 350f, h - 10f, d * 0.35f),
            Vector3(w - 350f, h - 10f, d * 0.70f),
            Vector3(w * 0.50f, h - 45f, d * 0.50f)
        )
        spotPositions.forEach { spotPos ->
            val sp = project(spotPos)?.first
            if (sp != null) {
                // Soft warm spotlight bloom halo
                drawCircle(color = Color(0x44FEF08A), radius = 22f, center = sp)
                drawCircle(color = Color(0x99FEF9C3), radius = 11f, center = sp)
                // Bright LED emitter core
                drawCircle(color = Color.White, radius = 5.5f, center = sp)
            }
        }

        // A2. Render 3D MEP Utility Points (Electrical sockets in 60cm backsplash zone, Plumbing, Gas, Ventilation)
        room.walls.filter { it.isEnabled }.forEach { wall ->
            wall.utilityPoints.forEach { util ->
                val uPos = when (wall.side) {
                    WallSide.NORTH -> Vector3(util.offsetFromWallStartMm, util.elevationFromFloorMm, 12f)
                    WallSide.SOUTH -> Vector3(util.offsetFromWallStartMm, util.elevationFromFloorMm, d - 12f)
                    WallSide.WEST -> Vector3(12f, util.elevationFromFloorMm, util.offsetFromWallStartMm)
                    WallSide.EAST -> Vector3(w - 12f, util.elevationFromFloorMm, util.offsetFromWallStartMm)
                }
                val screenPt = project(uPos)?.first
                if (screenPt != null) {
                    val utilColor = when (util.type) {
                        UtilityPointType.ELECTRICAL_SOCKET -> Color(0xFFF59E0B) // Amber socket in 60cm zone
                        UtilityPointType.PLUMBING_DRAIN -> Color(0xFF0EA5E9)   // Blue water/drain
                        UtilityPointType.GAS_OUTLET -> Color(0xFFEF4444)       // Red gas valve
                        UtilityPointType.VENTILATION_DUCT -> Color(0xFF10B981) // Green vent duct
                    }
                    drawCircle(color = Color(0xDD0F172A), radius = 9f, center = screenPt)
                    drawCircle(color = utilColor, radius = 6.5f, center = screenPt)
                    drawCircle(color = Color.White, radius = 2.5f, center = screenPt)
                }
            }
        }
    }

    /**
     * 3. Renders realistic cabinet sub-components:
     * - Dark recessed toe-kick plinth (وزرة سفلية 10 سم) with warm LED toe-kick glow
     * - Overhanging marble/granite countertop slab with Calacatta veining, specular edge & rear backsplash lip
     * - Recessed Gola aluminum profile channel under the countertop
     * - Door seams, multi-drawer fronts, handles, Gola channels
     * - Smoked black glass doors with internal glowing warm LED strips & shelves
     * - Illuminated decorative cubby niches (عمود النيش المضاء والبار المضاء)
     * - Oven glass window, control knobs, ventilation louver grilles, sink + faucet, cooktop burners
     * - Instant 3D back-panel hollowing when `elem.hasBackPanel == false`
     */
    fun DrawScope.renderCabinetRealisticDetails(
        elem: KitchenElement,
        cameraEye: Vector3,
        lightDir: Vector3,
        isSelected: Boolean,
        project: (Vector3) -> Pair<Offset, Float>?,
        isColliding: Boolean = false,
        archVizMode: ArchVizRenderMode = ArchVizRenderMode.PHOTOREAL_DAYLIGHT
    ) {
        val obb = elem.getOBB()
        val rawColor = when (archVizMode) {
            ArchVizRenderMode.ARCHITECTURAL_CLAY -> Color(0xFFE6E1D7)
            ArchVizRenderMode.TECHNICAL_XRAY -> Color(0xFF0F2942)
            ArchVizRenderMode.LUXURY_NIGHT_3000K -> {
                val c = Color(elem.material.colorHex)
                c.copy(
                    red = (c.red * 0.85f).coerceIn(0f, 1f),
                    green = (c.green * 0.82f).coerceIn(0f, 1f),
                    blue = (c.blue * 0.78f).coerceIn(0f, 1f)
                )
            }
            ArchVizRenderMode.PHOTOREAL_DAYLIGHT -> Color(elem.material.colorHex)
        }
        val baseColor = if (isColliding) {
            Color(
                red = (rawColor.red * 0.45f + 0.55f).coerceIn(0f, 1f),
                green = (rawColor.green * 0.25f + 0.08f).coerceIn(0f, 1f),
                blue = (rawColor.blue * 0.25f + 0.08f).coerceIn(0f, 1f),
                alpha = rawColor.alpha
            )
        } else {
            rawColor
        }
        val corners = obb.getCorners()
        val projectedCorners = corners.map { project(it) }
        if (!projectedCorners.all { it != null }) return
        val pts = projectedCorners.map { it!!.first }

        // Special rendering for Ceiling Chandelier & Ceiling Linear LED Panel
        if (elem.type == KitchenElementType.CEILING_CHANDELIER_MODERN) {
            renderChandelier3D(obb, project)
            return
        }
        if (elem.type == KitchenElementType.CEILING_LINEAR_LED_PANEL) {
            renderCeilingLinearPanel3D(obb, project)
            return
        }

        // A. Draw Main 6-Sided Shaded Carcass Box (Hollowing out Back Face if !elem.hasBackPanel)
        val faces = listOf(
            listOf(0, 1, 3, 2), // 0: Bottom
            listOf(4, 5, 7, 6), // 1: Top
            listOf(0, 1, 5, 4), // 2: Back (-Z)
            listOf(2, 3, 7, 6), // 3: Front (+Z)
            listOf(0, 2, 6, 4), // 4: Left (-X)
            listOf(1, 3, 7, 5)  // 5: Right (+X)
        )

        val isGlassUnit = elem.type == KitchenElementType.TALL_GLASS_VITRINE_LED ||
                elem.type == KitchenElementType.TALL_DOUBLE_VITRINE_LED ||
                elem.type == KitchenElementType.WALL_GLASS_DOOR ||
                elem.type == KitchenElementType.WALL_GLASS_BLACK_FRAME ||
                elem.type == KitchenElementType.BRIDGE_GLASS_LED_90 ||
                elem.type == KitchenElementType.WALL_CORNER_DIAGONAL_65

        val sideRawColor = elem.sidePanelMaterial?.let { Color(it.colorHex) } ?: baseColor
        val sidePanelColor = if (isColliding) baseColor else sideRawColor

        // Sort all 6 faces back-to-front by distance from cameraEye (Painter's algorithm)
        // so that rotating 360° around any cabinet always renders a complete, solid 6-sided enclosure
        // with full left/right side gable panels and a full rear back panel.
        val sortedFaces = faces.mapIndexed { faceIdx, idxList ->
            val center3D = (corners[idxList[0]] + corners[idxList[1]] + corners[idxList[2]] + corners[idxList[3]]) * 0.25f
            val distSq = (center3D - cameraEye).lengthSquared()
            Triple(faceIdx, idxList, distSq)
        }.sortedByDescending { it.third }

        sortedFaces.forEach { (faceIdx, idxList, _) ->
            // Section 4.3: If back panel is disabled (!elem.hasBackPanel), hollow out the back face in 3D immediately
            if (faceIdx == 2 && !elem.hasBackPanel) {
                val hollowFramePath = Path().apply {
                    moveTo(pts[idxList[0]].x, pts[idxList[0]].y)
                    for (i in 1 until idxList.size) {
                        lineTo(pts[idxList[i]].x, pts[idxList[i]].y)
                    }
                    close()
                }
                drawPath(hollowFramePath, color = if (isColliding) Color(0xFFEF4444) else Color(0xAA38BDF8), style = Stroke(width = 2.4f))
                return@forEach
            }

            val faceCenter = (corners[idxList[0]] + corners[idxList[1]] + corners[idxList[2]] + corners[idxList[3]]) * 0.25f
            // Outward-pointing normal away from OBB center
            val outwardNormal = (faceCenter - obb.center).normalized()

            val facePath = Path().apply {
                moveTo(pts[idxList[0]].x, pts[idxList[0]].y)
                for (i in 1 until idxList.size) {
                    lineTo(pts[idxList[i]].x, pts[idxList[i]].y)
                }
                close()
            }

            // Physically inspired Lambert + Fresnel multi-stop gradient shading on all 6 faces
            val nDotL = kotlin.math.abs(outwardNormal.dot(lightDir)).coerceIn(0.18f, 1.0f)
            val ambient = if (elem.type.category == ElementCategory.BRIDGE_CABINET) 0.48f else 0.56f
            val diffuse = 0.46f * nDotL
            val sideFactor = when (faceIdx) {
                2 -> 0.84f // Rear Back Panel (ظهر الخزانة الكامل)
                4, 5 -> if (elem.hasFullSidePanels) 0.94f else 0.86f // Full Left/Right Side Gable Panels (جنب كامل يمين ويسار)
                0 -> 0.72f // Bottom Carcass Panel
                else -> 1.0f
            }
            val topShade = ((ambient + diffuse * 1.06f) * sideFactor).coerceIn(0.28f, 1.12f)
            val bottomShade = (((ambient + diffuse) * 0.82f) * sideFactor).coerceIn(0.22f, 0.98f)
            val faceAlpha = when {
                archVizMode == ArchVizRenderMode.TECHNICAL_XRAY -> 0.28f
                isGlassUnit && faceIdx == 3 -> 0.86f
                else -> 1.0f
            }

            val activeFaceBaseColor = if (faceIdx == 4 || faceIdx == 5 || faceIdx == 2) sidePanelColor else baseColor

            val topFaceColor = activeFaceBaseColor.copy(
                red = (activeFaceBaseColor.red * topShade).coerceIn(0f, 1f),
                green = (activeFaceBaseColor.green * topShade).coerceIn(0f, 1f),
                blue = (activeFaceBaseColor.blue * topShade).coerceIn(0f, 1f),
                alpha = faceAlpha
            )
            val bottomFaceColor = activeFaceBaseColor.copy(
                red = (activeFaceBaseColor.red * bottomShade).coerceIn(0f, 1f),
                green = (activeFaceBaseColor.green * bottomShade).coerceIn(0f, 1f),
                blue = (activeFaceBaseColor.blue * bottomShade).coerceIn(0f, 1f),
                alpha = faceAlpha
            )

            drawPath(
                facePath,
                brush = Brush.linearGradient(
                    colors = listOf(topFaceColor, bottomFaceColor),
                    start = pts[idxList[2]],
                    end = pts[idxList[0]]
                )
            )

            // Full Back Panel structural groove & stretcher details when viewing from behind (ظهر كامل محكم)
            if (faceIdx == 2 && elem.hasBackPanel && archVizMode != ArchVizRenderMode.TECHNICAL_XRAY) {
                val p0 = pts[idxList[0]]
                val p1 = pts[idxList[1]]
                val p2 = pts[idxList[2]]
                val p3 = pts[idxList[3]]
                val inset = 0.06f
                val g0 = Offset(p0.x + (p2.x - p0.x) * inset, p0.y + (p2.y - p0.y) * inset)
                val g1 = Offset(p1.x + (p3.x - p1.x) * inset, p1.y + (p3.y - p1.y) * inset)
                val g2 = Offset(p2.x + (p0.x - p2.x) * inset, p2.y + (p0.y - p2.y) * inset)
                val g3 = Offset(p3.x + (p1.x - p3.x) * inset, p3.y + (p1.y - p3.y) * inset)
                val backGroovePath = Path().apply {
                    moveTo(g0.x, g0.y)
                    lineTo(g1.x, g1.y)
                    lineTo(g2.x, g2.y)
                    lineTo(g3.x, g3.y)
                    close()
                }
                drawPath(backGroovePath, color = Color(0x550F172A), style = Stroke(width = 1.6f))
            }

            // Full Left & Right Side Gable 18mm structural edge-banding highlight (جنب كامل يمين ويسار)
            if ((faceIdx == 4 || faceIdx == 5) && elem.hasFullSidePanels && archVizMode != ArchVizRenderMode.TECHNICAL_XRAY) {
                drawPath(
                    facePath,
                    color = Color(0x440F172A),
                    style = Stroke(width = 2.0f)
                )
            }

            // Red translucent collision / ergonomic clearance warning wash
            if (isColliding) {
                drawPath(facePath, color = Color(0x55EF4444))
            }

            // High-gloss specular reflection highlight for Acrylic / Glossy / Metallic doors & side gables
            if (archVizMode != ArchVizRenderMode.ARCHITECTURAL_CLAY &&
                archVizMode != ArchVizRenderMode.TECHNICAL_XRAY &&
                (elem.material.roughness <= 0.26f || elem.material.metallic >= 0.4f) &&
                (faceIdx == 3 || faceIdx == 4 || faceIdx == 5)
            ) {
                drawPath(
                    facePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (faceIdx == 3) 0.22f else 0.12f),
                            Color.Transparent,
                            Color(0xFFFFFBEB).copy(alpha = 0.08f)
                        ),
                        start = pts[idxList[0]],
                        end = pts[idxList[2]]
                    )
                )
            }

            val outlineColor = when {
                isColliding -> Color(0xFFEF4444)
                isSelected -> Color(0xFF06B6D4)
                archVizMode == ArchVizRenderMode.TECHNICAL_XRAY -> Color(0xCC38BDF8)
                archVizMode == ArchVizRenderMode.ARCHITECTURAL_CLAY -> Color(0x88475569)
                else -> Color(0x660F172A)
            }
            val outlineWidth = when {
                isColliding -> 4.2f
                isSelected -> 3.4f
                archVizMode == ArchVizRenderMode.TECHNICAL_XRAY -> 1.8f
                else -> 1.4f
            }
            drawPath(facePath, color = outlineColor, style = Stroke(width = outlineWidth))
        }

        // Draw 3D Red Collision Hazard Perimeter & Diagonal Cross on Floor/Base when colliding
        if (isColliding) {
            val fCorners = elem.getFloorCorners()
            if (fCorners.size == 4) {
                val c0 = fCorners[0].copy(y = 4f)
                val c1 = fCorners[1].copy(y = 4f)
                val c2 = fCorners[2].copy(y = 4f)
                val c3 = fCorners[3].copy(y = 4f)
                drawQuad3D(
                    c0, c1, c2, c3,
                    project = project,
                    fillColor = Color(0x44EF4444),
                    strokeColor = Color(0xFFEF4444),
                    strokeWidth = 3.8f
                )
                drawLine3D(c0, c2, project, Color(0xFFEF4444), 3.0f)
                drawLine3D(c1, c3, project, Color(0xFFEF4444), 3.0f)
            }
        }

        // B. Recessed Black Matte Toe-Kick Plinth (وزرة سفلية سوداء 10 سم) + Recessed Gola Channel for floor units
        if (elem.position.y <= 20f && elem.type.category != ElementCategory.APPLIANCE) {
            val toeRatio = (95f / elem.dimensions.y).coerceIn(0.04f, 0.14f)
            val vBottom = -0.5f
            val vToeTop = -0.5f + toeRatio
            drawQuad3D(
                frontPoint(obb, -0.495f, vBottom, 2f),
                frontPoint(obb, 0.495f, vBottom, 2f),
                frontPoint(obb, 0.495f, vToeTop, 2f),
                frontPoint(obb, -0.495f, vToeTop, 2f),
                project = project,
                fillColor = Color(0xFF0B0F17), // Deep matte black aluminum plinth
                strokeColor = Color(0xFF020617),
                strokeWidth = 1.2f
            )
            // Subtle warm LED toe-kick ambient line at top of plinth
            drawLine3D(
                frontPoint(obb, -0.48f, vToeTop, 3f),
                frontPoint(obb, 0.48f, vToeTop, 3f),
                project,
                Color(0x55FDE047),
                1.8f
            )
            // Brushed dark Gola shadow channel directly below countertop (قناة جولا العلوية)
            drawQuad3D(
                frontPoint(obb, -0.495f, 0.455f, 2.5f),
                frontPoint(obb, 0.495f, 0.455f, 2.5f),
                frontPoint(obb, 0.495f, 0.495f, 2.5f),
                frontPoint(obb, -0.495f, 0.495f, 2.5f),
                project = project,
                fillColor = Color(0xFF18181B)
            )
        }

        // C. Realistic 38mm Countertop Slab with Calacatta Veining, Polished Specular Edge & Rear Upstand Lip
        elem.countertopMaterial?.let { topMat ->
            val topColor = Color(topMat.colorHex)
            val t0 = topPoint(obb, -0.51f, -0.50f, 4f)
            val t1 = topPoint(obb, 0.51f, -0.50f, 4f)
            val t2 = topPoint(obb, 0.51f, 0.53f, 4f)  // 3% front overhang
            val t3 = topPoint(obb, -0.51f, 0.53f, 4f)

            // Countertop surface
            drawQuad3D(
                t0, t1, t2, t3,
                project = project,
                fillColor = topColor,
                strokeColor = Color(0x99CBD5E1),
                strokeWidth = 1.4f
            )

            // Calacatta Gold / Grey Veining across marble countertops + Black Galaxy Granite Crystal Flecks
            if (topMat.colorHex > 0xFF555555) {
                drawLine3D(
                    topPoint(obb, -0.42f, -0.38f, 5f),
                    topPoint(obb, 0.22f, 0.44f, 5f),
                    project,
                    Color(0x52B48A4E),
                    1.6f
                )
                drawLine3D(
                    topPoint(obb, -0.18f, -0.32f, 5f),
                    topPoint(obb, 0.04f, 0.12f, 5f),
                    project,
                    Color(0x38D4AF37),
                    1.1f
                )
                drawLine3D(
                    topPoint(obb, -0.05f, -0.15f, 5f),
                    topPoint(obb, 0.44f, 0.28f, 5f),
                    project,
                    Color(0x4264748B),
                    1.2f
                )
            } else {
                // Polished Black Galaxy Granite subtle bronze/silver crystal flecks & clearcoat reflection
                drawLine3D(
                    topPoint(obb, -0.32f, -0.20f, 5f),
                    topPoint(obb, 0.28f, 0.30f, 5f),
                    project,
                    Color(0x28FDE68A),
                    1.2f
                )
            }

            // Countertop Clearcoat Specular Reflection of Ceiling Spotlights & Under-Cabinet Warm LEDs
            val counterReflLeft = topPoint(obb, -0.34f, -0.18f, 5.2f)
            val counterReflRight = topPoint(obb, 0.34f, -0.18f, 5.2f)
            drawLine3D(counterReflLeft, counterReflRight, project, Color(0x38FEF08A), 4.5f)
            val spotReflPt = project(topPoint(obb, 0.12f, 0.08f, 5.5f))?.first
            if (spotReflPt != null) {
                drawCircle(color = Color(0x26FEF9C3), radius = 14f, center = spotReflPt)
                drawCircle(color = Color(0x55FFFFFF), radius = 5f, center = spotReflPt)
            }

            // Front 38mm thick granite/marble edge band
            val e0 = topPoint(obb, -0.51f, 0.53f, -32f)
            val e1 = topPoint(obb, 0.51f, 0.53f, -32f)
            drawQuad3D(
                e0, e1, t2, t3,
                project = project,
                fillColor = topColor.copy(
                    red = (topColor.red * 0.78f).coerceIn(0f, 1f),
                    green = (topColor.green * 0.78f).coerceIn(0f, 1f),
                    blue = (topColor.blue * 0.78f).coerceIn(0f, 1f)
                ),
                strokeColor = Color(0xBBFFFFFF), // Specular bevel highlight on granite/marble edge
                strokeWidth = 1.7f
            )

            // Rear backsplash marble upstand lip (وزرة الرخام الخلفية 5 سم) for wall-backed base cabinets
            if (elem.type.category == ElementCategory.BASE_CABINET) {
                val u0 = topPoint(obb, -0.50f, -0.48f, 4f)
                val u1 = topPoint(obb, 0.50f, -0.48f, 4f)
                val u2 = topPoint(obb, 0.50f, -0.48f, 55f)
                val u3 = topPoint(obb, -0.50f, -0.48f, 55f)
                drawQuad3D(u0, u1, u2, u3, project = project, fillColor = topColor, strokeColor = Color(0x55CBD5E1), strokeWidth = 1f)
            }

            // Subtle, Tasteful Countertop Staging Props (Minimalist Kettle, Small Indoor Plant, Glass Jars)
            if (!isColliding) {
                renderCountertopStagingProps(elem, obb, project)
            }
        }

        // D. Natural Wood Grain Fibers & Cathedral Grain Arches on Wooden Cabinets
        if (elem.material.category == FinishCategory.WOOD && !isGlassUnit) {
            val grainColorDark = Color(0x363E2723)
            val grainColorWarm = Color(0x2678350F)
            val satinHighlight = Color(0x18FEF3C7)
            for (g in -4..4) {
                val u = g * 0.102f
                drawLine3D(
                    frontPoint(obb, u, -0.37f, 2.5f),
                    frontPoint(obb, u + 0.014f, 0.44f, 2.5f),
                    project,
                    if (g % 2 == 0) grainColorDark else grainColorWarm,
                    1.25f
                )
            }
            // Subtle organic cathedral wood grain arch & satin sheen
            drawLine3D(
                frontPoint(obb, -0.22f, -0.28f, 2.8f),
                frontPoint(obb, 0.0f, 0.26f, 2.8f),
                project,
                grainColorDark,
                1.1f
            )
            drawLine3D(
                frontPoint(obb, 0.0f, 0.26f, 2.8f),
                frontPoint(obb, 0.22f, -0.28f, 2.8f),
                project,
                grainColorDark,
                1.1f
            )
            drawLine3D(
                frontPoint(obb, -0.32f, -0.20f, 3.0f),
                frontPoint(obb, 0.30f, 0.32f, 3.0f),
                project,
                satinHighlight,
                2.2f
            )
        }

        // E. Type-Specific Architectural Front & Top Details
        val handleColor = if (elem.material.category == FinishCategory.WOOD || elem.material.colorHex > 0xFF888888) {
            Color(0xFF18181B) // Sleek matte black architectural handle on oak/light doors
        } else {
            Color(0xFFE2E8F0) // Brushed chrome/champagne handle on dark doors
        }

        when (elem.type) {
            // --- GLASS VITRINES & LED WALL/BRIDGE GLASS CABINETS ---
            KitchenElementType.TALL_GLASS_VITRINE_LED,
            KitchenElementType.TALL_DOUBLE_VITRINE_LED,
            KitchenElementType.WALL_GLASS_DOOR,
            KitchenElementType.WALL_GLASS_BLACK_FRAME,
            KitchenElementType.BRIDGE_GLASS_LED_90 -> {
                val isDouble = elem.dimensions.x >= 750f
                val bottomV = if (elem.position.y <= 20f) -0.40f else -0.46f
                val topV = 0.46f

                // Outer Matte Black Aluminum Frame
                drawQuad3D(
                    frontPoint(obb, -0.46f, bottomV, 4f),
                    frontPoint(obb, 0.46f, bottomV, 4f),
                    frontPoint(obb, 0.46f, topV, 4f),
                    frontPoint(obb, -0.46f, topV, 4f),
                    project = project,
                    fillColor = Color(0xDD111827), // Dark smoked glass interior
                    strokeColor = Color(0xFF090D16), // Thick black aluminum frame
                    strokeWidth = 3.5f
                )

                // Warm Golden Interior Ambient Wash inside the Vitrine
                drawQuad3D(
                    frontPoint(obb, -0.42f, bottomV + 0.03f, 4.5f),
                    frontPoint(obb, 0.42f, bottomV + 0.03f, 4.5f),
                    frontPoint(obb, 0.42f, topV - 0.03f, 4.5f),
                    frontPoint(obb, -0.42f, topV - 0.03f, 4.5f),
                    project = project,
                    fillColor = Color(0x38F59E0B)
                )

                // Vertical Warm 3000K LED Strips inside Left & Right Frame Edges
                drawLine3D(
                    frontPoint(obb, -0.41f, bottomV + 0.03f, 5.5f),
                    frontPoint(obb, -0.41f, topV - 0.03f, 5.5f),
                    project,
                    Color(0xFFFDE047),
                    3.8f
                )
                drawLine3D(
                    frontPoint(obb, 0.41f, bottomV + 0.03f, 5.5f),
                    frontPoint(obb, 0.41f, topV - 0.03f, 5.5f),
                    project,
                    Color(0xFFFDE047),
                    3.8f
                )

                // Internal Illuminated Glass Shelves
                val shelfCount = if (elem.dimensions.y > 1500f) 4 else 2
                for (s in 1..shelfCount) {
                    val sv = bottomV + ((topV - bottomV) / (shelfCount + 1)) * s
                    drawLine3D(
                        frontPoint(obb, -0.42f, sv, 5f),
                        frontPoint(obb, 0.42f, sv, 5f),
                        project,
                        Color(0xAAFEF08A),
                        1.8f
                    )
                }

                // Center Mullion if Double Door Glass Vitrine
                if (isDouble) {
                    drawLine3D(
                        frontPoint(obb, 0f, bottomV, 6f),
                        frontPoint(obb, 0f, topV, 6f),
                        project,
                        Color(0xFF090D16),
                        3.0f
                    )
                }

                // Diagonal Glass Reflection Streak
                drawLine3D(
                    frontPoint(obb, -0.30f, bottomV + 0.12f, 6f),
                    frontPoint(obb, 0.25f, topV - 0.10f, 6f),
                    project,
                    Color(0x44FFFFFF),
                    2.2f
                )
            }

            // --- TALL ILLUMINATED 4-NICHE DISPLAY COLUMN (photo 3) ---
            KitchenElementType.TALL_NICHE_DISPLAY_COLUMN -> {
                val nicheCount = 4
                val startV = -0.38f
                val endV = 0.44f
                val band = (endV - startV) / nicheCount
                for (i in 0 until nicheCount) {
                    val nb = startV + i * band + 0.025f
                    val nt = startV + (i + 1) * band - 0.025f
                    // Recessed dark wood cubby box
                    drawQuad3D(
                        frontPoint(obb, -0.38f, nb, 4f),
                        frontPoint(obb, 0.38f, nb, 4f),
                        frontPoint(obb, 0.38f, nt, 4f),
                        frontPoint(obb, -0.38f, nt, 4f),
                        project = project,
                        fillColor = Color(0xFF3E2F20),
                        strokeColor = Color(0xFF1E1610),
                        strokeWidth = 2f
                    )
                    // Warm spotlight glow inside niche
                    drawQuad3D(
                        frontPoint(obb, -0.34f, nb + 0.01f, 5f),
                        frontPoint(obb, 0.34f, nb + 0.01f, 5f),
                        frontPoint(obb, 0.34f, nt - 0.01f, 5f),
                        frontPoint(obb, -0.34f, nt - 0.01f, 5f),
                        project = project,
                        fillColor = Color(0x55FDE047)
                    )
                    // Top LED Puck Emitter
                    val spotPt = project(frontPoint(obb, 0f, nt - 0.015f, 6f))?.first
                    if (spotPt != null) {
                        drawCircle(Color(0xFFFFFBEB), radius = 4.5f, center = spotPt)
                    }
                }
            }

            // --- PENINSULA WITH ILLUMINATED RECESSED NICHES (photo 4) ---
            KitchenElementType.PENINSULA_LIT_NICHES -> {
                // Upper staggered horizontal lit niche
                drawQuad3D(
                    frontPoint(obb, -0.25f, 0.05f, 4f),
                    frontPoint(obb, 0.42f, 0.05f, 4f),
                    frontPoint(obb, 0.42f, 0.32f, 4f),
                    frontPoint(obb, -0.25f, 0.32f, 4f),
                    project = project,
                    fillColor = Color(0xFF4A3728),
                    strokeColor = Color(0xFF1F2937),
                    strokeWidth = 2f
                )
                drawLine3D(
                    frontPoint(obb, -0.23f, 0.30f, 5.5f),
                    frontPoint(obb, 0.40f, 0.30f, 5.5f),
                    project,
                    Color(0xFFFDE047),
                    3.5f
                )

                // Lower wide horizontal lit niche
                drawQuad3D(
                    frontPoint(obb, -0.42f, -0.30f, 4f),
                    frontPoint(obb, 0.42f, -0.30f, 4f),
                    frontPoint(obb, 0.42f, -0.04f, 4f),
                    frontPoint(obb, -0.42f, -0.04f, 4f),
                    project = project,
                    fillColor = Color(0xFF4A3728),
                    strokeColor = Color(0xFF1F2937),
                    strokeWidth = 2f
                )
                drawLine3D(
                    frontPoint(obb, -0.40f, -0.06f, 5.5f),
                    frontPoint(obb, 0.40f, -0.06f, 5.5f),
                    project,
                    Color(0xFFFDE047),
                    3.5f
                )
            }

            // --- TALL OVEN & MICROWAVE TOWER WITH VENTILATION LOUVERS (photos 1, 2, 3) ---
            KitchenElementType.TALL_OVEN_TOWER -> {
                // 1. Upper 2-door storage above microwave (v = 0.16 .. 0.48)
                drawLine3D(
                    frontPoint(obb, 0f, 0.16f, 4f),
                    frontPoint(obb, 0f, 0.48f, 4f),
                    project,
                    Color(0xAA0F172A),
                    1.8f
                )
                drawLine3D(
                    frontPoint(obb, -0.48f, 0.16f, 4f),
                    frontPoint(obb, 0.48f, 0.16f, 4f),
                    project,
                    Color(0xAA0F172A),
                    1.8f
                )

                // 2. Microwave Nook / Alcove (v = -0.02 .. 0.15)
                drawQuad3D(
                    frontPoint(obb, -0.40f, -0.01f, 4f),
                    frontPoint(obb, 0.40f, -0.01f, 4f),
                    frontPoint(obb, 0.40f, 0.14f, 4f),
                    frontPoint(obb, -0.40f, 0.14f, 4f),
                    project = project,
                    fillColor = Color(0xFF1E293B),
                    strokeColor = Color(0xFF94A3B8),
                    strokeWidth = 1.5f
                )

                // 3. Built-in Oven with Black Glass & Stainless Control Panel (v = -0.30 .. -0.03)
                drawQuad3D(
                    frontPoint(obb, -0.44f, -0.30f, 4.5f),
                    frontPoint(obb, 0.44f, -0.30f, 4.5f),
                    frontPoint(obb, 0.44f, -0.03f, 4.5f),
                    frontPoint(obb, -0.44f, -0.03f, 4.5f),
                    project = project,
                    fillColor = Color(0xFF090D16),
                    strokeColor = Color(0xFFCBD5E1),
                    strokeWidth = 2f
                )
                // Oven stainless handle bar
                drawLine3D(
                    frontPoint(obb, -0.34f, -0.07f, 7f),
                    frontPoint(obb, 0.34f, -0.07f, 7f),
                    project,
                    Color(0xFFE2E8F0),
                    3.0f
                )
                // Glowing warm oven interior indicator
                drawQuad3D(
                    frontPoint(obb, -0.32f, -0.26f, 5.5f),
                    frontPoint(obb, 0.32f, -0.26f, 5.5f),
                    frontPoint(obb, 0.32f, -0.11f, 5.5f),
                    frontPoint(obb, -0.32f, -0.11f, 5.5f),
                    project = project,
                    fillColor = Color(0x44F59E0B)
                )

                // 4. Dual Black Ventilation Grilles on Bottom Panel (فتحات التهوية السوداء كما بالصور 1 و 3)
                drawQuad3D(
                    frontPoint(obb, -0.38f, -0.39f, 5f),
                    frontPoint(obb, -0.08f, -0.39f, 5f),
                    frontPoint(obb, -0.08f, -0.34f, 5f),
                    frontPoint(obb, -0.38f, -0.34f, 5f),
                    project = project,
                    fillColor = Color(0xFF090D16)
                )
                drawQuad3D(
                    frontPoint(obb, 0.08f, -0.39f, 5f),
                    frontPoint(obb, 0.38f, -0.39f, 5f),
                    frontPoint(obb, 0.38f, -0.34f, 5f),
                    frontPoint(obb, 0.08f, -0.34f, 5f),
                    project = project,
                    fillColor = Color(0xFF090D16)
                )
            }

            // --- MULTI-DRAWER BASE UNITS (2, 3, and 4 Drawers) ---
            KitchenElementType.BASE_2_DEEP_DRAWERS,
            KitchenElementType.BASE_3_DRAWERS,
            KitchenElementType.BASE_3_DRAWERS_90,
            KitchenElementType.BASE_4_DRAWERS -> {
                val drawerCount = when (elem.type) {
                    KitchenElementType.BASE_2_DEEP_DRAWERS -> 2
                    KitchenElementType.BASE_4_DRAWERS -> 4
                    else -> 3
                }
                val bottomV = -0.38f
                val topV = 0.45f
                val step = (topV - bottomV) / drawerCount

                for (i in 0 until drawerCount) {
                    val dBottom = bottomV + i * step
                    val dTop = dBottom + step
                    val dMid = (dBottom + dTop) * 0.5f

                    // Drawer horizontal reveal seam
                    if (i > 0) {
                        drawLine3D(
                            frontPoint(obb, -0.48f, dBottom, 4f),
                            frontPoint(obb, 0.48f, dBottom, 4f),
                            project,
                            Color(0xBB0F172A),
                            2.0f
                        )
                    }
                    // Centered horizontal architectural bar handle
                    drawLine3D(
                        frontPoint(obb, -0.16f, dMid, 7f),
                        frontPoint(obb, 0.16f, dMid, 7f),
                        project,
                        handleColor,
                        3.0f
                    )
                }
            }

            // --- BASE UNDER-OVEN & BUILT-IN OVEN ---
            KitchenElementType.BASE_UNDER_OVEN,
            KitchenElementType.BUILTIN_OVEN,
            KitchenElementType.BUILTIN_MICROWAVE -> {
                drawQuad3D(
                    frontPoint(obb, -0.44f, -0.26f, 5f),
                    frontPoint(obb, 0.44f, -0.26f, 5f),
                    frontPoint(obb, 0.44f, 0.38f, 5f),
                    frontPoint(obb, -0.44f, 0.38f, 5f),
                    project = project,
                    fillColor = Color(0xFF090D16),
                    strokeColor = Color(0xFFCBD5E1),
                    strokeWidth = 2f
                )
                // Stainless bar handle
                drawLine3D(
                    frontPoint(obb, -0.34f, 0.30f, 8f),
                    frontPoint(obb, 0.34f, 0.30f, 8f),
                    project,
                    Color(0xFFE2E8F0),
                    3.0f
                )
                // Crisp diagonal black-glass specular reflection + warm oven interior glow
                drawQuad3D(
                    frontPoint(obb, -0.32f, -0.18f, 5.5f),
                    frontPoint(obb, 0.32f, -0.18f, 5.5f),
                    frontPoint(obb, 0.32f, 0.18f, 5.5f),
                    frontPoint(obb, -0.32f, 0.18f, 5.5f),
                    project = project,
                    fillColor = Color(0x36F59E0B)
                )
                drawLine3D(
                    frontPoint(obb, -0.28f, -0.16f, 6.2f),
                    frontPoint(obb, 0.24f, 0.22f, 6.2f),
                    project,
                    Color(0x48FFFFFF),
                    2.2f
                )
                // Bottom ventilation grille slot
                drawQuad3D(
                    frontPoint(obb, -0.30f, -0.38f, 5f),
                    frontPoint(obb, 0.30f, -0.38f, 5f),
                    frontPoint(obb, 0.30f, -0.31f, 5f),
                    frontPoint(obb, -0.30f, -0.31f, 5f),
                    project = project,
                    fillColor = Color(0xFF111827)
                )
            }

            // --- SINK UNITS WITH BASIN, GOOSENECK FAUCET & VENT LOUVERS ---
            KitchenElementType.BASE_SINK_UNIT,
            KitchenElementType.BASE_SINK_VENTED_100 -> {
                // Double door split + handles
                drawLine3D(
                    frontPoint(obb, 0f, -0.38f, 4f),
                    frontPoint(obb, 0f, 0.45f, 4f),
                    project,
                    Color(0xBB0F172A),
                    2.0f
                )
                drawLine3D(
                    frontPoint(obb, -0.06f, 0.05f, 7f),
                    frontPoint(obb, -0.06f, 0.32f, 7f),
                    project,
                    handleColor,
                    2.8f
                )
                drawLine3D(
                    frontPoint(obb, 0.06f, 0.05f, 7f),
                    frontPoint(obb, 0.06f, 0.32f, 7f),
                    project,
                    handleColor,
                    2.8f
                )

                // Vented louver slots near top of sink doors (as in photo 4)
                drawLine3D(
                    frontPoint(obb, -0.34f, 0.36f, 5f),
                    frontPoint(obb, -0.14f, 0.36f, 5f),
                    project,
                    Color(0xFF1E293B),
                    3.0f
                )
                drawLine3D(
                    frontPoint(obb, 0.14f, 0.36f, 5f),
                    frontPoint(obb, 0.34f, 0.36f, 5f),
                    project,
                    Color(0xFF1E293B),
                    3.0f
                )

                // Recessed Dark Undermount Sink Basin on Countertop
                drawQuad3D(
                    topPoint(obb, -0.36f, -0.30f, 6f),
                    topPoint(obb, 0.36f, -0.30f, 6f),
                    topPoint(obb, 0.36f, 0.28f, 6f),
                    topPoint(obb, -0.36f, 0.28f, 6f),
                    project = project,
                    fillColor = Color(0xFF0F172A),
                    strokeColor = Color(0xFF94A3B8),
                    strokeWidth = 2f
                )

                // High-Arc Gooseneck Faucet (خلاط حوض عالي)
                val faucetBase = topPoint(obb, 0f, -0.36f, 8f)
                val faucetTop = topPoint(obb, 0f, -0.36f, 290f)
                val faucetSpout = topPoint(obb, 0f, -0.08f, 220f)
                drawLine3D(faucetBase, faucetTop, project, Color(0xFFE2E8F0), 3.5f)
                drawLine3D(faucetTop, faucetSpout, project, Color(0xFFE2E8F0), 3.2f)
            }

            // --- INDUCTION & GAS COOKTOPS ---
            KitchenElementType.COOKTOP_INDUCTION,
            KitchenElementType.COOKTOP_GAS_90 -> {
                val burnerOffsets = if (elem.type == KitchenElementType.COOKTOP_GAS_90) {
                    listOf(
                        Pair(-0.30f, -0.20f),
                        Pair(-0.30f, 0.20f),
                        Pair(0f, 0f),
                        Pair(0.30f, -0.20f),
                        Pair(0.30f, 0.20f)
                    )
                } else {
                    listOf(
                        Pair(-0.24f, -0.20f),
                        Pair(-0.24f, 0.20f),
                        Pair(0.24f, -0.20f),
                        Pair(0.24f, 0.20f)
                    )
                }
                burnerOffsets.forEach { (bx, bz) ->
                    val bp = project(topPoint(obb, bx, bz, 5f))?.first
                    if (bp != null) {
                        drawCircle(Color(0xFFEF4444).copy(alpha = 0.65f), radius = 10f, center = bp)
                        drawCircle(Color(0xFF94A3B8), radius = 11f, center = bp, style = Stroke(width = 1.8f))
                    }
                }
            }

            // --- AVENTOS HORIZONTAL LIFT-UP WALL CABINET (photo 3) ---
            KitchenElementType.WALL_LIFT_AVENTOS -> {
                // Horizontal center split
                drawLine3D(
                    frontPoint(obb, -0.48f, 0f, 4f),
                    frontPoint(obb, 0.48f, 0f, 4f),
                    project,
                    Color(0xCC0F172A),
                    2.2f
                )
                // Upper & lower wide horizontal handles
                drawLine3D(
                    frontPoint(obb, -0.18f, 0.12f, 7f),
                    frontPoint(obb, 0.18f, 0.12f, 7f),
                    project,
                    handleColor,
                    2.8f
                )
                drawLine3D(
                    frontPoint(obb, -0.18f, -0.34f, 7f),
                    frontPoint(obb, 0.18f, -0.34f, 7f),
                    project,
                    handleColor,
                    2.8f
                )
            }

            // --- REFRIGERATORS (2-Door & 4-Door French Door) ---
            KitchenElementType.REFRIGERATOR_FROST,
            KitchenElementType.REFRIGERATOR_FRENCH_DOOR -> {
                if (elem.type == KitchenElementType.REFRIGERATOR_FRENCH_DOOR) {
                    // Vertical & Horizontal Cross Split
                    drawLine3D(frontPoint(obb, 0f, -0.48f, 5f), frontPoint(obb, 0f, 0.48f, 5f), project, Color(0xFF0F172A), 2.5f)
                    drawLine3D(frontPoint(obb, -0.48f, -0.05f, 5f), frontPoint(obb, 0.48f, -0.05f, 5f), project, Color(0xFF0F172A), 2.5f)
                } else {
                    // Freezer split + ice dispenser
                    drawLine3D(frontPoint(obb, -0.48f, 0.12f, 5f), frontPoint(obb, 0.48f, 0.12f, 5f), project, Color(0xFF0F172A), 2.5f)
                }
                // Ice & Water Dispenser Recess
                drawQuad3D(
                    frontPoint(obb, -0.32f, 0.18f, 5.5f),
                    frontPoint(obb, -0.12f, 0.18f, 5.5f),
                    frontPoint(obb, -0.12f, 0.34f, 5.5f),
                    frontPoint(obb, -0.32f, 0.34f, 5.5f),
                    project = project,
                    fillColor = Color(0xFF0F172A),
                    strokeColor = Color(0xFF64748B),
                    strokeWidth = 1.2f
                )
                // Brushed Stainless Steel Anisotropic Vertical Sheen & Specular Edge Reflections
                drawLine3D(
                    frontPoint(obb, 0.22f, -0.44f, 6f),
                    frontPoint(obb, 0.22f, 0.44f, 6f),
                    project,
                    Color(0x38FFFFFF),
                    3.5f
                )
                drawLine3D(
                    frontPoint(obb, -0.04f, -0.36f, 7.5f),
                    frontPoint(obb, -0.04f, 0.36f, 7.5f),
                    project,
                    Color(0xFFE2E8F0),
                    2.6f
                )
                drawLine3D(
                    frontPoint(obb, 0.04f, -0.36f, 7.5f),
                    frontPoint(obb, 0.04f, 0.36f, 7.5f),
                    project,
                    Color(0xFFE2E8F0),
                    2.6f
                )
            }

            // --- CORNER CABINETS (UPPER & LOWER) & STANDARD SINGLE, DOUBLE & TRIPLE DOOR CABINETS ---
            else -> {
                val bottomV = if (elem.position.y <= 20f) -0.38f else -0.46f
                val topV = 0.46f

                when {
                    elem.type.isCornerUnit -> {
                        // L-Corner / Diagonal 45° Corner vertical recess & twin corner doors
                        drawLine3D(frontPoint(obb, 0f, bottomV, 4.5f), frontPoint(obb, 0f, topV, 4.5f), project, Color(0xDD0F172A), 2.6f)
                        drawLine3D(frontPoint(obb, -0.46f, topV, 4.5f), frontPoint(obb, 0f, topV - 0.06f, 4.5f), project, Color(0x8838BDF8), 1.8f)
                        drawLine3D(frontPoint(obb, 0.46f, topV, 4.5f), frontPoint(obb, 0f, topV - 0.06f, 4.5f), project, Color(0x8838BDF8), 1.8f)
                        val hV1 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.36f else 0.15f
                        val hV2 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.15f else 0.36f
                        drawLine3D(frontPoint(obb, -0.08f, hV1, 7f), frontPoint(obb, -0.08f, hV2, 7f), project, handleColor, 2.8f)
                        drawLine3D(frontPoint(obb, 0.08f, hV1, 7f), frontPoint(obb, 0.08f, hV2, 7f), project, handleColor, 2.8f)
                    }
                    elem.customDivisionsCount >= 2 -> {
                        val divs = elem.customDivisionsCount.coerceIn(2, 4)
                        for (d in 1 until divs) {
                            val u = -0.5f + (d.toFloat() / divs.toFloat())
                            drawLine3D(frontPoint(obb, u, bottomV, 4f), frontPoint(obb, u, topV, 4f), project, Color(0xBB0F172A), 2.0f)
                        }
                        val hV1 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.36f else 0.15f
                        val hV2 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.15f else 0.36f
                        drawLine3D(frontPoint(obb, -0.06f, hV1, 7f), frontPoint(obb, -0.06f, hV2, 7f), project, handleColor, 2.8f)
                        drawLine3D(frontPoint(obb, 0.06f, hV1, 7f), frontPoint(obb, 0.06f, hV2, 7f), project, handleColor, 2.8f)
                    }
                    elem.dimensions.x >= 1100f -> {
                        // 3-Door Unit (120cm)
                        drawLine3D(frontPoint(obb, -0.166f, bottomV, 4f), frontPoint(obb, -0.166f, topV, 4f), project, Color(0xBB0F172A), 1.8f)
                        drawLine3D(frontPoint(obb, 0.166f, bottomV, 4f), frontPoint(obb, 0.166f, topV, 4f), project, Color(0xBB0F172A), 1.8f)
                    }
                    elem.dimensions.x >= 750f -> {
                        // 2-Door Unit (80cm / 90cm)
                        drawLine3D(frontPoint(obb, 0f, bottomV, 4f), frontPoint(obb, 0f, topV, 4f), project, Color(0xBB0F172A), 2.0f)
                        val hV1 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.36f else 0.15f
                        val hV2 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.15f else 0.36f
                        if (elem.type.category != ElementCategory.BRIDGE_CABINET) {
                            drawLine3D(frontPoint(obb, -0.06f, hV1, 7f), frontPoint(obb, -0.06f, hV2, 7f), project, handleColor, 2.8f)
                            drawLine3D(frontPoint(obb, 0.06f, hV1, 7f), frontPoint(obb, 0.06f, hV2, 7f), project, handleColor, 2.8f)
                        }
                    }
                    else -> {
                        // Single-Door Unit (15cm / 30cm / 40cm / 60cm)
                        val hV1 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.36f else 0.12f
                        val hV2 = if (elem.type.category == ElementCategory.WALL_CABINET) -0.14f else 0.36f
                        if (elem.type.category != ElementCategory.BRIDGE_CABINET) {
                            drawLine3D(frontPoint(obb, 0.36f, hV1, 7f), frontPoint(obb, 0.36f, hV2, 7f), project, handleColor, 2.8f)
                        }
                    }
                }
            }
        }

        // F. Warm 3200K Under-Cabinet LED Strip Emitter Bar for all Wall Cabinets
        if (elem.type.category == ElementCategory.WALL_CABINET || (elem.position.y in 1200f..1700f)) {
            val ledLeft = frontPoint(obb, -0.48f, -0.49f, -40f)
            val ledRight = frontPoint(obb, 0.48f, -0.49f, -40f)
            // Soft outer golden halo
            drawLine3D(ledLeft, ledRight, project, Color(0x66F59E0B), 9.0f)
            // Bright warm LED strip core
            drawLine3D(ledLeft, ledRight, project, Color(0xFFFEF08A), 3.8f)
        }
    }

    /**
     * Renders a modern triple-pendant chandelier (نجفة سقف ثلاثية/كريستال معلقة).
     */
    private fun DrawScope.renderChandelier3D(
        obb: OBB,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val topPlateCenter = obb.center + (obb.axisY * obb.halfExtents.y)
        val topProj = project(topPlateCenter)?.first
        if (topProj != null) {
            drawCircle(Color(0xFF18181B), radius = 14f, center = topProj)
        }

        val drops = listOf(
            Triple(-140f, -obb.halfExtents.y * 0.55f, -90f),
            Triple(140f, -obb.halfExtents.y * 0.75f, -70f),
            Triple(0f, -obb.halfExtents.y * 0.95f, 110f)
        )
        drops.forEach { (dx, dy, dz) ->
            val cordStart = Vector3(topPlateCenter.x + dx * 0.4f, topPlateCenter.y, topPlateCenter.z + dz * 0.4f)
            val globePos = Vector3(obb.center.x + dx, obb.center.y + dy, obb.center.z + dz)
            drawLine3D(cordStart, globePos, project, Color(0xFF18181B), 2.0f)
            val gp = project(globePos)?.first
            if (gp != null) {
                // Warm pendant glow halo
                drawCircle(Color(0x44FDE047), radius = 24f, center = gp)
                drawCircle(Color(0xFF18181B), radius = 11f, center = gp)
                drawCircle(Color(0xFFFFFBEB), radius = 8f, center = Offset(gp.x, gp.y + 4f))
            }
        }
    }

    /**
     * Renders a geometric ceiling linear LED light frame (وحدة إضاءة سقفية خطية هندسية LED).
     */
    private fun DrawScope.renderCeilingLinearPanel3D(
        obb: OBB,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val c0 = topPoint(obb, -0.48f, -0.48f, -10f)
        val c1 = topPoint(obb, 0.48f, -0.48f, -10f)
        val c2 = topPoint(obb, 0.48f, 0.48f, -10f)
        val c3 = topPoint(obb, -0.48f, 0.48f, -10f)
        // Outer glow
        drawLine3D(c0, c1, project, Color(0x66FEF08A), 9f)
        drawLine3D(c1, c2, project, Color(0x66FEF08A), 9f)
        drawLine3D(c2, c3, project, Color(0x66FEF08A), 9f)
        drawLine3D(c3, c0, project, Color(0x66FEF08A), 9f)
        // Core white-warm LED tubes
        drawLine3D(c0, c1, project, Color.White, 3.8f)
        drawLine3D(c1, c2, project, Color.White, 3.8f)
        drawLine3D(c2, c3, project, Color.White, 3.8f)
        drawLine3D(c3, c0, project, Color.White, 3.8f)
    }

    /**
     * Renders subtle, tasteful 3D architectural countertop staging props (Minimalist Pour-Over Kettle,
     * Small Indoor Botanical Plant in a Ceramic Pot, and Tinted Glass Canister Jars with Oak Lids)
     * on free base and island countertops without altering any cabinet/appliance geometry.
     */
    private fun DrawScope.renderCountertopStagingProps(
        elem: KitchenElement,
        obb: OBB,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val type = elem.type
        // Never place staging props on top of cooktops, hoods, or upper/tall units
        if (type == KitchenElementType.COOKTOP_INDUCTION ||
            type == KitchenElementType.COOKTOP_GAS_90 ||
            type == KitchenElementType.BASE_UNDER_OVEN
        ) return

        val isIslandOrPeninsula = type == KitchenElementType.ISLAND_COUNTER ||
                type == KitchenElementType.PENINSULA_LIT_NICHES ||
                type == KitchenElementType.BREAKFAST_BAR
        val isSinkUnit = type == KitchenElementType.BASE_SINK_UNIT ||
                type == KitchenElementType.BASE_SINK_VENTED_100

        // Deterministic hash from element ID & position so staging props stay stable across frames
        val slotVariant = kotlin.math.abs(elem.id.hashCode() + (elem.position.x.toInt() / 100)) % 3

        if (isIslandOrPeninsula) {
            // Island / Peninsula Staging: Small Indoor Botanical Plant + Minimalist Kettle
            drawIndoorPlantProp3D(obb, u = -0.24f, w = 0.0f, project = project)
            drawMinimalistKettleProp3D(obb, u = 0.22f, w = 0.06f, project = project)
            return
        }

        if (isSinkUnit) {
            // On wide sink units (>= 80cm), place a small indoor plant on the right wing of the countertop
            if (elem.dimensions.x >= 800f) {
                drawIndoorPlantProp3D(obb, u = 0.39f, w = -0.18f, project = project)
            }
            return
        }

        if (type.category == ElementCategory.BASE_CABINET && elem.dimensions.x >= 450f) {
            when (slotVariant) {
                0 -> drawMinimalistKettleProp3D(obb, u = 0.16f, w = -0.08f, project = project)
                1 -> drawGlassJarsTrioProp3D(obb, u = -0.12f, w = -0.14f, project = project)
                else -> {
                    drawIndoorPlantProp3D(obb, u = -0.22f, w = -0.12f, project = project)
                    if (elem.dimensions.x >= 750f) {
                        drawGlassJarsTrioProp3D(obb, u = 0.20f, w = -0.14f, project = project)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawMinimalistKettleProp3D(
        obb: OBB,
        u: Float,
        w: Float,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val basePt = topPoint(obb, u, w, 6f)
        val topPt = topPoint(obb, u, w, 175f)
        val spoutTip = topPoint(obb, u + 0.11f, w + 0.04f, 155f)
        val handleTop = topPoint(obb, u - 0.09f, w, 145f)
        val handleBot = topPoint(obb, u - 0.09f, w, 55f)

        val pBase = project(basePt)?.first ?: return
        val pTop = project(topPt)?.first ?: return

        // Soft contact Ambient Occlusion shadow on marble countertop
        drawCircle(color = Color(0x55020617), radius = 11f, center = Offset(pBase.x + 2f, pBase.y + 3f))
        // Brushed stainless base ring
        drawCircle(color = Color(0xFFCBD5E1), radius = 9.5f, center = pBase)
        // Sleek Matte Black / Brushed Anthracite Kettle Body
        drawLine(color = Color(0xFF1E293B), start = pBase, end = pTop, strokeWidth = 16f)
        // Anisotropic specular highlight along kettle body
        drawLine(
            color = Color(0x66F8FAFC),
            start = Offset(pBase.x - 3f, pBase.y - 2f),
            end = Offset(pTop.x - 3f, pTop.y + 2f),
            strokeWidth = 3.2f
        )
        // Warm Walnut/Oak lid knob
        drawCircle(color = Color(0xFF334155), radius = 7.5f, center = pTop)
        drawCircle(color = Color(0xFFB45309), radius = 3.6f, center = Offset(pTop.x, pTop.y - 2f))
        // Gooseneck pour-over spout & minimalist handle
        drawLine3D(topPoint(obb, u + 0.04f, w, 65f), spoutTip, project, Color(0xFF94A3B8), 2.6f)
        drawLine3D(topPoint(obb, u - 0.04f, w, 145f), handleTop, project, Color(0xFF1E293B), 2.5f)
        drawLine3D(handleTop, handleBot, project, Color(0xFF1E293B), 2.5f)
    }

    private fun DrawScope.drawIndoorPlantProp3D(
        obb: OBB,
        u: Float,
        w: Float,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val potBase = topPoint(obb, u, w, 6f)
        val potRim = topPoint(obb, u, w, 115f)
        val foliageCenter = topPoint(obb, u, w, 195f)

        val pBase = project(potBase)?.first ?: return
        val pRim = project(potRim)?.first ?: return
        val pFoliage = project(foliageCenter)?.first ?: return

        // Soft contact AO shadow on countertop
        drawCircle(color = Color(0x55020617), radius = 11f, center = Offset(pBase.x + 2f, pBase.y + 3f))
        // Matte White Ceramic Pot with subtle warm shading
        drawLine(color = Color(0xFFF1F5F9), start = pBase, end = pRim, strokeWidth = 14f)
        drawCircle(color = Color(0xFFE2E8F0), radius = 7f, center = pRim)
        // Lush Multi-Toned Botanical Leaves
        drawCircle(color = Color(0xFF065F46), radius = 11f, center = Offset(pFoliage.x - 5f, pFoliage.y + 2f))
        drawCircle(color = Color(0xFF059669), radius = 10f, center = Offset(pFoliage.x + 5f, pFoliage.y + 1f))
        drawCircle(color = Color(0xFF10B981), radius = 8.5f, center = Offset(pFoliage.x, pFoliage.y - 4f))
        drawCircle(color = Color(0x55A7F3D0), radius = 4f, center = Offset(pFoliage.x - 2f, pFoliage.y - 6f))
    }

    private fun DrawScope.drawGlassJarsTrioProp3D(
        obb: OBB,
        u: Float,
        w: Float,
        project: (Vector3) -> Pair<Offset, Float>?
    ) {
        val offsets = listOf(
            Triple(-0.09f, 0.0f, 135f),
            Triple(0.0f, -0.04f, 165f),
            Triple(0.09f, 0.02f, 115f)
        )
        offsets.forEach { (du, dw, heightMm) ->
            val jBase = topPoint(obb, u + du, w + dw, 6f)
            val jTop = topPoint(obb, u + du, w + dw, heightMm)
            val pBase = project(jBase)?.first ?: return@forEach
            val pTop = project(jTop)?.first ?: return@forEach

            // Soft contact shadow
            drawCircle(color = Color(0x40020617), radius = 7.5f, center = Offset(pBase.x + 1.5f, pBase.y + 2f))
            // Translucent tinted glass body
            drawLine(color = Color(0x88CBD5E1), start = pBase, end = pTop, strokeWidth = 10.5f)
            // Crisp vertical glass specular reflection
            drawLine(
                color = Color(0xBBFFFFFF),
                start = Offset(pBase.x - 2f, pBase.y - 1f),
                end = Offset(pTop.x - 2f, pTop.y + 1f),
                strokeWidth = 2.0f
            )
            // Natural Oak wooden airtight lid
            drawCircle(color = Color(0xFFB45309), radius = 5.5f, center = pTop)
            drawCircle(color = Color(0xFFD97706), radius = 4.0f, center = Offset(pTop.x, pTop.y - 1f))
        }
    }
}
